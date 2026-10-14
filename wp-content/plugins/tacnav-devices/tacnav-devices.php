<?php
/**
 * Plugin Name: TacNav Devices
 * Description: Device provisioning, messaging, and Android release handoff for TacNav.
 * Version: 0.1.0
 * Author: SND Team
 * License: GPL-2.0-or-later
 * Requires at least: 6.7
 * Requires PHP: 7.2
 * Requires Plugins: tacnav-maps
 * Text Domain: tacnav-devices
 *
 * @package TacNav_Devices
 */

defined( 'ABSPATH' ) || exit;

define( 'TACNAV_DEVICES_VERSION', '0.1.0' );
define( 'TACNAV_DEVICES_FILE', __FILE__ );
define( 'TACNAV_DEVICES_DIR', plugin_dir_path( __FILE__ ) );
define( 'TACNAV_DEVICES_URL', plugin_dir_url( __FILE__ ) );

$tacnav_devices_autoload = TACNAV_DEVICES_DIR . 'vendor/autoload.php';
if ( is_readable( $tacnav_devices_autoload ) ) {
	require_once $tacnav_devices_autoload;
} else {
	spl_autoload_register(
		static function ( $class_name ) {
			$prefix = 'TacNav\\Devices\\';
			if ( 0 !== strncmp( $class_name, $prefix, strlen( $prefix ) ) ) {
				return;
			}

			$relative = str_replace( '\\', '/', substr( $class_name, strlen( $prefix ) ) );
			$file     = TACNAV_DEVICES_DIR . 'app/' . $relative . '.php';
			if ( is_readable( $file ) ) {
				require $file;
			}
		}
	);
}

register_activation_hook( TACNAV_DEVICES_FILE, array( \TacNav\Devices\Activator::class, 'activate' ) );
register_deactivation_hook( TACNAV_DEVICES_FILE, array( \TacNav\Devices\Activator::class, 'deactivate' ) );

add_action( 'plugins_loaded', array( \TacNav\Devices\Plugin::class, 'boot' ) );
