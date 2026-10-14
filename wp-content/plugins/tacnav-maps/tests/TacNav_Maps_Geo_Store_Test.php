<?php
/**
 * Geo-store spatial write and search tests.
 *
 * @package TacNav_Maps
 */

use PHPUnit\Framework\TestCase;
use TacNav\Maps\Geo_Store;

require_once __DIR__ . '/bootstrap.php';

/**
 * Small wpdb command recorder for isolated store tests.
 */
class TacNav_Maps_Test_Wpdb {

	/**
	 * Table prefix.
	 *
	 * @var string
	 */
	public $prefix = 'wp_';

	/**
	 * Insert id.
	 *
	 * @var int
	 */
	public $insert_id = 41;

	/**
	 * Last database error.
	 *
	 * @var string
	 */
	public $last_error = '';

	/**
	 * Last prepared command.
	 *
	 * @var array<string, mixed>
	 */
	public $last_command = array();

	/**
	 * Existing row returned by get().
	 *
	 * @var array<string, mixed>
	 */
	public $row = array();

	/**
	 * Search candidates.
	 *
	 * @var array<int, array<string, mixed>>
	 */
	public $results = array();

	/**
	 * Record prepared SQL and values.
	 *
	 * @param string $query Query.
	 * @param mixed  ...$args Values.
	 * @return array<string, mixed>
	 */
	public function prepare( $query, ...$args ) {
		if ( 1 === count( $args ) && is_array( $args[0] ) ) {
			$args = $args[0];
		}
		return array(
			'query' => $query,
			'args'  => $args,
		);
	}

	/**
	 * Execute a recorded write.
	 *
	 * @param array<string, mixed>|string $command Command.
	 * @return int
	 */
	public function query( $command ) {
		$this->last_command = is_array( $command ) ? $command : array( 'query' => $command, 'args' => array() );
		return 1;
	}

	/**
	 * Return an existing row.
	 *
	 * @return array<string, mixed>
	 */
	public function get_row() {
		return $this->row;
	}

	/**
	 * Return spatial candidates.
	 *
	 * @param array<string, mixed> $command Command.
	 * @return array<int, array<string, mixed>>
	 */
	public function get_results( $command ) {
		$this->last_command = $command;
		return $this->results;
	}
}

/**
 * @covers \TacNav\Maps\Geo_Store
 */
class TacNav_Maps_Geo_Store_Test extends TestCase {

	/**
	 * Test database.
	 *
	 * @var TacNav_Maps_Test_Wpdb
	 */
	private $database;

	/**
	 * Install the command recorder.
	 */
	protected function setUp(): void {
		parent::setUp();
		global $wpdb;
		$this->database = new TacNav_Maps_Test_Wpdb();
		$wpdb            = $this->database;
	}

	/**
	 * Canonical JSON and projection are written by the same statement.
	 *
	 */
	public function test_create_and_update_write_each_geometry_atomically() {
		foreach ( self::geometry_provider() as $case ) {
			$kind     = $case[0];
			$geometry = $case[1];
			$store    = new Geo_Store();
			$create   = $store->insert(
				array(
					'map_id'              => 4,
					'kind'                => $kind,
					'geometry'            => $geometry,
					'visible_team_ids'    => array(),
					'origin'              => 'application',
					'application_id'      => 9,
					'owner_team_id'       => 7,
					'position_accuracy_m' => 6.5,
				)
			);

			$this->assertSame( 41, $create );
			$this->assertStringContainsString( 'geometry,', $this->database->last_command['query'] );
			$this->assertStringContainsString( 'ST_GeomFromText', $this->database->last_command['query'] );

			$this->database->row = $this->database_row( $kind, $geometry );
			$updated             = $store->update( 41, array( 'geometry' => $geometry ) );

			$this->assertTrue( $updated );
			$this->assertStringContainsString( 'geometry = %s', $this->database->last_command['query'] );
			$this->assertStringContainsString( 'spatial_projection = ST_GeomFromText', $this->database->last_command['query'] );
		}
	}

	/**
	 * Search uses the spatial index and emits only the contract fields.
	 */
	public function test_search_is_map_scoped_unexpired_indexed_and_minimal() {
		$this->database->results = array(
			array(
				'id'                  => '8',
				'kind'                => 'marker',
				'geometry'            => '{"lat":50.0001,"lng":30}',
				'position_accuracy_m' => '6.500',
			),
		);

		$results = ( new Geo_Store() )->search(
			4,
			'circle',
			array(
				'lat'    => 50,
				'lng'    => 30,
				'radius' => 100,
			),
			1700000000
		);

		$this->assertSame(
			array(
				array(
					'id'                  => 8,
					'kind'                => 'marker',
					'geometry'            => array( 'lat' => 50.0001, 'lng' => 30 ),
					'position_accuracy_m' => 6.5,
				),
			),
			$results
		);
		$this->assertStringContainsString( 'FORCE INDEX (spatial_projection)', $this->database->last_command['query'] );
		$this->assertStringContainsString( 'map_id = %d', $this->database->last_command['query'] );
		$this->assertStringContainsString( 'expires_at IS NULL OR expires_at > %s', $this->database->last_command['query'] );
		$this->assertStringNotContainsString( 'visible_team_ids', $this->database->last_command['query'] );
		$this->assertArrayNotHasKey( 'distance_m', $results[0] );
		$this->assertArrayNotHasKey( 'application_id', $results[0] );
	}

	/**
	 * Geometry fixtures.
	 *
	 * @return array<string, array<int, mixed>>
	 */
	public static function geometry_provider() {
		return array(
			'marker'   => array( 'marker', array( 'lat' => 50, 'lng' => 30 ) ),
			'circle'   => array( 'circle', array( 'lat' => 50, 'lng' => 30, 'radius' => 25 ) ),
			'polyline' => array( 'polyline', array( 'path' => array( array( 50, 30 ), array( 50.001, 30.001 ) ) ) ),
			'polygon'  => array( 'polygon', array( 'path' => array( array( 50, 30 ), array( 50, 30.001 ), array( 50.001, 30 ) ) ) ),
		);
	}

	/**
	 * Complete database row fixture.
	 *
	 * @param string               $kind Kind.
	 * @param array<string, mixed> $geometry Geometry.
	 * @return array<string, mixed>
	 */
	private function database_row( $kind, $geometry ) {
		return array(
			'id'                  => '41',
			'map_id'              => '4',
			'kind'                => $kind,
			'geometry'            => json_encode( $geometry ),
			'owner_team_id'       => '7',
			'visible_team_ids'    => '[]',
			'origin'              => 'application',
			'application_id'      => '9',
			'created_by_user_id'  => '0',
			'position_accuracy_m' => '6.500',
			'icon_id'             => null,
			'title'               => '',
			'description'         => '',
			'expires_at'          => null,
			'created_at'          => '2026-01-01 00:00:00',
			'updated_at'          => '2026-01-01 00:00:00',
		);
	}
}
