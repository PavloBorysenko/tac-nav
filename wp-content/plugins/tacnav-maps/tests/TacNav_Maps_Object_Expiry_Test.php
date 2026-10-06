<?php
/**
 * Expiry chosen on create and update.
 *
 * @package TacNav_Maps
 */

use PHPUnit\Framework\TestCase;
use TacNav\Maps\Object_Expiry;

require_once __DIR__ . '/bootstrap.php';

/**
 * @covers \TacNav\Maps\Object_Expiry
 */
class TacNav_Maps_Object_Expiry_Test extends TestCase {

	/**
	 * An update that does not choose a duration leaves the stored expiry alone.
	 */
	public function test_update_without_a_duration_keeps_the_stored_expiry() {
		$payload = Object_Expiry::apply_to_payload(
			array(
				'title'      => 'Hill',
				'expires_at' => '2099-01-01 00:00:00',
			),
			false,
			1700000000
		);

		$this->assertSame( 'Hill', $payload['title'] );
		$this->assertArrayNotHasKey( 'expires_at', $payload );
	}

	/**
	 * Zero minutes clears a limited time-to-live.
	 */
	public function test_zero_minutes_clears_the_expiry() {
		$payload = Object_Expiry::apply_to_payload(
			array(
				'ttl_minutes' => 0,
				'expires_at'  => '2099-01-01 00:00:00',
			),
			false,
			1700000000
		);

		$this->assertNull( $payload['expires_at'] );
	}

	/**
	 * A chosen duration starts when the save is accepted.
	 */
	public function test_duration_is_counted_from_the_save() {
		$now   = 1700000000;
		$cases = array(
			array( 1, true ),
			array( 20, false ),
			array( 30, false ),
			array( 5, true ),
		);

		foreach ( $cases as $case ) {
			$payload = Object_Expiry::apply_to_payload(
				array( 'ttl_minutes' => $case[0] ),
				$case[1],
				$now
			);

			$this->assertSame( gmdate( 'Y-m-d H:i:s', $now + ( $case[0] * 60 ) ), $payload['expires_at'] );
			$this->assertArrayNotHasKey( 'ttl_minutes', $payload );
		}
	}
}
