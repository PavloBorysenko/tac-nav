<?php
/**
 * Object application types, validation, activation, and release.
 *
 * @package TacNav_Devices
 */

use PHPUnit\Framework\TestCase;
use TacNav\Devices\Admin;
use TacNav\Devices\App_Types;
use TacNav\Devices\Application_Store;
use TacNav\Devices\Release;
use TacNav\Devices\Validation;

require_once __DIR__ . '/bootstrap.php';

/**
 * Database double for activation refusal.
 */
class TacNav_Devices_Test_Wpdb {

	/**
	 * Table prefix.
	 *
	 * @var string
	 */
	public $prefix = 'wp_';

	/**
	 * Stored application type.
	 *
	 * @var string
	 */
	public $application_type = 'mortar';

	/**
	 * Executed writes.
	 *
	 * @var array<int, string>
	 */
	public $writes = array();

	/**
	 * Keep the query text.
	 *
	 * @param string $query Query.
	 * @return string
	 */
	public function prepare( $query ) {
		return $query;
	}

	/**
	 * Return the activation code row or the application row.
	 *
	 * @param string $query Query.
	 * @return array<string, mixed>
	 */
	public function get_row( $query ) {
		if ( false !== strpos( $query, 'code_hash' ) ) {
			return array(
				'id'             => 5,
				'application_id' => 9,
			);
		}
		return array(
			'id'                 => 9,
			'application_type'   => $this->application_type,
			'name'               => 'App',
			'reference_settings' => '{}',
			'default_map_id'     => 0,
			'default_team_id'    => 0,
		);
	}

	/**
	 * No scoped ids.
	 *
	 * @return array<int, int>
	 */
	public function get_col() {
		return array();
	}

	/**
	 * Record a write.
	 *
	 * @param string $query Query.
	 * @return int
	 */
	public function query( $query ) {
		$this->writes[] = $query;
		return 1;
	}
}

/**
 * @covers \TacNav\Devices\App_Types
 * @covers \TacNav\Devices\Validation
 * @covers \TacNav\Devices\Application_Store
 * @covers \TacNav\Devices\Release
 * @covers \TacNav\Devices\Admin
 */
class TacNav_Devices_Object_Test extends TestCase {

	/**
	 * Clear isolated globals.
	 */
	protected function tearDown(): void {
		$GLOBALS['tacnav_test_options'] = array();
		parent::tearDown();
	}

	/**
	 * Each type names its own package and release storage.
	 */
	public function test_each_type_has_its_own_definition() {
		$mortar = App_Types::get( App_Types::MORTAR );
		$object = App_Types::get( App_Types::OBJECT );

		$this->assertSame( 'com.tacnav.mortar', $mortar['package_id'] );
		$this->assertSame( 'tacnav_devices_release', $mortar['option'] );
		$this->assertSame( 'tacnav-mortar.apk', $mortar['file_name'] );
		$this->assertSame( 'tacnav-mortar', $mortar['slug'] );
		$this->assertSame( 'com.tacnav.beacon', $object['package_id'] );
		$this->assertSame( 'tacnav_devices_release_object', $object['option'] );
		$this->assertSame( 'tacnav-object.apk', $object['file_name'] );
		$this->assertSame( 'tacnav-object', $object['slug'] );
	}

	/**
	 * A legacy free-text type stays a Mortar record.
	 */
	public function test_unknown_stored_type_reads_as_mortar() {
		$this->assertSame( App_Types::MORTAR, App_Types::canonical( 'custom-text' ) );
		$this->assertSame( 'com.tacnav.mortar', App_Types::get( 'custom-text' )['package_id'] );
	}

	/**
	 * A new record needs a known type, and an old one keeps what it has.
	 */
	public function test_save_type_rules() {
		$this->assertSame( 'object', App_Types::resolve_save_type( 'object', null ) );
		$this->assertSame( 'tacnav_application_type', App_Types::resolve_save_type( 'sapper', null )->get_error_code() );
		$this->assertSame( 'custom-text', App_Types::resolve_save_type( 'mortar', 'custom-text' ) );
		$this->assertSame( 'tacnav_application_type_change', App_Types::resolve_save_type( 'mortar', 'object' )->get_error_code() );
		$this->assertSame( 'tacnav_application_type_change', App_Types::resolve_save_type( 'object', 'mortar' )->get_error_code() );
	}

