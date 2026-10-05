<?php
/**
 * Who shares one map picture.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Audience keys for staff and for each team.
 */
class Picture_Audience {

	/**
	 * Stable key for a viewer. Players and guests of one team share a key.
	 *
	 * @param string $kind            staff|player|guest.
	 * @param int    $team_id         Team ID for a player or guest.
	 * @param bool   $include_expired Staff expired representation.
	 * @return string
	 */
	public static function key( $kind, $team_id, $include_expired ) {
		if ( 'staff' === $kind ) {
			return $include_expired ? 'staff-expired' : 'staff';
		}

		$team_id = (int) $team_id;
		if ( $team_id <= 0 ) {
			return '';
		}

		return 'team-' . $team_id;
	}

	/**
	 * Whether a guest token may refresh this map.
	 *
	 * @param string          $token   Raw token.
	 * @param int             $map_id  Map ID.
	 * @param array<int, int> $allowed Teams listed on the map.
	 * @param string          $salt    Site secret.
	 * @return bool
	 */
	public static function guest_may_refresh( $token, $map_id, $allowed, $salt ) {
		return Guest_Token::team_from_token( $token, (int) $map_id, $allowed, $salt ) > 0;
	}

	/**
	 * Synthetic viewer for a stored picture.
	 *
	 * @param string $audience Audience key.
	 * @return array<string, mixed>|null
	 */
	public static function viewer( $audience ) {
		if ( 'staff' === $audience || 'staff-expired' === $audience ) {
			return array(
				'user_id'          => 0,
				'is_administrator' => false,
				'is_organizer'     => false,
				'is_staff'         => true,
				'team_ids'         => array(),
				'capabilities'     => array(),
			);
		}

		if ( 1 !== preg_match( '/^team-(\d+)$/', (string) $audience, $match ) ) {
			return null;
		}

		$team_id = (int) $match[1];
		if ( $team_id <= 0 ) {
			return null;
		}

		return array(
			'user_id'          => 0,
			'is_administrator' => false,
			'is_organizer'     => false,
			'is_staff'         => false,
			'team_ids'         => array( $team_id ),
			'capabilities'     => array(),
		);
	}

	/**
	 * Whether this audience includes expired rows.
	 *
	 * @param string $audience Audience key.
	 * @return bool
	 */
	public static function includes_expired( $audience ) {
		return 'staff-expired' === $audience;
	}

	/**
	 * Stored stamp still matches the form the editor opened.
	 *
	 * @param string $stored Current row timestamp.
	 * @param string $opened Timestamp the form opened with.
	 * @return bool
	 */
	public static function same_stamp( $stored, $opened ) {
		return '' !== (string) $opened && (string) $stored === (string) $opened;
	}
}
