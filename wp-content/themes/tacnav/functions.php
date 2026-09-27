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

/**
 * Hide player menu links that do not match the current session.
 *
 * Guest: keep /tacnav-player/#login and #register. Signed-in: keep the plain cabinet URL.
 *
 * @param string $block_content Block HTML.
 * @param array  $block         Block data.
 * @return string
 */
function tacnav_filter_player_nav_link( $block_content, $block ) {
	if ( ! isset( $block['blockName'] ) || 'core/navigation-link' !== $block['blockName'] ) {
		return $block_content;
	}

	$url  = isset( $block['attrs']['url'] ) ? (string) $block['attrs']['url'] : '';
	$path = wp_parse_url( $url, PHP_URL_PATH );
	if ( ! is_string( $path ) || ! preg_match( '#/tacnav-player/?$#', $path ) ) {
		return $block_content;
	}

	$fragment  = wp_parse_url( $url, PHP_URL_FRAGMENT );
	$auth_link = in_array( $fragment, array( 'login', 'register' ), true );
	if ( is_user_logged_in() ) {
		return $auth_link ? '' : $block_content;
	}

	return $auth_link ? $block_content : '';
}
add_filter( 'render_block', 'tacnav_filter_player_nav_link', 10, 2 );
