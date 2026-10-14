<?php
/**
 * Geometry normalization, projection, and exact intersection.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Shared canonical-geometry operations.
 */
class Geometry_Service {

	const KINDS = array( 'marker', 'polyline', 'polygon', 'circle' );

	/**
	 * Normalize geometry for one supported kind.
	 *
	 * @param string $kind     Geometry kind.
	 * @param mixed  $geometry Geometry payload.
	 * @return array<string, mixed>|\WP_Error
	 */
	public static function normalize( $kind, $geometry ) {
		$kind = sanitize_key( (string) $kind );
		if ( ! in_array( $kind, self::KINDS, true ) || ! is_array( $geometry ) ) {
			return new \WP_Error( 'tacnav_geo_geometry', __( 'Invalid geometry.', 'tacnav-maps' ), array( 'status' => 400 ) );
		}

		if ( 'marker' === $kind ) {
			return self::point( $geometry );
		}

		if ( 'circle' === $kind ) {
			$point = self::point( $geometry );
			if ( is_wp_error( $point ) ) {
				return $point;
			}
			$radius = isset( $geometry['radius'] ) ? (float) $geometry['radius'] : 0.0;
			if ( ! is_finite( $radius ) || $radius <= 0 ) {
				return new \WP_Error( 'tacnav_geo_radius', __( 'Circle radius must be greater than zero.', 'tacnav-maps' ), array( 'status' => 400 ) );
			}
			$point['radius'] = $radius;
			return $point;
		}

		$path = isset( $geometry['path'] ) && is_array( $geometry['path'] ) ? $geometry['path'] : array();
		$pts  = array();
		foreach ( $path as $pair ) {
			if ( ! is_array( $pair ) ) {
				continue;
			}
			$point = self::point(
				array(
					'lat' => $pair['lat'] ?? ( $pair[0] ?? null ),
					'lng' => $pair['lng'] ?? ( $pair[1] ?? null ),
				)
			);
			if ( is_wp_error( $point ) ) {
				return $point;
			}
			$pts[] = array( $point['lat'], $point['lng'] );
		}

		$minimum = 'polyline' === $kind ? 2 : 3;
		if ( count( $pts ) < $minimum ) {
			return new \WP_Error( 'tacnav_geo_path', __( 'Not enough vertices.', 'tacnav-maps' ), array( 'status' => 400 ) );
		}

		if ( 'polygon' === $kind && $pts[0] !== $pts[ count( $pts ) - 1 ] ) {
			$pts[] = $pts[0];
		}

		return array( 'path' => $pts );
	}

	/**
	 * Build an engine-neutral WKT projection in longitude/latitude order.
	 *
	 * Circles use a 32-point envelope. Exact circle checks remain in PHP.
	 *
	 * @param string               $kind     Geometry kind.
	 * @param array<string, mixed> $geometry Normalized geometry.
	 * @return string
	 */
	public static function projection_wkt( $kind, $geometry ) {
		if ( 'marker' === $kind ) {
			return 'POINT(' . self::number( $geometry['lng'] ) . ' ' . self::number( $geometry['lat'] ) . ')';
		}

		if ( 'circle' === $kind ) {
			$path = array();
			$lat  = (float) $geometry['lat'];
			$lng  = (float) $geometry['lng'];
			$cos  = max( 0.000001, cos( deg2rad( $lat ) ) );
			for ( $index = 0; $index < 32; ++$index ) {
				$angle  = 2 * M_PI * $index / 32;
				$north  = (float) $geometry['radius'] * sin( $angle );
				$east   = (float) $geometry['radius'] * cos( $angle );
				$path[] = array( $lat + ( $north / 111320 ), $lng + ( $east / ( 111320 * $cos ) ) );
			}
			$path[] = $path[0];
			return 'POLYGON((' . self::wkt_path( $path ) . '))';
		}

		if ( 'polyline' === $kind ) {
			return 'LINESTRING(' . self::wkt_path( $geometry['path'] ) . ')';
		}

		return 'POLYGON((' . self::wkt_path( $geometry['path'] ) . '))';
	}

