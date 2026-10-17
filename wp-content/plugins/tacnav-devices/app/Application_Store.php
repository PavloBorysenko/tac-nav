<?php
/**
 * Application definitions, activation, and credentials.
 *
 * @package TacNav_Devices
 */

namespace TacNav\Devices;

defined( 'ABSPATH' ) || exit;

/**
 * Persists device application identity and activation state.
 */
class Application_Store {

	/**
	 * List all definitions for staff administration.
	 *
	 * @return array<int, array<string, mixed>>
	 */
	public function all() {
		global $wpdb;

		$ids = $wpdb->get_col(
			$wpdb->prepare( 'SELECT id FROM %i ORDER BY name ASC', Activator::tables()['applications'] )
		);
		$out = array();
		foreach ( (array) $ids as $id ) {
			$item = $this->get( (int) $id );
			if ( $item ) {
				$out[] = $item;
			}
		}
		return $out;
	}

	/**
	 * Save a staff-managed definition and its scopes.
	 *
	 * @param array<string, mixed> $data Definition.
	 * @return int|\WP_Error
	 */
	public function save( $data ) {
		global $wpdb;

		$maps         = Validation::ids( isset( $data['allowed_map_ids'] ) ? $data['allowed_map_ids'] : array() );
		$teams        = Validation::ids( isset( $data['allowed_team_ids'] ) ? $data['allowed_team_ids'] : array() );
		$default_map  = isset( $data['default_map_id'] ) ? (int) $data['default_map_id'] : 0;
		$default_team = isset( $data['default_team_id'] ) ? (int) $data['default_team_id'] : 0;
		$catalog      = $this->validate_catalog_scope( $maps, $teams );
		if ( is_wp_error( $catalog ) ) {
			return $catalog;
		}
		$valid = Validation::defaults( $maps, $teams, $default_map, $default_team, array( $this, 'team_on_map' ) );
		if ( is_wp_error( $valid ) ) {
			return $valid;
		}

		$type = isset( $data['application_type'] ) ? sanitize_key( (string) $data['application_type'] ) : '';
		$name = isset( $data['name'] ) ? sanitize_text_field( (string) $data['name'] ) : '';
		if ( '' === $type || '' === $name ) {
			return new \WP_Error( 'tacnav_application_required', __( 'Application type and name are required.', 'tacnav-devices' ) );
		}
		$reference = Validation::reference_settings(
			isset( $data['reference_settings'] ) ? $data['reference_settings'] : array()
		);
		if ( is_wp_error( $reference ) ) {
			return $reference;
		}

		$tables = Activator::tables();
		$now    = current_time( 'mysql', true );
		$row    = array(
			'application_type'   => $type,
			'name'               => $name,
			'reference_settings' => wp_json_encode( $reference ),
			'default_map_id'     => $default_map > 0 ? $default_map : null,
			'default_team_id'    => $default_team > 0 ? $default_team : null,
			'updated_at'         => $now,
		);
		$id     = isset( $data['id'] ) ? (int) $data['id'] : 0;
		if ( $id > 0 && ! $this->get( $id ) ) {
			return new \WP_Error( 'tacnav_application_missing', __( 'Application not found.', 'tacnav-devices' ), array( 'status' => 404 ) );
		}

		$wpdb->query( 'START TRANSACTION' ); // phpcs:ignore WordPress.DB.DirectDatabaseQuery
		if ( $id > 0 ) {
			$ok = $wpdb->update( $tables['applications'], $row, array( 'id' => $id ) );
		} else {
			$row['created_at'] = $now;
			$ok                = $wpdb->insert( $tables['applications'], $row );
			$id                = (int) $wpdb->insert_id;
		}
		if ( false === $ok || $id <= 0 || ! $this->replace_scope( $id, $maps, $teams ) ) {
			$wpdb->query( 'ROLLBACK' ); // phpcs:ignore WordPress.DB.DirectDatabaseQuery
			return new \WP_Error( 'tacnav_application_save', __( 'Could not save the application definition.', 'tacnav-devices' ), array( 'status' => 500 ) );
		}
		$wpdb->query( 'COMMIT' ); // phpcs:ignore WordPress.DB.DirectDatabaseQuery
		return $id;
	}

