<?php
/**
 * Provisioning and mailbox contract tests.
 *
 * @package TacNav_Devices
 */

use PHPUnit\Framework\TestCase;
use TacNav\Devices\Activator;
use TacNav\Devices\Application_Context;
use TacNav\Devices\Application_Store;
use TacNav\Devices\Mailbox;
use TacNav\Devices\Validation;

require_once __DIR__ . '/bootstrap.php';

/**
 * @covers \TacNav\Devices\Activator
 * @covers \TacNav\Devices\Application_Store
 * @covers \TacNav\Devices\Validation
 */
class TacNav_Devices_Contracts_Test extends TestCase {

	/**
	 * Versioned schema declares all tables and critical uniqueness/cursor keys.
	 */
	public function test_schema_contains_provisioning_and_mailbox_constraints() {
		$names = array(
			'applications'      => 'wp_tacnav_applications',
			'application_maps'  => 'wp_tacnav_application_maps',
			'application_teams' => 'wp_tacnav_application_teams',
			'installations'     => 'wp_tacnav_installations',
			'activations'       => 'wp_tacnav_activation_codes',
			'bindings'          => 'wp_tacnav_object_bindings',
			'events'            => 'wp_tacnav_device_events',
			'deliveries'        => 'wp_tacnav_device_deliveries',
		);
		$sql   = implode( "\n", Activator::schema_sql( $names, 'DEFAULT CHARACTER SET utf8mb4' ) );

		$this->assertCount( 8, Activator::schema_sql( $names, '' ) );
		$this->assertStringContainsString( 'UNIQUE KEY credential_hash', $sql );
		$this->assertStringContainsString( 'UNIQUE KEY code_hash', $sql );
		$this->assertStringContainsString( 'UNIQUE KEY logical_delivery (source_application_id,client_event_id,recipient_object_id)', $sql );
		$this->assertStringContainsString( 'KEY recipient_cursor (recipient_application_id,recipient_installation_id,id)', $sql );
		$this->assertStringContainsString( 'KEY pending_retention (acknowledged_at,expires_at)', $sql );
	}

	/**
	 * Invalid defaults cannot escape the allowed scopes.
	 */
	public function test_invalid_default_scope_is_rejected() {
		$result = Validation::defaults(
			array( 10 ),
			array( 20 ),
			99,
			20,
			static function () {
				return true;
			}
		);

		$this->assertInstanceOf( WP_Error::class, $result );
		$this->assertSame( 'tacnav_default_map_scope', $result->get_error_code() );
	}

	/**
	 * Current context requires both activated scope and map membership.
	 */
	public function test_context_tampering_and_team_membership_are_rejected() {
		$application = array(
			'allowed_map_ids'  => array( 10 ),
			'allowed_team_ids' => array( 20 ),
		);
		$tampered    = Validation::context( $application, 11, 20, '__return_true' );
		$unlisted    = Validation::context(
			$application,
			10,
			20,
			static function () {
				return false;
			}
		);

		$this->assertSame( 'tacnav_application_scope', $tampered->get_error_code() );
		$this->assertSame( 'tacnav_application_membership', $unlisted->get_error_code() );
	}

	/**
	 * Requests without a bearer remain available to Maps' existing principals.
	 */
	public function test_maps_filter_does_not_intercept_ordinary_requests() {
		$this->assertNull( Application_Context::filter( null, new WP_REST_Request() ) );
	}

	/**
	 * Bearers are high-entropy opaque values and only deterministic hashes persist.
	 */
	public function test_bearer_is_opaque_and_hashable() {
		$first  = Application_Store::new_secret();
		$second = Application_Store::new_secret();

		$this->assertMatchesRegularExpression( '/^[A-Za-z0-9_-]{40,}$/', $first );
		$this->assertNotSame( $first, $second );
		$this->assertSame( 64, strlen( Application_Store::hash_secret( $first ) ) );
		$this->assertStringNotContainsString( $first, Application_Store::hash_secret( $first ) );
	}

	/**
	 * Retention windows preserve a 12-hour pending delivery.
	 */
	public function test_retention_windows_match_mailbox_contract() {
		$this->assertSame( 24 * 60 * 60, Mailbox::PENDING_SECONDS );
		$this->assertSame( 60 * 60, Mailbox::ACK_SECONDS );
		$this->assertLessThan( Mailbox::PENDING_SECONDS, 12 * 60 * 60 );
	}
}
