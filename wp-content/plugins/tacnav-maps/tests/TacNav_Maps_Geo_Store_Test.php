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
	 * Rows stored under an application key.
	 *
	 * @var array<string, array<string, mixed>>
	 */
	public $keyed_rows = array();

	/**
	 * Insert statements executed.
	 *
	 * @var int
	 */
	public $inserts = 0;

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
		if ( 0 === strpos( ltrim( (string) $this->last_command['query'] ), 'INSERT' ) ) {
			$this->insert_id += empty( $this->keyed_rows ) ? 0 : 1;
			++$this->inserts;
			$this->remember_key();
		}
		return 1;
	}

	/**
	 * Return an existing row, or the row stored under an application key.
	 *
	 * @param array<string, mixed>|null $command Command.
	 * @return array<string, mixed>|null
	 */
	public function get_row( $command = null ) {
		if ( is_array( $command ) && false !== strpos( (string) $command['query'], 'client_key = %s' ) ) {
			$key = $command['args'][1] . '|' . $command['args'][2];
			return isset( $this->keyed_rows[ $key ] ) ? $this->keyed_rows[ $key ] : null;
		}
		return $this->row;
	}

	/**
	 * Store an inserted keyed row for later key lookups.
	 *
	 * @return void
	 */
	private function remember_key() {
		$args = $this->last_command['args'];
		$key  = (string) $args[ count( $args ) - 1 ];
		if ( '' === $key ) {
			return;
		}
		$this->keyed_rows[ $args[7] . '|' . $key ] = array(
			'id'                  => (string) $this->insert_id,
			'map_id'              => '4',
			'kind'                => 'marker',
			'geometry'            => '{"lat":50,"lng":30}',
			'owner_team_id'       => '7',
			'visible_team_ids'    => '[]',
			'origin'              => 'application',
			'application_id'      => (string) $args[7],
			'created_by_user_id'  => '0',
			'position_accuracy_m' => null,
			'icon_id'             => null,
			'title'               => '',
			'description'         => '',
			'expires_at'          => null,
			'created_at'          => '2026-01-01 00:00:00',
			'updated_at'          => '2026-01-01 00:00:00',
			'searchable'          => '0',
		);
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
	 * Search never selects a row excluded from targeting.
	 */
	public function test_search_excludes_not_searchable_rows() {
		( new Geo_Store() )->search(
			4,
			'circle',
			array(
				'lat'    => 50,
				'lng'    => 30,
				'radius' => 100,
			),
			1700000000
		);

		$this->assertStringContainsString( 'AND searchable = 1', $this->database->last_command['query'] );
	}

	/**
	 * A row stored before the flag existed stays searchable, and an explicit zero does not.
	 */
	public function test_flag_defaults_to_searchable_and_survives_hydration() {
		$legacy              = $this->database_row( 'marker', array( 'lat' => 50, 'lng' => 30 ) );
		$this->database->row = $legacy;
		$this->assertTrue( ( new Geo_Store() )->get( 41 )['searchable'] );

		$this->database->row = array_merge( $legacy, array( 'searchable' => '0' ) );
		$this->assertFalse( ( new Geo_Store() )->get( 41 )['searchable'] );
	}

	/**
	 * A write that omits the flag, such as a staff edit, keeps the stored value.
	 */
	public function test_update_without_the_flag_preserves_it() {
		$this->database->row = array_merge(
			$this->database_row( 'circle', array( 'lat' => 50, 'lng' => 30, 'radius' => 25 ) ),
			array( 'searchable' => '0' )
		);

		$this->assertTrue( ( new Geo_Store() )->update( 41, array( 'title' => 'Renamed' ) ) );
		$this->assertStringContainsString( 'searchable = %d', $this->database->last_command['query'] );
		$this->assertContains( 0, $this->database->last_command['args'] );
		$this->assertStringNotContainsString( 'client_key', $this->database->last_command['query'] );
	}

	/**
	 * Repeating a create with one key stores one object.
	 */
	public function test_repeat_create_with_one_key_stores_one_object() {
		$store  = new Geo_Store();
		$first  = $store->insert_once( $this->keyed_object( 9, 'wreck-1' ) );
		$second = $store->insert_once( $this->keyed_object( 9, 'wreck-1' ) );

		$this->assertSame( 41, $first['id'] );
		$this->assertTrue( $first['created'] );
		$this->assertSame( 41, $second['id'] );
		$this->assertFalse( $second['created'] );
		$this->assertSame( 1, $this->database->inserts );
	}

	/**
	 * A new key after a local reset stores a separate object.
	 */
	public function test_new_key_stores_a_new_object() {
		$store  = new Geo_Store();
		$first  = $store->insert_once( $this->keyed_object( 9, 'wreck-1' ) );
		$second = $store->insert_once( $this->keyed_object( 9, 'wreck-2' ) );

		$this->assertNotSame( $first['id'], $second['id'] );
		$this->assertSame( 2, $this->database->inserts );
	}

	/**
	 * Equal keys from two applications do not match each other.
	 */
	public function test_keys_are_scoped_to_one_application() {
		$store  = new Geo_Store();
		$first  = $store->insert_once( $this->keyed_object( 9, 'wreck-1' ) );
		$second = $store->insert_once( $this->keyed_object( 10, 'wreck-1' ) );

		$this->assertNotSame( $first['id'], $second['id'] );
		$this->assertSame( 2, $this->database->inserts );
	}

	/**
	 * A create without a key always inserts.
	 */
	public function test_create_without_a_key_always_inserts() {
		$store = new Geo_Store();
		$data  = $this->keyed_object( 9, '' );

		$store->insert_once( $data );
		$store->insert_once( $data );

		$this->assertSame( 2, $this->database->inserts );
	}

	/**
	 * Object fixture with an idempotency key.
	 *
	 * @param int    $application_id Application.
	 * @param string $key            Key.
	 * @return array<string, mixed>
	 */
	private function keyed_object( $application_id, $key ) {
		return array(
			'map_id'           => 4,
			'kind'             => 'marker',
			'geometry'         => array( 'lat' => 50, 'lng' => 30 ),
			'visible_team_ids' => array(),
			'origin'           => 'application',
			'application_id'   => $application_id,
			'owner_team_id'    => 7,
			'searchable'       => false,
			'client_key'       => $key,
		);
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