	/**
	 * Read one complete definition.
	 *
	 * @param int $id Application ID.
	 * @return array<string, mixed>|null
	 */
	public function get( $id ) {
		global $wpdb;

		$tables = Activator::tables();
		$row    = $wpdb->get_row(
			$wpdb->prepare( 'SELECT * FROM %i WHERE id = %d', $tables['applications'], (int) $id ),
			ARRAY_A
		);
		if ( ! $row ) {
			return null;
		}
		$row['id']                 = (int) $row['id'];
		$row['default_map_id']     = $row['default_map_id'] ? (int) $row['default_map_id'] : 0;
		$row['default_team_id']    = $row['default_team_id'] ? (int) $row['default_team_id'] : 0;
		$row['reference_settings'] = json_decode( (string) $row['reference_settings'], true );
		$row['allowed_map_ids']    = $wpdb->get_col(
			$wpdb->prepare( 'SELECT map_id FROM %i WHERE application_id = %d ORDER BY map_id', $tables['application_maps'], (int) $id )
		);
		$row['allowed_team_ids']   = $wpdb->get_col(
			$wpdb->prepare( 'SELECT team_id FROM %i WHERE application_id = %d ORDER BY team_id', $tables['application_teams'], (int) $id )
		);
		$row['allowed_map_ids']    = array_map( 'intval', $row['allowed_map_ids'] );
		$row['allowed_team_ids']   = array_map( 'intval', $row['allowed_team_ids'] );
		return $row;
	}

	/**
	 * Generate a short-lived, single-use activation secret.
	 *
	 * @param int $application_id Application ID.
	 * @param int $user_id        Issuing staff user.
	 * @param int $lifetime       Lifetime in seconds.
	 * @return string|\WP_Error Plain code, returned once.
	 */
	public function create_activation( $application_id, $user_id, $lifetime = 900 ) {
		global $wpdb;

		if ( ! $this->get( $application_id ) ) {
			return new \WP_Error( 'tacnav_application_missing', __( 'Application not found.', 'tacnav-devices' ), array( 'status' => 404 ) );
		}
		$plain = strtoupper( bin2hex( random_bytes( 8 ) ) );
		$ok    = $wpdb->insert(
			Activator::tables()['activations'],
			array(
				'application_id'     => (int) $application_id,
				'code_hash'          => self::hash_secret( $plain ),
				'expires_at'         => gmdate( 'Y-m-d H:i:s', time() + max( 60, (int) $lifetime ) ),
				'created_by_user_id' => (int) $user_id,
				'created_at'         => current_time( 'mysql', true ),
			)
		);
		return false === $ok ? new \WP_Error( 'tacnav_activation_create', __( 'Could not create activation.', 'tacnav-devices' ), array( 'status' => 500 ) ) : $plain;
	}