	/**
	 * Test exact intersection using a local metre-scale projection.
	 *
	 * @param string               $left_kind      Left kind.
	 * @param array<string, mixed> $left_geometry Left geometry.
	 * @param string               $right_kind     Right kind.
	 * @param array<string, mixed> $right_geometry Right geometry.
	 * @return bool
	 */
	public static function intersects( $left_kind, $left_geometry, $right_kind, $right_geometry ) {
		$reference = ( self::latitude_sum( $left_kind, $left_geometry ) + self::latitude_sum( $right_kind, $right_geometry ) ) /
			max( 1, self::point_count( $left_kind, $left_geometry ) + self::point_count( $right_kind, $right_geometry ) );
		$left      = self::planar_shape( $left_kind, $left_geometry, $reference );
		$right     = self::planar_shape( $right_kind, $right_geometry, $reference );

		if ( 'marker' === $left_kind ) {
			return self::point_intersects( $left['point'], $right_kind, $right );
		}
		if ( 'marker' === $right_kind ) {
			return self::point_intersects( $right['point'], $left_kind, $left );
		}
		if ( 'circle' === $left_kind ) {
			return self::circle_intersects( $left, $right_kind, $right );
		}
		if ( 'circle' === $right_kind ) {
			return self::circle_intersects( $right, $left_kind, $left );
		}
		if ( 'polyline' === $left_kind && 'polyline' === $right_kind ) {
			return self::paths_cross( $left['path'], $right['path'] );
		}

		if ( 'polygon' === $left_kind && self::path_intersects_polygon( $right['path'], $left['path'] ) ) {
			return true;
		}
		if ( 'polygon' === $right_kind && self::path_intersects_polygon( $left['path'], $right['path'] ) ) {
			return true;
		}

		return false;
	}

	/**
	 * Normalize a point.
	 *
	 * @param array<string, mixed> $geometry Point input.
	 * @return array<string, float>|\WP_Error
	 */
	private static function point( $geometry ) {
		if ( ! isset( $geometry['lat'], $geometry['lng'] ) ) {
			return new \WP_Error( 'tacnav_geo_point', __( 'Latitude and longitude are required.', 'tacnav-maps' ), array( 'status' => 400 ) );
		}
		$lat = (float) $geometry['lat'];
		$lng = (float) $geometry['lng'];
		if ( ! is_finite( $lat ) || ! is_finite( $lng ) || $lat < -90 || $lat > 90 || $lng < -180 || $lng > 180 ) {
			return new \WP_Error( 'tacnav_geo_point', __( 'Coordinates are out of range.', 'tacnav-maps' ), array( 'status' => 400 ) );
		}
		return array(
			'lat' => $lat,
			'lng' => $lng,
		);
	}

	/**
	 * Format one WKT path.
	 *
	 * @param array<int, array<int, float>> $path Latitude/longitude pairs.
	 * @return string
	 */
	private static function wkt_path( $path ) {
		$parts = array();
		foreach ( $path as $point ) {
			$parts[] = self::number( $point[1] ) . ' ' . self::number( $point[0] );
		}
		return implode( ',', $parts );
	}

	/**
	 * Stable decimal format.
	 *
	 * @param mixed $value Number.
	 * @return string
	 */
	private static function number( $value ) {
		return rtrim( rtrim( sprintf( '%.12F', (float) $value ), '0' ), '.' );
	}

	/**
	 * Convert geometry to local planar coordinates.
	 *
	 * @param string               $kind      Kind.
	 * @param array<string, mixed> $geometry  Geometry.
	 * @param float                $reference Reference latitude.
	 * @return array<string, mixed>
	 */
	private static function planar_shape( $kind, $geometry, $reference ) {
		if ( 'marker' === $kind || 'circle' === $kind ) {
			$shape = array( 'point' => self::xy( (float) $geometry['lat'], (float) $geometry['lng'], $reference ) );
			if ( 'circle' === $kind ) {
				$shape['radius'] = (float) $geometry['radius'];
			}
			return $shape;
		}
		$path = array();
		foreach ( $geometry['path'] as $point ) {
			$path[] = self::xy( (float) $point[0], (float) $point[1], $reference );
		}
		return array( 'path' => $path );
	}

