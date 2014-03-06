package geotrellis.feature

import com.vividsolutions.jts.{geom => jts}
import GeomFactory._

object Point {

  def apply(x: Double, y: Double): Point =
    Point(factory.createPoint(new jts.Coordinate(x, y)))

  implicit def jts2Point(geom: jts.Point): Point = apply(geom)

}

case class Point(geom: jts.Point) extends Geometry
                                     with Relatable
                                     with ZeroDimensions {

  assert(!geom.isEmpty)

  val x: Double =
    geom.getX
  val y: Double =
    geom.getY

  // -- Intersection

  def &(other: Geometry): PointOrNoResult =
    intersection(other)
  def intersection(other: Geometry): PointOrNoResult =
    geom.intersection(other.geom)

  // -- Union

  def |(g: ZeroDimensions): PointZeroDimensionsUnionResult =
    union(g)
  def union(g: ZeroDimensions): PointZeroDimensionsUnionResult =
    geom.union(g.geom)

  def |(l: Line): PointLineUnionResult =
    union(l)
  def union(l: Line): PointLineUnionResult =
    geom.union(l.geom)

  def |(p: Polygon): AtMostOneDimensionPolygonUnionResult =
    union(p)
  def union(p: Polygon): AtMostOneDimensionPolygonUnionResult =
    geom.union(p.geom)

  def |(ls: MultiLine): PointMultiLineUnionResult =
    union(ls)
  def union(ls: MultiLine): PointMultiLineUnionResult =
    geom.union(ls.geom)

  def |(ps: MultiPolygon): AtMostOneDimensionMultiPolygonUnionResult =
    union(ps)
  def union(ps: MultiPolygon): AtMostOneDimensionMultiPolygonUnionResult =
    geom.union(ps.geom)

  // -- Difference

  def -(other: Geometry): PointGeometryDifferenceResult =
    difference(other)
  def difference(other: Geometry): PointGeometryDifferenceResult =
    geom.difference(other.geom)

  // -- SymDifference

  def symDifference(p: Point): PointPointSymDifferenceResult =
    geom.symDifference(p.geom)

  def symDifference(l: Line): ZeroDimensionsLineSymDifferenceResult =
    geom.symDifference(l.geom)

  def symDifference(p: Polygon): ZeroDimensionsPolygonSymDifferenceResult =
    geom.symDifference(p.geom)

  def symDifference(ps: MultiPoint): ZeroDimensionsMultiPointSymDifferenceResult =
    geom.symDifference(ps.geom)

  def symDifference(ls: MultiLine): ZeroDimensionsMultiLineSymDifferenceResult =
    geom.symDifference(ls.geom)

  def symDifference(ps: MultiPolygon): ZeroDimensionsMultiPolygonSymDifferenceResult =
    geom.symDifference(ps.geom)

  // -- Buffer

  def buffer(d: Double): Polygon = {
    val result = geom.buffer(d)
    result match {
      case p: jts.Polygon => Polygon(p)
      case _ =>
        sys.error(s"Unexpected result for Point buffer: ${result.getGeometryType}")
    }
  }

  // -- Predicates

  def contains(g: ZeroDimensions): Boolean =
    geom.contains(g.geom)

  def coveredBy(g: Geometry): Boolean =
    geom.coveredBy(g.geom)

  def covers(p: Point): Boolean =
    geom.covers(p.geom)

  def touches(g: AtLeastOneDimension): Boolean =
    geom.touches(g.geom)

  def within(g: Geometry): Boolean =
    geom.within(g.geom)

}