	/**
	 * Consume an activation and replace the active phone.
	 *
	 * @param string $code One-time code.
	 * @return array<string, mixed>|\WP_Error
	 */
	public function activate( $code ) {
		global $wpdb;

		$tables = Activator::tables();
		$hash   = self::hash_secret( strtoupper( preg_replace( '/[^A-Fa-f0-9]/', '', $code ) ) );
		$row    = $wpdb->get_row(
			$wpdb->prepare(
				'SELECT * FROM %i WHERE code_hash = %s AND consumed_at IS NULL AND expires_at > %s',
				$tables['activations'],
				$hash,
				current_time( 'mysql', true )
			),
			ARRAY_A
		);
		if ( ! $row ) {
			return new \WP_Error( 'tacnav_activation_invalid', __( 'Activation code is invalid, expired, or already used.', 'tacnav-devices' ), array( 'status' => 401 ) );
		}

		$plain = self::new_secret();
		$now   = current_time( 'mysql', true );
		$wpdb->query( 'START TRANSACTION' ); // phpcs:ignore WordPress.DB.DirectDatabaseQuery
		$used = $wpdb->query(
			$wpdb->prepare(
				'UPDATE %i SET consumed_at = %s WHERE id = %d AND consumed_at IS NULL',
				$tables['activations'],
				$now,
				(int) $row['id']
			)
		);
		if ( 1 !== (int) $used ) {
			$wpdb->query( 'ROLLBACK' ); // phpcs:ignore WordPress.DB.DirectDatabaseQuery
			return new \WP_Error( 'tacnav_activation_used', __( 'Activation code has already been used.', 'tacnav-devices' ), array( 'status' => 409 ) );
		}
		$this->revoke_installations( (int) $row['application_id'], $now );
		$ok = $wpdb->insert(
			$tables['installations'],
			array(
				'application_id'  => (int) $row['application_id'],
				'credential_hash' => self::hash_secret( $plain ),
				'activated_at'    => $now,
			)
		);
		if ( false === $ok ) {
			$wpdb->query( 'ROLLBACK' ); // phpcs:ignore WordPress.DB.DirectDatabaseQuery
			return new \WP_Error( 'tacnav_activation_install', __( 'Could not activate this phone.', 'tacnav-devices' ), array( 'status' => 500 ) );
		}
		$installation_id = (int) $wpdb->insert_id;
		$wpdb->query( 'COMMIT' ); // phpcs:ignore WordPress.DB.DirectDatabaseQuery

		$application = $this->get( (int) $row['application_id'] );
		return $this->activation_result( $application, $installation_id, $plain );
	}

	/**
	 * Resolve an active bearer without returning its stored hash.
	 *
	 * @param string $bearer Opaque bearer.
	 * @return array<string, mixed>|null
	 */
	public function resolve_bearer( $bearer ) {
		global $wpdb;

		if ( '' === $bearer ) {
			return null;
		}
		$tables = Activator::tables();
		$row    = $wpdb->get_row(
			$wpdb->prepare(
				'SELECT id, application_id FROM %i WHERE credential_hash = %s AND revoked_at IS NULL',
				$tables['installations'],
				self::hash_secret( $bearer )
			),
			ARRAY_A
		);
		if ( ! $row ) {
			return null;
		}
		$application = $this->get( (int) $row['application_id'] );
		if ( ! $application || ! empty( $application['revoked_at'] ) ) {
			return null;
		}
		$application['installation_id'] = (int) $row['id'];
		return $application;
	}

	/**
	 * Revoke active phone and push registration.
	 *
	 * @param int $application_id Application ID.
	 * @return bool
	 */
	public function revoke( $application_id ) {
		return $this->revoke_installations( $application_id, current_time( 'mysql', true ) );
	}

	/**
	 * Replace the active installation's Firebase token.
	 *
	 * @param int    $installation_id Installation ID.
	 * @param string $token           Registration token, empty to remove.
	 * @return bool
	 */
	public function set_fcm_token( $installation_id, $token ) {
		global $wpdb;

		$token = trim( $token );
		$ok    = $wpdb->update(
			Activator::tables()['installations'],
			array(
				'fcm_token'      => '' === $token ? null : $token,
				'fcm_token_hash' => '' === $token ? null : hash( 'sha256', $token ),
				'last_seen_at'   => current_time( 'mysql', true ),
			),
			array(
				'id'         => (int) $installation_id,
				'revoked_at' => null,
			)
		);
		return false !== $ok;
	}

	/**
	 * Does a map list a team?
	 *
	 * @param int $map_id  Map ID.
	 * @param int $team_id Team ID.
	 * @return bool
	 */
	public function team_on_map( $map_id, $team_id ) {
		if ( ! class_exists( '\TacNav\Maps\Catalog' ) ) {
			return false;
		}
		$map = \TacNav\Maps\Catalog::get_map( (int) $map_id );
		return $map && in_array( (int) $team_id, array_map( 'intval', (array) $map['team_ids'] ), true );
	}

