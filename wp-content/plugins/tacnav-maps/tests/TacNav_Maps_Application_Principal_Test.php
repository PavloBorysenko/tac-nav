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
	 * Applications edit any object except a player's self-point.
	 */
	public function test_application_can_edit_any_object_except_self_points() {
		$viewer = Application_Principal::viewer(
			array(
				'application_id' => 9,
				'team_id'        => 7,
			)
		);

		$this->assertTrue( Access::default_editable( array( 'origin' => 'application', 'application_id' => 9 ), $viewer ) );
		$this->assertTrue( Access::default_editable( array( 'origin' => 'application', 'application_id' => 10 ), $viewer ) );
		$this->assertTrue( Access::default_editable( array( 'origin' => 'team', 'owner_team_id' => 8 ), $viewer ) );
		$this->assertTrue( Access::default_editable( array( 'origin' => 'staff', 'owner_team_id' => null ), $viewer ) );
		$this->assertFalse(
			Access::default_editable(
				array(
					'origin'        => 'team',
					'owner_team_id' => 7,
					'self_point'    => true,
				),
				$viewer
			)
		);
	}

	/**
	 * An update with only a lifetime changes only the expiry.
	 */
	public function test_application_update_with_only_ttl_changes_only_expiry() {
		$payload = Application_Write::normalize(
			array( 'ttl_seconds' => 1 ),
			array( 'id' => 4, 'team_ids' => array( 7, 8 ) ),
			array( 'application_id' => 9, 'team_id' => 7 ),
			false,
			1700000000
		);

		$this->assertSame( array( 'expires_at' => gmdate( 'Y-m-d H:i:s', 1700000001 ) ), $payload );
	}

	/**
	 * Another team's visibility edit keeps the stored belonging and creator.
	 */
	public function test_application_update_never_restamps_belonging() {
		$payload = Application_Write::normalize(
			array(
				'visible_team_ids' => array( 8, 7 ),
				'updated_at'       => '2026-01-01 00:00:00',
				'owner_team_id'    => 7,
				'origin'           => 'staff',
				'application_id'   => 9,
			),
			array( 'id' => 4, 'team_ids' => array( 7, 8 ) ),
			array( 'application_id' => 9, 'team_id' => 7 ),
			false,
			1700000000
		);

		$this->assertSame( array( 'visible_team_ids' => array( 8, 7 ) ), $payload );
	}

	/**
	 * A full Mortar-style body updates every object field it sends.
	 */
	public function test_full_application_update_writes_every_sent_field() {
		global $tacnav_test_post_types;
		$tacnav_test_post_types[55] = Post_Types::ICON;

		$payload = Application_Write::normalize(
			array(
				'kind'                => 'marker',
				'geometry'            => array( 'lat' => 50, 'lng' => 30 ),
				'visible_team_ids'    => array(),
				'icon_id'             => 55,
				'title'               => 'Mortar',
				'description'         => 'Ready',
				'position_accuracy_m' => 4.0,
				'ttl_seconds'         => 60,
			),
			array( 'id' => 4, 'team_ids' => array( 7 ) ),
			array( 'application_id' => 9, 'team_id' => 7 ),
			false,
			1700000000
		);

		$this->assertSame(
			array(
				'kind'                => 'marker',
				'geometry'            => array( 'lat' => 50, 'lng' => 30 ),
				'visible_team_ids'    => array(),
				'position_accuracy_m' => 4.0,
				'icon_id'             => 55,
				'title'               => 'Mortar',
				'description'         => 'Ready',
				'expires_at'          => gmdate( 'Y-m-d H:i:s', 1700000060 ),
			),
			$payload
		);
	}

	/**
	 * Only the creating application's update rebinds the mailbox.
	 */
	public function test_only_the_creator_update_binds() {
		$application = array( 'application_id' => 9, 'team_id' => 7 );

		$this->assertTrue( Application_Write::binds_on_update( array( 'application_id' => 9 ), $application ) );
		$this->assertFalse( Application_Write::binds_on_update( array( 'application_id' => 10 ), $application ) );
		$this->assertFalse( Application_Write::binds_on_update( array( 'application_id' => null ), $application ) );
	}

	/**
	 * An application may name a role on create and on update.
	 */
	public function test_application_write_carries_role() {
		$create = Application_Write::normalize(
			array(
				'kind'     => 'circle',
				'geometry' => array( 'lat' => 50, 'lng' => 30, 'radius' => 10 ),
				'role'     => 'minefield',
			),
			array( 'id' => 4, 'team_ids' => array( 7 ) ),
			array( 'application_id' => 9, 'team_id' => 7 ),
			true,
			1700000000
		);
		$update = Application_Write::normalize(
			array( 'role' => 'hint' ),
			array( 'id' => 4, 'team_ids' => array( 7 ) ),
			array( 'application_id' => 9, 'team_id' => 7 ),
			false,
			1700000000
		);

		$this->assertSame( 'minefield', $create['role'] );
		$this->assertSame( array( 'role' => 'hint' ), $update );
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
	 * A create may be named by a key; an old client's search flag is ignored.
	 */
	public function test_application_write_ignores_the_search_flag_and_keeps_the_key() {
		$payload = Application_Write::normalize(
			array(
				'kind'            => 'circle',
				'geometry'        => array( 'lat' => 50, 'lng' => 30, 'radius' => 100 ),
				'searchable'      => false,
				'idempotency_key' => 'wreck-1',
			),
			array( 'id' => 4, 'team_ids' => array( 7 ) ),
			array( 'application_id' => 9, 'team_id' => 7 ),
			true,
			1700000000
		);

		$this->assertIsArray( $payload );
		$this->assertArrayNotHasKey( 'searchable', $payload );
		$this->assertSame( 'wreck-1', $payload['client_key'] );
	}

	/**
	 * A create without a role is ordinary, and client_object_id has no server effect.
	 */
	public function test_application_write_defaults_to_ordinary() {
		$payload = Application_Write::normalize(
			array(
				'kind'             => 'marker',
				'geometry'         => array( 'lat' => 50, 'lng' => 30 ),
				'client_object_id' => 'mortar-position',
			),
			array( 'id' => 4, 'team_ids' => array( 7 ) ),
			array( 'application_id' => 9, 'team_id' => 7 ),
			true,
			1700000000
		);

		$this->assertIsArray( $payload );
		$this->assertArrayNotHasKey( 'role', $payload );
		$this->assertArrayNotHasKey( 'searchable', $payload );
		$this->assertArrayNotHasKey( 'client_key', $payload );
	}

	/**
	 * An update that omits the flag leaves it alone, and a key is create-only.
	 */
	public function test_application_update_omits_absent_flag_and_ignores_key() {
		$payload = Application_Write::normalize(
			array(
				'kind'            => 'marker',
				'geometry'        => array( 'lat' => 50, 'lng' => 30 ),
				'idempotency_key' => 'late',
			),
			array( 'id' => 4, 'team_ids' => array( 7 ) ),
			array( 'application_id' => 9, 'team_id' => 7 ),
			false,
			1700000000
		);

		$this->assertIsArray( $payload );
		$this->assertArrayNotHasKey( 'searchable', $payload );
		$this->assertArrayNotHasKey( 'client_key', $payload );
	}

	/**
	 * A malformed key is refused instead of silently losing idempotency.
	 */
	public function test_application_write_refuses_a_malformed_key() {
		$refused = Application_Write::normalize(
			array(
				'kind'            => 'marker',
				'geometry'        => array( 'lat' => 50, 'lng' => 30 ),
				'idempotency_key' => str_repeat( 'x', 65 ),
			),
			array( 'id' => 4, 'team_ids' => array( 7 ) ),
			array( 'application_id' => 9, 'team_id' => 7 ),
			true,
			1700000000
		);

		$this->assertInstanceOf( WP_Error::class, $refused );
		$this->assertSame( 'tacnav_idempotency_key', $refused->get_error_code() );
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
