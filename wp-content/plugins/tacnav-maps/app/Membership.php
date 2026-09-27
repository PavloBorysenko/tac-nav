<?php
/**
 * Player team membership and self-points.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * One team per player, stored on the user.
 */
class Membership {

	const TEAM_META = 'tacnav_team_id';
	const SELF_META = 'tacnav_self_points';
	const AVATAR    = 'tacnav_avatar_id';

	/**
	 * Next membership map after saving one team's roster.
	 *
	 * @param int               $team_id  Team being saved.
	 * @param array<int, int>   $selected Player IDs that should belong to it.
	 * @param array<int, int>   $current  User ID => current team ID.
	 * @return array<int, int>
	 */
	public static function reassign( $team_id, $selected, $current ) {
		$team_id  = (int) $team_id;
		$selected = array_values( array_unique( array_map( 'intval', $selected ) ) );
		$next     = array();
		foreach ( $current as $user_id => $existing ) {
			$next[ (int) $user_id ] = (int) $existing;
		}
		foreach ( $next as $user_id => $existing ) {
			if ( $existing === $team_id && ! in_array( $user_id, $selected, true ) ) {
				$next[ $user_id ] = 0;
			}
		}
		foreach ( $selected as $user_id ) {
			if ( $user_id > 0 ) {
				$next[ $user_id ] = $team_id;
			}
		}
		return $next;
	}

	/**
	 * Object id stored as this player's self-point on a map.
	 *
	 * @param array<int|string, mixed> $points User meta map.
	 * @param int                      $map_id Map ID.
	 * @return int
	 */
	public static function previous_self_id( $points, $map_id ) {
		$map_id = (int) $map_id;
		foreach ( $points as $key => $object_id ) {
			if ( (int) $key === $map_id ) {
				return (int) $object_id;
			}
		}
		return 0;
	}

	/**
	 * Player's team, or 0.
	 *
	 * @param int $user_id User ID.
	 * @return int
	 */
	public static function team_id( $user_id ) {
		return (int) get_user_meta( (int) $user_id, self::TEAM_META, true );
	}

	/**
	 * Whether the user has the Player role.
	 *
	 * @param int $user_id User ID.
	 * @return bool
	 */
	public static function is_player( $user_id ) {
		$user = get_userdata( (int) $user_id );
		return $user && in_array( Roles::ROLE_PLAYER, (array) $user->roles, true );
	}

	/**
	 * Published map lists this player's team.
	 *
	 * @param int $map_id Map ID.
	 * @param int $user_id User ID. Current user when 0.
	 * @return bool
	 */
	public static function player_can_open( $map_id, $user_id = 0 ) {
		$user_id = $user_id > 0 ? (int) $user_id : get_current_user_id();
		if ( ! self::is_player( $user_id ) ) {
			return false;
		}
		$map = Catalog::get_map( (int) $map_id );
		if ( ! $map || 'publish' !== $map['status'] ) {
			return false;
		}
		$team = self::team_id( $user_id );
		return $team > 0 && in_array( $team, $map['team_ids'], true );
	}

	/**
	 * Users with the Player role and their current team.
	 *
	 * @return array<int, int>
	 */
	public static function current_assignments() {
		$users = get_users(
			array(
				'role'   => Roles::ROLE_PLAYER,
				'fields' => array( 'ID' ),
				'number' => 500,
			)
		);
		$out   = array();
		foreach ( $users as $user ) {
			$out[ (int) $user->ID ] = self::team_id( (int) $user->ID );
		}
		return $out;
	}

	/**
	 * Write the roster for one team.
	 *
	 * @param int             $team_id  Team ID.
	 * @param array<int, int> $selected Player IDs.
	 * @return void
	 */
	public static function save_roster( $team_id, $selected ) {
		$next = self::reassign( $team_id, $selected, self::current_assignments() );
		foreach ( $next as $user_id => $assigned ) {
			if ( $assigned > 0 ) {
				update_user_meta( $user_id, self::TEAM_META, $assigned );
			} else {
				delete_user_meta( $user_id, self::TEAM_META );
			}
		}
	}

	/**
	 * Clear players who belonged to a deleted team.
	 *
	 * @param int $team_id Team ID.
	 * @return void
	 */
	public static function clear_team( $team_id ) {
		$team_id = (int) $team_id;
		foreach ( self::current_assignments() as $user_id => $assigned ) {
			if ( $assigned === $team_id ) {
				delete_user_meta( $user_id, self::TEAM_META );
			}
		}
	}

	/**
	 * Player IDs currently on a team.
	 *
	 * @param int $team_id Team ID.
	 * @return array<int, int>
	 */
	public static function player_ids_for_team( $team_id ) {
		$team_id = (int) $team_id;
		$ids     = array();
		foreach ( self::current_assignments() as $user_id => $assigned ) {
			if ( $assigned === $team_id ) {
				$ids[] = $user_id;
			}
		}
		return $ids;
	}

	/**
	 * Self-point map for a user.
	 *
	 * @param int $user_id User ID.
	 * @return array<int, int>
	 */
	public static function self_points( $user_id ) {
		$raw = get_user_meta( (int) $user_id, self::SELF_META, true );
		if ( ! is_array( $raw ) ) {
			return array();
		}
		$out = array();
		foreach ( $raw as $map_id => $object_id ) {
			$out[ (int) $map_id ] = (int) $object_id;
		}
		return $out;
	}

	/**
	 * Remember the current self-point.
	 *
	 * @param int $user_id   User ID.
	 * @param int $map_id    Map ID.
	 * @param int $object_id Object ID.
	 * @return void
	 */
	public static function set_self_point( $user_id, $map_id, $object_id ) {
		$points                  = self::self_points( $user_id );
		$points[ (int) $map_id ] = (int) $object_id;
		update_user_meta( (int) $user_id, self::SELF_META, $points );
	}

	/**
	 * Whether this row is the author's current self-point.
	 *
	 * @param array<string, mixed> $geo Geo object.
	 * @return bool
	 */
	public static function is_self_point( $geo ) {
		$author = isset( $geo['created_by_user_id'] ) ? (int) $geo['created_by_user_id'] : 0;
		$map_id = isset( $geo['map_id'] ) ? (int) $geo['map_id'] : 0;
		$id     = isset( $geo['id'] ) ? (int) $geo['id'] : 0;
		if ( $author <= 0 || $map_id <= 0 || $id <= 0 ) {
			return false;
		}
		return self::previous_self_id( self::self_points( $author ), $map_id ) === $id;
	}

	/**
	 * Avatar URL for a user, or empty.
	 *
	 * @param int $user_id User ID.
	 * @return string
	 */
	public static function avatar_url( $user_id ) {
		$attachment = (int) get_user_meta( (int) $user_id, self::AVATAR, true );
		if ( $attachment <= 0 ) {
			return '';
		}
		$url = wp_get_attachment_image_url( $attachment, 'thumbnail' );
		return $url ? $url : '';
	}
}
