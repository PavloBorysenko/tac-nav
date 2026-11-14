package com.tacnav.sapper

import com.tacnav.sapper.domain.Coordinate
import com.tacnav.sapper.domain.Geodesy
import com.tacnav.sapper.domain.Geometry
import com.tacnav.sapper.domain.MapObject
import com.tacnav.sapper.domain.Shapes
import com.tacnav.sapper.game.Fix

/** Test geography: a home point and fields placed by bearing and distance from it. */
object Fields {
    val HOME = Coordinate(50.4501, 30.5234)
    const val BLUE = 7L
    const val RED = 8L

    fun at(bearing: Double, metres: Double, from: Coordinate = HOME): Coordinate =
        Geodesy.destination(from.latitude, from.longitude, bearing, metres)

    fun fix(position: Coordinate = HOME, accuracy: Double = 3.0, epoch: Long = 1_000_000L): Fix =
        Fix(position.latitude, position.longitude, accuracy, epoch)

    /** A 10 by 10 metre square field whose centre is [metres] away on [bearing]. */
    fun square(
        id: Long,
        bearing: Double,
        metres: Double,
        owner: Long? = RED,
        visible: List<Long> = listOf(RED),
        side: Double = 10.0,
    ): MapObject = MapObject(
        id = id,
        geometry = Shapes.rectangle(at(bearing, metres), side, side, 0.0),
        role = "minefield",
        ownerTeamId = owner,
        visibleTeamIds = visible,
        updatedAt = "2026-11-08 10:00:00",
    )

    fun circle(id: Long, centre: Coordinate, radius: Double, owner: Long? = RED, visible: List<Long> = listOf(RED)) =
        MapObject(id, Geometry.Circle(centre.latitude, centre.longitude, radius), "minefield", owner, visible, "2026-11-08 10:00:00")
}
