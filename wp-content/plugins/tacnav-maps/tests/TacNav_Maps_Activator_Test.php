<?php
/**
 * Geo table schema tests.
 *
 * @package TacNav_Maps
 */

use PHPUnit\Framework\TestCase;
use TacNav\Maps\Activator;

require_once __DIR__ . '/bootstrap.php';

/**
 * @covers \TacNav\Maps\Activator
 */
class TacNav_Maps_Activator_Test extends TestCase {

	/**
	 * Every object has a role that defaults to ordinary.
	 */
	public function test_schema_has_role_column_defaulting_to_ordinary() {
		$sql = Activator::schema_sql( 'wp_tacnav_geo_objects', '' );

		$this->assertStringContainsString( "role varchar(32) NOT NULL DEFAULT '',", $sql );
		$this->assertStringNotContainsString( 'KEY role', $sql );
		$this->assertStringNotContainsString( 'searchable', $sql );
	}

	/**
	 * Legacy iconless not-searchable areas become hint areas before the flag is dropped.
	 */
	public function test_searchable_migration_converts_hint_areas_then_drops_the_column() {
		$sql = Activator::searchable_migration_sql( 'wp_tacnav_geo_objects' );

		$this->assertCount( 2, $sql );
		$this->assertSame(
			"UPDATE wp_tacnav_geo_objects SET role = 'hint' WHERE searchable = 0 AND icon_id IS NULL AND kind IN ('circle', 'polygon') AND role = ''",
			$sql[0]
		);
		$this->assertSame( 'ALTER TABLE wp_tacnav_geo_objects DROP COLUMN searchable', $sql[1] );
	}
}
