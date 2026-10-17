<?php
/**
 * Hosted APK upload validation.
 *
 * @package TacNav_Devices
 */

use PHPUnit\Framework\TestCase;
use TacNav\Devices\Release;

require_once __DIR__ . '/bootstrap.php';
require_once dirname( __DIR__ ) . '/app/Release.php';

/**
 * @covers \TacNav\Devices\Release
 */
class TacNav_Devices_Release_Test extends TestCase {

	/**
	 * A non-package file is refused.
	 */
	public function test_non_apk_upload_is_refused() {
		$path = tempnam( sys_get_temp_dir(), 'tacnav-not-apk' );
		file_put_contents( $path, 'not an android package' );

		$result = Release::validate_apk_upload(
			array(
				'name'     => 'payload.txt',
				'type'     => 'text/plain',
				'tmp_name' => $path,
				'error'    => 0,
				'size'     => filesize( $path ),
			)
		);

		unlink( $path );
		$this->assertInstanceOf( WP_Error::class, $result );
		$this->assertSame( 'tacnav_apk_type', $result->get_error_code() );
	}

	/**
	 * A zip-shaped APK name is accepted for hosting.
	 */
	public function test_apk_upload_is_accepted() {
		$path = tempnam( sys_get_temp_dir(), 'tacnav-apk' );
		file_put_contents( $path, "PK\x03\x04fake-android-package" );

		$result = Release::validate_apk_upload(
			array(
				'name'     => 'tacnav-mortar.apk',
				'type'     => 'application/vnd.android.package-archive',
				'tmp_name' => $path,
				'error'    => 0,
				'size'     => filesize( $path ),
			)
		);

		unlink( $path );
		$this->assertTrue( $result );
	}
}
