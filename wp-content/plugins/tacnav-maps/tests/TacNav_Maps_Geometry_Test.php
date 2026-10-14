<?php
/**
 * Spatial projection and exact-intersection tests.
 *
 * @package TacNav_Maps
 */

use PHPUnit\Framework\TestCase;
use TacNav\Maps\Geometry_Service;

require_once __DIR__ . '/bootstrap.php';

/**
 * @covers \TacNav\Maps\Geometry_Service
 */
class TacNav_Maps_Geometry_Test extends TestCase {

	/**
	 * Every canonical geometry kind has an indexable projection.
	 *
	 */
	public function test_every_kind_has_a_spatial_projection() {
		foreach ( self::geometry_provider() as $case ) {
			$kind     = $case[0];
			$geometry = $case[1];
			$prefix   = $case[2];
			$normal   = Geometry_Service::normalize( $kind, $geometry );

			$this->assertIsArray( $normal );
			$this->assertStringStartsWith( $prefix, Geometry_Service::projection_wkt( $kind, $normal ) );
		}
	}

	/**
	 * Geometry fixtures.
	 *
	 * @return array<string, array<int, mixed>>
	 */
	public static function geometry_provider() {
		return array(
			'marker'   => array( 'marker', array( 'lat' => 50, 'lng' => 30 ), 'POINT(' ),
			'circle'   => array( 'circle', array( 'lat' => 50, 'lng' => 30, 'radius' => 25 ), 'POLYGON((' ),
			'polyline' => array( 'polyline', array( 'path' => array( array( 50, 30 ), array( 50.001, 30.001 ) ) ), 'LINESTRING(' ),
			'polygon'  => array( 'polygon', array( 'path' => array( array( 50, 30 ), array( 50, 30.001 ), array( 50.001, 30 ) ) ), 'POLYGON((' ),
		);
	}

	/**
	 * A circle finds points, crossing lines, and overlapping polygons.
	 */
	public function test_circle_exact_intersection_covers_all_target_shapes() {
		$circle = array(
			'lat'    => 50,
			'lng'    => 30,
			'radius' => 100,
		);

		$this->assertTrue( Geometry_Service::intersects( 'circle', $circle, 'marker', array( 'lat' => 50.0002, 'lng' => 30 ) ) );
		$this->assertTrue(
			Geometry_Service::intersects(
				'circle',
				$circle,
				'polyline',
				array( 'path' => array( array( 49.999, 30 ), array( 50.001, 30 ) ) )
			)
		);
		$this->assertTrue(
			Geometry_Service::intersects(
				'circle',
				$circle,
				'polygon',
				array( 'path' => array( array( 49.9999, 29.9999 ), array( 49.9999, 30.0001 ), array( 50.0001, 30.0001 ), array( 49.9999, 29.9999 ) ) )
			)
		);
		$this->assertFalse( Geometry_Service::intersects( 'circle', $circle, 'marker', array( 'lat' => 50.01, 'lng' => 30 ) ) );
	}
}
