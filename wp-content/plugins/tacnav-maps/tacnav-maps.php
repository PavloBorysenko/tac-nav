<?php
/**
 * Plugin Name: TacNav Maps
 * Description: Staff map studio: maps, teams, icons, and geo objects.
 * Version: 0.1.4
 * Author: SND Team
 * License: GPL-2.0-or-later
 * Requires at least: 6.7
 * Requires PHP: 7.2
 * Text Domain: tacnav-maps
 *
 * @package TacNav_Maps
 */

defined( 'ABSPATH' ) || exit;

define( 'TACNAV_MAPS_VERSION', '0.1.4' );
define( 'TACNAV_MAPS_FILE', __FILE__ );
define( 'TACNAV_MAPS_DIR', plugin_dir_path( __FILE__ ) );
define( 'TACNAV_MAPS_URL', plugin_dir_url( __FILE__ ) );

$tacnav_maps_autoload = TACNAV_MAPS_DIR . 'vendor/autoload.php';
if ( is_readable( $tacnav_maps_autoload ) ) {
	require_once $tacnav_maps_autoload;
} else {
	spl_autoload_register(
		static function ( $class_name ) {
			$prefix = 'TacNav\\Maps\\';
			if ( 0 !== strncmp( $class_name, $prefix, 12 ) ) {
				return;
			}
			$rel  = str_replace( '\\', '/', substr( $class_name, 12 ) );
			$file = TACNAV_MAPS_DIR . 'app/' . $rel . '.php';
			if ( is_readable( $file ) ) {
				require $file;
			}
		}
	);
}

register_activation_hook( TACNAV_MAPS_FILE, array( \TacNav\Maps\Activator::class, 'activate' ) );
register_deactivation_hook( TACNAV_MAPS_FILE, array( \TacNav\Maps\Activator::class, 'deactivate' ) );

add_action( 'plugins_loaded', array( \TacNav\Maps\Plugin::class, 'boot' ) );
