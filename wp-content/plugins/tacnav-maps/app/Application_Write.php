<?php
/**
 * Application geo-object write normalization.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Stamps trusted identity and validates application-controlled fields.
 */
class Application_Write {

	/**
	 * Build an application-stamped object payload.
	 *
	 * @param array<string, mixed> $body        Request body.
	 * @param array<string, mixed> $map         Map passport.
	 * @param array<string, mixed> $application Application principal.
	 * @param bool                 $create      Creating.
	 * @param int                  $now         Acceptance timestamp.
	 * @return array<string, mixed>|\WP_Error
	 */
	public static function normalize( $body, $map, $application, $create, $now ) {
		if ( array_key_exists( 'ttl_minutes', $body ) ) {
			return new \WP_Error( 'tacnav_ttl_minutes_removed', __( 'ttl_minutes is no longer accepted; use ttl_seconds.', 'tacnav-maps' ), array( 'status' => 400 ) );
		}

		$visible   = isset( $body['visible_team_ids'] ) && is_array( $body['visible_team_ids'] ) ? array_values( array_unique( array_filter( array_map( 'intval', $body['visible_team_ids'] ) ) ) ) : array();
		$map_teams = isset( $map['team_ids'] ) && is_array( $map['team_ids'] ) ? array_map( 'intval', $map['team_ids'] ) : array();
		if ( array_diff( $visible, $map_teams ) ) {
			return new \WP_Error( 'tacnav_geo_visibility', __( 'Visibility includes a team that is not on this map.', 'tacnav-maps' ), array( 'status' => 400 ) );
		}

		$icon_id = isset( $body['icon_id'] ) ? (int) $body['icon_id'] : 0;
		if ( $icon_id <= 0 || Post_Types::ICON !== get_post_type( $icon_id ) ) {
			$icon_id = 0;
		}

		$payload = array(
			'map_id'              => (int) $map['id'],
			'kind'                => isset( $body['kind'] ) ? $body['kind'] : 'marker',
			'geometry'            => isset( $body['geometry'] ) && is_array( $body['geometry'] ) ? $body['geometry'] : array(),
			'owner_team_id'       => (int) $application['team_id'],
			'visible_team_ids'    => $visible,
			'origin'              => 'application',
			'application_id'      => (int) $application['application_id'],
			'created_by_user_id'  => 0,
			'position_accuracy_m' => $body['position_accuracy_m'] ?? null,
			'icon_id'             => $icon_id > 0 ? $icon_id : null,
			'title'               => isset( $body['title'] ) ? $body['title'] : '',
			'description'         => isset( $body['description'] ) ? $body['description'] : '',
		);
		if ( array_key_exists( 'ttl_seconds', $body ) ) {
			$payload['ttl_seconds'] = $body['ttl_seconds'];
		}
		if ( ! $create ) {
			unset( $payload['origin'], $payload['application_id'], $payload['created_by_user_id'] );
		}
		return Object_Expiry::apply_to_payload( $payload, $create, $now );
	}
}
