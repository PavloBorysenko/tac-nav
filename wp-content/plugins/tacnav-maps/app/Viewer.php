<?php
/**
 * Who is opening a map.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Staff, listed player, or guest token.
 */
class Viewer {

	/**
	 * Viewer kind for a map.
	 *
	 * @param array<string, mixed> $map   Map passport.
	 * @param string               $token Guest token.
	 * @return array<string, mixed>
	 */
	public static function resolve( $map, $token ) {
		$user_id   = get_current_user_id();
		$published = isset( $map['status'] ) && 'publish' === $map['status'];
		$teams     = isset( $map['team_ids'] ) && is_array( $map['team_ids'] ) ? $map['team_ids'] : array();

		if ( $user_id && Roles::is_staff( $user_id ) ) {
			return array(
				'kind'    => 'staff',
				'team_id' => 0,
			);
		}

		if ( $user_id && Membership::is_player( $user_id ) && $published ) {
			$team = Membership::team_id( $user_id );
			if ( $team > 0 && in_array( $team, $teams, true ) ) {
				return array(
					'kind'    => 'player',
					'team_id' => $team,
				);
			}
		}

		if ( $user_id || ! $published ) {
			return array(
				'kind'    => '',
				'team_id' => 0,
			);
		}

		$guest = Guest_Token::team_from_token( $token, (int) $map['id'], $teams, wp_salt( 'auth' ) );
		if ( $guest > 0 ) {
			return array(
				'kind'    => 'guest',
				'team_id' => $guest,
			);
		}

		return array(
			'kind'    => '',
			'team_id' => 0,
		);
	}

	/**
	 * Access viewer array.
	 *
	 * @param array<string, mixed> $resolved Result of resolve().
	 * @return array<string, mixed>
	 */
	public static function context( $resolved ) {
		if ( 'guest' === $resolved['kind'] ) {
			return array(
				'user_id'          => 0,
				'is_administrator' => false,
				'is_organizer'     => false,
				'is_staff'         => false,
				'team_ids'         => array( (int) $resolved['team_id'] ),
				'capabilities'     => array(),
			);
		}

		return Access::viewer_from_user( get_current_user_id() );
	}
}
