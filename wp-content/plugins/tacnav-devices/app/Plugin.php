<?php
/**
 * Plugin loader.
 *
 * @package TacNav_Devices
 */

namespace TacNav\Devices;

defined( 'ABSPATH' ) || exit;

/**
 * Wires plugin hooks after required components load.
 */
class Plugin {

	/**
	 * Boot the plugin.
	 *
	 * @return void
	 */
	public static function boot() {
		load_plugin_textdomain( 'tacnav-devices', false, dirname( plugin_basename( TACNAV_DEVICES_FILE ) ) . '/languages' );

		if ( ! defined( 'TACNAV_MAPS_VERSION' ) ) {
			add_action( 'admin_notices', array( __CLASS__, 'missing_dependency_notice' ) );
			return;
		}

		add_action( 'init', array( Activator::class, 'maybe_upgrade' ), 99 );
		add_action( 'init', array( Release::class, 'register_rewrites' ) );
		add_filter( 'query_vars', array( Release::class, 'query_vars' ) );
		add_action( 'template_redirect', array( Release::class, 'template_redirect' ) );
		add_action( 'rest_api_init', array( REST::class, 'register' ) );
		add_action( 'admin_menu', array( Admin::class, 'menu' ) );
		add_action( 'admin_enqueue_scripts', array( Admin::class, 'scripts' ) );
		add_action( 'admin_post_tacnav_devices_save_application', array( Admin::class, 'save_application' ) );
		add_action( 'admin_post_tacnav_devices_create_activation', array( Admin::class, 'create_activation' ) );
		add_action( 'admin_post_tacnav_devices_revoke', array( Admin::class, 'revoke' ) );
		add_action( 'admin_post_tacnav_devices_save_release', array( Admin::class, 'save_release' ) );
		add_action( 'tacnav_devices_cleanup', array( __CLASS__, 'cleanup' ) );
		add_action( 'tacnav_application_object_saved', array( __CLASS__, 'bind_object' ), 10, 2 );
		add_filter( 'tacnav_maps_application_principal', array( Application_Context::class, 'filter' ), 10, 2 );
	}

	/**
	 * Run scheduled mailbox cleanup.
	 *
	 * @return void
	 */
	public static function cleanup() {
		Mailbox::cleanup();
	}

	/**
	 * Bind an application-owned object after Maps commits it.
	 *
	 * @param int                  $object_id Geo object ID.
	 * @param array<string, mixed> $context   Authenticated context.
	 * @return void
	 */
	public static function bind_object( $object_id, $context ) {
		Mailbox::bind_object( $object_id, $context );
	}

	/**
	 * Explain why the plugin did not boot.
	 *
	 * @return void
	 */
	public static function missing_dependency_notice() {
		if ( ! current_user_can( 'activate_plugins' ) ) {
			return;
		}

		printf(
			'<div class="notice notice-error"><p>%s</p></div>',
			esc_html__( 'TacNav Devices requires the TacNav Maps plugin to be active.', 'tacnav-devices' )
		);
	}
}
