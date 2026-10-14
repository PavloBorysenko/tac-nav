<?php
/**
 * Device application validation rules.
 *
 * @package TacNav_Devices
 */

namespace TacNav\Devices;

defined( 'ABSPATH' ) || exit;

/**
 * Pure scope and context validation.
 */
class Validation {

	/**
	 * Normalize positive integer identifiers.
	 *
	 * @param mixed $values Candidate values.
	 * @return array<int, int>
	 */
	public static function ids( $values ) {
		if ( ! is_array( $values ) ) {
			return array();
		}
		return array_values( array_unique( array_filter( array_map( 'intval', $values ) ) ) );
	}

	/**
	 * Validate defaults against allowed scopes.
	 *
	 * @param array<int, int> $maps         Allowed map IDs.
	 * @param array<int, int> $teams        Allowed team IDs.
	 * @param int             $default_map  Default map ID.
	 * @param int             $default_team Default team ID.
	 * @param callable        $team_on_map  Receives map and team IDs.
	 * @return true|\WP_Error
	 */
	public static function defaults( $maps, $teams, $default_map, $default_team, $team_on_map ) {
		if ( $default_map > 0 && ! in_array( $default_map, $maps, true ) ) {
			return new \WP_Error( 'tacnav_default_map_scope', __( 'Default map must be in the allowed map scope.', 'tacnav-devices' ) );
		}
		if ( $default_team > 0 && ! in_array( $default_team, $teams, true ) ) {
			return new \WP_Error( 'tacnav_default_team_scope', __( 'Default team must be in the allowed team scope.', 'tacnav-devices' ) );
		}
		if ( $default_map > 0 && $default_team > 0 && ! call_user_func( $team_on_map, $default_map, $default_team ) ) {
			return new \WP_Error( 'tacnav_default_team_map', __( 'Default team must be listed on the default map.', 'tacnav-devices' ) );
		}
		return true;
	}

	/**
	 * Validate a submitted current map/team without trusting the client.
	 *
	 * @param array<string, mixed> $application Application definition.
	 * @param int                  $map_id      Submitted map.
	 * @param int                  $team_id     Submitted team.
	 * @param callable             $team_on_map Receives map and team IDs.
	 * @return array<string, int>|\WP_Error
	 */
	public static function context( $application, $map_id, $team_id, $team_on_map ) {
		$maps  = isset( $application['allowed_map_ids'] ) ? self::ids( $application['allowed_map_ids'] ) : array();
		$teams = isset( $application['allowed_team_ids'] ) ? self::ids( $application['allowed_team_ids'] ) : array();
		if ( ! in_array( $map_id, $maps, true ) || ! in_array( $team_id, $teams, true ) ) {
			return new \WP_Error( 'tacnav_application_scope', __( 'The selected map or team is outside the activated scope.', 'tacnav-devices' ), array( 'status' => 403 ) );
		}
		if ( ! call_user_func( $team_on_map, $map_id, $team_id ) ) {
			return new \WP_Error( 'tacnav_application_membership', __( 'The selected team is not listed on the selected map.', 'tacnav-devices' ), array( 'status' => 403 ) );
		}
		return array(
			'map_id'  => $map_id,
			'team_id' => $team_id,
		);
	}
}
