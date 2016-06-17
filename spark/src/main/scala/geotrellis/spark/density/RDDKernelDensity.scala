/*
 * Copyright (c) 2016 Azavea.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.spark.density

import geotrellis.spark._
import geotrellis.spark.tiling._

import org.apache.spark.rdd.RDD

import geotrellis.vector._
import geotrellis.proj4._
import geotrellis.raster._
//import geotrellis.raster.testkit._
import geotrellis.raster.mapalgebra.focal._
import geotrellis.raster.mapalgebra.local._
import geotrellis.raster.density._

object RDDKernelDensity {

  // TODO: CellType
  // TODO: MethodExtensions on RDD[PointFeature[Int]] and RDD[PointFeature[Double]]

  private def pointFeatureToExtent[D](kernelWidth: Double, ld: LayoutDefinition)(ptf: PointFeature[D]): Extent = {
    val p = ptf.geom
    Extent(p.x - kernelWidth * ld.cellwidth / 2,
           p.y - kernelWidth * ld.cellheight / 2,
           p.x + kernelWidth * ld.cellwidth / 2,
           p.y + kernelWidth * ld.cellheight / 2)
  }

  def pointFeatureToSpatialKey[D](kernelWidth: Double, 
                                  tl: TileLayout, 
                                  ld: LayoutDefinition,
                                  dummy: D)(ptf: PointFeature[D]): Seq[(SpatialKey, PointFeature[D])] = {
      val ptextent = pointFeatureToExtent(kernelWidth, ld)(ptf)
      val gridBounds = ld.mapTransform(ptextent)
      for ((c,r) <- gridBounds.coords;
           if r < tl.totalRows;
           if c < tl.totalCols) yield (SpatialKey(c,r), ptf)
    }

  object Adder extends LocalTileBinaryOp {
    def combine(z1: Int, z2: Int) = {
      if (isNoData(z1)) {
        z2
      } else if (isNoData(z2)) {
        z1
      } else {
        z1 + z2
      }
    }
    def combine(r1: Double, r2:Double) = {
      if (isNoData(r1)) {
        r2
      } else if (isNoData(r2)) {
        r1
      } else {
        r1 + r2
      }
    }
  }

  def apply(rdd: RDD[PointFeature[Int]], 
            ld: LayoutDefinition, 
            kern: Kernel, 
            crs: CRS)(implicit d: DummyImplicit): RDD[(SpatialKey,Tile)] with Metadata[TileLayerMetadata[SpatialKey]] = {

    val kw = 2 * kern.extent.toDouble + 1.0
    val tl = ld.tileLayout

    val ptfToSpatialKey: PointFeature[Int] => Seq[(SpatialKey,PointFeature[Int])] = pointFeatureToSpatialKey(kw, tl, ld, 0)_

    def stampPointFeature(tile: IntArrayTile, tup: (SpatialKey, PointFeature[Int])): IntArrayTile = {
      val (spatialKey, pointFeature) = tup
      val tileExtent = ld.mapTransform(spatialKey)
      val re = RasterExtent(tileExtent, tile)
      val result = tile.copy.asInstanceOf[IntArrayTile]
      KernelStamper(result, kern).stampKernel(re.mapToGrid(pointFeature.geom), pointFeature.data)
      result
    }

    def sumTiles(t1: IntArrayTile, t2: IntArrayTile): IntArrayTile = {
      Adder(t1.asInstanceOf[Tile], t2.asInstanceOf[Tile]).asInstanceOf[IntArrayTile]
    }

    val tileRdd: RDD[(SpatialKey, Tile)] =
      rdd
        .flatMap(ptfToSpatialKey)
        .mapPartitions({ partition =>
          partition.map { case (spatialKey, pointFeature) =>
            (spatialKey, (spatialKey, pointFeature))
          }
        }, preservesPartitioning = true)
        .aggregateByKey(IntArrayTile.empty(ld.tileCols, ld.tileRows))(stampPointFeature, sumTiles)
        .mapValues{ tile => tile.asInstanceOf[Tile] }

    val metadata = TileLayerMetadata(DoubleCellType,
                                     ld,
                                     ld.extent,
                                     crs,
                                     KeyBounds(SpatialKey(0,0),
                                               SpatialKey(ld.layoutCols-1,
                                                          ld.layoutRows-1)))
    ContextRDD(tileRdd, metadata)
  }

  def apply(rdd: RDD[PointFeature[Double]], 
            ld: LayoutDefinition, 
            kern: Kernel, 
            crs: CRS): RDD[(SpatialKey,Tile)] with Metadata[TileLayerMetadata[SpatialKey]] = {

    val kw = 2 * kern.extent.toDouble + 1.0
    val tl = ld.tileLayout

    val ptfToSpatialKey = pointFeatureToSpatialKey(kw, tl, ld, 0.0)_

    def sumTiles(t1: DoubleArrayTile, t2: DoubleArrayTile): DoubleArrayTile = { 
      Adder(t1, t2).asInstanceOf[DoubleArrayTile]
    }

    def stampPointFeature(tile: DoubleArrayTile, tup: (SpatialKey, PointFeature[Double])): DoubleArrayTile = {
      val (spatialKey, pointFeature) = tup
      val tileExtent = ld.mapTransform(spatialKey)
      val re = RasterExtent(tileExtent, tile)
      val result = tile.copy.asInstanceOf[DoubleArrayTile]
      KernelStamper(result, kern).stampKernelDouble(re.mapToGrid(pointFeature.geom), pointFeature.data)
      result
    }

    val tileRdd: RDD[(SpatialKey, Tile)] =
      rdd
        .flatMap(ptfToSpatialKey)
        .mapPartitions({ partition =>
          partition.map { case (spatialKey, pointFeature) =>
            (spatialKey, (spatialKey, pointFeature))
          }
        }, preservesPartitioning = true)
        .aggregateByKey(DoubleArrayTile.empty(ld.tileCols, ld.tileRows))(stampPointFeature, sumTiles)
        .mapValues{ tile: DoubleArrayTile => tile.asInstanceOf[Tile] }

    val metadata = TileLayerMetadata(DoubleCellType,
                                     ld,
                                     ld.extent,
                                     crs,
                                     KeyBounds(SpatialKey(0,0),
                                               SpatialKey(ld.layoutCols-1,
                                                          ld.layoutRows-1)))

    ContextRDD(tileRdd, metadata)
  }

}
