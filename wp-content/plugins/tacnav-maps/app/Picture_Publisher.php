<?php
/**
 * Rebuild stored pictures after a successful write.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Bumps the map version and stores one list per audience.
 */
class Picture_Publisher {

	/**
	 * Advance the version and fill the transient store.
	 *
	 * @param int                  $map_id Map ID.
	 * @param array<string, mixed> $map    Map passport.
	 * @return int
	 */
	public static function publish( $map_id, $map ) {
		$map_id  = (int) $map_id;
		$version = Picture_Version::bump( $map_id );
		$store   = new Transient_Picture_Store();
		$teams   = isset( $map['team_ids'] ) && is_array( $map['team_ids'] ) ? $map['team_ids'] : array();

		$store->put( $map_id, 'staff', $version, REST::objects_for_audience( $map_id, 'staff' ) );
		$store->put( $map_id, 'staff-expired', $version, REST::objects_for_audience( $map_id, 'staff-expired' ) );

		foreach ( $teams as $team_id ) {
			$key = Picture_Audience::key( 'player', (int) $team_id, false );
			if ( '' === $key ) {
				continue;
			}
			$store->put( $map_id, $key, $version, REST::objects_for_audience( $map_id, $key ) );
		}

		return $version;
	}
}
