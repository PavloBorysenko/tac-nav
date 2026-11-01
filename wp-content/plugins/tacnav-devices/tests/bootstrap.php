<?php
/**
 * Isolated test bootstrap (no WordPress runtime).
 *
 * @package TacNav_Devices
 */

if ( ! defined( 'ABSPATH' ) ) {
	define( 'ABSPATH', __DIR__ . '/' );
}
if ( ! defined( 'TACNAV_DEVICES_FILE' ) ) {
	define( 'TACNAV_DEVICES_FILE', dirname( __DIR__ ) . '/tacnav-devices.php' );
}

if ( ! function_exists( 'plugin_basename' ) ) {
	/**
	 * Return a stable test plugin basename.
	 *
	 * @param string $file Plugin file.
	 * @return string
	 */
	function plugin_basename( $file ) {
		return basename( $file );
	}
}

if ( ! function_exists( 'deactivate_plugins' ) ) {
	/**
	 * Record the deactivated plugin in isolated tests.
	 *
	 * @param string $plugin Plugin basename.
	 * @return void
	 */
	function deactivate_plugins( $plugin ) {
		$GLOBALS['tacnav_devices_deactivated'] = $plugin;
	}
}

if ( ! function_exists( 'esc_html__' ) ) {
	/**
	 * Translation stub.
	 *
	 * @param string $text   Source text.
	 * @param string $domain Text domain.
	 * @return string
	 */
	function esc_html__( $text, $domain ) {
		unset( $domain );
		return $text;
	}
}

if ( ! function_exists( '__' ) ) {
	/**
	 * Translation stub.
	 *
	 * @param string $text   Source text.
	 * @param string $domain Text domain.
	 * @return string
	 */
	function __( $text, $domain ) {
		unset( $domain );
		return $text;
	}
}

if ( ! class_exists( 'WP_Error' ) ) {
	/**
	 * Minimal isolated WP_Error test double.
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
		 * Create an error.
		 *
		 * @param string $code    Code.
		 * @param string $message Message.
		 */
		public function __construct( $code = '', $message = '' ) {
			$this->code    = $code;
			$this->message = $message;
		}

		/**
		 * Error code.
		 *
		 * @return string
		 */
		public function get_error_code() {
			return $this->code;
		}

		/**
		 * Error message.
		 *
		 * @return string
		 */
		public function get_error_message() {
			return $this->message;
		}
	}
}

if ( ! class_exists( 'WP_REST_Request' ) ) {
	/**
	 * Minimal isolated request test double.
	 */
	class WP_REST_Request {
		/**
		 * Request headers.
		 *
		 * @var array<string, string>
		 */
		private $headers;

		/**
		 * Create request.
		 *
		 * @param array<string, string> $headers Headers.
		 */
		public function __construct( $headers = array() ) {
			$this->headers = $headers;
		}

		/**
		 * Read header.
		 *
		 * @param string $name Header name.
		 * @return string
		 */
		public function get_header( $name ) {
			return isset( $this->headers[ $name ] ) ? $this->headers[ $name ] : '';
		}
	}
}

if ( ! function_exists( 'is_wp_error' ) ) {
	/**
	 * Whether value is a WP_Error.
	 *
	 * @param mixed $value Value.
	 * @return bool
	 */
	function is_wp_error( $value ) {
		return $value instanceof WP_Error;
	}
}

if ( ! function_exists( 'wp_die' ) ) {
	/**
	 * Turn activation termination into an assertion-friendly exception.
	 *
	 * @param string $message Error message.
	 * @return never
	 * @throws RuntimeException Always.
	 */
	function wp_die( $message ) {
		throw new RuntimeException( $message );
	}
}

if ( ! function_exists( 'sanitize_key' ) ) {
	/**
	 * Key sanitizer stub.
	 *
	 * @param string $key Key.
	 * @return string
	 */
	function sanitize_key( $key ) {
		return preg_replace( '/[^a-z0-9_\-]/', '', strtolower( $key ) );
	}
}

