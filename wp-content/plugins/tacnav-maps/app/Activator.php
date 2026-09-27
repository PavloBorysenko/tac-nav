<?php
/**
 * Activation and schema.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Creates the geo table, role, and rewrite rules.
 */
class Activator {

	const DB_VERSION = '3';

	/**
	 * Run on plugin activation.
	 *
	 * @return void
	 */
	public static function activate() {
		self::create_table();
		Roles::install();
		Post_Types::register();
		Canvas::register_rewrite();
		flush_rewrite_rules();
		update_option( 'tacnav_maps_db_version', self::DB_VERSION );
	}

	/**
	 * Flush rewrites on deactivation.
	 *
	 * @return void
	 */
	public static function deactivate() {
		flush_rewrite_rules();
	}

	/**
	 * Install new roles and routes after an upgrade.
	 *
	 * @return void
	 */
	public static function maybe_upgrade() {
		if ( self::DB_VERSION === (string) get_option( 'tacnav_maps_db_version' ) ) {
			return;
		}

		self::create_table();
		Roles::install();
		flush_rewrite_rules( false );
		update_option( 'tacnav_maps_db_version', self::DB_VERSION );
	}

	/**
	 * Create or update the geo-object table.
	 *
	 * @return void
	 */
	public static function create_table() {
		global $wpdb;

		require_once ABSPATH . 'wp-admin/includes/upgrade.php';

		$table   = self::table_name();
		$charset = $wpdb->get_charset_collate();

		$sql = "CREATE TABLE {$table} (
			id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
			map_id bigint(20) unsigned NOT NULL,
			kind varchar(20) NOT NULL,
			geometry longtext NOT NULL,
			owner_team_id bigint(20) unsigned DEFAULT NULL,
			visible_team_ids longtext NOT NULL,
			origin varchar(10) NOT NULL,
			created_by_user_id bigint(20) unsigned NOT NULL DEFAULT 0,
			icon_id bigint(20) unsigned DEFAULT NULL,
			title text NOT NULL,
			description longtext NOT NULL,
			expires_at datetime DEFAULT NULL,
			created_at datetime NOT NULL,
			updated_at datetime NOT NULL,
			PRIMARY KEY  (id),
			KEY map_id (map_id),
			KEY owner_team_id (owner_team_id),
			KEY expires_at (expires_at)
		) {$charset};";

		dbDelta( $sql );
	}

	/**
	 * Geo-object table name.
	 *
	 * @return string
	 */
	public static function table_name() {
		global $wpdb;

		return $wpdb->prefix . 'tacnav_geo_objects';
	}
}
