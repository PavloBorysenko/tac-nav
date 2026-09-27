<?php
/**
 * Pure player and guest rules.
 *
 * HTTP canvas, roster save, and registration need WordPress and are not covered here.
 *
 * @package TacNav_Maps
 */

use PHPUnit\Framework\TestCase;
use TacNav\Maps\Access;
use TacNav\Maps\Guest_Token;
use TacNav\Maps\Membership;
use TacNav\Maps\Player_Write;

require_once __DIR__ . '/bootstrap.php';

/**
 * @covers \TacNav\Maps\Guest_Token
 * @covers \TacNav\Maps\Player_Write
 * @covers \TacNav\Maps\Membership
 */
class TacNav_Maps_Player_Access_Test extends TestCase {

	/**
	 * A signed token names the listed team.
	 */
	public function test_token_names_listed_team() {
		$token = Guest_Token::token( 4, 7, 'salt' );

		$this->assertSame( 7, Guest_Token::team_from_token( $token, 4, array( 7, 8 ), 'salt' ) );
	}

	/**
	 * A team that is not on the map cannot use its token.
	 */
	public function test_token_rejects_unlisted_team() {
		$token = Guest_Token::token( 4, 7, 'salt' );

		$this->assertSame( 0, Guest_Token::team_from_token( $token, 4, array( 8 ), 'salt' ) );
	}

	/**
	 * A bad signature is rejected.
	 */
	public function test_token_rejects_bad_signature() {
		$this->assertSame( 0, Guest_Token::team_from_token( '7.deadbeef', 4, array( 7 ), 'salt' ) );
	}

	/**
	 * Saving Blue moves a Red player onto Blue.
	 */
	public function test_reassign_moves_player_onto_saved_team() {
		$next = Membership::reassign( 8, array( 1 ), array( 1 => 7, 2 => 8 ) );

		$this->assertSame( 8, $next[1] );
		$this->assertSame( 0, $next[2] );
	}

	/**
	 * A missing self-point id is zero.
	 */
	public function test_previous_self_id_is_zero_when_missing() {
		$this->assertSame( 0, Membership::previous_self_id( array(), 4 ) );
		$this->assertSame( 9, Membership::previous_self_id( array( '4' => 9 ), 4 ) );
	}

	/**
	 * Player writes are stamped to that team and reject other icons.
	 */
	public function test_player_write_stamps_team_and_rejects_outside_icons() {
		$ok = Player_Write::normalize(
			array(
				'kind'        => 'marker',
				'geometry'    => array( 'lat' => 1, 'lng' => 2 ),
				'icon_id'     => 3,
				'title'       => 'Flag',
				'ttl_minutes' => 0,
				'origin'      => 'staff',
			),
			7,
			array( 3 )
		);

		$this->assertSame( '', $ok['error'] );
		$this->assertSame( 7, $ok['owner_team_id'] );
		$this->assertSame( array( 7 ), $ok['visible_team_ids'] );
		$this->assertSame( 'team', $ok['origin'] );
		$this->assertSame( 0, $ok['ttl_minutes'] );

		$bad = Player_Write::normalize( array( 'icon_id' => 9 ), 7, array( 3 ) );
		$this->assertSame( 'icon', $bad['error'] );
	}

	/**
	 * A self-point is not editable, including for staff.
	 */
	public function test_self_point_is_not_editable() {
		$geo = array(
			'self_point'    => true,
			'origin'        => 'team',
			'owner_team_id' => 7,
		);

		$this->assertFalse( Access::is_editable( $geo, array( 'is_staff' => true, 'team_ids' => array() ), array() ) );
		$this->assertFalse( Access::default_editable( $geo, array( 'is_staff' => false, 'team_ids' => array( 7 ) ) ) );
	}
}
