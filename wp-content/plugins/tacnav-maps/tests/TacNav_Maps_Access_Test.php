<?php
/**
 * Access resolver tests.
 *
 * @package TacNav_Maps
 */

use PHPUnit\Framework\TestCase;
use TacNav\Maps\Access;

require_once __DIR__ . '/bootstrap.php';

/**
 * @covers \TacNav\Maps\Access
 */
class TacNav_Maps_Access_Test extends TestCase {

	/**
	 * Staff-origin Red object is not editable by a Red member.
	 */
	public function test_staff_origin_red_object_is_not_editable_by_red_member() {
		$geo    = array(
			'origin'        => 'staff',
			'owner_team_id' => 7,
		);
		$viewer = array(
			'is_staff' => false,
			'team_ids' => array( 7 ),
		);

		$this->assertFalse( Access::default_editable( $geo, $viewer ) );
	}

	/**
	 * Team-origin Red object is editable by any Red member.
	 */
	public function test_team_origin_red_object_is_editable_by_red_member() {
		$geo    = array(
			'origin'        => 'team',
			'owner_team_id' => 7,
		);
		$viewer = array(
			'is_staff' => false,
			'team_ids' => array( 7 ),
		);

		$this->assertTrue( Access::default_editable( $geo, $viewer ) );
	}

	/**
	 * Organizer sees a team-only object.
	 */
	public function test_organizer_sees_team_only_object() {
		$geo    = array(
			'visible_team_ids' => array( 7 ),
		);
		$viewer = array(
			'is_staff' => true,
			'team_ids' => array(),
		);

		$this->assertTrue( Access::default_visible( $geo, $viewer ) );
	}

	/**
	 * Empty visibility is visible to all authorized viewers.
	 */
	public function test_empty_visibility_is_visible_to_all() {
		$geo    = array(
			'visible_team_ids' => array(),
		);
		$viewer = array(
			'is_staff' => false,
			'team_ids' => array( 3 ),
		);

		$this->assertTrue( Access::default_visible( $geo, $viewer ) );
	}

	/**
	 * Expired objects are detected from expires_at.
	 */
	public function test_expired_object_is_detected() {
		$geo = array(
			'expires_at' => '2020-01-01 00:00:00',
		);

		$this->assertTrue( Access::is_expired( $geo, strtotime( '2020-01-02 UTC' ) ) );
		$this->assertFalse( Access::is_expired( array( 'expires_at' => '' ), time() ) );
	}

	/**
	 * Visibility hook receives the default and can flip it.
	 */
	public function test_visibility_hook_passthrough_without_wordpress() {
		$geo    = array( 'visible_team_ids' => array() );
		$viewer = array(
			'is_staff' => false,
			'team_ids' => array(),
		);

		$this->assertTrue( Access::is_visible( $geo, $viewer, array( 'surface' => 'studio', 'map_id' => 1 ) ) );
	}
}