	/**
	 * Convert latitude/longitude to local metres.
	 *
	 * @param float $lat       Latitude.
	 * @param float $lng       Longitude.
	 * @param float $reference Reference latitude.
	 * @return array<int, float>
	 */
	private static function xy( $lat, $lng, $reference ) {
		return array(
			$lng * 111320 * cos( deg2rad( $reference ) ),
			$lat * 111320,
		);
	}

	/**
	 * Point against a shape.
	 *
	 * @param array<int, float>   $point Point.
	 * @param string              $kind  Other kind.
	 * @param array<string,mixed> $shape Other shape.
	 * @return bool
	 */
	private static function point_intersects( $point, $kind, $shape ) {
		if ( 'marker' === $kind ) {
			return self::distance( $point, $shape['point'] ) < 0.001;
		}
		if ( 'circle' === $kind ) {
			return self::distance( $point, $shape['point'] ) <= $shape['radius'];
		}
		if ( 'polygon' === $kind && self::point_in_polygon( $point, $shape['path'] ) ) {
			return true;
		}
		return self::distance_to_path( $point, $shape['path'] ) < 0.001;
	}

	/**
	 * Circle against a shape.
	 *
	 * @param array<string,mixed> $circle Circle.
	 * @param string              $kind   Other kind.
	 * @param array<string,mixed> $shape  Other shape.
	 * @return bool
	 */
	private static function circle_intersects( $circle, $kind, $shape ) {
		if ( 'circle' === $kind ) {
			return self::distance( $circle['point'], $shape['point'] ) <= (float) $circle['radius'] + (float) $shape['radius'];
		}
		if ( 'polygon' === $kind && self::point_in_polygon( $circle['point'], $shape['path'] ) ) {
			return true;
		}
		return self::distance_to_path( $circle['point'], $shape['path'] ) <= (float) $circle['radius'];
	}

	/**
	 * Whether a path intersects or lies in a polygon.
	 *
	 * @param array<int, array<int, float>> $path    Path.
	 * @param array<int, array<int, float>> $polygon Polygon.
	 * @return bool
	 */
	private static function path_intersects_polygon( $path, $polygon ) {
		return self::point_in_polygon( $path[0], $polygon ) || self::paths_cross( $path, $polygon );
	}