	/**
	 * Each tab lists only its own records, and legacy records stay on Mortar.
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
				'application_type' => 'custom-text',
			),
		);

		$this->assertSame( array( 1, 3 ), array_column( App_Types::filter_rows( $rows, App_Types::MORTAR ), 'id' ) );
		$this->assertSame( array( 2 ), array_column( App_Types::filter_rows( $rows, App_Types::OBJECT ), 'id' ) );
	}

	/**
	 * Only the name is required, and the rest takes the specified defaults.
	 */
	public function test_defaults_are_filled_from_only_a_name() {
		$result = Validation::object_reference_settings( array(), 'Antenna 1' );

		$this->assertIsArray( $result );
		$this->assertSame( 'Antenna 1', $result['object_name'] );
		$this->assertSame( 0, $result['hits_to_destroy'] );
		$this->assertSame( 0, $result['activation_seconds'] );
		$this->assertSame( 'team', $result['visibility'] );
		$this->assertFalse( $result['random_area_enabled'] );
		$this->assertSame( 0.0, $result['auto_deactivate_m'] );
		$this->assertSame( 15.0, $result['max_location_accuracy_m'] );
		$this->assertFalse( $result['auto_activate_enabled'] );
		$this->assertNull( $result['max_downtime_minutes'] );
		$this->assertNull( $result['downtime_reset_minutes'] );
		$this->assertNull( $result['size_a_m'] );
		$this->assertNull( $result['size_b_m'] );
		$this->assertSame( '4321', $result['settings_pin'] );
	}

	/**
	 * A complete form keeps every value.
	 */
	public function test_complete_form_keeps_values() {
		$result = Validation::object_reference_settings(
			array(
				'object_name'             => 'Antenna',
				'description'             => 'Guard it',
				'icon_id'                 => '12',
				'destroyed_icon_id'       => '13',
				'hits_to_destroy'         => '2',
				'size_a_m'                => '10',
				'size_b_m'                => '20',
				'activation_seconds'      => '30',
				'visibility'              => 'all',
				'random_area_enabled'     => '1',
				'random_area_radius_m'    => '100',
				'auto_deactivate_m'       => '30',
				'max_location_accuracy_m' => '15',
				'auto_activate_enabled'   => '1',
				'auto_activate_seconds'   => '60',
				'max_downtime_minutes'    => '20',
				'downtime_reset_minutes'  => '0',
				'settings_pin'            => '9876',
			)
		);

		$this->assertIsArray( $result );
		$this->assertSame( 13, $result['destroyed_icon_id'] );
		$this->assertSame( 2, $result['hits_to_destroy'] );
		$this->assertSame( 10.0, $result['size_a_m'] );
		$this->assertSame( 'all', $result['visibility'] );
		$this->assertTrue( $result['random_area_enabled'] );
		$this->assertSame( 60, $result['auto_activate_seconds'] );
		$this->assertSame( 20, $result['max_downtime_minutes'] );
		$this->assertSame( 0, $result['downtime_reset_minutes'] );
		$this->assertSame( '9876', $result['settings_pin'] );
	}

	/**
	 * Each invalid combination is refused with its own reason.
	 */
	public function test_invalid_values_are_refused() {
		$cases = array(
			'tacnav_object_name'     => array( array(), '' ),
			'tacnav_object_number'   => array( array( 'hits_to_destroy' => '-1' ), 'A' ),
			'tacnav_object_pin'      => array( array( 'settings_pin' => '12a' ), 'A' ),
			'tacnav_object_area'     => array( array( 'random_area_enabled' => '1' ), 'A' ),
			'tacnav_object_activate' => array( array( 'auto_activate_enabled' => '1' ), 'A' ),
		);
		foreach ( $cases as $code => $case ) {
			$result = Validation::object_reference_settings( $case[0], $case[1] );
			$this->assertInstanceOf( WP_Error::class, $result, $code );
			$this->assertSame( $code, $result->get_error_code() );
		}
	}

