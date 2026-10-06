<?php
/**
 * Staff REST routes.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Geo-object REST API.
 */
class REST {

	const NS = 'tacnav/v1';

	/**
	 * Register routes.
	 *
	 * @return void
	 */
	public static function register() {
		register_rest_route(
			self::NS,
			'/maps/(?P<map_id>\d+)/studio',
			array(
				'methods'             => \WP_REST_Server::READABLE,
				'callback'            => array( __CLASS__, 'get_studio' ),
				'permission_callback' => array( __CLASS__, 'can_canvas' ),
				'args'                => array(
					'map_id' => array(
						'type'              => 'integer',
						'required'          => true,
						'sanitize_callback' => 'absint',
					),
				),
			)
		);

		register_rest_route(
			self::NS,
			'/maps/(?P<map_id>\d+)/objects',
			array(
				array(
					'methods'             => \WP_REST_Server::READABLE,
					'callback'            => array( __CLASS__, 'list_objects' ),
					'permission_callback' => array( __CLASS__, 'can_read' ),
					'args'                => self::map_args(),
				),
				array(
					'methods'             => \WP_REST_Server::CREATABLE,
					'callback'            => array( __CLASS__, 'create_object' ),
					'permission_callback' => array( __CLASS__, 'can_write' ),
					'args'                => self::map_args(),
				),
			)
		);

		register_rest_route(
			self::NS,
			'/maps/(?P<map_id>\d+)/objects/self',
			array(
				'methods'             => \WP_REST_Server::CREATABLE,
				'callback'            => array( __CLASS__, 'mark_self' ),
				'permission_callback' => array( __CLASS__, 'can_write' ),
				'args'                => self::map_args(),
			)
		);

		register_rest_route(
			self::NS,
			'/maps/(?P<map_id>\d+)/objects/purge-expired',
			array(
				'methods'             => \WP_REST_Server::CREATABLE,
				'callback'            => array( __CLASS__, 'purge_expired' ),
				'permission_callback' => array( __CLASS__, 'can_purge' ),
				'args'                => self::map_args(),
			)
		);

		register_rest_route(
			self::NS,
			'/maps/(?P<map_id>\d+)/objects/(?P<object_id>\d+)',
			array(
				array(
					'methods'             => \WP_REST_Server::EDITABLE,
					'callback'            => array( __CLASS__, 'update_object' ),
					'permission_callback' => array( __CLASS__, 'can_write' ),
					'args'                => self::object_args(),
				),
				array(
					'methods'             => \WP_REST_Server::DELETABLE,
					'callback'            => array( __CLASS__, 'delete_object' ),
					'permission_callback' => array( __CLASS__, 'can_write' ),
					'args'                => self::object_args(),
				),
			)
		);
	}

	/**
	 * Studio payload.
	 *
	 * @param \WP_REST_Request $request Request.
	 * @return \WP_REST_Response|\WP_Error
	 */
	public static function get_studio( $request ) {
		$map_id = (int) $request['map_id'];
		$map    = Catalog::get_map( $map_id );
		if ( ! $map ) {
			return new \WP_Error( 'tacnav_map_missing', __( 'Map not found.', 'tacnav-maps' ), array( 'status' => 404 ) );
		}

		return rest_ensure_response(
			array(
				'map'     => $map,
				'teams'   => Catalog::teams_on_map( $map ),
				'icons'   => Catalog::get_icons(),
				'objects' => self::visible_objects(
					$map_id,
					false,
					array(
						'kind'    => 'staff',
						'team_id' => 0,
					)
				),
			)
		);
	}

