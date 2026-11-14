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
	 * Search uses the spatial index and returns whole stored objects.
	 */
	public function test_search_is_map_scoped_unexpired_indexed_and_whole() {
		$this->database->results = array(
			array_merge(
				$this->database_row( 'marker', array( 'lat' => 50.0001, 'lng' => 30 ) ),
				array(
					'id'               => '8',
					'visible_team_ids' => '[7]',
					'role'             => '',
					'client_key'       => 'secret-key',
				)
			),
		);

		$results = $this->search_circle( null );

		$this->assertCount( 1, $results );
		$this->assertSame( 8, $results[0]['id'] );
		$this->assertSame( 'marker', $results[0]['kind'] );
		$this->assertSame( array( 'lat' => 50.0001, 'lng' => 30 ), $results[0]['geometry'] );
		$this->assertSame( 6.5, $results[0]['position_accuracy_m'] );
		$this->assertSame( '', $results[0]['role'] );
		$this->assertSame( 7, $results[0]['owner_team_id'] );
		$this->assertSame( array( 7 ), $results[0]['visible_team_ids'] );
		$this->assertSame( 'application', $results[0]['origin'] );
		$this->assertSame( 9, $results[0]['application_id'] );
		$this->assertSame( '2026-01-01 00:00:00', $results[0]['updated_at'] );
		$this->assertArrayNotHasKey( 'client_key', $results[0] );
		$this->assertArrayNotHasKey( 'distance_m', $results[0] );
		$this->assertStringContainsString( 'FORCE INDEX (spatial_projection)', $this->database->last_command['query'] );
		$this->assertStringContainsString( 'map_id = %d', $this->database->last_command['query'] );
		$this->assertStringContainsString( 'expires_at IS NULL OR expires_at > %s', $this->database->last_command['query'] );
		$this->assertStringNotContainsString( 'visible_team_ids', $this->database->last_command['query'] );
	}

	/**
	 * Without a role every role matches; a role narrows to exact matches.
	 */
	public function test_search_role_filter() {
		$this->search_circle( null );
		$this->assertStringNotContainsString( 'role = %s', $this->database->last_command['query'] );

		$this->search_circle( '' );
		$this->assertStringContainsString( 'AND role = %s', $this->database->last_command['query'] );
		$this->assertSame( '', end( $this->database->last_command['args'] ) );

		$this->search_circle( 'minefield' );
		$this->assertStringContainsString( 'AND role = %s', $this->database->last_command['query'] );
		$this->assertSame( 'minefield', end( $this->database->last_command['args'] ) );
	}

	/**
	 * An unregistered role is refused before the query.
	 */
	public function test_search_refuses_unknown_role() {
		$result = $this->search_circle( 'minefeild' );

		$this->assertInstanceOf( WP_Error::class, $result );
		$this->assertSame( 'tacnav_geo_role', $result->get_error_code() );
		$this->assertSame( array(), $this->database->last_command );
	}

	/**
	 * Search a 100 metre circle.
	 *
	 * @param string|null $role Role filter.
	 * @return array<int, array<string, mixed>>|\WP_Error
	 */
	private function search_circle( $role ) {
		return ( new Geo_Store() )->search(
			4,
			'circle',
			array(
				'lat'    => 50,
				'lng'    => 30,
				'radius' => 100,
			),
			1700000000,
			$role
		);
	}

	/**
	 * Without a role, search returns hint areas, wrecks, minefields, and ordinary objects.
	 */
	public function test_search_without_role_returns_every_kind_of_object() {
		$area = array( 'lat' => 50, 'lng' => 30, 'radius' => 50 );
		$rows = array(
			array( '1', 'circle', $area, 'hint', null ),
			array( '2', 'circle', $area, '', '9' ),
			array( '3', 'circle', $area, 'minefield', null ),
			array( '4', 'marker', array( 'lat' => 50.0001, 'lng' => 30 ), '', null ),
		);
		foreach ( $rows as $row ) {
			$this->database->results[] = array_merge(
				$this->database_row( $row[1], $row[2] ),
				array(
					'id'      => $row[0],
					'role'    => $row[3],
					'icon_id' => $row[4],
				)
			);
		}

		$results = $this->search_circle( null );

		$this->assertSame( array( 1, 2, 3, 4 ), array_column( $results, 'id' ) );
		$this->assertSame( array( 'hint', '', 'minefield', '' ), array_column( $results, 'role' ) );
		$this->assertStringNotContainsString( 'searchable', $this->database->last_command['query'] );
	}

	/**
	 * Writes never name the removed search flag, and an update never touches the key.
	 */
	public function test_writes_do_not_use_the_removed_flag() {
		$store = new Geo_Store();
		$store->insert( $this->keyed_object( 9, '' ) );
		$this->assertStringNotContainsString( 'searchable', $this->database->last_command['query'] );

		$this->database->row = $this->database_row( 'circle', array( 'lat' => 50, 'lng' => 30, 'radius' => 25 ) );
		$this->assertTrue( $store->update( 41, array( 'title' => 'Renamed' ) ) );
		$this->assertStringNotContainsString( 'searchable', $this->database->last_command['query'] );
		$this->assertStringNotContainsString( 'client_key', $this->database->last_command['query'] );
		$this->assertArrayNotHasKey( 'searchable', $store->get( 41 ) );
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
	 * A create stores the role it names.
	 */
	public function test_create_stores_role() {
		$data         = $this->keyed_object( 9, '' );
		$data['kind'] = 'polygon';
		$data['role'] = 'minefield';
		$data['geometry'] = array( 'path' => array( array( 50, 30 ), array( 50, 30.001 ), array( 50.001, 30 ) ) );

		$this->assertSame( 41, ( new Geo_Store() )->insert( $data ) );
		$this->assertStringContainsString( 'role, client_key', $this->database->last_command['query'] );
		$this->assertContains( 'minefield', $this->database->last_command['args'] );
	}

	/**
	 * A create without a role is ordinary.
	 */
	public function test_create_without_role_is_ordinary() {
		( new Geo_Store() )->insert( $this->keyed_object( 9, '' ) );

		$args = $this->database->last_command['args'];
		$this->assertSame( '', $args[ count( $args ) - 2 ] );
	}

	/**
	 * An update that omits the role keeps the stored one.
	 */
	public function test_update_without_role_keeps_it() {
		$this->database->row = array_merge(
			$this->database_row( 'circle', array( 'lat' => 50, 'lng' => 30, 'radius' => 25 ) ),
			array( 'role' => 'hint' )
		);

		$this->assertTrue( ( new Geo_Store() )->update( 41, array( 'title' => 'Renamed' ) ) );
		$this->assertStringContainsString( 'role = %s', $this->database->last_command['query'] );
		$this->assertContains( 'hint', $this->database->last_command['args'] );
		$this->assertSame( 'hint', ( new Geo_Store() )->get( 41 )['role'] );
	}

	/**
	 * An unregistered role is refused before any write.
	 */
	public function test_unknown_role_is_refused() {
		$data         = $this->keyed_object( 9, '' );
		$data['role'] = 'minefeild';

		$result = ( new Geo_Store() )->insert( $data );

		$this->assertInstanceOf( WP_Error::class, $result );
		$this->assertSame( 'tacnav_geo_role', $result->get_error_code() );
		$this->assertSame( 0, $this->database->inserts );
	}

	/**
	 * A role the kind does not allow is refused, also on update.
	 */
	public function test_role_not_allowed_for_kind_is_refused() {
		$data         = $this->keyed_object( 9, '' );
		$data['role'] = 'minefield';
		$this->assertSame( 'tacnav_geo_role', ( new Geo_Store() )->insert( $data )->get_error_code() );

		$this->database->row = $this->database_row( 'marker', array( 'lat' => 50, 'lng' => 30 ) );
		$result              = ( new Geo_Store() )->update( 41, array( 'role' => 'minefield' ) );

		$this->assertSame( 'tacnav_geo_role', $result->get_error_code() );
		$this->assertSame( array(), $this->database->last_command );
	}

	/**
	 * An application update sent with a stale stamp is refused and writes nothing.
	 */
	public function test_stale_update_stamp_is_a_conflict() {
		$this->database->row = array_merge(
			$this->database_row( 'polygon', array( 'path' => array( array( 50, 30 ), array( 50, 30.001 ), array( 50.001, 30 ) ) ) ),
			array( 'updated_at' => '2026-01-01 00:00:05' )
		);

		$result = ( new Geo_Store() )->update( 41, array( 'visible_team_ids' => array( 7, 8 ) ), '2026-01-01 00:00:00' );

		$this->assertInstanceOf( WP_Error::class, $result );
		$this->assertSame( 'tacnav_geo_conflict', $result->get_error_code() );
		$this->assertSame( array(), $this->database->last_command );
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