	/**
	 * A false-trigger distance names the smallest accepted value.
	 */
	public function test_deactivation_distance_must_exceed_twice_the_gps_limit() {
		$result = Validation::object_reference_settings( array( 'auto_deactivate_m' => '20' ), 'A' );

		$this->assertInstanceOf( WP_Error::class, $result );
		$this->assertSame( 'tacnav_object_deactivate', $result->get_error_code() );
		$this->assertStringContainsString( '30', $result->get_error_message() );
		$this->assertIsArray( Validation::object_reference_settings( array( 'auto_deactivate_m' => '30' ), 'A' ) );
	}

	/**
	 * The area must contain the whole object.
	 */
	public function test_area_must_cover_the_object() {
		$circle = Validation::object_reference_settings(
			array(
				'size_a_m'             => '30',
				'random_area_enabled'  => '1',
				'random_area_radius_m' => '10',
			),
			'A'
		);
		$rect   = Validation::object_reference_settings(
			array(
				'size_a_m'             => '30',
				'size_b_m'             => '40',
				'random_area_enabled'  => '1',
				'random_area_radius_m' => '24',
			),
			'A'
		);

		$this->assertSame( 'tacnav_object_area', $circle->get_error_code() );
		$this->assertSame( 'tacnav_object_area', $rect->get_error_code() );
	}

	/**
	 * Mortar and Object rules stay separate.
	 */
	public function test_reference_rules_are_chosen_by_type() {
		$mortar = App_Types::reference_settings( 'mortar', array( 'mortar_name' => 'Crew A' ) );
		$object = App_Types::reference_settings( 'object', array(), 'Antenna' );

		$this->assertSame( 'Crew A', $mortar['mortar_name'] );
		$this->assertArrayNotHasKey( 'hits_to_destroy', $mortar );
		$this->assertSame( 'Antenna', $object['object_name'] );
		$this->assertArrayNotHasKey( 'mortar_name', $object );
	}

	/**
	 * A package activates only its own type, and an older client means Mortar.
	 */
	public function test_package_must_match_the_application_type() {
		$this->assertTrue( App_Types::package_matches( 'mortar', 'com.tacnav.mortar' ) );
		$this->assertTrue( App_Types::package_matches( 'mortar', '' ) );
		$this->assertTrue( App_Types::package_matches( 'custom-text', 'com.tacnav.mortar' ) );
		$this->assertTrue( App_Types::package_matches( 'object', 'com.tacnav.beacon' ) );
		$this->assertFalse( App_Types::package_matches( 'object', 'com.tacnav.mortar' ) );
		$this->assertFalse( App_Types::package_matches( 'object', '' ) );
		$this->assertFalse( App_Types::package_matches( 'mortar', 'com.tacnav.beacon' ) );
	}

	/**
	 * A refused package changes nothing, in either direction.
	 */
	public function test_wrong_package_consumes_nothing() {
		global $wpdb;
		$cases = array(
			'object' => 'com.tacnav.mortar',
			'mortar' => 'com.tacnav.beacon',
		);
		foreach ( $cases as $type => $package ) {
			$wpdb                   = new TacNav_Devices_Test_Wpdb();
			$wpdb->application_type = $type;

			$result = ( new Application_Store() )->activate( 'ABC123', $package );

			$this->assertInstanceOf( WP_Error::class, $result );
			$this->assertSame( 'tacnav_activation_package', $result->get_error_code() );
			$this->assertSame( array(), $wpdb->writes );
		}
	}

