<?php
/**
 * Player geo-object write rules.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Stamps player creates so the client cannot choose team or origin.
 */
class Player_Write {

	/**
	 * Normalize a player create or update body.
	 *
	 * @param array<string, mixed> $body     Request fields.
	 * @param int                  $team_id  Player team.
	 * @param array<int, int>      $palette  Icon IDs on that team.
	 * @return array<string, mixed>
	 */
	public static function normalize( $body, $team_id, $palette ) {
		$icon    = isset( $body['icon_id'] ) ? (int) $body['icon_id'] : 0;
		$palette = array_map( 'intval', $palette );
		if ( $icon > 0 && ! in_array( $icon, $palette, true ) ) {
			return array( 'error' => 'icon' );
		}

		$ttl = null;
		if ( array_key_exists( 'ttl_minutes', $body ) ) {
			$ttl = max( 0, (int) $body['ttl_minutes'] );
		}

		return array(
			'error'            => '',
			'kind'             => isset( $body['kind'] ) ? (string) $body['kind'] : 'marker',
			'geometry'         => isset( $body['geometry'] ) && is_array( $body['geometry'] ) ? $body['geometry'] : array(),
			'owner_team_id'    => (int) $team_id,
			'visible_team_ids' => array( (int) $team_id ),
			'origin'           => 'team',
			'icon_id'          => $icon > 0 ? $icon : null,
			'title'            => isset( $body['title'] ) ? (string) $body['title'] : '',
			'description'      => isset( $body['description'] ) ? (string) $body['description'] : '',
			'ttl_minutes'      => $ttl,
		);
	}
}
