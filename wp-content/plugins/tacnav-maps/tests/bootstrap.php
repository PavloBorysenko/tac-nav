<?php
/**
 * Isolated test bootstrap (no WordPress runtime).
 *
 * @package TacNav_Maps
 */

if ( ! defined( 'ABSPATH' ) ) {
	define( 'ABSPATH', __DIR__ . '/' );
}

if ( ! function_exists( 'apply_filters' ) ) {
	/**
	 * Pass-through filter stub.
	 *
	 * @param string $hook  Hook name.
	 * @param mixed  $value Value.
	 * @return mixed
	 */
	function apply_filters( $hook, $value ) {
		unset( $hook );
		return $value;
	}
}

$tacnav_maps_autoload = dirname( __DIR__ ) . '/vendor/autoload.php';
if ( is_readable( $tacnav_maps_autoload ) ) {
	require_once $tacnav_maps_autoload;
} else {
	require_once dirname( __DIR__ ) . '/app/Guest_Token.php';
	require_once dirname( __DIR__ ) . '/app/Player_Write.php';
	require_once dirname( __DIR__ ) . '/app/Membership.php';
	require_once dirname( __DIR__ ) . '/app/Access.php';
}
