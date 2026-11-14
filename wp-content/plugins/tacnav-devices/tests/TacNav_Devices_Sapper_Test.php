<?php
/**
 * Tac Nav Sapper application type.
 *
 * @package TacNav_Devices
 */

use PHPUnit\Framework\TestCase;
use TacNav\Devices\Admin;
use TacNav\Devices\App_Types;
use TacNav\Devices\Apps\App_Pages;
use TacNav\Devices\Release;
use TacNav\Devices\Validation;

require_once __DIR__ . '/bootstrap.php';

/**
 * @covers \TacNav\Devices\App_Types
 * @covers \TacNav\Devices\Validation
 * @covers \TacNav\Devices\Admin
 * @covers \TacNav\Devices\Release
 * @covers \TacNav\Devices\Apps\App_Pages
 */
class TacNav_Devices_Sapper_Test extends TestCase {

	/**
	 * Clear isolated globals.
	 */
	protected function tearDown(): void {
		$GLOBALS['tacnav_test_options'] = array();
		parent::tearDown();
	}

	/**
	 * Sapper has its own package, release option, file, and addresses.
	 */
	public function test_sapper_has_its_own_definition() {
		$sapper = App_Types::get( App_Types::SAPPER );

		$this->assertSame( 'Tac Nav Sapper', $sapper['label'] );
		$this->assertSame( 'com.tacnav.sapper', $sapper['package_id'] );
		$this->assertSame( 'tacnav_devices_release_sapper', $sapper['option'] );
		$this->assertSame( 'tacnav-sapper.apk', $sapper['file_name'] );
		$this->assertSame( 'tacnav-sapper', $sapper['slug'] );
		$this->assertSame( 'tacnav_sapper', $sapper['query_prefix'] );
	}

	/**
	 * Object and Sapper keep their type, and anything else stays Mortar.
	 */
	public function test_canonical_types() {
		$this->assertSame( App_Types::SAPPER, App_Types::canonical( 'sapper' ) );
		$this->assertSame( App_Types::OBJECT, App_Types::canonical( 'object' ) );
		$this->assertSame( App_Types::MORTAR, App_Types::canonical( 'mortar' ) );
		$this->assertSame( App_Types::MORTAR, App_Types::canonical( 'custom-text' ) );
		$this->assertSame( App_Types::MORTAR, App_Types::canonical( '' ) );
		$this->assertSame( 'sapper', App_Types::resolve_save_type( 'sapper', null ) );
		$this->assertSame( 'tacnav_application_type_change', App_Types::resolve_save_type( 'object', 'sapper' )->get_error_code() );
	}

	/**
	 * Each package activates only its own type.
	 */
	public function test_packages_match_only_their_type() {
		$this->assertTrue( App_Types::package_matches( 'sapper', 'com.tacnav.sapper' ) );
		$this->assertFalse( App_Types::package_matches( 'sapper', 'com.tacnav.beacon' ) );
		$this->assertFalse( App_Types::package_matches( 'sapper', 'com.tacnav.mortar' ) );
		$this->assertFalse( App_Types::package_matches( 'sapper', '' ) );
		$this->assertFalse( App_Types::package_matches( 'object', 'com.tacnav.sapper' ) );
		$this->assertFalse( App_Types::package_matches( 'mortar', 'com.tacnav.sapper' ) );
		$this->assertTrue( App_Types::package_matches( 'object', 'com.tacnav.beacon' ) );
		$this->assertTrue( App_Types::package_matches( 'mortar', 'com.tacnav.mortar' ) );
	}

	/**
	 * Sapper records stay off the Mortar and Object tabs.
	 */
	public function test_rows_are_filtered_by_type() {
		$rows = array(
			array(
				'id'               => 1,
				'application_type' => 'mortar',
			),
			array(
				'id'               => 2,
				'application_type' => 'object',
			),
			array(
				'id'               => 3,
				'application_type' => 'sapper',
			),
			array(
				'id'               => 4,
				'application_type' => 'custom-text',
			),
		);

		$this->assertSame( array( 1, 4 ), array_column( App_Types::filter_rows( $rows, App_Types::MORTAR ), 'id' ) );
		$this->assertSame( array( 2 ), array_column( App_Types::filter_rows( $rows, App_Types::OBJECT ), 'id' ) );
		$this->assertSame( array( 3 ), array_column( App_Types::filter_rows( $rows, App_Types::SAPPER ), 'id' ) );
	}

