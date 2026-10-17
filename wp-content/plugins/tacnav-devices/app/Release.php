<?php
/**
 * Hosted Tac Nav Mortar APK and public download metadata.
 *
 * @package TacNav_Devices
 */

namespace TacNav\Devices;

defined( 'ABSPATH' ) || exit;

/**
 * Stores a staff-uploaded APK and exposes public download routes.
 */
class Release {

	const PACKAGE_ID = 'com.tacnav.mortar';
	const FILE_NAME  = 'tacnav-mortar.apk';
	const MAX_BYTES  = 104857600;

	/**
	 * Register public download rewrite rules.
	 *
	 * @return void
	 */
	public static function register_rewrites() {
		add_rewrite_rule( '^tacnav-mortar\.apk$', 'index.php?tacnav_mortar_apk=1', 'top' );
		add_rewrite_rule( '^tacnav-mortar/?$', 'index.php?tacnav_mortar_download=1', 'top' );
	}

	/**
	 * Public query vars.
	 *
	 * @param array<int, string> $vars Query vars.
	 * @return array<int, string>
	 */
	public static function query_vars( $vars ) {
		$vars[] = 'tacnav_mortar_download';
		$vars[] = 'tacnav_mortar_apk';
		return $vars;
	}

	/**
	 * Serve the public download page or APK file.
	 *
	 * @return void
	 */
	public static function template_redirect() {
		if ( get_query_var( 'tacnav_mortar_apk' ) ) {
			self::send_apk();
		}
		if ( get_query_var( 'tacnav_mortar_download' ) ) {
			self::render_download_page();
		}
	}

	/**
	 * Stable public APK URL.
	 *
	 * @return string
	 */
	public static function public_apk_url() {
		return self::pretty_permalinks()
			? esc_url_raw( home_url( '/tacnav-mortar.apk' ) )
			: esc_url_raw( home_url( '/?tacnav_mortar_apk=1' ) );
	}

	/**
	 * Stable public download page URL.
	 *
	 * @return string
	 */
	public static function public_page_url() {
		return self::pretty_permalinks()
			? esc_url_raw( home_url( '/tacnav-mortar/' ) )
			: esc_url_raw( home_url( '/?tacnav_mortar_download=1' ) );
	}

	/**
	 * Whether pretty permalinks can serve the public routes.
	 *
	 * @return bool
	 */
	private static function pretty_permalinks() {
		$structure = get_option( 'permalink_structure' );
		return is_string( $structure ) && '' !== $structure;
	}

	/**
	 * Absolute path of the hosted APK, if present.
	 *
	 * @return string
	 */
	public static function stored_path() {
		$uploads = wp_upload_dir();
		if ( ! empty( $uploads['error'] ) ) {
			return '';
		}
		return trailingslashit( $uploads['basedir'] ) . 'tacnav-devices/' . self::FILE_NAME;
	}

	/**
	 * Whether a hosted APK is available.
	 *
	 * @return bool
	 */
	public static function has_stored_apk() {
		$path = self::stored_path();
		return '' !== $path && is_readable( $path );
	}

	/**
	 * Accept only a real Android package upload.
	 *
	 * @param mixed $file $_FILES row.
	 * @return true|\WP_Error
	 */
	public static function validate_apk_upload( $file ) {
		if ( ! is_array( $file ) || empty( $file['tmp_name'] ) || ! is_string( $file['tmp_name'] ) ) {
			return new \WP_Error( 'tacnav_apk_missing', __( 'Choose an APK file to upload.', 'tacnav-devices' ) );
		}
		$error = isset( $file['error'] ) ? (int) $file['error'] : 0;
		if ( 0 !== $error ) {
			return new \WP_Error( 'tacnav_apk_upload', __( 'The APK could not be uploaded.', 'tacnav-devices' ) );
		}
		$name = isset( $file['name'] ) ? (string) $file['name'] : '';
		if ( ! preg_match( '/\.apk$/i', $name ) ) {
			return new \WP_Error( 'tacnav_apk_type', __( 'The file must be a Tac Nav Mortar APK.', 'tacnav-devices' ) );
		}
		$size = isset( $file['size'] ) ? (int) $file['size'] : 0;
		if ( $size <= 0 || $size > self::MAX_BYTES ) {
			return new \WP_Error( 'tacnav_apk_size', __( 'The APK is missing or larger than 100 MB.', 'tacnav-devices' ) );
		}
		if ( ! is_readable( $file['tmp_name'] ) ) {
			return new \WP_Error( 'tacnav_apk_upload', __( 'The APK could not be uploaded.', 'tacnav-devices' ) );
		}
		// phpcs:disable WordPress.WP.AlternativeFunctions.file_system_operations_fopen,WordPress.WP.AlternativeFunctions.file_system_operations_fread,WordPress.WP.AlternativeFunctions.file_system_operations_fclose -- inspect a local upload, not a remote URL.
		$handle = fopen( $file['tmp_name'], 'rb' );
		$header = $handle ? (string) fread( $handle, 4 ) : '';
		if ( $handle ) {
			fclose( $handle );
		}
		// phpcs:enable
		if ( 0 !== strpos( $header, 'PK' ) ) {
			return new \WP_Error( 'tacnav_apk_type', __( 'The file must be a Tac Nav Mortar APK.', 'tacnav-devices' ) );
		}
		return true;
	}

