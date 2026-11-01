<?php
/**
 * Staff device administration and Android handoff.
 *
 * @package TacNav_Devices
 */

namespace TacNav\Devices;

defined( 'ABSPATH' ) || exit;

/**
 * Provides capability-protected application and release controls.
 */
class Admin {

	const PAGE = 'tacnav-devices';

	/**
	 * Register menu.
	 *
	 * @return void
	 */
	public static function menu() {
		add_submenu_page(
			'edit.php?post_type=tacnav_map',
			__( 'Device applications', 'tacnav-devices' ),
			__( 'Device applications', 'tacnav-devices' ),
			\TacNav\Maps\Roles::CAP_GEO,
			self::PAGE,
			array( __CLASS__, 'render' )
		);
	}

	/**
	 * Render application administration.
	 *
	 * @return void
	 */
	public static function render() {
		if ( ! REST::staff_permission() ) {
			wp_die( esc_html__( 'You are not allowed to manage device applications.', 'tacnav-devices' ) );
		}
		$tacnav_pages = Apps\App_Pages::all();
		// phpcs:disable WordPress.Security.NonceVerification.Recommended -- staff tab selection after capability check.
		$tacnav_requested = isset( $_GET['app'] ) ? sanitize_key( wp_unslash( $_GET['app'] ) ) : '';
		// phpcs:enable WordPress.Security.NonceVerification.Recommended
		$tacnav_current = Apps\App_Pages::resolve( $tacnav_pages, $tacnav_requested );
		require TACNAV_DEVICES_DIR . 'templates/admin-shell.php';
	}

	/**
	 * Save an application from the protected form.
	 *
	 * @return void
	 */
	public static function save_application() {
		self::staff_guard();
		check_admin_referer( 'tacnav_devices_save_application' );
		$posted = wp_unslash( $_POST );
		$type   = isset( $posted['application_type'] ) ? sanitize_key( (string) $posted['application_type'] ) : '';
		$name   = isset( $posted['name'] ) ? (string) $posted['name'] : '';
		if ( App_Types::MORTAR === App_Types::canonical( $type ) ) {
			$posted['firing_modes'] = isset( $posted['firing_modes'] ) ? $posted['firing_modes'] : array();
			$posted['aiming_modes'] = isset( $posted['aiming_modes'] ) ? $posted['aiming_modes'] : array();
		}
		$reference = App_Types::reference_settings( $type, $posted, $name );
		if ( is_wp_error( $reference ) ) {
			wp_die( esc_html( $reference->get_error_message() ) );
		}
		$result = ( new Application_Store() )->save(
			array(
				'id'                 => isset( $_POST['id'] ) ? (int) $_POST['id'] : 0,
				'application_type'   => $type,
				'name'               => $name,
				'allowed_map_ids'    => self::posted_ids( isset( $posted['allowed_map_ids'] ) ? $posted['allowed_map_ids'] : array() ),
				'allowed_team_ids'   => self::posted_ids( isset( $posted['allowed_team_ids'] ) ? $posted['allowed_team_ids'] : array() ),
				'default_map_id'     => isset( $_POST['default_map_id'] ) ? (int) $_POST['default_map_id'] : 0,
				'default_team_id'    => isset( $_POST['default_team_id'] ) ? (int) $_POST['default_team_id'] : 0,
				'reference_settings' => $reference,
			)
		);
		if ( is_wp_error( $result ) ) {
			wp_die( esc_html( $result->get_error_message() ) );
		}
		wp_safe_redirect( admin_url( self::page_query( App_Types::canonical( $type ), 'saved' ) ) );
		exit;
	}

	/**
	 * Generate and display a one-time activation without redirects or logs.
	 *
	 * @return void
	 */
	public static function create_activation() {
		self::staff_guard();
		check_admin_referer( 'tacnav_devices_create_activation' );
		$id    = isset( $_POST['id'] ) ? (int) $_POST['id'] : 0;
		$store = new Application_Store();
		$code  = $store->create_activation( $id, get_current_user_id() );
		if ( is_wp_error( $code ) ) {
			wp_die( esc_html( $code->get_error_message() ) );
		}
		$application = $store->get( $id );
		$tacnav_type = App_Types::canonical( $application ? (string) $application['application_type'] : '' );
		self::scripts();
		wp_enqueue_script( 'tacnav-devices-admin' );
		wp_enqueue_style(
			'tacnav-devices-activation',
			TACNAV_DEVICES_URL . 'assets/activation.css',
			array(),
			TACNAV_DEVICES_VERSION
		);
		$release = self::release_metadata( $tacnav_type );
		$payload = wp_json_encode(
			array(
				'server_url' => home_url( '/' ),
				'code'       => $code,
			)
		);
		require TACNAV_DEVICES_DIR . 'templates/activation.php';
		exit;
	}

