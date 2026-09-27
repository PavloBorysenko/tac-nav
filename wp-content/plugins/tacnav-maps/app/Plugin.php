<?php
/**
 * Plugin loader.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Wires hooks.
 */
class Plugin {

	/**
	 * Boot the plugin.
	 *
	 * @return void
	 */
	public static function boot() {
		load_plugin_textdomain( 'tacnav-maps', false, dirname( plugin_basename( TACNAV_MAPS_FILE ) ) . '/languages' );

		add_action( 'init', array( Post_Types::class, 'register' ) );
		add_action( 'init', array( Canvas::class, 'register_rewrite' ) );
		add_action( 'init', array( Account::class, 'register_rewrite' ) );
		add_action( 'init', array( Guest_Link::class, 'register_rewrite' ) );
		add_action( 'init', array( Activator::class, 'maybe_upgrade' ), 99 );
		add_filter( 'query_vars', array( Canvas::class, 'query_vars' ) );
		add_filter( 'query_vars', array( Account::class, 'query_vars' ) );
		add_filter( 'query_vars', array( Guest_Link::class, 'query_vars' ) );
		add_action( 'template_redirect', array( Canvas::class, 'template_redirect' ) );
		add_action( 'template_redirect', array( Account::class, 'template_redirect' ) );
		add_action( 'template_redirect', array( Guest_Link::class, 'template_redirect' ) );
		add_action( 'rest_api_init', array( REST::class, 'register' ) );
		add_action( 'add_meta_boxes', array( Admin::class, 'add_meta_boxes' ) );
		add_action( 'admin_enqueue_scripts', array( Admin::class, 'enqueue_admin' ) );
		add_action( 'load-index.php', array( Admin::class, 'redirect_organizer_home' ) );
		add_action( 'admin_bar_menu', array( Admin::class, 'admin_bar' ), 80 );
		add_filter( 'use_block_editor_for_post_type', array( Admin::class, 'disable_block_editor' ), 10, 2 );
		add_action( 'after_setup_theme', array( Admin::class, 'support_thumbnails' ) );
		add_action( 'save_post', array( Admin::class, 'save_post' ) );
		add_filter( 'post_row_actions', array( Admin::class, 'row_actions' ), 10, 2 );
		add_action( 'before_delete_post', array( Cascades::class, 'before_delete_post' ), 10, 2 );
	}
}
