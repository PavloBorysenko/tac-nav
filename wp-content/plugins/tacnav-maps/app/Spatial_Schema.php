<?php
/**
 * Versioned spatial schema migration.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Installs and verifies the indexed geometry projection.
 */
class Spatial_Schema {

	const INDEX = 'spatial_projection';

	/**
	 * Probe the exact spatial operations required by search.
	 *
	 * @return true|\WP_Error
	 */
	public static function check_capability() {
		global $wpdb;

		$probe = preg_replace( '/[^A-Za-z0-9_]/', '', $wpdb->prefix . 'tacnav_spatial_probe' );
		$wpdb->query( $wpdb->prepare( 'DROP TEMPORARY TABLE IF EXISTS %i', $probe ) );
		$created = $wpdb->query(
			$wpdb->prepare(
				'CREATE TEMPORARY TABLE %i (
				id bigint(20) unsigned NOT NULL,
				spatial_projection GEOMETRY NOT NULL,
				PRIMARY KEY (id),
				SPATIAL KEY spatial_projection (spatial_projection)
			) ENGINE=InnoDB',
				$probe
			)
		);
		if ( false === $created ) {
			return self::unsupported( $wpdb->last_error );
		}

		$inserted = $wpdb->query(
			$wpdb->prepare(
				'INSERT INTO %i (id, spatial_projection) VALUES (1, ST_GeomFromText(%s))',
				$probe,
				'POINT(0 0)'
			)
		);
		$matched  = false;
		if ( false !== $inserted ) {
			$matched = $wpdb->get_var(
				$wpdb->prepare(
					'SELECT COUNT(*) FROM %i WHERE MBRIntersects(spatial_projection, ST_GeomFromText(%s))',
					$probe,
					'POLYGON((-1 -1,1 -1,1 1,-1 1,-1 -1))'
				)
			);
		}
		$error = $wpdb->last_error;
		$wpdb->query( $wpdb->prepare( 'DROP TEMPORARY TABLE IF EXISTS %i', $probe ) );

		if ( false === $inserted || '1' !== (string) $matched ) {
			return self::unsupported( $error );
		}
		return true;
	}

	/**
	 * Add, backfill, and index the projection.
	 *
	 * @return true|\WP_Error
	 */
	public static function migrate() {
		global $wpdb;

		$capability = self::check_capability();
		if ( is_wp_error( $capability ) ) {
			return $capability;
		}

		$table = Activator::table_name();
		if ( ! self::column_exists( $table, 'spatial_projection' ) ) {
			$added = $wpdb->query( $wpdb->prepare( 'ALTER TABLE %i ADD COLUMN spatial_projection GEOMETRY NULL', $table ) );
			if ( false === $added ) {
				return self::migration_error( __( 'Could not add the spatial projection column.', 'tacnav-maps' ) );
			}
		}

		$rows = $wpdb->get_results( $wpdb->prepare( 'SELECT id, kind, geometry FROM %i WHERE spatial_projection IS NULL ORDER BY id ASC', $table ), ARRAY_A );
		if ( ! is_array( $rows ) ) {
			return self::migration_error( __( 'Could not read geo objects for spatial backfill.', 'tacnav-maps' ) );
		}
		foreach ( $rows as $row ) {
			$geometry = json_decode( (string) $row['geometry'], true );
			$normal   = Geometry_Service::normalize( (string) $row['kind'], $geometry );
			if ( is_wp_error( $normal ) ) {
				return self::migration_error(
					sprintf(
						/* translators: %d: geo-object id. */
						__( 'Geo object %d has invalid canonical geometry and could not be spatially indexed.', 'tacnav-maps' ),
						(int) $row['id']
					)
				);
			}
			$updated = $wpdb->query(
				$wpdb->prepare(
					'UPDATE %i SET spatial_projection = ST_GeomFromText(%s) WHERE id = %d',
					$table,
					Geometry_Service::projection_wkt( (string) $row['kind'], $normal ),
					(int) $row['id']
				)
			);
			if ( 1 !== (int) $updated ) {
				return self::migration_error( __( 'Could not backfill every geo-object spatial projection.', 'tacnav-maps' ) );
			}
		}

		$missing = $wpdb->get_var( $wpdb->prepare( 'SELECT COUNT(*) FROM %i WHERE spatial_projection IS NULL', $table ) );
		if ( '0' !== (string) $missing ) {
			return self::migration_error( __( 'Spatial projection backfill is incomplete.', 'tacnav-maps' ) );
		}

		$not_null = $wpdb->query( $wpdb->prepare( 'ALTER TABLE %i MODIFY spatial_projection GEOMETRY NOT NULL', $table ) );
		if ( false === $not_null ) {
			return self::migration_error( __( 'The database cannot enforce an indexed spatial projection.', 'tacnav-maps' ) );
		}

		if ( ! self::index_exists( $table, self::INDEX ) ) {
			$indexed = $wpdb->query( $wpdb->prepare( 'ALTER TABLE %i ADD SPATIAL INDEX spatial_projection (spatial_projection)', $table ) );
			if ( false === $indexed ) {
				return self::migration_error( __( 'Could not create the required spatial index.', 'tacnav-maps' ) );
			}
		}

		return true;
	}

	/**
	 * Whether a table column exists.
	 *
	 * @param string $table  Table.
	 * @param string $column Column.
	 * @return bool
	 */
	private static function column_exists( $table, $column ) {
		global $wpdb;
		return null !== $wpdb->get_var( $wpdb->prepare( 'SHOW COLUMNS FROM %i LIKE %s', $table, $column ) );
	}

	/**
	 * Whether an index exists.
	 *
	 * @param string $table Table.
	 * @param string $index Index.
	 * @return bool
	 */
	private static function index_exists( $table, $index ) {
		global $wpdb;
		return null !== $wpdb->get_var( $wpdb->prepare( 'SHOW INDEX FROM %i WHERE Key_name = %s', $table, $index ) );
	}

	/**
	 * Unsupported capability error.
	 *
	 * @param string $database_error Database detail.
	 * @return \WP_Error
	 */
	private static function unsupported( $database_error ) {
		return new \WP_Error(
			'tacnav_spatial_unsupported',
			__( 'TacNav Maps requires InnoDB geometry columns, spatial indexes, ST_GeomFromText, and MBRIntersects. Spatial search was not enabled; no JSON-scan fallback is available.', 'tacnav-maps' ),
			array(
				'status'         => 500,
				'database_error' => (string) $database_error,
			)
		);
	}

	/**
	 * Migration failure with database detail.
	 *
	 * @param string $message Message.
	 * @return \WP_Error
	 */
	private static function migration_error( $message ) {
		global $wpdb;
		return new \WP_Error(
			'tacnav_spatial_migration',
			$message,
			array(
				'status'         => 500,
				'database_error' => (string) $wpdb->last_error,
			)
		);
	}
}
