<?php
/**
 * Picture version, audience, and refresh tests.
 *
 * @package TacNav_Maps
 */

use PHPUnit\Framework\TestCase;
use TacNav\Maps\Guest_Token;
use TacNav\Maps\Picture_Audience;
use TacNav\Maps\Picture_Lookup;
use TacNav\Maps\Picture_Store;
use TacNav\Maps\Picture_Version;

require_once __DIR__ . '/bootstrap.php';

/**
 * In-memory picture store for refresh tests.
 */
class TacNav_Maps_Memory_Picture_Store implements Picture_Store {

	/**
	 * Stored lists.
	 *
	 * @var array<string, array<int, array<string, mixed>>>
	 */
	private $rows = array();

	/**
	 * Save a list.
	 *
	 * @param int                              $map_id   Map ID.
	 * @param string                           $audience Audience key.
	 * @param int                              $version  Version.
	 * @param array<int, array<string, mixed>> $objects  Objects.
	 * @return void
	 */
	public function put( $map_id, $audience, $version, array $objects ) {
		$this->rows[ $map_id . '|' . $audience . '|' . $version ] = $objects;
	}

	/**
	 * Read a list.
	 *
	 * @param int    $map_id   Map ID.
	 * @param string $audience Audience key.
	 * @param int    $version  Version.
	 * @return array<int, array<string, mixed>>|null
	 */
	public function get( $map_id, $audience, $version ) {
		$key = $map_id . '|' . $audience . '|' . $version;
		return array_key_exists( $key, $this->rows ) ? $this->rows[ $key ] : null;
	}
}

/**
 * @covers \TacNav\Maps\Picture_Version
 * @covers \TacNav\Maps\Picture_Audience
 * @covers \TacNav\Maps\Picture_Lookup
 */
class TacNav_Maps_Picture_Test extends TestCase {

	/**
	 * A refused write keeps the current version.
	 */
	public function test_refused_write_does_not_advance_the_version() {
		$this->assertSame( 4, Picture_Version::advance_after( 4, false ) );
	}

	/**
	 * A successful write moves the version by one.
	 */
	public function test_successful_write_advances_the_version_by_one() {
		$this->assertSame( 5, Picture_Version::advance_after( 4, true ) );
	}

	/**
	 * A player and a guest of one team share a picture. Staff does not.
	 */
	public function test_player_and_guest_share_a_team_picture() {
		$this->assertSame( 'team-7', Picture_Audience::key( 'player', 7, false ) );
		$this->assertSame( Picture_Audience::key( 'player', 7, false ), Picture_Audience::key( 'guest', 7, false ) );
		$this->assertSame( 'staff', Picture_Audience::key( 'staff', 0, false ) );
		$this->assertNotSame( 'staff', Picture_Audience::key( 'guest', 7, false ) );
	}

	/**
	 * Showing expired objects is a staff picture of its own.
	 */
	public function test_staff_without_expired_is_not_the_expired_picture() {
		$this->assertSame( 'staff', Picture_Audience::key( 'staff', 0, false ) );
		$this->assertSame( 'staff-expired', Picture_Audience::key( 'staff', 0, true ) );
	}

	/**
	 * A matching version does not return or rebuild the object list.
	 */
	public function test_matching_version_omits_the_object_list() {
		$store  = new TacNav_Maps_Memory_Picture_Store();
		$lookup = new Picture_Lookup( $store );
		$loads  = 0;

		$result = $lookup->read(
			1,
			'team-7',
			'4',
			4,
			static function () use ( &$loads ) {
				++$loads;
				return array( array( 'id' => 1 ) );
			}
		);

		$this->assertSame( 304, $result['status'] );
		$this->assertNull( $result['objects'] );
		$this->assertSame( 0, $loads );
	}

	/**
	 * A new version returns the list from the store, not a fresh query.
	 */
	public function test_changed_version_returns_the_stored_list() {
		$store = new TacNav_Maps_Memory_Picture_Store();
		$store->put( 1, 'team-7', 5, array( array( 'id' => 9, 'title' => 'Flag' ) ) );
		$lookup = new Picture_Lookup( $store );
		$loads  = 0;

		$result = $lookup->read(
			1,
			'team-7',
			'4',
			5,
			static function () use ( &$loads ) {
				++$loads;
				return array();
			}
		);

		$this->assertSame( 200, $result['status'] );
		$this->assertSame( 'Flag', $result['objects'][0]['title'] );
		$this->assertSame( 0, $loads );
	}

	/**
	 * A missing or bad guest token cannot refresh. The page token can.
	 */
	public function test_guest_refresh_without_the_page_token_is_refused() {
		$salt  = 'salt';
		$token = Guest_Token::token( 3, 4, $salt );

		$this->assertFalse( Picture_Audience::guest_may_refresh( '', 3, array( 4 ), $salt ) );
		$this->assertFalse( Picture_Audience::guest_may_refresh( '4.deadbeefdeadbeef', 3, array( 4 ), $salt ) );
		$this->assertTrue( Picture_Audience::guest_may_refresh( $token, 3, array( 4 ), $salt ) );
	}

	/**
	 * The second save matches only the stamp the form opened with.
	 */
	public function test_stale_stamp_does_not_match_the_opened_object() {
		$this->assertTrue( Picture_Audience::same_stamp( '2026-01-01 00:00:00', '2026-01-01 00:00:00' ) );
		$this->assertFalse( Picture_Audience::same_stamp( '2026-01-01 00:00:01', '2026-01-01 00:00:00' ) );
	}
}
