<?php
/**
 * Stored map picture.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * One full object list for a map, audience, and version.
 */
interface Picture_Store {

	/**
	 * Save a picture.
	 *
	 * @param int                            $map_id   Map ID.
	 * @param string                         $audience Audience key.
	 * @param int                            $version  Picture version.
	 * @param array<int, array<string, mixed>> $objects Object list.
	 * @return void
	 */
	public function put( $map_id, $audience, $version, array $objects );

	/**
	 * Read a picture, or null when this version is not stored.
	 *
	 * @param int    $map_id   Map ID.
	 * @param string $audience Audience key.
	 * @param int    $version  Picture version.
	 * @return array<int, array<string, mixed>>|null
	 */
	public function get( $map_id, $audience, $version );
}