	/**
	 * A save with no Sapper fields stores every default.
	 */
	public function test_defaults_are_filled() {
		$this->assertSame(
			array(
				'detect_radius_m'         => 10.0,
				'detect_sector_deg'       => 90.0,
				'mark_hold_seconds'       => 30,
				'clear_hold_seconds'      => 180,
				'lay_hold_seconds'        => 180,
				'minefield_length_m'      => 10.0,
				'minefield_width_m'       => 10.0,
				'minefield_icon_id'       => 0,
				'no_mines_icon_id'        => 0,
				'max_minefields'          => 3,
				'max_no_mines_markers'    => 10,
				'max_location_accuracy_m' => 15.0,
				'settings_pin'            => '4321',
			),
			App_Types::reference_settings( 'sapper', array( 'name' => 'Sapper 1' ), 'Sapper 1' )
		);
	}

	/**
	 * Submitted values are kept, and 0 means unlimited for the maximums.
	 */
	public function test_values_are_kept_and_zero_is_unlimited() {
		$result = Validation::sapper_reference_settings(
			array(
				'detect_radius_m'      => '15',
				'minefield_icon_id'    => '42',
				'max_minefields'       => '0',
				'max_no_mines_markers' => '0',
				'detect_sector_deg'    => '360',
				'settings_pin'         => '987654',
			)
		);

		$this->assertSame( 15.0, $result['detect_radius_m'] );
		$this->assertSame( 42, $result['minefield_icon_id'] );
		$this->assertSame( 0, $result['max_minefields'] );
		$this->assertSame( 0, $result['max_no_mines_markers'] );
		$this->assertSame( 360.0, $result['detect_sector_deg'] );
		$this->assertSame( '987654', $result['settings_pin'] );
	}

	/**
	 * Invalid values are refused.
	 */
	public function test_invalid_values_are_refused() {
		$cases = array(
			'negative number'   => array( array( 'max_minefields' => '-1' ), 'tacnav_sapper_number' ),
			'text number'       => array( array( 'lay_hold_seconds' => 'soon' ), 'tacnav_sapper_number' ),
			'zero radius'       => array( array( 'detect_radius_m' => '0' ), 'tacnav_sapper_positive' ),
			'zero hold'         => array( array( 'mark_hold_seconds' => '0' ), 'tacnav_sapper_positive' ),
			'zero side'         => array( array( 'minefield_width_m' => '0' ), 'tacnav_sapper_positive' ),
			'zero gps limit'    => array( array( 'max_location_accuracy_m' => '0' ), 'tacnav_sapper_positive' ),
			'wide sector'       => array( array( 'detect_sector_deg' => '400' ), 'tacnav_sapper_sector' ),
			'zero sector'       => array( array( 'detect_sector_deg' => '0' ), 'tacnav_sapper_sector' ),
			'three digit pin'   => array( array( 'settings_pin' => '123' ), 'tacnav_sapper_pin' ),
			'letters in the pin' => array( array( 'settings_pin' => '12ab' ), 'tacnav_sapper_pin' ),
		);
		foreach ( $cases as $label => $case ) {
			$result = Validation::sapper_reference_settings( $case[0] );
			$this->assertInstanceOf( WP_Error::class, $result, $label );
			$this->assertSame( $case[1], $result->get_error_code(), $label );
		}
	}

	/**
	 * Object and Mortar records still validate with their own rules.
	 */
	public function test_other_types_keep_their_rules() {
		$object = App_Types::reference_settings( 'object', array(), 'Antenna' );
		$this->assertSame( 'Antenna', $object['object_name'] );
		$this->assertArrayNotHasKey( 'detect_radius_m', $object );
		$this->assertArrayNotHasKey( 'detect_radius_m', (array) App_Types::reference_settings( 'mortar', array() ) );
	}