	/**
	 * Publishing one release leaves the other untouched.
	 */
	public function test_releases_are_independent() {
		$GLOBALS['tacnav_test_options'] = array(
			'tacnav_devices_release'        => array(
				'version'      => '1.2.0',
				'download_url' => 'https://example.test/mortar.apk',
			),
			'tacnav_devices_release_object' => array(
				'version'      => '0.1.0',
				'download_url' => 'https://example.test/object.apk',
			),
		);

		$mortar = Admin::release_metadata();
		$object = Admin::release_metadata( App_Types::OBJECT );

		$this->assertSame( 'com.tacnav.mortar', $mortar['package_id'] );
		$this->assertSame( '1.2.0', $mortar['version'] );
		$this->assertSame( 'com.tacnav.beacon', $object['package_id'] );
		$this->assertSame( '0.1.0', $object['version'] );
		$this->assertSame( 'https://example.test/object.apk', $object['download_url'] );
	}

	/**
	 * Each application keeps its own file and public addresses.
	 */
	public function test_release_paths_and_addresses_are_per_type() {
		$GLOBALS['tacnav_test_options'] = array( 'permalink_structure' => '/%postname%/' );

		$this->assertStringEndsWith( 'tacnav-devices/tacnav-mortar.apk', str_replace( '\\', '/', Release::stored_path() ) );
		$this->assertStringEndsWith( 'tacnav-devices/tacnav-object.apk', str_replace( '\\', '/', Release::stored_path( App_Types::OBJECT ) ) );
		$this->assertSame( 'https://example.test/tacnav-mortar/', Release::public_page_url() );
		$this->assertSame( 'https://example.test/tacnav-mortar.apk', Release::public_apk_url() );
		$this->assertSame( 'https://example.test/tacnav-object/', Release::public_page_url( App_Types::OBJECT ) );
		$this->assertSame( 'https://example.test/tacnav-object.apk', Release::public_apk_url( App_Types::OBJECT ) );
	}

	/**
	 * Plain permalinks use each application's own query argument.
	 */
	public function test_plain_permalink_addresses_stay_valid() {
		$this->assertSame( 'https://example.test/?tacnav_mortar_apk=1', Release::public_apk_url() );
		$this->assertSame( 'https://example.test/?tacnav_mortar_download=1', Release::public_page_url() );
		$this->assertSame( 'https://example.test/?tacnav_object_download=1', Release::public_page_url( App_Types::OBJECT ) );
	}

	/**
	 * Both public pages have rewrite rules and query vars.
	 */
	public function test_public_routes_exist_for_each_application() {
		$GLOBALS['tacnav_test_rewrites'] = array();

		Release::register_rewrites();

		$this->assertSame( 'index.php?tacnav_mortar_download=1', $GLOBALS['tacnav_test_rewrites']['^tacnav-mortar/?$'] );
		$this->assertSame( 'index.php?tacnav_mortar_apk=1', $GLOBALS['tacnav_test_rewrites']['^tacnav-mortar\.apk$'] );
		$this->assertSame( 'index.php?tacnav_object_download=1', $GLOBALS['tacnav_test_rewrites']['^tacnav-object/?$'] );
		$this->assertSame( 'index.php?tacnav_object_apk=1', $GLOBALS['tacnav_test_rewrites']['^tacnav-object\.apk$'] );

		$vars = Release::query_vars( array() );
		$this->assertContains( 'tacnav_mortar_download', $vars );
		$this->assertContains( 'tacnav_mortar_apk', $vars );
		$this->assertContains( 'tacnav_object_download', $vars );
		$this->assertContains( 'tacnav_object_apk', $vars );
	}

	/**
	 * Object actions return to the Object tab, and everything else keeps its address.
	 */
	public function test_redirects_keep_the_application_tab() {
		$base = 'edit.php?post_type=tacnav_map&page=tacnav-devices';

		$this->assertSame( $base . '&app=object&saved=1', Admin::page_query( 'object', 'saved' ) );
		$this->assertSame( $base . '&saved=1', Admin::page_query( '', 'saved' ) );
		$this->assertSame( $base . '&saved=1', Admin::page_query( 'mortar', 'saved' ) );
		$this->assertSame( $base . '&saved=1', Admin::page_query( 'unknown', 'saved' ) );
		$this->assertSame( $base . '&app=object', Admin::page_query( 'object' ) );
	}
}
