<?php
/**
 * Device provisioning and mailbox REST API.
 *
 * @package TacNav_Devices
 */

namespace TacNav\Devices;

defined( 'ABSPATH' ) || exit;

/**
 * Registers versioned device endpoints.
 */
class REST {

	const NS = 'tacnav-devices/v1';

	/**
	 * Register routes.
	 *
	 * @return void
	 */
	public static function register() {
		register_rest_route(
			self::NS,
			'/activate',
			array(
				'methods'             => \WP_REST_Server::CREATABLE,
				'callback'            => array( __CLASS__, 'activate' ),
				'permission_callback' => '__return_true',
			)
		);
		register_rest_route(
			self::NS,
			'/applications(?:/(?P<id>\d+))?',
			array(
				array(
					'methods'             => \WP_REST_Server::READABLE,
					'callback'            => array( __CLASS__, 'get_application' ),
					'permission_callback' => array( __CLASS__, 'staff_permission' ),
				),
				array(
					'methods'             => \WP_REST_Server::CREATABLE,
					'callback'            => array( __CLASS__, 'save_application' ),
					'permission_callback' => array( __CLASS__, 'staff_permission' ),
				),
			)
		);
		register_rest_route(
			self::NS,
			'/applications/(?P<id>\d+)/activation',
			array(
				'methods'             => \WP_REST_Server::CREATABLE,
				'callback'            => array( __CLASS__, 'create_activation' ),
				'permission_callback' => array( __CLASS__, 'staff_permission' ),
			)
		);
		register_rest_route(
			self::NS,
			'/applications/(?P<id>\d+)/revoke',
			array(
				'methods'             => \WP_REST_Server::CREATABLE,
				'callback'            => array( __CLASS__, 'revoke' ),
				'permission_callback' => array( __CLASS__, 'staff_permission' ),
			)
		);

		$authenticated = array( __CLASS__, 'application_permission' );
		register_rest_route(
			self::NS,
			'/icons',
			array(
				'methods'             => \WP_REST_Server::READABLE,
				'callback'            => array( __CLASS__, 'icons' ),
				'permission_callback' => $authenticated,
			)
		);
		register_rest_route(
			self::NS,
			'/release',
			array(
				'methods'             => \WP_REST_Server::READABLE,
				'callback'            => array( __CLASS__, 'release' ),
				'permission_callback' => $authenticated,
			)
		);
		register_rest_route(
			self::NS,
			'/fcm',
			array(
				'methods'             => \WP_REST_Server::EDITABLE,
				'callback'            => array( __CLASS__, 'register_fcm' ),
				'permission_callback' => $authenticated,
			)
		);
		register_rest_route(
			self::NS,
			'/events',
			array(
				'methods'             => \WP_REST_Server::CREATABLE,
				'callback'            => array( __CLASS__, 'submit_event' ),
				'permission_callback' => $authenticated,
			)
		);
		register_rest_route(
			self::NS,
			'/inbox',
			array(
				'methods'             => \WP_REST_Server::READABLE,
				'callback'            => array( __CLASS__, 'inbox' ),
				'permission_callback' => $authenticated,
			)
		);
		register_rest_route(
			self::NS,
			'/inbox/ack',
			array(
				'methods'             => \WP_REST_Server::CREATABLE,
				'callback'            => array( __CLASS__, 'acknowledge' ),
				'permission_callback' => $authenticated,
			)
		);
	}

	/**
	 * Staff capability check.
	 *
	 * @return bool
	 */
	public static function staff_permission() {
		return current_user_can( \TacNav\Maps\Roles::CAP_GEO ) && current_user_can( \TacNav\Maps\Roles::CAP_CANVAS );
	}

	/**
	 * Application bearer permission.
	 *
	 * @param \WP_REST_Request $request Request.
	 * @return bool|\WP_Error
	 */
	public static function application_permission( $request ) {
		$context = Application_Context::resolve( $request );
		return is_wp_error( $context ) ? $context : true;
	}

	/**
	 * Consume an activation code.
	 *
	 * @param \WP_REST_Request $request Request.
	 * @return \WP_REST_Response|\WP_Error
	 */
	public static function activate( $request ) {
		$result = ( new Application_Store() )->activate( (string) $request->get_param( 'code' ) );
		return is_wp_error( $result ) ? $result : rest_ensure_response( $result );
	}

	/**
	 * Read one staff definition.
	 *
	 * @param \WP_REST_Request $request Request.
	 * @return \WP_REST_Response|\WP_Error
	 */
	public static function get_application( $request ) {
		$id = (int) $request->get_param( 'id' );
		if ( $id <= 0 ) {
			return new \WP_Error( 'tacnav_application_id', __( 'Application id is required.', 'tacnav-devices' ), array( 'status' => 400 ) );
		}
		$item = ( new Application_Store() )->get( $id );
		return $item ? rest_ensure_response( $item ) : new \WP_Error( 'tacnav_application_missing', __( 'Application not found.', 'tacnav-devices' ), array( 'status' => 404 ) );
	}