	/**
	 * List objects.
	 *
	 * @param \WP_REST_Request $request Request.
	 * @return \WP_REST_Response|\WP_Error
	 */
	public static function list_objects( $request ) {
		$map_id = (int) $request['map_id'];
		$map    = Catalog::get_map( $map_id );
		if ( ! $map ) {
			return new \WP_Error( 'tacnav_map_missing', __( 'Map not found.', 'tacnav-maps' ), array( 'status' => 404 ) );
		}

		$resolved = Viewer::resolve( $map, self::token_from_request( $request ) );
		if ( 'guest' === $resolved['kind'] && ! Picture_Audience::guest_may_refresh( self::token_from_request( $request ), $map_id, isset( $map['team_ids'] ) && is_array( $map['team_ids'] ) ? $map['team_ids'] : array(), wp_salt( 'auth' ) ) ) {
			return new \WP_Error( 'tacnav_geo_forbidden', __( 'You cannot view this map.', 'tacnav-maps' ), array( 'status' => 403 ) );
		}

		$include  = 'staff' === $resolved['kind'] && rest_sanitize_boolean( $request->get_param( 'include_expired' ) );
		$audience = Picture_Audience::key( (string) $resolved['kind'], (int) $resolved['team_id'], $include );
		$current  = Picture_Version::current( $map_id );
		$client   = Picture_Version::from_header( $request->get_header( 'if_none_match' ) );
		$lookup   = new Picture_Lookup( new Transient_Picture_Store() );
		$result   = $lookup->read(
			$map_id,
			$audience,
			$client,
			$current,
			static function () use ( $map_id, $audience ) {
				return self::objects_for_audience( $map_id, $audience );
			}
		);

		$response = new \WP_REST_Response( 304 === $result['status'] ? null : $result['objects'], (int) $result['status'] );
		$response->header( 'ETag', '"' . (int) $result['version'] . '"' );
		$response->header( 'Cache-Control', 'private, no-cache' );
		return $response;
	}

	/**
	 * Create object.
	 *
	 * @param \WP_REST_Request $request Request.
	 * @return \WP_REST_Response|\WP_Error
	 */
	public static function create_object( $request ) {
		$map_id = (int) $request['map_id'];
		$map    = Catalog::get_map( $map_id );
		if ( ! $map ) {
			return new \WP_Error( 'tacnav_map_missing', __( 'Map not found.', 'tacnav-maps' ), array( 'status' => 404 ) );
		}

		$resolved = Viewer::resolve( $map, self::token_from_request( $request ) );
		$payload  = self::payload_for_viewer( $request, $map, $resolved, true );
		if ( is_wp_error( $payload ) ) {
			return $payload;
		}

		$store = new Geo_Store();
		$id    = $store->insert( $payload );
		if ( is_wp_error( $id ) ) {
			return $id;
		}

		$object = $store->get( $id );
		self::publish_picture( $map_id );
		return rest_ensure_response( self::with_can_edit( $object, $map_id ) );
	}

	/**
	 * Update object.
	 *
	 * @param \WP_REST_Request $request Request.
	 * @return \WP_REST_Response|\WP_Error
	 */
	public static function update_object( $request ) {
		$store  = new Geo_Store();
		$object = $store->get( (int) $request['object_id'] );
		if ( ! $object || (int) $object['map_id'] !== (int) $request['map_id'] ) {
			return new \WP_Error( 'tacnav_geo_missing', __( 'Geo object not found.', 'tacnav-maps' ), array( 'status' => 404 ) );
		}

		$map = Catalog::get_map( (int) $request['map_id'] );
		if ( ! $map ) {
			return new \WP_Error( 'tacnav_map_missing', __( 'Map not found.', 'tacnav-maps' ), array( 'status' => 404 ) );
		}

		$object['self_point'] = Membership::is_self_point( $object );
		$resolved             = Viewer::resolve( $map, self::token_from_request( $request ) );
		$viewer               = Viewer::context( $resolved );
		$request_ctx          = Access::request_context( 'studio', (int) $request['map_id'] );
		if ( ! Access::is_editable( $object, $viewer, $request_ctx ) ) {
			return new \WP_Error( 'tacnav_geo_forbidden', __( 'You cannot edit this object.', 'tacnav-maps' ), array( 'status' => 403 ) );
		}

		$payload = self::payload_for_viewer( $request, $map, $resolved, false );
		if ( is_wp_error( $payload ) ) {
			return $payload;
		}

		$body     = self::body( $request );
		$expected = isset( $body['updated_at'] ) ? sanitize_text_field( (string) $body['updated_at'] ) : '';
		$updated  = $store->update( (int) $object['id'], $payload, $expected );
		if ( is_wp_error( $updated ) ) {
			return $updated;
		}

		self::publish_picture( (int) $request['map_id'] );
		return rest_ensure_response( self::with_can_edit( $store->get( (int) $object['id'] ), (int) $request['map_id'] ) );
	}

