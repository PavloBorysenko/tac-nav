<?php
/**
 * Authenticated application request context.
 *
 * @package TacNav_Devices
 */

namespace TacNav\Devices;

defined( 'ABSPATH' ) || exit;

/**
 * Resolves bearer identity and validates submitted playable context.
 */
class Application_Context {

	/**
	 * Resolve and validate an application request.
	 *
	 * @param \WP_REST_Request $request REST request.
	 * @return array<string, mixed>|\WP_Error
	 */
	public static function resolve( $request ) {
		$authorization = trim( (string) $request->get_header( 'authorization' ) );
		if ( ! preg_match( '/^Bearer\s+([A-Za-z0-9_-]{40,})$/i', $authorization, $matches ) ) {
			return new \WP_Error( 'tacnav_bearer_required', __( 'A valid application bearer credential is required.', 'tacnav-devices' ), array( 'status' => 401 ) );
		}

		$store       = new Application_Store();
		$application = $store->resolve_bearer( $matches[1] );
		if ( ! $application ) {
			return new \WP_Error( 'tacnav_bearer_invalid', __( 'The application credential is invalid or revoked.', 'tacnav-devices' ), array( 'status' => 401 ) );
		}

		$map_id  = (int) self::request_value( $request, 'map_id', 'x-tacnav-map' );
		$team_id = (int) self::request_value( $request, 'team_id', 'x-tacnav-team' );
		if ( $map_id <= 0 ) {
			$map_id = (int) $application['default_map_id'];
		}
		if ( $team_id <= 0 ) {
			$team_id = (int) $application['default_team_id'];
		}
		$selected = Validation::context( $application, $map_id, $team_id, array( $store, 'team_on_map' ) );
		if ( is_wp_error( $selected ) ) {
			return $selected;
		}

		return array(
			'principal'        => 'application',
			'application_id'   => (int) $application['id'],
			'installation_id'  => (int) $application['installation_id'],
			'type'             => (string) $application['application_type'],
			'allowed_map_ids'  => $application['allowed_map_ids'],
			'allowed_team_ids' => $application['allowed_team_ids'],
			'map_id'           => $selected['map_id'],
			'team_id'          => $selected['team_id'],
		);
	}

	/**
	 * Integration boundary consumed by TacNav Maps.
	 *
	 * TacNav Maps should pass its WP_REST_Request as the second filter argument
	 * and preserve WP_Error responses.
	 *
	 * @param mixed            $current Existing context.
	 * @param \WP_REST_Request $request REST request.
	 * @return mixed
	 */
	public static function filter( $current, $request = null ) {
		if ( null !== $current || ! $request instanceof \WP_REST_Request ) {
			return $current;
		}
		if ( '' === trim( (string) $request->get_header( 'authorization' ) ) ) {
			return null;
		}
		return self::resolve( $request );
	}

	/**
	 * Prefer a request parameter but support immutable transport headers.
	 *
	 * @param \WP_REST_Request $request Request.
	 * @param string           $parameter Parameter name.
	 * @param string           $header Header name.
	 * @return mixed
	 */
	private static function request_value( $request, $parameter, $header ) {
		$value = $request->get_param( $parameter );
		return null === $value || '' === $value ? $request->get_header( $header ) : $value;
	}
}
