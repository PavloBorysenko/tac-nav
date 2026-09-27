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

	const KINDS = array( 'marker', 'polyline', 'polygon', 'circle' );

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

		$ok = $wpdb->insert( Activator::table_name(), $prepared['row'], $prepared['formats'] );
		if ( false === $ok ) {
			return new \WP_Error( 'tacnav_geo_insert', __( 'Could not save the geo object.', 'tacnav-maps' ), array( 'status' => 500 ) );
		}

		return (int) $wpdb->insert_id;
	}

	/**
	 * Update a geo object.
	 *
	 * @param int                  $id   Object ID.
	 * @param array<string, mixed> $data Fields to merge.
	 * @return true|\WP_Error
	 */
	public function update( $id, $data ) {
		global $wpdb;

		$existing = $this->get( $id );
		if ( ! $existing ) {
			return new \WP_Error( 'tacnav_geo_missing', __( 'Geo object not found.', 'tacnav-maps' ), array( 'status' => 404 ) );
		}

		$merged   = array_merge( $existing, $data );
		$prepared = $this->prepare_row( $merged, false );
		if ( is_wp_error( $prepared ) ) {
			return $prepared;
		}

		$ok = $wpdb->update(
			Activator::table_name(),
			$prepared['row'],
			array( 'id' => (int) $id ),
			$prepared['formats'],
			array( '%d' )
		);
		if ( false === $ok ) {
			return new \WP_Error( 'tacnav_geo_update', __( 'Could not update the geo object.', 'tacnav-maps' ), array( 'status' => 500 ) );
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

		$geometry = $this->normalize_geometry( $kind, isset( $data['geometry'] ) ? $data['geometry'] : array() );
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
		if ( ! in_array( $origin, array( 'staff', 'team' ), true ) ) {
			$origin = 'staff';
		}

		$expires = null;
		if ( ! empty( $data['expires_at'] ) ) {
			$expires = sanitize_text_field( (string) $data['expires_at'] );
		}

		$now = gmdate( 'Y-m-d H:i:s' );

		$row = array(
			'map_id'             => $map_id,
			'kind'               => $kind,
			'geometry'           => wp_json_encode( $geometry ),
			'owner_team_id'      => $owner > 0 ? $owner : null,
			'visible_team_ids'   => wp_json_encode( $visible ),
			'origin'             => $origin,
			'created_by_user_id' => isset( $data['created_by_user_id'] ) ? (int) $data['created_by_user_id'] : get_current_user_id(),
			'icon_id'            => $icon > 0 ? $icon : null,
			'title'              => isset( $data['title'] ) ? sanitize_text_field( (string) $data['title'] ) : '',
			'description'        => isset( $data['description'] ) ? wp_kses_post( (string) $data['description'] ) : '',
			'expires_at'         => $expires,
			'updated_at'         => $now,
		);

		$formats = array( '%d', '%s', '%s', '%d', '%s', '%s', '%d', '%d', '%s', '%s', '%s', '%s' );
		if ( $create ) {
			$row['created_at'] = $now;
			$formats[]         = '%s';
		}

		return array(
			'row'     => $row,
			'formats' => $formats,
		);
	}

	/**
	 * Validate geometry for a kind.
	 *
	 * @param string              $kind     Kind.
	 * @param mixed $geometry Geometry.
	 * @return array<string, mixed>|\WP_Error
	 */
	private function normalize_geometry( $kind, $geometry ) {
		if ( ! is_array( $geometry ) ) {
			return new \WP_Error( 'tacnav_geo_geometry', __( 'Invalid geometry.', 'tacnav-maps' ), array( 'status' => 400 ) );
		}

		if ( 'marker' === $kind ) {
			return $this->point( $geometry );
		}

		if ( 'circle' === $kind ) {
			$point = $this->point( $geometry );
			if ( is_wp_error( $point ) ) {
				return $point;
			}
			$radius = isset( $geometry['radius'] ) ? (float) $geometry['radius'] : 0;
			if ( $radius <= 0 ) {
				return new \WP_Error( 'tacnav_geo_radius', __( 'Circle radius must be greater than zero.', 'tacnav-maps' ), array( 'status' => 400 ) );
			}
			$point['radius'] = $radius;
			return $point;
		}

		$path = isset( $geometry['path'] ) && is_array( $geometry['path'] ) ? $geometry['path'] : array();
		$pts  = array();
		foreach ( $path as $pair ) {
			if ( ! is_array( $pair ) ) {
				continue;
			}
			$pt = $this->point(
				array(
					'lat' => $pair['lat'] ?? ( $pair[0] ?? null ),
					'lng' => $pair['lng'] ?? ( $pair[1] ?? null ),
				)
			);
			if ( is_wp_error( $pt ) ) {
				return $pt;
			}
			$pts[] = array( $pt['lat'], $pt['lng'] );
		}

		$min = 'polyline' === $kind ? 2 : 3;
		if ( count( $pts ) < $min ) {
			return new \WP_Error( 'tacnav_geo_path', __( 'Not enough vertices.', 'tacnav-maps' ), array( 'status' => 400 ) );
		}

		if ( 'polygon' === $kind ) {
			$first = $pts[0];
			$last  = $pts[ count( $pts ) - 1 ];
			if ( $first[0] !== $last[0] || $first[1] !== $last[1] ) {
				$pts[] = $first;
			}
		}

		return array( 'path' => $pts );
	}

	/**
	 * Lat/lng pair.
	 *
	 * @param array<string, mixed> $geometry Input.
	 * @return array<string, float>|\WP_Error
	 */
	private function point( $geometry ) {
		if ( ! isset( $geometry['lat'], $geometry['lng'] ) ) {
			return new \WP_Error( 'tacnav_geo_point', __( 'Latitude and longitude are required.', 'tacnav-maps' ), array( 'status' => 400 ) );
		}

		$lat = (float) $geometry['lat'];
		$lng = (float) $geometry['lng'];
		if ( $lat < -90 || $lat > 90 || $lng < -180 || $lng > 180 ) {
			return new \WP_Error( 'tacnav_geo_point', __( 'Coordinates are out of range.', 'tacnav-maps' ), array( 'status' => 400 ) );
		}

		return array(
			'lat' => $lat,
			'lng' => $lng,
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
			'id'                 => (int) $row['id'],
			'map_id'             => (int) $row['map_id'],
			'kind'               => (string) $row['kind'],
			'geometry'           => is_array( $geometry ) ? $geometry : array(),
			'owner_team_id'      => '' === (string) $row['owner_team_id'] ? null : (int) $row['owner_team_id'],
			'visible_team_ids'   => is_array( $visible ) ? array_map( 'intval', $visible ) : array(),
			'origin'             => (string) $row['origin'],
			'created_by_user_id' => (int) $row['created_by_user_id'],
			'icon_id'            => '' === (string) $row['icon_id'] ? null : (int) $row['icon_id'],
			'title'              => (string) $row['title'],
			'description'        => (string) $row['description'],
			'expires_at'         => $row['expires_at'] ? (string) $row['expires_at'] : '',
			'created_at'         => (string) $row['created_at'],
			'updated_at'         => (string) $row['updated_at'],
		);
	}
}