	/**
	 * Delete object.
	 *
	 * @param \WP_REST_Request $request Request.
	 * @return \WP_REST_Response|\WP_Error
	 */
	public static function delete_object( $request ) {
		$store  = new Geo_Store();
		$object = $store->get( (int) $request['object_id'] );
		if ( ! $object || (int) $object['map_id'] !== (int) $request['map_id'] ) {
			return new \WP_Error( 'tacnav_geo_missing', __( 'Geo object not found.', 'tacnav-maps' ), array( 'status' => 404 ) );
		}

		$object['self_point'] = Membership::is_self_point( $object );
		$viewer               = Access::viewer_from_user( get_current_user_id() );
		$request_ctx          = Access::request_context( 'studio', (int) $request['map_id'] );
		if ( ! Access::is_editable( $object, $viewer, $request_ctx ) ) {
			return new \WP_Error( 'tacnav_geo_forbidden', __( 'You cannot delete this object.', 'tacnav-maps' ), array( 'status' => 403 ) );
		}

		$deleted = $store->delete( (int) $object['id'] );
		if ( $deleted ) {
			self::publish_picture( (int) $request['map_id'] );
		}
		return rest_ensure_response( array( 'deleted' => true ) );
	}

	/**
	 * Place or replace the current player's self-point.
	 *
	 * @param \WP_REST_Request $request Request.
	 * @return \WP_REST_Response|\WP_Error
	 */
	public static function mark_self( $request ) {
		$map_id = (int) $request['map_id'];
		$map    = Catalog::get_map( $map_id );
		if ( ! $map ) {
			return new \WP_Error( 'tacnav_map_missing', __( 'Map not found.', 'tacnav-maps' ), array( 'status' => 404 ) );
		}

		$resolved = Viewer::resolve( $map, '' );
		if ( 'player' !== $resolved['kind'] ) {
			return new \WP_Error( 'tacnav_geo_forbidden', __( 'You cannot mark yourself on this map.', 'tacnav-maps' ), array( 'status' => 403 ) );
		}

		$body = self::body( $request );
		if ( ! isset( $body['lat'], $body['lng'] ) ) {
			return new \WP_Error( 'tacnav_geo_point', __( 'Latitude and longitude are required.', 'tacnav-maps' ), array( 'status' => 400 ) );
		}

		$user    = wp_get_current_user();
		$store   = new Geo_Store();
		$points  = Membership::self_points( (int) $user->ID );
		$old_id  = Membership::previous_self_id( $points, $map_id );
		$payload = array(
			'map_id'             => $map_id,
			'kind'               => 'marker',
			'geometry'           => array(
				'lat' => $body['lat'],
				'lng' => $body['lng'],
			),
			'owner_team_id'      => (int) $resolved['team_id'],
			'visible_team_ids'   => array( (int) $resolved['team_id'] ),
			'origin'             => 'team',
			'icon_id'            => null,
			'title'              => $user->display_name,
			'description'        => '',
			'ttl_minutes'        => 1,
			'created_by_user_id' => (int) $user->ID,
		);
		$payload = self::apply_ttl( $payload, true );
		$id      = $store->insert( $payload );
		if ( is_wp_error( $id ) ) {
			return $id;
		}

		if ( $old_id > 0 && $old_id !== (int) $id ) {
			$store->delete( $old_id );
		}

		Membership::set_self_point( (int) $user->ID, $map_id, (int) $id );
		self::publish_picture( $map_id );
		return rest_ensure_response( self::with_can_edit( $store->get( (int) $id ), $map_id ) );
	}

	/**
	 * Purge expired objects on one map.
	 *
	 * @param \WP_REST_Request $request Request.
	 * @return \WP_REST_Response|\WP_Error
	 */
	public static function purge_expired( $request ) {
		$map_id = (int) $request['map_id'];
		if ( ! Catalog::get_map( $map_id ) ) {
			return new \WP_Error( 'tacnav_map_missing', __( 'Map not found.', 'tacnav-maps' ), array( 'status' => 404 ) );
		}

		$store   = new Geo_Store();
		$deleted = $store->purge_expired( $map_id, gmdate( 'Y-m-d H:i:s' ) );
		self::publish_picture( $map_id );
		return rest_ensure_response( array( 'deleted' => $deleted ) );
	}

	/**
	 * Permission: staff geo or a player on this map.
	 *
	 * @param \WP_REST_Request $request Request.
	 * @return bool
	 */
	public static function can_write( $request ) {
		if ( current_user_can( Roles::CAP_GEO ) ) {
			return true;
		}
		return Membership::player_can_open( (int) $request['map_id'] );
	}

	/**
	 * Permission: staff, listed player, or valid guest token.
	 *
	 * @param \WP_REST_Request $request Request.
	 * @return bool
	 */
	public static function can_read( $request ) {
		if ( self::can_write( $request ) ) {
			return true;
		}
		$map = Catalog::get_map( (int) $request['map_id'] );
		if ( ! $map ) {
			return false;
		}
		$resolved = Viewer::resolve( $map, self::token_from_request( $request ) );
		return 'guest' === $resolved['kind'];
	}

