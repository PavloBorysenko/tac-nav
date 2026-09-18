<?php
/**
 * TacNav child theme bootstrap.
 *
 * @package TacNav
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

/**
 * Load the child theme text domain.
 *
 * @return void
 */
function tacnav_load_textdomain() {
	load_theme_textdomain( 'tacnav', get_stylesheet_directory() . '/languages' );
}
add_action( 'after_setup_theme', 'tacnav_load_textdomain' );
