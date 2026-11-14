package com.tacnav.sapper

import com.tacnav.sapper.domain.Geodesy
import com.tacnav.sapper.domain.Geometry
import com.tacnav.sapper.domain.Plane
import com.tacnav.sapper.domain.Shapes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {
    @Test
    fun circleDistanceIsToTheEdgeAndZeroInside() {
        val circle = Geometry.Circle(Fields.at(90.0, 20.0).latitude, Fields.at(90.0, 20.0).longitude, 5.0)
        val outside = Plane.view(Fields.HOME, circle)
        assertFalse(outside.inside)
        assertEquals(15.0, outside.distanceMetres, 0.05)
        assertEquals(90.0, outside.bearingDegrees!!, 0.5)

        val inside = Plane.view(Fields.at(90.0, 18.0), circle)
        assertTrue(inside.inside)
        assertEquals(0.0, inside.distanceMetres, 0.0)
        assertNull(inside.bearingDegrees)
    }

    @Test
    fun polygonDistanceIsToTheNearestEdge() {
        val field = Fields.square(1, 0.0, 20.0)
        val view = Plane.view(Fields.HOME, field.geometry)
        assertFalse(view.inside)
        assertEquals(15.0, view.distanceMetres, 0.05)
        assertEquals(0.0, Geodesy.angleBetween(view.bearingDegrees!!, 0.0), 0.5)
        assertEquals(20.0, view.centreDistanceMetres, 0.05)
    }

    @Test
    fun nearestPointGivesTheBearingToTheCorner() {
        val field = Fields.square(1, 45.0, 30.0)
        val view = Plane.view(Fields.HOME, field.geometry)
        assertEquals(45.0, view.bearingDegrees!!, 1.0)
        assertEquals(30.0 - 5.0 * Math.sqrt(2.0), view.distanceMetres, 0.1)
    }

    @Test
    fun pointInsidePolygonIsInside() {
        val field = Fields.square(1, 0.0, 3.0)
        assertTrue(Plane.view(Fields.HOME, field.geometry).inside)
    }

    @Test
    fun rectangleAt40DegreesHasItsLengthAlongTheHeading() {
        val polygon = Shapes.rectangle(Fields.HOME, 20.0, 10.0, 40.0)
        val corners = polygon.points
        assertEquals(4, corners.size)
        // The first two corners form the front side, across the heading.
        assertEquals(10.0, Geodesy.distanceMetres(corners[0], corners[1]), 0.05)
        assertEquals(20.0, Geodesy.distanceMetres(corners[1], corners[2]), 0.05)
        val frontMiddle = Plane.centre(Geometry.Polygon(listOf(corners[0], corners[1])))
        val towardsFront = Plane.view(Fields.HOME, Geometry.Marker(frontMiddle.latitude, frontMiddle.longitude))
        assertEquals(40.0, towardsFront.bearingDegrees!!, 0.5)
        assertEquals(10.0, towardsFront.distanceMetres, 0.05)
        val centre = Plane.centre(polygon)
        assertEquals(0.0, Geodesy.distanceMetres(centre, Fields.HOME), 0.05)
    }

    @Test
    fun angleBetweenWrapsAroundNorth() {
        assertEquals(20.0, Geodesy.angleBetween(350.0, 10.0), 1e-9)
        assertEquals(180.0, Geodesy.angleBetween(0.0, 180.0), 1e-9)
    }
}