	/**
	 * Test whether any segments cross.
	 *
	 * @param array<int, array<int, float>> $left  Left path.
	 * @param array<int, array<int, float>> $right Right path.
	 * @return bool
	 */
	private static function paths_cross( $left, $right ) {
		$left_count  = count( $left );
		$right_count = count( $right );
		for ( $left_index = 1; $left_index < $left_count; ++$left_index ) {
			for ( $right_index = 1; $right_index < $right_count; ++$right_index ) {
				if ( self::segments_cross( $left[ $left_index - 1 ], $left[ $left_index ], $right[ $right_index - 1 ], $right[ $right_index ] ) ) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Segment intersection including touching.
	 *
	 * @param array<int,float> $a First start.
	 * @param array<int,float> $b First end.
	 * @param array<int,float> $c Second start.
	 * @param array<int,float> $d Second end.
	 * @return bool
	 */
	private static function segments_cross( $a, $b, $c, $d ) {
		$one   = self::cross( $a, $b, $c );
		$two   = self::cross( $a, $b, $d );
		$three = self::cross( $c, $d, $a );
		$four  = self::cross( $c, $d, $b );
		return $one * $two <= 0 && $three * $four <= 0 &&
			max( min( $a[0], $b[0] ), min( $c[0], $d[0] ) ) <= min( max( $a[0], $b[0] ), max( $c[0], $d[0] ) ) &&
			max( min( $a[1], $b[1] ), min( $c[1], $d[1] ) ) <= min( max( $a[1], $b[1] ), max( $c[1], $d[1] ) );
	}

	/**
	 * Signed cross product.
	 *
	 * @param array<int,float> $a Start.
	 * @param array<int,float> $b End.
	 * @param array<int,float> $point Point.
	 * @return float
	 */
	private static function cross( $a, $b, $point ) {
		return ( $b[0] - $a[0] ) * ( $point[1] - $a[1] ) - ( $b[1] - $a[1] ) * ( $point[0] - $a[0] );
	}

	/**
	 * Ray-casting point in polygon.
	 *
	 * @param array<int,float>             $point   Point.
	 * @param array<int,array<int,float>> $polygon Closed polygon.
	 * @return bool
	 */
	private static function point_in_polygon( $point, $polygon ) {
		$inside = false;
		$count  = count( $polygon );
		for ( $index = 0, $previous = $count - 1; $index < $count; $previous = $index++ ) {
			$left  = $polygon[ $index ];
			$right = $polygon[ $previous ];
			if ( self::distance_to_segment( $point, $left, $right ) < 0.001 ) {
				return true;
			}
			if ( ( $left[1] > $point[1] ) !== ( $right[1] > $point[1] ) &&
				$point[0] < ( $right[0] - $left[0] ) * ( $point[1] - $left[1] ) / ( $right[1] - $left[1] ) + $left[0] ) {
				$inside = ! $inside;
			}
		}
		return $inside;
	}

	/**
	 * Shortest distance to a path.
	 *
	 * @param array<int,float>             $point Point.
	 * @param array<int,array<int,float>> $path  Path.
	 * @return float
	 */
	private static function distance_to_path( $point, $path ) {
		$minimum = INF;
		$count   = count( $path );
		for ( $index = 1; $index < $count; ++$index ) {
			$minimum = min( $minimum, self::distance_to_segment( $point, $path[ $index - 1 ], $path[ $index ] ) );
		}
		return $minimum;
	}

	/**
	 * Shortest point-to-segment distance.
	 *
	 * @param array<int,float> $point Point.
	 * @param array<int,float> $start Segment start.
	 * @param array<int,float> $end   Segment end.
	 * @return float
	 */
	private static function distance_to_segment( $point, $start, $end ) {
		$dx = $end[0] - $start[0];
		$dy = $end[1] - $start[1];
		if ( 0.0 === $dx && 0.0 === $dy ) {
			return self::distance( $point, $start );
		}
		$ratio = max( 0.0, min( 1.0, ( ( $point[0] - $start[0] ) * $dx + ( $point[1] - $start[1] ) * $dy ) / ( $dx * $dx + $dy * $dy ) ) );
		return self::distance( $point, array( $start[0] + $ratio * $dx, $start[1] + $ratio * $dy ) );
	}

	/**
	 * Euclidean distance.
	 *
	 * @param array<int,float> $left  Left point.
	 * @param array<int,float> $right Right point.
	 * @return float
	 */
	private static function distance( $left, $right ) {
		return hypot( $left[0] - $right[0], $left[1] - $right[1] );
	}

	/**
	 * Sum latitudes in geometry.
	 *
	 * @param string               $kind Kind.
	 * @param array<string, mixed> $geometry Geometry.
	 * @return float
	 */
	private static function latitude_sum( $kind, $geometry ) {
		if ( 'marker' === $kind || 'circle' === $kind ) {
			return (float) $geometry['lat'];
		}
		return array_sum( array_column( $geometry['path'], 0 ) );
	}

	/**
	 * Number of points represented by geometry.
	 *
	 * @param string               $kind Kind.
	 * @param array<string, mixed> $geometry Geometry.
	 * @return int
	 */
	private static function point_count( $kind, $geometry ) {
		return 'marker' === $kind || 'circle' === $kind ? 1 : count( $geometry['path'] );
	}
}
