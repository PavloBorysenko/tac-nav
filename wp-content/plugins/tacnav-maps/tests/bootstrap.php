<?php
/**
 * Isolated test bootstrap (no WordPress runtime).
 *
 * @package TacNav_Maps
 */

if ( ! defined( 'ABSPATH' ) ) {
	define( 'ABSPATH', __DIR__ . '/' );
}
if ( ! defined( 'ARRAY_A' ) ) {
	define( 'ARRAY_A', 'ARRAY_A' );
}

if ( ! class_exists( 'WP_Error' ) ) {
	/**
	 * Minimal isolated WP_Error.
	 */
	class WP_Error {
		/**
		 * Error code.
		 *
		 * @var string
		 */
		private $code;

		/**
		 * Error message.
		 *
		 * @var string
		 */
		private $message;

		/**
		 * @param string $code    Code.
		 * @param string $message Message.
		 */
		public function __construct( $code = '', $message = '' ) {
			$this->code    = $code;
			$this->message = $message;
		}

		/**
		 * @return string
		 */
		public function get_error_code() {
			return $this->code;
		}

		/**
		 * @return string
		 */
		public function get_error_message() {
			return $this->message;
		}
	}
}

if ( ! function_exists( 'is_wp_error' ) ) {
	/**
	 * @param mixed $thing Value.
	 * @return bool
	 */
	function is_wp_error( $thing ) {
		return $thing instanceof WP_Error;
	}
}

if ( ! function_exists( '__' ) ) {
	/**
	 * @param string $text Text.
	 * @return string
	 */
	function __( $text ) {
		return $text;
	}
}

if ( ! function_exists( 'sanitize_key' ) ) {
	/**
	 * @param string $key Key.
	 * @return string
	 */
	function sanitize_key( $key ) {
		return preg_replace( '/[^a-z0-9_\-]/', '', strtolower( $key ) );
	}
}

if ( ! function_exists( 'sanitize_text_field' ) ) {
	/**
	 * @param string $text Text.
	 * @return string
	 */
	function sanitize_text_field( $text ) {
		return trim( $text );
	}
}

if ( ! function_exists( 'wp_json_encode' ) ) {
	/**
	 * @param mixed $value Value.
	 * @return string|false
	 */
	function wp_json_encode( $value ) {
		return json_encode( $value );
	}
}

if ( ! function_exists( 'wp_kses_post' ) ) {
	/**
	 * @param string $text Text.
	 * @return string
	 */
	function wp_kses_post( $text ) {
		return $text;
	}
}

if ( ! function_exists( 'get_current_user_id' ) ) {
	/**
	 * @return int
	 */
	function get_current_user_id() {
		return 0;
	}
}

if ( ! function_exists( 'get_post_type' ) ) {
	/**
	 * @param int $post_id Post ID.
	 * @return string|false
	 */
	function get_post_type( $post_id ) {
		global $tacnav_test_post_types;
		return isset( $tacnav_test_post_types[ $post_id ] ) ? $tacnav_test_post_types[ $post_id ] : false;
	}
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
		global $tacnav_test_filters;
		$args = func_get_args();
		if ( isset( $tacnav_test_filters[ $hook ] ) && is_callable( $tacnav_test_filters[ $hook ] ) ) {
			return call_user_func_array( $tacnav_test_filters[ $hook ], array_slice( $args, 1 ) );
		}
		return $value;
	}
}

$tacnav_maps_autoload = dirname( __DIR__ ) . '/vendor/autoload.php';
if ( is_readable( $tacnav_maps_autoload ) ) {
	require_once $tacnav_maps_autoload;
} else {
	require_once dirname( __DIR__ ) . '/app/Guest_Token.php';
	require_once dirname( __DIR__ ) . '/app/Object_Expiry.php';
	require_once dirname( __DIR__ ) . '/app/Player_Write.php';
	require_once dirname( __DIR__ ) . '/app/Membership.php';
	require_once dirname( __DIR__ ) . '/app/Access.php';
	require_once dirname( __DIR__ ) . '/app/Geometry_Service.php';
	require_once dirname( __DIR__ ) . '/app/Application_Principal.php';
	require_once dirname( __DIR__ ) . '/app/Post_Types.php';
	require_once dirname( __DIR__ ) . '/app/Application_Write.php';
	require_once dirname( __DIR__ ) . '/app/Activator.php';
	require_once dirname( __DIR__ ) . '/app/Geo_Store.php';
}