	/**
	 * Create or update a definition.
	 *
	 * @param \WP_REST_Request $request Request.
	 * @return \WP_REST_Response|\WP_Error
	 */
	public static function save_application( $request ) {
		$data = $request->get_json_params();
		if ( (int) $request->get_param( 'id' ) > 0 ) {
			$data['id'] = (int) $request->get_param( 'id' );
		}
		$id = ( new Application_Store() )->save( $data );
		return is_wp_error( $id ) ? $id : rest_ensure_response( array( 'id' => $id ) );
	}

	/**
	 * Issue one activation and handoff values.
	 *
	 * @param \WP_REST_Request $request Request.
	 * @return \WP_REST_Response|\WP_Error
	 */
	public static function create_activation( $request ) {
		$code = ( new Application_Store() )->create_activation( (int) $request['id'], get_current_user_id() );
		if ( is_wp_error( $code ) ) {
			return $code;
		}
		$url = rest_url( self::NS . '/activate' );
		return rest_ensure_response(
			array(
				'server_url'     => esc_url_raw( home_url( '/' ) ),
				'activation_url' => esc_url_raw( $url ),
				'code'           => $code,
				'qr_payload'     => wp_json_encode(
					array(
						'server_url' => home_url( '/' ),
						'code'       => $code,
					)
				),
				'expires_in'     => 900,
			)
		);
	}

	/**
	 * Revoke a current phone.
	 *
	 * @param \WP_REST_Request $request Request.
	 * @return \WP_REST_Response
	 */
	public static function revoke( $request ) {
		return rest_ensure_response( array( 'revoked' => ( new Application_Store() )->revoke( (int) $request['id'] ) ) );
	}

	/**
	 * Return every map icon, not a team palette.
	 *
	 * @return \WP_REST_Response
	 */
	public static function icons() {
		$items = array();
		foreach ( \TacNav\Maps\Catalog::get_icons() as $icon ) {
			$items[] = array(
				'id'    => (int) $icon['id'],
				'title' => (string) $icon['title'],
				'url'   => esc_url_raw( (string) $icon['url'] ),
			);
		}
		return rest_ensure_response( $items );
	}

	/**
	 * Public release metadata safe for devices.
	 *
	 * @return \WP_REST_Response
	 */
	public static function release() {
		return rest_ensure_response( Admin::release_metadata() );
	}

	/**
	 * Register, rotate, or clear the active installation's FCM token.
	 *
	 * @param \WP_REST_Request $request Request.
	 * @return \WP_REST_Response|\WP_Error
	 */
	public static function register_fcm( $request ) {
		$context = Application_Context::resolve( $request );
		if ( is_wp_error( $context ) ) {
			return $context;
		}
		$token = sanitize_text_field( (string) $request->get_param( 'token' ) );
		$ok    = ( new Application_Store() )->set_fcm_token( (int) $context['installation_id'], $token );
		return rest_ensure_response( array( 'registered' => $ok && '' !== $token ) );
	}

	/**
	 * Submit an addressed batch.
	 *
	 * @param \WP_REST_Request $request Request.
	 * @return \WP_REST_Response|\WP_Error
	 */
	public static function submit_event( $request ) {
		$context = Application_Context::resolve( $request );
		if ( is_wp_error( $context ) ) {
			return $context;
		}
		$input  = $request->get_json_params();
		$result = ( new Mailbox() )->submit( $context, $input );
		return is_wp_error( $result ) ? $result : rest_ensure_response( $result );
	}

	/**
	 * Read pending mailbox rows.
	 *
	 * @param \WP_REST_Request $request Request.
	 * @return \WP_REST_Response|\WP_Error
	 */
	public static function inbox( $request ) {
		$context = Application_Context::resolve( $request );
		if ( is_wp_error( $context ) ) {
			return $context;
		}
		return rest_ensure_response( ( new Mailbox() )->inbox( $context, (int) $request->get_param( 'cursor' ), (int) $request->get_param( 'limit' ) ) );
	}

	/**
	 * Acknowledge processed deliveries.
	 *
	 * @param \WP_REST_Request $request Request.
	 * @return \WP_REST_Response|\WP_Error
	 */
	public static function acknowledge( $request ) {
		$context = Application_Context::resolve( $request );
		if ( is_wp_error( $context ) ) {
			return $context;
		}
		$ids = $request->get_param( 'delivery_ids' );
		return rest_ensure_response( array( 'acknowledged' => ( new Mailbox() )->acknowledge( $context, is_array( $ids ) ? $ids : array() ) ) );
	}
}
