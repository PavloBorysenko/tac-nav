<?php
/**
 * Signed guest tokens.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Team id plus a short HMAC bound to one map.
 */
class Guest_Token {

	/**
	 * Build a token.
	 *
	 * @param int    $map_id  Map ID.
	 * @param int    $team_id Team ID.
	 * @param string $salt    Site secret.
	 * @return string
	 */
	public static function token( $map_id, $team_id, $salt ) {
		$team_id = (int) $team_id;
		return $team_id . '.' . self::signature( $map_id, $team_id, $salt );
	}

	/**
	 * Team named by a valid token, or 0.
	 *
	 * @param string           $token    Raw token.
	 * @param int              $map_id   Map ID.
	 * @param array<int, int>  $allowed  Team IDs listed on the map.
	 * @param string           $salt     Site secret.
	 * @return int
	 */
	public static function team_from_token( $token, $map_id, $allowed, $salt ) {
		$parts = explode( '.', (string) $token, 2 );
		if ( 2 !== count( $parts ) ) {
			return 0;
		}

		$team_id = (int) $parts[0];
		$allowed = array_map( 'intval', $allowed );
		if ( $team_id <= 0 || ! in_array( $team_id, $allowed, true ) ) {
			return 0;
		}

		$expected = self::signature( $map_id, $team_id, $salt );
		if ( ! hash_equals( $expected, $parts[1] ) ) {
			return 0;
		}

		return $team_id;
	}

	/**
	 * Truncated HMAC of map and team.
	 *
	 * @param int    $map_id  Map ID.
	 * @param int    $team_id Team ID.
	 * @param string $salt    Site secret.
	 * @return string
	 */
	public static function signature( $map_id, $team_id, $salt ) {
		return substr( hash_hmac( 'sha256', (int) $map_id . '|' . (int) $team_id, (string) $salt ), 0, 16 );
	}
}
