<?php
/**
 * Database spatial-capability probe tests.
 *
 * @package TacNav_Maps
 */

use PHPUnit\Framework\TestCase;
use TacNav\Maps\Spatial_Schema;

require_once __DIR__ . '/bootstrap.php';

/**
 * Probe-only wpdb recorder.
 */
class TacNav_Maps_Spatial_Test_Wpdb {

	/**
	 * Prefix.
	 *
	 * @var string
	 */
	public $prefix = 'wp_';

	/**
	 * Error detail.
	 *
	 * @var string
	 */
	public $last_error = '';

	/**
	 * Whether CREATE succeeds.
	 *
	 * @var bool
	 */
	public $supports_spatial = true;

	/**
	 * Queries issued.
	 *
	 * @var array<int, string>
	 */
	public $queries = array();

	/**
	 * @param string $query Query.
	 * @param mixed  ...$args Values.
	 * @return array<string, mixed>
	 */
	public function prepare( $query, ...$args ) {
		return array(
			'query' => $query,
			'args'  => $args,
		);
	}

	/**
	 * @param array<string, mixed>|string $command Command.
	 * @return int|false
	 */
	public function query( $command ) {
		$query           = is_array( $command ) ? $command['query'] : $command;
		$this->queries[] = $query;
		if ( 0 === strpos( $query, 'CREATE TEMPORARY TABLE' ) && ! $this->supports_spatial ) {
			$this->last_error = 'Spatial index unsupported';
			return false;
		}
		return 1;
	}

	/**
	 * @param array<string, mixed> $command Command.
	 * @return string
	 */
	public function get_var( $command ) {
		$this->queries[] = $command['query'];
		return '1';
	}
}

/**
 * @covers \TacNav\Maps\Spatial_Schema
 */
class TacNav_Maps_Spatial_Capability_Test extends TestCase {

	/**
	 * Supported engines prove geometry, spatial indexing, and indexed matching.
	 */
	public function test_capability_probe_exercises_required_spatial_plan() {
		global $wpdb;
		$wpdb   = new TacNav_Maps_Spatial_Test_Wpdb();
		$result = Spatial_Schema::check_capability();

		$this->assertTrue( $result );
		$this->assertStringContainsString( 'GEOMETRY NOT NULL', implode( "\n", $wpdb->queries ) );
		$this->assertStringContainsString( 'SPATIAL KEY', implode( "\n", $wpdb->queries ) );
		$this->assertStringContainsString( 'MBRIntersects', implode( "\n", $wpdb->queries ) );
	}

	/**
	 * Unsupported engines fail clearly and never select a JSON-scan path.
	 */
	public function test_unsupported_database_returns_clear_failure() {
		global $wpdb;
		$wpdb                   = new TacNav_Maps_Spatial_Test_Wpdb();
		$wpdb->supports_spatial = false;

		$result = Spatial_Schema::check_capability();

		$this->assertInstanceOf( WP_Error::class, $result );
		$this->assertSame( 'tacnav_spatial_unsupported', $result->get_error_code() );
		$this->assertStringContainsString( 'no JSON-scan fallback', $result->get_error_message() );
	}
}