	/**
	 * Permission: purge.
	 *
	 * @return bool
	 */
	public static function can_purge() {
		return current_user_can( Roles::CAP_PURGE );
	}

	/**
	 * Guest token from the request.
	 *
	 * @param \WP_REST_Request $request Request.
	 * @return string
	 */
	public static function token_from_request( $request ) {
		$token = $request->get_param( 't' );
		return is_string( $token ) ? $token : '';
	}

	/**
	 * Publish stored pictures after a successful write.
	 *
	 * @param int $map_id Map ID.
	 * @return void
	 */
	private static function publish_picture( $map_id ) {
		$map = Catalog::get_map( (int) $map_id );
		if ( ! $map ) {
			return;
		}

		Picture_Publisher::publish( (int) $map_id, $map );
	}

	/**
	 * Visible objects for a resolved viewer.
	 *
	 * @param int                  $map_id          Map ID.
	 * @param bool                 $include_expired Include expired.
	 * @param array<string, mixed> $resolved        Viewer kind.
	 * @return array<int, array<string, mixed>>
	 */
	public static function visible_objects( $map_id, $include_expired, $resolved ) {
		$viewer = Viewer::context( $resolved );
		return self::visible_objects_for( $map_id, $include_expired && 'staff' === $resolved['kind'], $viewer, 'guest' === $resolved['kind'] ? 'public' : 'studio' );
	}

	/**
	 * Object list for one stored audience.
	 *
	 * @param int    $map_id   Map ID.
	 * @param string $audience Audience key.
	 * @return array<int, array<string, mixed>>
	 */
	public static function objects_for_audience( $map_id, $audience ) {
		$viewer = Picture_Audience::viewer( $audience );
		if ( null === $viewer ) {
			return array();
		}

		return self::visible_objects_for( $map_id, Picture_Audience::includes_expired( $audience ), $viewer, 'studio' );
	}

	/**
	 * Visible objects for an explicit viewer.
	 *
	 * @param int                  $map_id          Map ID.
	 * @param bool                 $include_expired Include expired.
	 * @param array<string, mixed> $viewer          Viewer context.
	 * @param string               $surface         studio|public.
	 * @return array<int, array<string, mixed>>
	 */
	public static function visible_objects_for( $map_id, $include_expired, $viewer, $surface ) {
		$store   = new Geo_Store();
		$request = Access::request_context( $surface, $map_id );
		$rows    = $store->list_for_map(
			$map_id,
			array(
				'include_expired' => $include_expired,
				'viewer'          => $viewer,
				'request'         => $request,
			)
		);

		$out = array();
		foreach ( $rows as $object ) {
			if ( Access::is_visible( $object, $viewer, $request ) ) {
				$out[] = self::decorate( $object, $viewer, $request );
			}
		}

		return $out;
	}

	/**
	 * Attach edit flag, self-point, and avatar.
	 *
	 * @param array<string, mixed> $geo     Object row.
	 * @param array<string, mixed> $viewer  Viewer.
	 * @param array<string, mixed> $request Request context.
	 * @return array<string, mixed>
	 */
	public static function decorate( $geo, $viewer, $request ) {
		$geo['self_point'] = Membership::is_self_point( $geo );
		if ( $geo['self_point'] ) {
			$geo['avatar_url'] = Membership::avatar_url( (int) $geo['created_by_user_id'] );
		}
		$geo['can_edit'] = Access::is_editable( $geo, $viewer, $request );
		return $geo;
	}

	/**
	 * Attach can_edit for the current studio viewer.
	 *
	 * @param array<string, mixed>|null $geo    Object row.
	 * @param int                       $map_id Map ID.
	 * @return array<string, mixed>|null
	 */
	private static function with_can_edit( $geo, $map_id ) {
		if ( ! $geo ) {
			return $geo;
		}

		$viewer  = Access::viewer_from_user( get_current_user_id() );
		$request = Access::request_context( 'studio', $map_id );
		return self::decorate( $geo, $viewer, $request );
	}

	/**
	 * JSON or form body.
	 *
	 * @param \WP_REST_Request $request Request.
	 * @return array<string, mixed>
	 */
	private static function body( $request ) {
		$body = $request->get_json_params();
		if ( array() === $body ) {
			return $request->get_params();
		}
		return $body;
	}