	/**
	 * Sapper and Object actions return to their tabs; Mortar keeps its address.
	 */
	public function test_redirects_carry_the_tab_slug() {
		$base = 'edit.php?post_type=tacnav_map&page=tacnav-devices';

		$this->assertSame( $base . '&app=sapper&saved=1', Admin::page_query( 'sapper', 'saved' ) );
		$this->assertSame( $base . '&app=sapper', Admin::page_query( 'sapper' ) );
		$this->assertSame( $base . '&app=object&revoked=1', Admin::page_query( 'object', 'revoked' ) );
		$this->assertSame( $base . '&saved=1', Admin::page_query( 'mortar', 'saved' ) );
		$this->assertSame( $base . '&saved=1', Admin::page_query( 'drone', 'saved' ) );
	}

	/**
	 * Sapper is a tab after Object and resolves from its slug.
	 */
	public function test_sapper_is_a_tab_after_object() {
		$pages = App_Pages::all();

		$this->assertSame( array( 'mortar', 'object', 'sapper' ), array_map( static function ( $page ) { return $page->slug(); }, $pages ) );
		$this->assertSame( 'Sapper', $pages[2]->label() );
		$this->assertSame( $pages[2], App_Pages::resolve( $pages, 'sapper' ) );
	}

	/**
	 * The Sapper release has its own option, file, addresses, and routes.
	 */
	public function test_sapper_release_is_separate() {
		$GLOBALS['tacnav_test_options'] = array(
			'permalink_structure'           => '/%postname%/',
			'tacnav_devices_release'        => array(
				'version'      => '1.2.0',
				'download_url' => 'https://example.test/mortar.apk',
			),
			'tacnav_devices_release_sapper' => array(
				'version'      => '0.1.0',
				'download_url' => 'https://example.test/sapper.apk',
			),
		);

		$sapper = Admin::release_metadata( App_Types::SAPPER );
		$this->assertSame( 'com.tacnav.sapper', $sapper['package_id'] );
		$this->assertSame( '0.1.0', $sapper['version'] );
		$this->assertSame( '1.2.0', Admin::release_metadata()['version'] );
		$this->assertSame( '', Admin::release_metadata( App_Types::OBJECT )['version'] );
		$this->assertStringEndsWith( 'tacnav-devices/tacnav-sapper.apk', str_replace( '\\', '/', Release::stored_path( App_Types::SAPPER ) ) );
		$this->assertSame( 'https://example.test/tacnav-sapper/', Release::public_page_url( App_Types::SAPPER ) );
		$this->assertSame( 'https://example.test/tacnav-sapper.apk', Release::public_apk_url( App_Types::SAPPER ) );

		$GLOBALS['tacnav_test_rewrites'] = array();
		Release::register_rewrites();
		$this->assertSame( 'index.php?tacnav_sapper_download=1', $GLOBALS['tacnav_test_rewrites']['^tacnav-sapper/?$'] );
		$this->assertSame( 'index.php?tacnav_sapper_apk=1', $GLOBALS['tacnav_test_rewrites']['^tacnav-sapper\.apk$'] );
		$this->assertContains( 'tacnav_sapper_download', Release::query_vars( array() ) );
		$this->assertContains( 'tacnav_sapper_apk', Release::query_vars( array() ) );
	}

	/**
	 * The theme front page links to the public Sapper page.
	 */
	public function test_front_page_links_to_sapper() {
		$template = (string) file_get_contents( dirname( __DIR__, 3 ) . '/themes/tacnav/templates/front-page.html' );

		$this->assertStringContainsString( 'href="/tacnav-sapper/">Sapper app</a>', $template );
		$this->assertLessThan( strpos( $template, '/tacnav-sapper/' ), strpos( $template, '/tacnav-object/' ) );
	}
}
