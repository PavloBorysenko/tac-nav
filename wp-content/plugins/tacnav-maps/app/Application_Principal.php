<?php
/**
 * Application-principal integration boundary.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Resolves a principal supplied by a credential-owning plugin.
 */
class Application_Principal {

	/**
	 * Resolve and validate an application context for a map request.
	 *
	 * The tacnav_maps_application_principal filter returns null when it does not
	 * recognize the request, WP_Error for invalid credentials, or a context.
	 *
	 * @param \WP_REST_Request     $request REST request.
	 * @param array<string, mixed> $map     Map passport.
	 * @return array<string, mixed>|null|\WP_Error
	 */
	public static function resolve( $request, $map ) {
		/**
		 * Supplies an authenticated application principal.
		 *
		 * @param array<string, mixed>|null|\WP_Error $principal Principal or error.
		 * @param \WP_REST_Request                    $request   REST request.
		 */
		$principal = apply_filters( 'tacnav_maps_application_principal', null, $request );
		if ( null === $principal || is_wp_error( $principal ) ) {
			return $principal;
		}

		$application_id = isset( $principal['application_id'] ) ? (int) $principal['application_id'] : 0;
		$map_id         = isset( $principal['map_id'] ) ? (int) $principal['map_id'] : 0;
		$team_id        = isset( $principal['team_id'] ) ? (int) $principal['team_id'] : 0;
		$allowed_maps   = self::ids( $principal['allowed_map_ids'] ?? array() );
		$allowed_teams  = self::ids( $principal['allowed_team_ids'] ?? array() );
		$route_map      = isset( $map['id'] ) ? (int) $map['id'] : 0;
		$map_teams      = self::ids( $map['team_ids'] ?? array() );

		if ( $application_id <= 0 || $map_id !== $route_map || ! in_array( $map_id, $allowed_maps, true ) ||
			$team_id <= 0 || ! in_array( $team_id, $allowed_teams, true ) || ! in_array( $team_id, $map_teams, true ) ) {
			return self::forbidden();
		}

		return array(
			'application_id'  => $application_id,
			'installation_id' => isset( $principal['installation_id'] ) ? (int) $principal['installation_id'] : 0,
			'map_id'          => $map_id,
			'team_id'         => $team_id,
			'type'            => isset( $principal['type'] ) ? sanitize_key( (string) $principal['type'] ) : '',
		);
	}

	/**
	 * Convert a principal into the standard access viewer shape.
	 *
	 * @param array<string, mixed> $principal Principal.
	 * @return array<string, mixed>
	 */
	public static function viewer( $principal ) {
		return array(
			'user_id'        => 0,
			'is_staff'       => false,
			'team_ids'       => array( (int) $principal['team_id'] ),
			'application_id' => (int) $principal['application_id'],
			'is_application' => true,
		);
	}

	/**
	 * Normalize positive integer ids.
	 *
	 * @param mixed $values Values.
	 * @return array<int, int>
	 */
	private static function ids( $values ) {
		if ( ! is_array( $values ) ) {
			return array();
		}
		return array_values( array_unique( array_filter( array_map( 'intval', $values ) ) ) );
	}

	/**
	 * Standard scope failure.
	 *
	 * @return \WP_Error
	 */
	private static function forbidden() {
		return new \WP_Error( 'tacnav_application_scope', __( 'The application is not allowed to use this map and team.', 'tacnav-maps' ), array( 'status' => 403 ) );
	}
}