	/**
	 * Payload for staff or the current player.
	 *
	 * @param \WP_REST_Request     $request  Request.
	 * @param array<string, mixed> $map      Map passport.
	 * @param array<string, mixed> $resolved Viewer kind.
	 * @param bool                 $create   Creating.
	 * @return array<string, mixed>|\WP_Error
	 */
	private static function payload_for_viewer( $request, $map, $resolved, $create ) {
		if ( 'player' === $resolved['kind'] ) {
			$team    = Catalog::get_team( (int) $resolved['team_id'] );
			$palette = $team ? $team['palette_icon_ids'] : array();
			$normal  = Player_Write::normalize( self::body( $request ), (int) $resolved['team_id'], $palette );
			if ( '' !== $normal['error'] ) {
				return new \WP_Error( 'tacnav_geo_icon', __( 'That icon is not on your team palette.', 'tacnav-maps' ), array( 'status' => 400 ) );
			}
			$payload = array(
				'map_id'             => (int) $map['id'],
				'kind'               => $normal['kind'],
				'geometry'           => $normal['geometry'],
				'owner_team_id'      => $normal['owner_team_id'],
				'visible_team_ids'   => $normal['visible_team_ids'],
				'origin'             => 'team',
				'icon_id'            => $normal['icon_id'],
				'title'              => $normal['title'],
				'description'        => $normal['description'],
				'created_by_user_id' => get_current_user_id(),
			);
			if ( null !== $normal['ttl_minutes'] ) {
				$payload['ttl_minutes'] = $normal['ttl_minutes'];
			}
			if ( ! $create ) {
				unset( $payload['origin'] );
				unset( $payload['created_by_user_id'] );
			}
			return self::apply_ttl( $payload, $create );
		}

		return self::payload_from_request( $request, (int) $map['id'], $create );
	}

	/**
	 * Turn ttl_minutes into expires_at.
	 *
	 * @param array<string, mixed> $payload Payload.
	 * @param bool                 $create  Creating.
	 * @return array<string, mixed>
	 */
	private static function apply_ttl( $payload, $create ) {
		return Object_Expiry::apply_to_payload( $payload, $create, time() );
	}

	/**
	 * Permission: canvas.
	 *
	 * @return bool
	 */
	public static function can_canvas() {
		return current_user_can( Roles::CAP_CANVAS );
	}

	/**
	 * Body fields.
	 *
	 * @param \WP_REST_Request $request Request.
	 * @param int             $map_id  Map ID.
	 * @param bool            $create  Creating.
	 * @return array<string, mixed>
	 */
	private static function payload_from_request( $request, $map_id, $create ) {
		$body = $request->get_json_params();
		if ( array() === $body ) {
			$body = $request->get_params();
		}

		$payload = array(
			'map_id'             => $map_id,
			'kind'               => isset( $body['kind'] ) ? $body['kind'] : 'marker',
			'geometry'           => isset( $body['geometry'] ) && is_array( $body['geometry'] ) ? $body['geometry'] : array(),
			'owner_team_id'      => isset( $body['owner_team_id'] ) ? $body['owner_team_id'] : null,
			'visible_team_ids'   => isset( $body['visible_team_ids'] ) && is_array( $body['visible_team_ids'] ) ? $body['visible_team_ids'] : array(),
			'icon_id'            => isset( $body['icon_id'] ) ? $body['icon_id'] : null,
			'title'              => isset( $body['title'] ) ? $body['title'] : '',
			'description'        => isset( $body['description'] ) ? $body['description'] : '',
			'origin'             => 'staff',
			'created_by_user_id' => get_current_user_id(),
		);

		if ( array_key_exists( 'ttl_minutes', $body ) ) {
			$payload['ttl_minutes'] = $body['ttl_minutes'];
		}
		$payload = self::apply_ttl( $payload, $create );

		if ( ! $create && isset( $body['origin'] ) ) {
			unset( $payload['origin'] );
			unset( $payload['created_by_user_id'] );
		}

		return $payload;
	}

	/**
	 * Map ID args.
	 *
	 * @return array<string, array<string, mixed>>
	 */
	private static function map_args() {
		return array(
			'map_id' => array(
				'type'              => 'integer',
				'required'          => true,
				'sanitize_callback' => 'absint',
			),
		);
	}

	/**
	 * Object args.
	 *
	 * @return array<string, array<string, mixed>>
	 */
	private static function object_args() {
		return array_merge(
			self::map_args(),
			array(
				'object_id' => array(
					'type'              => 'integer',
					'required'          => true,
					'sanitize_callback' => 'absint',
				),
			)
		);
	}
}
