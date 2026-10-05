<?php
/**
 * Map picture version.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Integer cursor for one map's shared pictures.
 */
class Picture_Version {

	const META = 'tacnav_picture_rev';

	/**
	 * Next version after a successful write.
	 *
	 * @param int $current Current version.
	 * @return int
	 */
	public static function next( $current ) {
		return max( 0, (int) $current ) + 1;
	}

	/**
	 * Version to store after a write attempt.
	 *
	 * @param int  $current   Current version.
	 * @param bool $succeeded Whether the write was stored.
	 * @return int
	 */
	public static function advance_after( $current, $succeeded ) {
		if ( ! $succeeded ) {
			return (int) $current;
		}

		return self::next( $current );
	}

	/**
	 * Whether the client already has this version.
	 *
	 * @param string|null $client  Header value, or null when absent.
	 * @param int         $current Current version.
	 * @return bool
	 */
	public static function matches( $client, $current ) {
		if ( null === $client || '' === (string) $client ) {
			return false;
		}

		return (int) $client === (int) $current;
	}

	/**
	 * Strip a weak-validator prefix and quotes from If-None-Match.
	 *
	 * @param mixed $header Raw header.
	 * @return string|null
	 */
	public static function from_header( $header ) {
		if ( ! is_string( $header ) ) {
			return null;
		}

		$header = trim( $header );
		if ( '' === $header || '*' === $header ) {
			return null;
		}

		if ( 0 === strpos( $header, 'W/' ) ) {
			$header = substr( $header, 2 );
		}

		return trim( $header, " \t\"" );
	}

	/**
	 * Read the stored version, treating a missing value as zero.
	 *
	 * @param int $map_id Map ID.
	 * @return int
	 */
	public static function current( $map_id ) {
		return (int) get_post_meta( (int) $map_id, self::META, true );
	}

	/**
	 * Advance the stored version after a successful write.
	 *
	 * @param int $map_id Map ID.
	 * @return int
	 */
	public static function bump( $map_id ) {
		$map_id = (int) $map_id;
		$next   = self::advance_after( self::current( $map_id ), true );
		update_post_meta( $map_id, self::META, $next );
		return $next;
	}
}
