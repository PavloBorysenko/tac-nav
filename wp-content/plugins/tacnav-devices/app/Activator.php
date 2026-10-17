<?php
/**
 * Activation and schema version handling.
 *
 * @package TacNav_Devices
 */

namespace TacNav\Devices;

defined( 'ABSPATH' ) || exit;

/**
 * Guards activation and applies versioned upgrades.
 */
class Activator {

	const DB_VERSION = '3';

	/**
	 * Logical table names.
	 *
	 * @return array<string, string>
	 */
	public static function tables() {
		global $wpdb;

		$base = $wpdb->prefix . 'tacnav_';
		return array(
			'applications'      => $base . 'applications',
			'application_maps'  => $base . 'application_maps',
			'application_teams' => $base . 'application_teams',
			'installations'     => $base . 'installations',
			'activations'       => $base . 'activation_codes',
			'bindings'          => $base . 'object_bindings',
			'events'            => $base . 'device_events',
			'deliveries'        => $base . 'device_deliveries',
		);
	}

	/**
	 * Activate the plugin only when TacNav Maps is active.
	 *
	 * @return void
	 */
	public static function activate() {
		if ( ! self::dependency_available() ) {
			deactivate_plugins( plugin_basename( TACNAV_DEVICES_FILE ) );
			wp_die(
				esc_html__( 'TacNav Devices requires the TacNav Maps plugin to be active.', 'tacnav-devices' ),
				esc_html__( 'Plugin dependency missing', 'tacnav-devices' ),
				array( 'back_link' => true )
			);
		}

		self::install_schema();
		Release::register_rewrites();
		flush_rewrite_rules( false );
		self::schedule_cleanup();
	}

	/**
	 * Remove only the maintenance schedule; persistent data is retained.
	 *
	 * @return void
	 */
	public static function deactivate() {
		$timestamp = wp_next_scheduled( 'tacnav_devices_cleanup' );
		if ( $timestamp ) {
			wp_unschedule_event( $timestamp, 'tacnav_devices_cleanup' );
		}
	}

	/**
	 * Apply future versioned upgrades.
	 *
	 * @return void
	 */
	public static function maybe_upgrade() {
		if ( self::DB_VERSION === (string) get_option( 'tacnav_devices_db_version' ) ) {
			return;
		}

		self::install_schema();
		Release::register_rewrites();
		flush_rewrite_rules( false );
	}

	/**
	 * Install or upgrade all device tables.
	 *
	 * @return void
	 */
	public static function install_schema() {
		global $wpdb;

		require_once ABSPATH . 'wp-admin/includes/upgrade.php';
		$charset = $wpdb->get_charset_collate();
		foreach ( self::schema_sql( self::tables(), $charset ) as $sql ) {
			dbDelta( $sql );
		}
		update_option( 'tacnav_devices_db_version', self::DB_VERSION );
	}

