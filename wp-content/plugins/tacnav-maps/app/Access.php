<?php
/**
 * Visibility and edit resolvers.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Default access rules plus filter hooks.
 */
class Access {

	/**
	 * Whether the object is past its TTL.
	 *
	 * @param array<string, mixed> $geo Geo object.
	 * @param int                  $now    Unix timestamp.
	 * @return bool
	 */
	public static function is_expired( $geo, $now ) {
		$expires = isset( $geo['expires_at'] ) ? (string) $geo['expires_at'] : '';
		if ( '' === $expires ) {
			return false;
		}

		$ts = strtotime( $expires );
		return false !== $ts && $ts <= $now;
	}

	/**
	 * Default visibility without hooks.
	 *
	 * @param array<string, mixed> $geo Geo object.
	 * @param array<string, mixed> $viewer Viewer context.
	 * @return bool
	 */
	public static function default_visible( $geo, $viewer ) {
		if ( ! empty( $viewer['is_staff'] ) ) {
			return true;
		}

		$visible = isset( $geo['visible_team_ids'] ) && is_array( $geo['visible_team_ids'] )
			? $geo['visible_team_ids']
			: array();
		$visible = array_map( 'intval', $visible );
		$visible = array_values( array_filter( $visible ) );

		if ( array() === $visible ) {
			return true;
		}

		$teams = isset( $viewer['team_ids'] ) && is_array( $viewer['team_ids'] )
			? array_map( 'intval', $viewer['team_ids'] )
			: array();

		return (bool) array_intersect( $visible, $teams );
	}

	/**
	 * Default edit permission without hooks.
	 *
	 * @param array<string, mixed> $geo Geo object.
	 * @param array<string, mixed> $viewer Viewer context.
	 * @return bool
	 */
	public static function default_editable( $geo, $viewer ) {
		if ( ! empty( $geo['self_point'] ) ) {
			return false;
		}

		if ( ! empty( $viewer['is_staff'] ) ) {
			return true;
		}

		$origin = isset( $geo['origin'] ) ? (string) $geo['origin'] : '';
		if ( 'team' !== $origin ) {
			return false;
		}

		$owner = isset( $geo['owner_team_id'] ) ? (int) $geo['owner_team_id'] : 0;
		if ( $owner <= 0 ) {
			return false;
		}

		$teams = isset( $viewer['team_ids'] ) && is_array( $viewer['team_ids'] )
			? array_map( 'intval', $viewer['team_ids'] )
			: array();

		return in_array( $owner, $teams, true );
	}

	/**
	 * Visibility after hooks.
	 *
	 * @param array<string, mixed> $geo  Geo object.
	 * @param array<string, mixed> $viewer  Viewer context.
	 * @param array<string, mixed> $request Request context.
	 * @return bool
	 */
	public static function is_visible( $geo, $viewer, $request ) {
		$default = self::default_visible( $geo, $viewer );

		/**
		 * Filters whether a geo object is visible.
		 *
		 * @param bool                 $visible Default result.
		 * @param array<string, mixed> $geo  Geo object.
		 * @param array<string, mixed> $viewer  Viewer context.
		 * @param array<string, mixed> $request Request context.
		 */
		return (bool) apply_filters( 'tacnav_geo_object_visible', $default, $geo, $viewer, $request );
	}

	/**
	 * Edit permission after hooks.
	 *
	 * @param array<string, mixed> $geo  Geo object.
	 * @param array<string, mixed> $viewer  Viewer context.
	 * @param array<string, mixed> $request Request context.
	 * @return bool
	 */
	public static function is_editable( $geo, $viewer, $request ) {
		if ( ! empty( $geo['self_point'] ) ) {
			return false;
		}

		$default = self::default_editable( $geo, $viewer );

		/**
		 * Filters whether a geo object is editable.
		 *
		 * @param bool                 $editable Default result.
		 * @param array<string, mixed> $geo   Geo object.
		 * @param array<string, mixed> $viewer   Viewer context.
		 * @param array<string, mixed> $request  Request context.
		 */
		return (bool) apply_filters( 'tacnav_geo_object_editable', $default, $geo, $viewer, $request );
	}

	/**
	 * Viewer context for a user.
	 *
	 * @param int $user_id User ID.
	 * @return array<string, mixed>
	 */
	public static function viewer_from_user( $user_id ) {
		$user_id = (int) $user_id;
		$user    = $user_id > 0 ? get_userdata( $user_id ) : false;
		$staff   = $user_id > 0 && Roles::is_staff( $user_id );
		$roles   = $user ? (array) $user->roles : array();
		$caps    = $user ? array_keys( array_filter( (array) $user->allcaps ) ) : array();

		$team = $user_id > 0 ? Membership::team_id( $user_id ) : 0;

		return array(
			'user_id'          => $user_id,
			'is_administrator' => $user_id > 0 && user_can( $user_id, 'manage_options' ),
			'is_organizer'     => in_array( Roles::ROLE, $roles, true ),
			'is_staff'         => $staff,
			'team_ids'         => $team > 0 ? array( $team ) : array(),
			'capabilities'     => $caps,
		);
	}

	/**
	 * Request context.
	 *
	 * @param string $surface studio|public|live.
	 * @param int    $map_id  Map post ID.
	 * @return array<string, mixed>
	 */
	public static function request_context( $surface, $map_id ) {
		return array(
			'surface' => (string) $surface,
			'map_id'  => (int) $map_id,
		);
	}
}
