<?php
/**
 * Staff and player role writes.
 *
 * @package TacNav_Maps
 */

use PHPUnit\Framework\TestCase;
use TacNav\Maps\Player_Write;
use TacNav\Maps\REST;

require_once __DIR__ . '/bootstrap.php';

/**
 * @covers \TacNav\Maps\REST::staff_payload
 * @covers \TacNav\Maps\Player_Write
 */
class TacNav_Maps_Write_Role_Test extends TestCase {

	/**
	 * A staff save carries the chosen role.
	 */
	public function test_staff_save_carries_role() {
		$payload = REST::staff_payload(
			array(
				'kind'     => 'polygon',
				'geometry' => array( 'path' => array( array( 50, 30 ), array( 50, 30.001 ), array( 50.001, 30 ) ) ),
				'role'     => 'minefield',
			),
			4,
			true,
			1700000000
		);

		$this->assertSame( 'minefield', $payload['role'] );
	}

	/**
	 * A staff edit without a role leaves the stored role to the merge.
	 */
	public function test_staff_edit_without_role_omits_it() {
		$payload = REST::staff_payload( array( 'title' => 'Renamed' ), 4, false, 1700000000 );

		$this->assertArrayNotHasKey( 'role', $payload );
	}

	/**
	 * A player body cannot set a role, so the merge keeps the stored one.
	 */
	public function test_player_write_drops_role() {
		$normal = Player_Write::normalize(
			array(
				'kind'  => 'polygon',
				'title' => 'Renamed',
				'role'  => 'minefield',
			),
			7,
			array()
		);

		$this->assertArrayNotHasKey( 'role', $normal );
	}
}
