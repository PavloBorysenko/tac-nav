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

	const DB_VERSION = '4';

	/**
	 * Run on plugin activation.
	 *
	 * @return void
	 */
	public static function activate() {
		self::create_table();
		self::install_spatial_or_fail();
		self::migrate_quick_presets();
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
		self::install_spatial_or_fail();
		self::migrate_quick_presets();
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
			origin varchar(20) NOT NULL,
			application_id bigint(20) unsigned DEFAULT NULL,
			created_by_user_id bigint(20) unsigned NOT NULL DEFAULT 0,
			position_accuracy_m decimal(10,3) unsigned DEFAULT NULL,
			icon_id bigint(20) unsigned DEFAULT NULL,
			title text NOT NULL,
			description longtext NOT NULL,
			expires_at datetime DEFAULT NULL,
			created_at datetime NOT NULL,
			updated_at datetime NOT NULL,
			PRIMARY KEY  (id),
			KEY map_id (map_id),
			KEY owner_team_id (owner_team_id),
			KEY application_id (application_id),
			KEY expires_at (expires_at)
		) {$charset};";

		dbDelta( $sql );
	}

	/**
	 * Install the required spatial projection or stop with a useful error.
	 *
	 * @return void
	 */
	private static function install_spatial_or_fail() {
		$result = Spatial_Schema::migrate();
		if ( ! is_wp_error( $result ) ) {
			return;
		}

		wp_die(
			esc_html( $result->get_error_message() ),
			esc_html__( 'TacNav Maps spatial requirements are unavailable', 'tacnav-maps' ),
			array( 'back_link' => true )
		);
	}

	/**
	 * Replace stored minute presets with seconds once.
	 *
	 * @return void
	 */
	private static function migrate_quick_presets() {
		$team_ids = get_posts(
			array(
				'post_type'      => Post_Types::TEAM,
				'post_status'    => 'any',
				'fields'         => 'ids',
				'posts_per_page' => -1,
			)
		);
		foreach ( $team_ids as $team_id ) {
			$presets = get_post_meta( (int) $team_id, 'tacnav_quick_presets', true );
			if ( ! is_array( $presets ) ) {
				continue;
			}
			$changed = false;
			foreach ( $presets as &$preset ) {
				if ( ! is_array( $preset ) || ! array_key_exists( 'ttl_minutes', $preset ) ) {
					continue;
				}
				$preset['ttl_seconds'] = max( 0, (int) $preset['ttl_minutes'] * 60 );
				unset( $preset['ttl_minutes'] );
				$changed = true;
			}
			unset( $preset );
			if ( $changed ) {
				update_post_meta( (int) $team_id, 'tacnav_quick_presets', $presets );
			}
		}
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
