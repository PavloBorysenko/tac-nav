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
	 * Body fields an application update may change. Belonging, origin, the
	 * creating application, and the author never change on update.
	 */
	const UPDATE_FIELDS = array( 'kind', 'geometry', 'title', 'description', 'icon_id', 'visible_team_ids', 'position_accuracy_m', 'ttl_seconds', 'role' );

	/**
	 * Build an application-stamped object payload.
	 *
	 * A create gets a complete object. An update carries only the fields the
	 * body sends, and the store merges them over the stored row.
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
		if ( isset( $body['role'] ) ) {
			$payload['role'] = $body['role'];
		}
		// An older client may still send `searchable`; search no longer has that flag, so it is ignored.
		if ( $create && isset( $body['idempotency_key'] ) && '' !== $body['idempotency_key'] ) {
			$key = (string) $body['idempotency_key'];
			if ( ! preg_match( '/^[A-Za-z0-9_-]{1,64}$/', $key ) ) {
				return new \WP_Error( 'tacnav_idempotency_key', __( 'idempotency_key must be 1 to 64 letters, digits, hyphens, or underscores.', 'tacnav-maps' ), array( 'status' => 400 ) );
			}
			$payload['client_key'] = $key;
		}
		if ( ! $create ) {
			$payload = array_intersect_key( $payload, array_intersect_key( $body, array_flip( self::UPDATE_FIELDS ) ) );
		}
		return Object_Expiry::apply_to_payload( $payload, $create, $now );
	}

	/**
	 * Whether an application update should rebind the object's mailbox.
	 *
	 * Only the creating application's writes bind, so another application's
	 * edit never takes over the object's hit deliveries.
	 *
	 * @param array<string, mixed> $stored      Object before the update.
	 * @param array<string, mixed> $application Writing application principal.
	 * @return bool
	 */
	public static function binds_on_update( $stored, $application ) {
		$creator = isset( $stored['application_id'] ) ? (int) $stored['application_id'] : 0;
		$writer  = isset( $application['application_id'] ) ? (int) $application['application_id'] : 0;

		return $creator > 0 && $creator === $writer;
	}
}