	/**
	 * Ensure every scoped catalog identifier currently exists.
	 *
	 * @param array<int, int> $maps  Allowed maps.
	 * @param array<int, int> $teams Allowed teams.
	 * @return true|\WP_Error
	 */
	private function validate_catalog_scope( $maps, $teams ) {
		if ( ! class_exists( '\TacNav\Maps\Catalog' ) ) {
			return new \WP_Error( 'tacnav_maps_unavailable', __( 'TacNav Maps is unavailable.', 'tacnav-devices' ) );
		}
		foreach ( $maps as $map_id ) {
			if ( ! \TacNav\Maps\Catalog::get_map( $map_id ) ) {
				return new \WP_Error( 'tacnav_allowed_map_missing', __( 'Every allowed map must exist.', 'tacnav-devices' ) );
			}
		}
		foreach ( $teams as $team_id ) {
			if ( ! \TacNav\Maps\Catalog::get_team( $team_id ) ) {
				return new \WP_Error( 'tacnav_allowed_team_missing', __( 'Every allowed team must exist.', 'tacnav-devices' ) );
			}
		}
		return true;
	}

	/**
	 * Hash a secret for storage.
	 *
	 * @param string $secret Secret.
	 * @return string
	 */
	public static function hash_secret( $secret ) {
		return hash( 'sha256', $secret );
	}

	/**
	 * Generate an opaque bearer.
	 *
	 * @return string
	 */
	public static function new_secret() {
		return rtrim( strtr( base64_encode( random_bytes( 32 ) ), '+/', '-_' ), '=' ); // phpcs:ignore WordPress.PHP.DiscouragedPHPFunctions.obfuscation_base64_encode -- URL-safe encoding of random bytes, not obfuscation.
	}

	/**
	 * Replace scope join rows.
	 *
	 * @param int             $id    Application ID.
	 * @param array<int, int> $maps  Map IDs.
	 * @param array<int, int> $teams Team IDs.
	 * @return bool
	 */
	private function replace_scope( $id, $maps, $teams ) {
		global $wpdb;

		$tables = Activator::tables();
		if ( false === $wpdb->delete( $tables['application_maps'], array( 'application_id' => $id ) ) ||
			false === $wpdb->delete( $tables['application_teams'], array( 'application_id' => $id ) ) ) {
			return false;
		}
		foreach ( $maps as $map_id ) {
			if ( false === $wpdb->insert(
				$tables['application_maps'],
				array(
					'application_id' => $id,
					'map_id'         => $map_id,
				)
			) ) {
				return false;
			}
		}
		foreach ( $teams as $team_id ) {
			if ( false === $wpdb->insert(
				$tables['application_teams'],
				array(
					'application_id' => $id,
					'team_id'        => $team_id,
				)
			) ) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Revoke all phones for an application.
	 *
	 * @param int    $application_id Application ID.
	 * @param string $now            UTC database timestamp.
	 * @return bool
	 */
	private function revoke_installations( $application_id, $now ) {
		global $wpdb;

		$ok = $wpdb->query(
			$wpdb->prepare(
				'UPDATE %i SET revoked_at = %s, fcm_token = NULL, fcm_token_hash = NULL WHERE application_id = %d AND revoked_at IS NULL',
				Activator::tables()['installations'],
				$now,
				(int) $application_id
			)
		);
		return false !== $ok;
	}

	/**
	 * Public activation response.
	 *
	 * @param array<string, mixed>|null $application   Definition.
	 * @param int                       $installation_id Installation ID.
	 * @param string                    $credential      Plain bearer.
	 * @return array<string, mixed>
	 */
	private function activation_result( $application, $installation_id, $credential ) {
		return array(
			'application_id'     => (int) $application['id'],
			'installation_id'    => $installation_id,
			'application_type'   => (string) $application['application_type'],
			'credential'         => $credential,
			'allowed_map_ids'    => $application['allowed_map_ids'],
			'allowed_team_ids'   => $application['allowed_team_ids'],
			'default_map_id'     => (int) $application['default_map_id'],
			'default_team_id'    => (int) $application['default_team_id'],
			'reference_settings' => is_array( $application['reference_settings'] ) ? $application['reference_settings'] : array(),
			'services'           => array(
				'rest_url'             => esc_url_raw( rest_url( 'tacnav-devices/v1/' ) ),
				'firebase_project_id'  => 'tac-nav',
				'fcm_sender_available' => '' !== Firebase::credential_path(),
			),
		);
	}
}
