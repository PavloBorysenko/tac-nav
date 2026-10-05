<?php
/**
 * Picture store backed by a transient.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Keeps one assembled list per map, audience, and version.
 */
class Transient_Picture_Store implements Picture_Store {

	/**
	 * Save a picture for one day.
	 *
	 * @param int                              $map_id   Map ID.
	 * @param string                           $audience Audience key.
	 * @param int                              $version  Picture version.
	 * @param array<int, array<string, mixed>> $objects  Object list.
	 * @return void
	 */
	public function put( $map_id, $audience, $version, array $objects ) {
		set_transient( self::name( $map_id, $audience, $version ), $objects, DAY_IN_SECONDS );
	}

	/**
	 * Read a picture.
	 *
	 * @param int    $map_id   Map ID.
	 * @param string $audience Audience key.
	 * @param int    $version  Picture version.
	 * @return array<int, array<string, mixed>>|null
	 */
	public function get( $map_id, $audience, $version ) {
		$stored = get_transient( self::name( $map_id, $audience, $version ) );
		if ( ! is_array( $stored ) ) {
			return null;
		}

		return $stored;
	}

	/**
	 * Transient name for one picture.
	 *
	 * @param int    $map_id   Map ID.
	 * @param string $audience Audience key.
	 * @param int    $version  Picture version.
	 * @return string
	 */
	public static function name( $map_id, $audience, $version ) {
		return 'tacnav_pic_' . (int) $map_id . '_' . (string) $audience . '_' . (int) $version;
	}
}
