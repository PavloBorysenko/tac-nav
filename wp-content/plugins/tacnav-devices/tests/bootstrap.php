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

require_once dirname( __DIR__ ) . '/app/Activator.php';
require_once dirname( __DIR__ ) . '/app/Validation.php';
require_once dirname( __DIR__ ) . '/app/Application_Store.php';
require_once dirname( __DIR__ ) . '/app/Application_Context.php';
require_once dirname( __DIR__ ) . '/app/Mailbox.php';
require_once dirname( __DIR__ ) . '/app/Firebase.php';
