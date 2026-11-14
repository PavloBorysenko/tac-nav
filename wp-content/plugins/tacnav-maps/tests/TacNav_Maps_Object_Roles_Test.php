<?php
/**
 * Role registry tests.
 *
 * @package TacNav_Maps
 */

use PHPUnit\Framework\TestCase;
use TacNav\Maps\Object_Roles;

require_once __DIR__ . '/bootstrap.php';

/**
 * @covers \TacNav\Maps\Object_Roles
 */
class TacNav_Maps_Object_Roles_Test extends TestCase {

	/**
	 * Remove registry filters.
	 */
	protected function tearDown(): void {
		global $tacnav_test_filters;
		unset( $tacnav_test_filters[ Object_Roles::FILTER ] );
		parent::tearDown();
	}

	/**
	 * The registry starts with ordinary, minefield, and hint.
	 */
	public function test_registry_lists_three_roles() {
		$roles = Object_Roles::all();

		$this->assertSame( array( '', 'minefield', 'hint' ), array_keys( $roles ) );
		$this->assertSame( 'Object', $roles['']['label'] );
		$this->assertSame( 'Minefield', $roles['minefield']['label'] );
		$this->assertSame( 'Hint area', $roles['hint']['label'] );
		$this->assertSame( array( 'marker', 'polyline', 'polygon', 'circle' ), $roles['']['kinds'] );
		$this->assertSame( array( 'polygon', 'circle' ), $roles['minefield']['kinds'] );
		$this->assertSame( array( 'polygon', 'circle' ), $roles['hint']['kinds'] );
		$this->assertTrue( $roles['']['assignable'] );
		$this->assertTrue( $roles['minefield']['assignable'] );
		$this->assertFalse( $roles['hint']['assignable'] );
	}

	/**
	 * Allowed kinds pass and other kinds are refused.
	 */
	public function test_validation_checks_kind() {
		$this->assertTrue( Object_Roles::validate( 'minefield', 'polygon' ) );
		$this->assertTrue( Object_Roles::validate( 'hint', 'circle' ) );
		$this->assertTrue( Object_Roles::validate( '', 'marker' ) );

		$refused = Object_Roles::validate( 'minefield', 'marker' );
		$this->assertInstanceOf( WP_Error::class, $refused );
		$this->assertSame( 'tacnav_geo_role', $refused->get_error_code() );
	}

	/**
	 * An unknown slug is refused.
	 */
	public function test_unknown_slug_is_refused() {
		$this->assertFalse( Object_Roles::exists( 'minefeild' ) );
		$this->assertFalse( Object_Roles::exists( null ) );
		$this->assertSame( 'tacnav_geo_role', Object_Roles::validate( 'minefeild', 'polygon' )->get_error_code() );
		$this->assertSame( 'tacnav_geo_role', Object_Roles::validate( null, 'polygon' )->get_error_code() );
	}

	/**
	 * The filter can add a role but cannot remove the ordinary role.
	 */
	public function test_filter_extends_registry() {
		global $tacnav_test_filters;
		$tacnav_test_filters[ Object_Roles::FILTER ] = static function ( $roles ) {
			unset( $roles[''] );
			$roles['beacon'] = array(
				'label'      => 'Beacon',
				'kinds'      => array( 'marker', 'bogus' ),
				'assignable' => true,
			);
			return $roles;
		};

		$this->assertTrue( Object_Roles::exists( '' ) );
		$this->assertTrue( Object_Roles::validate( 'beacon', 'marker' ) );
		$this->assertSame( array( 'marker' ), Object_Roles::all()['beacon']['kinds'] );
	}

	/**
	 * The studio config lists slug, label, kinds, and assignable flag.
	 */
	public function test_canvas_config_lists_roles_with_kinds() {
		$config = Object_Roles::for_canvas();
		$slugs  = array_column( $config, 'slug' );

		$this->assertSame( array( '', 'minefield', 'hint' ), $slugs );
		$this->assertSame( array( 'polygon', 'circle' ), $config[1]['kinds'] );
		$this->assertTrue( $config[1]['assignable'] );
		$this->assertFalse( $config[2]['assignable'] );
	}
}
