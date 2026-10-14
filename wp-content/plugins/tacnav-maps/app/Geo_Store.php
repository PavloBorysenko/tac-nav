<?php
/**
 * Geo-object table access.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Persists geo objects.
 */
class Geo_Store {

	const KINDS = Geometry_Service::KINDS;

	/**
	 * Insert a geo object.
	 *
	 * @param array<string, mixed> $data Object fields.
	 * @return int|\WP_Error
	 */
	public function insert( $data ) {
		global $wpdb;

		$prepared = $this->prepare_row( $data, true );
		if ( is_wp_error( $prepared ) ) {
			return $prepared;
		}

		$row   = $prepared['row'];
		$table = Activator::table_name();
		$ok    = $wpdb->query(
			$wpdb->prepare(
				'INSERT INTO %i
				(map_id, kind, geometry, owner_team_id, visible_team_ids, origin, application_id, created_by_user_id, position_accuracy_m, icon_id, title, description, expires_at, created_at, updated_at, spatial_projection)
				VALUES (%d, %s, %s, NULLIF(%d, 0), %s, %s, NULLIF(%d, 0), %d, NULLIF(%f, -1), NULLIF(%d, 0), %s, %s, NULLIF(%s, \'\'), %s, %s, ST_GeomFromText(%s))',
				$table,
				$row['map_id'],
				$row['kind'],
				$row['geometry'],
				$row['owner_team_id'],
				$row['visible_team_ids'],
				$row['origin'],
				$row['application_id'],
				$row['created_by_user_id'],
				$row['position_accuracy_m'],
				$row['icon_id'],
				$row['title'],
				$row['description'],
				$row['expires_at'],
				$row['created_at'],
				$row['updated_at'],
				$prepared['projection_wkt']
			)
		);
		if ( false === $ok ) {
			return new \WP_Error( 'tacnav_geo_insert', __( 'Could not save the geo object.', 'tacnav-maps' ), array( 'status' => 500 ) );
		}

		return (int) $wpdb->insert_id;
	}

	/**
	 * Update a geo object.
	 *
	 * @param int                  $id       Object ID.
	 * @param array<string, mixed> $data     Fields to merge.
	 * @param string               $expected Updated-at stamp the editor opened with. Empty skips the check.
	 * @return true|\WP_Error
	 */
	public function update( $id, $data, $expected = '' ) {
		global $wpdb;

		$existing = $this->get( $id );
		if ( ! $existing ) {
			return new \WP_Error( 'tacnav_geo_missing', __( 'Geo object not found.', 'tacnav-maps' ), array( 'status' => 404 ) );
		}

		$expected = sanitize_text_field( (string) $expected );
		if ( '' !== $expected && ! Picture_Audience::same_stamp( (string) $existing['updated_at'], $expected ) ) {
			return new \WP_Error( 'tacnav_geo_conflict', __( 'Someone else saved this object.', 'tacnav-maps' ), array( 'status' => 409 ) );
		}

		unset( $data['updated_at'] );
		$merged   = array_merge( $existing, $data );
		$prepared = $this->prepare_row( $merged, false );
		if ( is_wp_error( $prepared ) ) {
			return $prepared;
		}

		$row   = $prepared['row'];
		$table = Activator::table_name();
		$sql   = 'UPDATE %i SET
			map_id = %d, kind = %s, geometry = %s, owner_team_id = NULLIF(%d, 0),
			visible_team_ids = %s, origin = %s, application_id = NULLIF(%d, 0),
			created_by_user_id = %d, position_accuracy_m = NULLIF(%f, -1),
			icon_id = NULLIF(%d, 0), title = %s, description = %s,
			expires_at = NULLIF(%s, \'\'), updated_at = %s,
			spatial_projection = ST_GeomFromText(%s)
			WHERE id = %d';
		$args  = array(
			$table,
			$row['map_id'],
			$row['kind'],
			$row['geometry'],
			$row['owner_team_id'],
			$row['visible_team_ids'],
			$row['origin'],
			$row['application_id'],
			$row['created_by_user_id'],
			$row['position_accuracy_m'],
			$row['icon_id'],
			$row['title'],
			$row['description'],
			$row['expires_at'],
			$row['updated_at'],
			$prepared['projection_wkt'],
			(int) $id,
		);
		if ( '' !== $expected ) {
			$sql   .= ' AND updated_at = %s';
			$args[] = $expected;
		}
		$ok = $wpdb->query( $wpdb->prepare( $sql, $args ) ); // phpcs:ignore WordPress.DB.PreparedSQL.NotPrepared -- SQL has fixed clauses and is prepared with the assembled values.
		if ( false === $ok ) {
			return new \WP_Error( 'tacnav_geo_update', __( 'Could not update the geo object.', 'tacnav-maps' ), array( 'status' => 500 ) );
		}
		if ( 0 === (int) $ok && '' !== $expected ) {
			return new \WP_Error( 'tacnav_geo_conflict', __( 'Someone else saved this object.', 'tacnav-maps' ), array( 'status' => 409 ) );
		}

		return true;
	}

	/**
	 * Delete one object.
	 *
	 * @param int $id Object ID.
	 * @return bool
	 */
	public function delete( $id ) {
		global $wpdb;

		$deleted = $wpdb->delete(
			Activator::table_name(),
			array( 'id' => (int) $id ),
			array( '%d' )
		);

		return false !== $deleted && $deleted > 0;
	}

	/**
	 * Fetch one object.
	 *
	 * @param int $id Object ID.
	 * @return array<string, mixed>|null
	 */
	public function get( $id ) {
		global $wpdb;

		$table = Activator::table_name();
		$row   = $wpdb->get_row(
			$wpdb->prepare( 'SELECT * FROM %i WHERE id = %d', $table, (int) $id ),
			ARRAY_A
		);

		return $row ? $this->hydrate( $row ) : null;
	}

	/**
	 * List objects for a map.
	 *
	 * @param int                  $map_id Map post ID.
	 * @param array<string, mixed> $args   Query args.
	 * @return array<int, array<string, mixed>>
	 */
	public function list_for_map( $map_id, $args = array() ) {
		global $wpdb;

		$map_id = (int) $map_id;
		$table  = Activator::table_name();

		/**
		 * Filters the geo-object listing query arguments.
		 *
		 * @param array<string, mixed> $args   Query args.
		 * @param array<string, mixed> $viewer Viewer context.
		 * @param array<string, mixed> $request Request context.
		 */
		$args = apply_filters(
			'tacnav_geo_objects_query',
			$args,
			isset( $args['viewer'] ) ? $args['viewer'] : array(),
			isset( $args['request'] ) ? $args['request'] : array()
		);

		$now             = isset( $args['now'] ) ? (int) $args['now'] : time();
		$include_expired = ! empty( $args['include_expired'] );

		if ( $include_expired ) {
			$sql = $wpdb->prepare( 'SELECT * FROM %i WHERE map_id = %d ORDER BY id ASC', $table, $map_id );
		} else {
			$sql = $wpdb->prepare(
				'SELECT * FROM %i WHERE map_id = %d AND (expires_at IS NULL OR expires_at > %s) ORDER BY id ASC',
				$table,
				$map_id,
				gmdate( 'Y-m-d H:i:s', $now )
			);
		}

		$rows = $wpdb->get_results( $sql, ARRAY_A ); // phpcs:ignore WordPress.DB.PreparedSQL.NotPrepared -- prepared above.
		if ( ! is_array( $rows ) ) {
			return array();
		}

		$out = array();
		foreach ( $rows as $row ) {
			$out[] = $this->hydrate( $row );
		}

		return $out;
	}

	/**
	 * Search indexed candidates and exact-filter their canonical geometry.
	 *
	 * @param int                  $map_id   Map post ID.
	 * @param string               $kind     Query kind.
	 * @param array<string, mixed> $geometry Normalized query geometry.
	 * @param int                  $now      Server timestamp.
	 * @return array<int, array<string, mixed>>|\WP_Error
	 */
	public function search( $map_id, $kind, $geometry, $now ) {
		global $wpdb;

		$table = Activator::table_name();
		$rows  = $wpdb->get_results(
			$wpdb->prepare(
				'SELECT id, kind, geometry, position_accuracy_m
				FROM %i FORCE INDEX (spatial_projection)
				WHERE map_id = %d
				AND (expires_at IS NULL OR expires_at > %s)
				AND MBRIntersects(spatial_projection, ST_GeomFromText(%s))
				ORDER BY id ASC',
				$table,
				(int) $map_id,
				gmdate( 'Y-m-d H:i:s', $now ),
				Geometry_Service::projection_wkt( $kind, $geometry )
			),
			ARRAY_A
		);
		if ( ! is_array( $rows ) ) {
			return new \WP_Error( 'tacnav_spatial_search', __( 'Indexed geometry search failed.', 'tacnav-maps' ), array( 'status' => 500 ) );
		}

		$results = array();
		foreach ( $rows as $row ) {
			$object_geometry = json_decode( (string) $row['geometry'], true );
			if ( ! is_array( $object_geometry ) || ! Geometry_Service::intersects( $kind, $geometry, (string) $row['kind'], $object_geometry ) ) {
				continue;
			}
			$result = array(
				'id'       => (int) $row['id'],
				'kind'     => (string) $row['kind'],
				'geometry' => $object_geometry,
			);
			if ( null !== $row['position_accuracy_m'] && '' !== (string) $row['position_accuracy_m'] ) {
				$result['position_accuracy_m'] = (float) $row['position_accuracy_m'];
			}
			$results[] = $result;
		}
		return $results;
	}

	/**
	 * Delete every object on a map.
	 *
	 * @param int $map_id Map post ID.
	 * @return int
	 */
	public function delete_for_map( $map_id ) {
		global $wpdb;

		$deleted = $wpdb->delete(
			Activator::table_name(),
			array( 'map_id' => (int) $map_id ),
			array( '%d' )
		);

		return false === $deleted ? 0 : (int) $deleted;
	}

	/**
	 * Delete objects that belong to a team or list it in visibility.
	 *
	 * @param int $team_id Team post ID.
	 * @return int
	 */
	public function delete_for_team( $team_id ) {
		global $wpdb;

		$team_id = (int) $team_id;
		$table   = Activator::table_name();
		$like    = '%"' . $wpdb->esc_like( (string) $team_id ) . '"%';

		$deleted = $wpdb->query(
			$wpdb->prepare(
				'DELETE FROM %i WHERE owner_team_id = %d OR visible_team_ids LIKE %s',
				$table,
				$team_id,
				$like
			)
		);

		return false === $deleted ? 0 : (int) $deleted;
	}

	/**
	 * Permanently delete expired objects on one map.
	 *
	 * @param int    $map_id Map post ID.
	 * @param string $now    MySQL datetime UTC.
	 * @return int
	 */
	public function purge_expired( $map_id, $now ) {
		global $wpdb;

		$table   = Activator::table_name();
		$deleted = $wpdb->query(
			$wpdb->prepare(
				'DELETE FROM %i WHERE map_id = %d AND expires_at IS NOT NULL AND expires_at <= %s',
				$table,
				(int) $map_id,
				$now
			)
		);

		return false === $deleted ? 0 : (int) $deleted;
	}

	/**
	 * Clear a deleted icon from objects.
	 *
	 * @param int $icon_id Icon post ID.
	 * @return int
	 */
	public function clear_icon( $icon_id ) {
		global $wpdb;

		$updated = $wpdb->update(
			Activator::table_name(),
			array( 'icon_id' => null ),
			array( 'icon_id' => (int) $icon_id ),
			array( '%s' ),
			array( '%d' )
		);

		return false === $updated ? 0 : (int) $updated;
	}

	/**
	 * Normalize a row for insert/update.
	 *
	 * @param array<string, mixed> $data   Input.
	 * @param bool                 $create Require map_id and kind.
	 * @return array<string, mixed>|\WP_Error
	 */
	private function prepare_row( $data, $create ) {
		$kind = isset( $data['kind'] ) ? sanitize_key( (string) $data['kind'] ) : '';
		if ( ! in_array( $kind, self::KINDS, true ) ) {
			return new \WP_Error( 'tacnav_geo_kind', __( 'Invalid geometry kind.', 'tacnav-maps' ), array( 'status' => 400 ) );
		}

		$map_id = isset( $data['map_id'] ) ? (int) $data['map_id'] : 0;
		if ( $create && $map_id <= 0 ) {
			return new \WP_Error( 'tacnav_geo_map', __( 'Map is required.', 'tacnav-maps' ), array( 'status' => 400 ) );
		}

		$geometry = Geometry_Service::normalize( $kind, isset( $data['geometry'] ) ? $data['geometry'] : array() );
		if ( is_wp_error( $geometry ) ) {
			return $geometry;
		}

		$visible = array();
		if ( isset( $data['visible_team_ids'] ) && is_array( $data['visible_team_ids'] ) ) {
			$visible = array_values( array_unique( array_filter( array_map( 'intval', $data['visible_team_ids'] ) ) ) );
		}

		$owner  = isset( $data['owner_team_id'] ) ? (int) $data['owner_team_id'] : 0;
		$icon   = isset( $data['icon_id'] ) ? (int) $data['icon_id'] : 0;
		$origin = isset( $data['origin'] ) ? sanitize_key( (string) $data['origin'] ) : 'staff';
		if ( ! in_array( $origin, array( 'staff', 'team', 'application' ), true ) ) {
			$origin = 'staff';
		}
		$application_id = isset( $data['application_id'] ) ? (int) $data['application_id'] : 0;
		$accuracy       = -1.0;
		if ( array_key_exists( 'position_accuracy_m', $data ) && null !== $data['position_accuracy_m'] ) {
			if ( ! is_numeric( $data['position_accuracy_m'] ) || (float) $data['position_accuracy_m'] < 0 ) {
				return new \WP_Error( 'tacnav_position_accuracy', __( 'Position accuracy must be zero or greater.', 'tacnav-maps' ), array( 'status' => 400 ) );
			}
			$accuracy = (float) $data['position_accuracy_m'];
		}

		$expires = null;
		if ( ! empty( $data['expires_at'] ) ) {
			$expires = sanitize_text_field( (string) $data['expires_at'] );
		}

		$now = gmdate( 'Y-m-d H:i:s' );

		$row = array(
			'map_id'              => $map_id,
			'kind'                => $kind,
			'geometry'            => wp_json_encode( $geometry ),
			'owner_team_id'       => $owner > 0 ? $owner : null,
			'visible_team_ids'    => wp_json_encode( $visible ),
			'origin'              => $origin,
			'application_id'      => $application_id > 0 ? $application_id : 0,
			'created_by_user_id'  => isset( $data['created_by_user_id'] ) ? (int) $data['created_by_user_id'] : get_current_user_id(),
			'position_accuracy_m' => $accuracy,
			'icon_id'             => $icon > 0 ? $icon : null,
			'title'               => isset( $data['title'] ) ? sanitize_text_field( (string) $data['title'] ) : '',
			'description'         => isset( $data['description'] ) ? wp_kses_post( (string) $data['description'] ) : '',
			'expires_at'          => $expires,
			'updated_at'          => $now,
		);

		if ( $create ) {
			$row['created_at'] = $now;
		}

		return array(
			'row'            => $row,
			'projection_wkt' => Geometry_Service::projection_wkt( $kind, $geometry ),
		);
	}

	/**
	 * Hydrate a database row.
	 *
	 * @param array<string, mixed> $row Row.
	 * @return array<string, mixed>
	 */
	private function hydrate( $row ) {
		$geometry = json_decode( (string) $row['geometry'], true );
		$visible  = json_decode( (string) $row['visible_team_ids'], true );

		return array(
			'id'                  => (int) $row['id'],
			'map_id'              => (int) $row['map_id'],
			'kind'                => (string) $row['kind'],
			'geometry'            => is_array( $geometry ) ? $geometry : array(),
			'owner_team_id'       => '' === (string) $row['owner_team_id'] ? null : (int) $row['owner_team_id'],
			'visible_team_ids'    => is_array( $visible ) ? array_map( 'intval', $visible ) : array(),
			'origin'              => (string) $row['origin'],
			'application_id'      => '' === (string) $row['application_id'] ? null : (int) $row['application_id'],
			'created_by_user_id'  => (int) $row['created_by_user_id'],
			'position_accuracy_m' => '' === (string) $row['position_accuracy_m'] ? null : (float) $row['position_accuracy_m'],
			'icon_id'             => '' === (string) $row['icon_id'] ? null : (int) $row['icon_id'],
			'title'               => (string) $row['title'],
			'description'         => (string) $row['description'],
			'expires_at'          => $row['expires_at'] ? (string) $row['expires_at'] : '',
			'created_at'          => (string) $row['created_at'],
			'updated_at'          => (string) $row['updated_at'],
		);
	}
}