	/**
	 * Revoke an active phone.
	 *
	 * @return void
	 */
	public static function revoke() {
		self::staff_guard();
		check_admin_referer( 'tacnav_devices_revoke' );
		$id  = isset( $_POST['id'] ) ? (int) $_POST['id'] : 0;
		$app = isset( $_POST['app'] ) ? sanitize_key( wp_unslash( $_POST['app'] ) ) : '';
		( new Application_Store() )->revoke( $id );
		wp_safe_redirect( admin_url( self::page_query( $app, 'revoked' ) ) );
		exit;
	}

	/**
	 * Save public release metadata only.
	 *
	 * @return void
	 */
	public static function save_release() {
		self::staff_guard();
		check_admin_referer( 'tacnav_devices_save_release' );
		$app     = isset( $_POST['app'] ) ? sanitize_key( wp_unslash( $_POST['app'] ) ) : '';
		$type    = App_Types::canonical( $app );
		$details = App_Types::get( $type );
		$current = self::release_metadata( $type );
		$version = sanitize_text_field( (string) wp_unslash( isset( $_POST['version'] ) ? $_POST['version'] : '' ) );
		$url     = esc_url_raw( (string) wp_unslash( isset( $_POST['download_url'] ) ? $_POST['download_url'] : '' ) );

		if ( isset( $_FILES['apk'] ) && is_array( $_FILES['apk'] ) && ! empty( $_FILES['apk']['tmp_name'] ) ) {
			$stored = Release::store_upload( $_FILES['apk'], $type );
			if ( is_wp_error( $stored ) ) {
				wp_die( esc_html( $stored->get_error_message() ) );
			}
			$url = $stored;
		}
		if ( '' === $url ) {
			$url = $current['download_url'];
		}
		if ( '' === $url && Release::has_stored_apk( $type ) ) {
			$url = Release::public_apk_url( $type );
		}

		update_option(
			$details['option'],
			array(
				'package_id'   => $details['package_id'],
				'version'      => $version,
				'download_url' => $url,
			),
			false
		);
		wp_safe_redirect( admin_url( self::page_query( $app, 'release_saved' ) ) );
		exit;
	}

	/**
	 * Staff page address for a tab. Mortar keeps the address it always had.
	 *
	 * @param string $app  Application tab slug.
	 * @param string $flag Result flag to show, if any.
	 * @return string
	 */
	public static function page_query( $app, $flag = '' ) {
		$query = 'edit.php?post_type=tacnav_map&page=' . self::PAGE;
		if ( App_Types::OBJECT === $app ) {
			$query .= '&app=' . $app;
		}
		if ( '' !== $flag ) {
			$query .= '&' . $flag . '=1';
		}
		return $query;
	}

	/**
	 * Current safe APK metadata for one application.
	 *
	 * @param string $type Application type.
	 * @return array<string, string>
	 */
	public static function release_metadata( $type = App_Types::MORTAR ) {
		$details = App_Types::get( $type );
		$value   = get_option( $details['option'], array() );
		$value   = is_array( $value ) ? $value : array();
		$url     = esc_url_raw( isset( $value['download_url'] ) ? (string) $value['download_url'] : '' );
		if ( '' === $url && Release::has_stored_apk( $type ) ) {
			$url = Release::public_apk_url( $type );
		}
		return array(
			'package_id'   => $details['package_id'],
			'version'      => sanitize_text_field( isset( $value['version'] ) ? (string) $value['version'] : '' ),
			'download_url' => $url,
		);
	}

	/**
	 * Register scripts shared by the handoff response, and style the staff page.
	 *
	 * @param string $hook Admin page hook, empty outside `admin_enqueue_scripts`.
	 * @return void
	 */
	public static function scripts( $hook = '' ) {
		wp_register_script( 'tacnav-qrcode', TACNAV_MAPS_URL . 'assets/vendor/qrcode.js', array(), TACNAV_MAPS_VERSION, true );
		wp_register_script( 'tacnav-devices-admin', TACNAV_DEVICES_URL . 'assets/admin.js', array( 'tacnav-qrcode' ), TACNAV_DEVICES_VERSION, true );
		if ( false !== strpos( (string) $hook, self::PAGE ) ) {
			wp_enqueue_style( 'tacnav-devices-admin', TACNAV_DEVICES_URL . 'assets/admin.css', array(), TACNAV_DEVICES_VERSION );
		}
	}

	/**
	 * Check staff capability.
	 *
	 * @return void
	 */
	private static function staff_guard() {
		if ( ! REST::staff_permission() ) {
			wp_die( esc_html__( 'You are not allowed to manage device applications.', 'tacnav-devices' ) );
		}
	}

	/**
	 * Parse posted catalog identifiers from checkboxes or a leftover CSV.
	 *
	 * @param mixed $value Input.
	 * @return array<int, int>
	 */
	private static function posted_ids( $value ) {
		if ( is_array( $value ) ) {
			return Validation::ids( $value );
		}
		return Validation::ids( preg_split( '/\s*,\s*/', trim( (string) $value ) ) );
	}
}