	/**
	 * SQL declarations kept public for deterministic upgrade tests.
	 *
	 * @param array<string, string> $tables  Table map.
	 * @param string                $charset Character/collation clause.
	 * @return array<int, string>
	 */
	public static function schema_sql( $tables, $charset ) {
		return array(
			"CREATE TABLE {$tables['applications']} (
				id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
				application_type varchar(64) NOT NULL,
				name varchar(190) NOT NULL,
				reference_settings longtext NOT NULL,
				default_map_id bigint(20) unsigned DEFAULT NULL,
				default_team_id bigint(20) unsigned DEFAULT NULL,
				revoked_at datetime DEFAULT NULL,
				created_at datetime NOT NULL,
				updated_at datetime NOT NULL,
				PRIMARY KEY  (id),
				KEY application_type (application_type),
				KEY revoked_at (revoked_at)
			) {$charset};",
			"CREATE TABLE {$tables['application_maps']} (
				application_id bigint(20) unsigned NOT NULL,
				map_id bigint(20) unsigned NOT NULL,
				PRIMARY KEY  (application_id,map_id),
				KEY map_id (map_id)
			) {$charset};",
			"CREATE TABLE {$tables['application_teams']} (
				application_id bigint(20) unsigned NOT NULL,
				team_id bigint(20) unsigned NOT NULL,
				PRIMARY KEY  (application_id,team_id),
				KEY team_id (team_id)
			) {$charset};",
			"CREATE TABLE {$tables['installations']} (
				id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
				application_id bigint(20) unsigned NOT NULL,
				credential_hash char(64) NOT NULL,
				fcm_token longtext DEFAULT NULL,
				fcm_token_hash char(64) DEFAULT NULL,
				activated_at datetime NOT NULL,
				last_seen_at datetime DEFAULT NULL,
				revoked_at datetime DEFAULT NULL,
				PRIMARY KEY  (id),
				UNIQUE KEY credential_hash (credential_hash),
				KEY active_application (application_id,revoked_at),
				KEY fcm_token_hash (fcm_token_hash)
			) {$charset};",
			"CREATE TABLE {$tables['activations']} (
				id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
				application_id bigint(20) unsigned NOT NULL,
				code_hash char(64) NOT NULL,
				expires_at datetime NOT NULL,
				consumed_at datetime DEFAULT NULL,
				created_by_user_id bigint(20) unsigned NOT NULL,
				created_at datetime NOT NULL,
				PRIMARY KEY  (id),
				UNIQUE KEY code_hash (code_hash),
				KEY usable_code (expires_at,consumed_at),
				KEY application_id (application_id)
			) {$charset};",
			"CREATE TABLE {$tables['bindings']} (
				object_id bigint(20) unsigned NOT NULL,
				application_id bigint(20) unsigned NOT NULL,
				installation_id bigint(20) unsigned NOT NULL,
				map_id bigint(20) unsigned NOT NULL,
				expires_at datetime DEFAULT NULL,
				created_at datetime NOT NULL,
				PRIMARY KEY  (object_id),
				KEY active_application (application_id,installation_id,expires_at),
				KEY map_id (map_id)
			) {$charset};",
			"CREATE TABLE {$tables['events']} (
				id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
				source_application_id bigint(20) unsigned NOT NULL,
				source_team_id bigint(20) unsigned NOT NULL,
				map_id bigint(20) unsigned NOT NULL,
				client_event_id varchar(190) NOT NULL,
				event_type varchar(64) NOT NULL,
				payload longtext NOT NULL,
				accepted_at datetime NOT NULL,
				PRIMARY KEY  (id),
				UNIQUE KEY source_event (source_application_id,client_event_id),
				KEY map_accepted (map_id,accepted_at)
			) {$charset};",
			"CREATE TABLE {$tables['deliveries']} (
				id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
				event_id bigint(20) unsigned NOT NULL,
				source_application_id bigint(20) unsigned NOT NULL,
				client_event_id varchar(190) NOT NULL,
				recipient_object_id bigint(20) unsigned NOT NULL,
				recipient_application_id bigint(20) unsigned NOT NULL,
				recipient_installation_id bigint(20) unsigned NOT NULL,
				created_at datetime NOT NULL,
				acknowledged_at datetime DEFAULT NULL,
				expires_at datetime NOT NULL,
				PRIMARY KEY  (id),
				UNIQUE KEY logical_delivery (source_application_id,client_event_id,recipient_object_id),
				KEY recipient_cursor (recipient_application_id,recipient_installation_id,id),
				KEY pending_retention (acknowledged_at,expires_at),
				KEY event_id (event_id)
			) {$charset};",
		);
	}

	/**
	 * Ensure scheduled retention maintenance exists.
	 *
	 * @return void
	 */
	public static function schedule_cleanup() {
		if ( ! wp_next_scheduled( 'tacnav_devices_cleanup' ) ) {
			wp_schedule_event( time() + HOUR_IN_SECONDS, 'hourly', 'tacnav_devices_cleanup' );
		}
	}

	/**
	 * Whether the required map plugin loaded.
	 *
	 * @return bool
	 */
	public static function dependency_available() {
		return defined( 'TACNAV_MAPS_VERSION' );
	}
}