if ( ! function_exists( 'sanitize_text_field' ) ) {
	/**
	 * Text sanitizer stub.
	 *
	 * @param string $text Text.
	 * @return string
	 */
	function sanitize_text_field( $text ) {
		return trim( $text );
	}
}

if ( ! function_exists( 'esc_url_raw' ) ) {
	/**
	 * URL sanitizer stub.
	 *
	 * @param string $url URL.
	 * @return string
	 */
	function esc_url_raw( $url ) {
		return $url;
	}
}

if ( ! function_exists( 'get_option' ) ) {
	/**
	 * Read an isolated option.
	 *
	 * @param string $name     Option.
	 * @param mixed  $fallback Default.
	 * @return mixed
	 */
	function get_option( $name, $fallback = false ) {
		return isset( $GLOBALS['tacnav_test_options'][ $name ] ) ? $GLOBALS['tacnav_test_options'][ $name ] : $fallback;
	}
}

if ( ! function_exists( 'home_url' ) ) {
	/**
	 * Site URL stub.
	 *
	 * @param string $path Path.
	 * @return string
	 */
	function home_url( $path = '' ) {
		return 'https://example.test' . $path;
	}
}

if ( ! function_exists( 'wp_upload_dir' ) ) {
	/**
	 * Uploads directory stub.
	 *
	 * @return array<string, mixed>
	 */
	function wp_upload_dir() {
		return array(
			'basedir' => sys_get_temp_dir() . '/tacnav-test-uploads',
			'error'   => false,
		);
	}
}

if ( ! function_exists( 'trailingslashit' ) ) {
	/**
	 * Trailing slash stub.
	 *
	 * @param string $value Path.
	 * @return string
	 */
	function trailingslashit( $value ) {
		return rtrim( $value, '/\\' ) . '/';
	}
}

if ( ! function_exists( 'add_rewrite_rule' ) ) {
	/**
	 * Record a rewrite rule.
	 *
	 * @param string $regex Pattern.
	 * @param string $query Query.
	 * @return void
	 */
	function add_rewrite_rule( $regex, $query ) {
		$GLOBALS['tacnav_test_rewrites'][ $regex ] = $query;
	}
}

if ( ! function_exists( 'current_user_can' ) ) {
	/**
	 * Isolated capability check.
	 *
	 * @param string $capability Capability.
	 * @return bool
	 */
	function current_user_can( $capability ) {
		unset( $capability );
		return ! empty( $GLOBALS['tacnav_test_is_staff'] );
	}
}

if ( ! function_exists( 'current_time' ) ) {
	/**
	 * Current UTC database time.
	 *
	 * @return string
	 */
	function current_time() {
		return gmdate( 'Y-m-d H:i:s' );
	}
}

require_once dirname( __DIR__, 2 ) . '/tacnav-maps/app/Roles.php';
require_once dirname( __DIR__ ) . '/app/Activator.php';
require_once dirname( __DIR__ ) . '/app/App_Types.php';
require_once dirname( __DIR__ ) . '/app/Validation.php';
require_once dirname( __DIR__ ) . '/app/Application_Store.php';
require_once dirname( __DIR__ ) . '/app/Application_Context.php';
require_once dirname( __DIR__ ) . '/app/Mailbox.php';
require_once dirname( __DIR__ ) . '/app/Firebase.php';
require_once dirname( __DIR__ ) . '/app/Release.php';
require_once dirname( __DIR__ ) . '/app/REST.php';
require_once dirname( __DIR__ ) . '/app/Admin.php';
require_once dirname( __DIR__ ) . '/app/Apps/App_Page.php';
require_once dirname( __DIR__ ) . '/app/Apps/App_Pages.php';
require_once dirname( __DIR__ ) . '/app/Apps/Mortar_Page.php';
require_once dirname( __DIR__ ) . '/app/Apps/Object_Page.php';
