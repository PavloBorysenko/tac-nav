<?php
/**
 * Application-principal boundary tests.
 *
 * @package TacNav_Maps
 */

use PHPUnit\Framework\TestCase;
use TacNav\Maps\Access;
use TacNav\Maps\Application_Principal;
use TacNav\Maps\Application_Write;
use TacNav\Maps\Post_Types;

require_once __DIR__ . '/bootstrap.php';

/**
 * @covers \TacNav\Maps\Application_Principal
 * @covers \TacNav\Maps\Access
 */
class TacNav_Maps_Application_Principal_Test extends TestCase {

	/**
	 * Clear the isolated principal provider.
	 */
	protected function tearDown(): void {
		global $tacnav_test_filters, $tacnav_test_post_types;
		$tacnav_test_filters    = array();
		$tacnav_test_post_types = array();
		parent::tearDown();
	}

	/**
	 * An application context is accepted only inside both activated scopes.
	 */
	public function test_selected_map_and_team_must_be_allowed_and_listed() {
		global $tacnav_test_filters;
		$tacnav_test_filters['tacnav_maps_application_principal'] = static function () {
			return array(
				'application_id'   => 9,
				'installation_id'  => 12,
				'map_id'          => 4,
				'team_id'         => 7,
				'allowed_map_ids'  => array( 4 ),
				'allowed_team_ids' => array( 7 ),
			);
		};

		$principal = Application_Principal::resolve( new stdClass(), array( 'id' => 4, 'team_ids' => array( 7 ) ) );
		$this->assertIsArray( $principal );
		$this->assertSame( 9, $principal['application_id'] );
		$this->assertSame( 12, $principal['installation_id'] );

		$refused = Application_Principal::resolve( new stdClass(), array( 'id' => 4, 'team_ids' => array( 8 ) ) );
		$this->assertInstanceOf( WP_Error::class, $refused );
		$this->assertSame( 'tacnav_application_scope', $refused->get_error_code() );
	}

	/**
	 * Applications edit only ordinary objects carrying their own origin id.
	 */
	public function test_application_can_edit_only_its_own_objects() {
		$viewer = Application_Principal::viewer(
			array(
				'application_id' => 9,
				'team_id'        => 7,
			)
		);

		$this->assertTrue( Access::default_editable( array( 'origin' => 'application', 'application_id' => 9 ), $viewer ) );
		$this->assertFalse( Access::default_editable( array( 'origin' => 'application', 'application_id' => 10 ), $viewer ) );
		$this->assertFalse( Access::default_editable( array( 'origin' => 'team', 'owner_team_id' => 7 ), $viewer ) );
		$this->assertFalse( Access::default_editable( array( 'origin' => 'staff', 'owner_team_id' => 7 ), $viewer ) );
	}

	/**
	 * Application identity is stamped and an icon need not be in a team palette.
	 */
	public function test_application_write_stamps_identity_and_allows_a_catalog_icon() {
		global $tacnav_test_post_types;
		$tacnav_test_post_types[55] = Post_Types::ICON;

		$payload = Application_Write::normalize(
			array(
				'kind'                 => 'marker',
				'geometry'             => array( 'lat' => 50, 'lng' => 30 ),
				'visible_team_ids'     => array(),
				'icon_id'              => 55,
				'application_id'       => 999,
				'owner_team_id'        => 999,
				'position_accuracy_m'  => 6.5,
				'ttl_seconds'          => 30,
			),
			array( 'id' => 4, 'team_ids' => array( 7 ) ),
			array( 'application_id' => 9, 'team_id' => 7 ),
			true,
			1700000000
		);

		$this->assertIsArray( $payload );
		$this->assertSame( 4, $payload['map_id'] );
		$this->assertSame( 7, $payload['owner_team_id'] );
		$this->assertSame( 9, $payload['application_id'] );
		$this->assertSame( 'application', $payload['origin'] );
		$this->assertSame( array(), $payload['visible_team_ids'] );
		$this->assertSame( 55, $payload['icon_id'] );
		$this->assertSame( 6.5, $payload['position_accuracy_m'] );
		$this->assertSame( gmdate( 'Y-m-d H:i:s', 1700000030 ), $payload['expires_at'] );
	}

	/**
	 * A deleted icon falls back to the default marker.
	 */
	public function test_application_write_falls_back_for_a_missing_icon() {
		$payload = Application_Write::normalize(
			array(
				'kind'     => 'marker',
				'geometry' => array( 'lat' => 50, 'lng' => 30 ),
				'icon_id'  => 404,
			),
			array( 'id' => 4, 'team_ids' => array( 7 ) ),
			array( 'application_id' => 9, 'team_id' => 7 ),
			true,
			1700000000
		);

		$this->assertIsArray( $payload );
		$this->assertNull( $payload['icon_id'] );
	}
}
