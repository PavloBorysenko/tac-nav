<?php
/**
 * Plugin activation dependency behavior.
 *
 * @package TacNav_Devices
 */

use PHPUnit\Framework\TestCase;
use TacNav\Devices\Activator;

require_once __DIR__ . '/bootstrap.php';

/**
 * @covers \TacNav\Devices\Activator
 */
class TacNav_Devices_Activation_Test extends TestCase {

	/**
	 * Activation stops clearly when TacNav Maps is unavailable.
	 */
	public function test_activation_fails_when_tacnav_maps_is_missing() {
		$GLOBALS['tacnav_devices_deactivated'] = '';

		try {
			Activator::activate();
			$this->fail( 'Activation should stop when TacNav Maps is missing.' );
		} catch ( RuntimeException $error ) {
			$this->assertSame( 'TacNav Devices requires the TacNav Maps plugin to be active.', $error->getMessage() );
			$this->assertSame( 'tacnav-devices.php', $GLOBALS['tacnav_devices_deactivated'] );
		}
	}
}