	/**
	 * Store a validated staff upload and return the public APK URL.
	 *
	 * @param array<string, mixed> $file $_FILES row.
	 * @return string|\WP_Error
	 */
	public static function store_upload( $file ) {
		$valid = self::validate_apk_upload( $file );
		if ( is_wp_error( $valid ) ) {
			return $valid;
		}

		$uploads = wp_upload_dir();
		if ( ! empty( $uploads['error'] ) ) {
			return new \WP_Error( 'tacnav_apk_store', __( 'The APK could not be stored.', 'tacnav-devices' ) );
		}

		$directory = trailingslashit( $uploads['basedir'] ) . 'tacnav-devices';
		if ( ! wp_mkdir_p( $directory ) ) {
			return new \WP_Error( 'tacnav_apk_store', __( 'The APK could not be stored.', 'tacnav-devices' ) );
		}
		self::protect_directory( $directory );

		$destination = trailingslashit( $directory ) . self::FILE_NAME;
		$moved       = move_uploaded_file( (string) $file['tmp_name'], $destination );
		if ( ! $moved && ! copy( (string) $file['tmp_name'], $destination ) ) {
			return new \WP_Error( 'tacnav_apk_store', __( 'The APK could not be stored.', 'tacnav-devices' ) );
		}
		return self::public_apk_url();
	}

	/**
	 * Send the hosted APK.
	 *
	 * @return void
	 */
	private static function send_apk() {
		$path = self::stored_path();
		if ( '' === $path || ! is_readable( $path ) ) {
			status_header( 404 );
			nocache_headers();
			wp_die( esc_html__( 'No Tac Nav Mortar APK has been published.', 'tacnav-devices' ), '', array( 'response' => 404 ) );
		}

		nocache_headers();
		header( 'Content-Type: application/vnd.android.package-archive' );
		header( 'Content-Disposition: attachment; filename="' . self::FILE_NAME . '"' );
		header( 'Content-Length: ' . (string) filesize( $path ) );
		// Local binary stream; WP_Filesystem would load the whole APK into memory.
		readfile( $path ); // phpcs:ignore WordPress.WP.AlternativeFunctions.file_system_operations_readfile
		exit;
	}

	/**
	 * Render the public English download page.
	 *
	 * @return void
	 */
	private static function render_download_page() {
		$release = Admin::release_metadata();
		nocache_headers();
		status_header( 200 );
		wp_enqueue_style(
			'tacnav-devices-download',
			TACNAV_DEVICES_URL . 'assets/download.css',
			array(),
			TACNAV_DEVICES_VERSION
		);
		require TACNAV_DEVICES_DIR . 'templates/download.php';
		exit;
	}

	/**
	 * Keep the upload directory from listing files.
	 *
	 * @param string $directory Destination directory.
	 * @return void
	 */
	private static function protect_directory( $directory ) {
		$index = trailingslashit( $directory ) . 'index.php';
		if ( ! is_readable( $index ) ) {
			// phpcs:disable WordPress.WP.AlternativeFunctions.file_system_operations_fopen,WordPress.WP.AlternativeFunctions.file_system_operations_fwrite,WordPress.WP.AlternativeFunctions.file_system_operations_fclose -- tiny local directory guard.
			$index_file = fopen( $index, 'wb' );
			if ( $index_file ) {
				fwrite( $index_file, "<?php\n// Silence is golden.\n" );
				fclose( $index_file );
			}
			// phpcs:enable
		}
	}
}
