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
		$applications = ( new Application_Store() )->all();
		$action       = admin_url( 'admin-post.php' );
		$release      = self::release_metadata();
		// phpcs:disable WordPress.Security.NonceVerification.Recommended -- staff list filter after capability check.
		$edit_id = isset( $_GET['edit'] ) ? (int) wp_unslash( $_GET['edit'] ) : 0;
		// phpcs:enable WordPress.Security.NonceVerification.Recommended
		$tacnav_draft     = $edit_id > 0 ? ( new Application_Store() )->get( $edit_id ) : null;
		$tacnav_reference = Validation::reference_settings(
			is_array( $tacnav_draft ) && isset( $tacnav_draft['reference_settings'] ) ? $tacnav_draft['reference_settings'] : array()
		);
		if ( is_wp_error( $tacnav_reference ) ) {
			$tacnav_reference = Validation::reference_defaults();
		}
		require TACNAV_DEVICES_DIR . 'templates/admin-page.php';
	}

	/**
	 * Save an application from the protected form.
	 *
	 * @return void
	 */
	public static function save_application() {
		self::staff_guard();
		check_admin_referer( 'tacnav_devices_save_application' );
		$posted                 = wp_unslash( $_POST );
		$posted['firing_modes'] = isset( $posted['firing_modes'] ) ? $posted['firing_modes'] : array();
		$posted['aiming_modes'] = isset( $posted['aiming_modes'] ) ? $posted['aiming_modes'] : array();
		$reference              = Validation::reference_settings( $posted );
		if ( is_wp_error( $reference ) ) {
			wp_die( esc_html( $reference->get_error_message() ) );
		}
		$result = ( new Application_Store() )->save(
			array(
				'id'                 => isset( $_POST['id'] ) ? (int) $_POST['id'] : 0,
				'application_type'   => isset( $_POST['application_type'] ) ? wp_unslash( $_POST['application_type'] ) : '',
				'name'               => isset( $_POST['name'] ) ? wp_unslash( $_POST['name'] ) : '',
				'allowed_map_ids'    => self::csv_ids( isset( $_POST['allowed_map_ids'] ) ? wp_unslash( $_POST['allowed_map_ids'] ) : '' ),
				'allowed_team_ids'   => self::csv_ids( isset( $_POST['allowed_team_ids'] ) ? wp_unslash( $_POST['allowed_team_ids'] ) : '' ),
				'default_map_id'     => isset( $_POST['default_map_id'] ) ? (int) $_POST['default_map_id'] : 0,
				'default_team_id'    => isset( $_POST['default_team_id'] ) ? (int) $_POST['default_team_id'] : 0,
				'reference_settings' => $reference,
			)
		);
		if ( is_wp_error( $result ) ) {
			wp_die( esc_html( $result->get_error_message() ) );
		}
		wp_safe_redirect( admin_url( 'edit.php?post_type=tacnav_map&page=' . self::PAGE . '&saved=1' ) );
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
		$id   = isset( $_POST['id'] ) ? (int) $_POST['id'] : 0;
		$code = ( new Application_Store() )->create_activation( $id, get_current_user_id() );
		if ( is_wp_error( $code ) ) {
			wp_die( esc_html( $code->get_error_message() ) );
		}
		self::scripts();
		wp_enqueue_script( 'tacnav-devices-admin' );
		$release = self::release_metadata();
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
		$id = isset( $_POST['id'] ) ? (int) $_POST['id'] : 0;
		( new Application_Store() )->revoke( $id );
		wp_safe_redirect( admin_url( 'edit.php?post_type=tacnav_map&page=' . self::PAGE . '&revoked=1' ) );
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
		$current = self::release_metadata();
		$version = sanitize_text_field( (string) wp_unslash( isset( $_POST['version'] ) ? $_POST['version'] : '' ) );
		$url     = esc_url_raw( (string) wp_unslash( isset( $_POST['download_url'] ) ? $_POST['download_url'] : '' ) );

		if ( isset( $_FILES['apk'] ) && is_array( $_FILES['apk'] ) && ! empty( $_FILES['apk']['tmp_name'] ) ) {
			$stored = Release::store_upload( $_FILES['apk'] );
			if ( is_wp_error( $stored ) ) {
				wp_die( esc_html( $stored->get_error_message() ) );
			}
			$url = $stored;
		}
		if ( '' === $url ) {
			$url = $current['download_url'];
		}
		if ( '' === $url && Release::has_stored_apk() ) {
			$url = Release::public_apk_url();
		}

		update_option(
			'tacnav_devices_release',
			array(
				'package_id'   => Release::PACKAGE_ID,
				'version'      => $version,
				'download_url' => $url,
			),
			false
		);
		wp_safe_redirect( admin_url( 'edit.php?post_type=tacnav_map&page=' . self::PAGE . '&release_saved=1' ) );
		exit;
	}

	/**
	 * Current safe APK metadata.
	 *
	 * @return array<string, string>
	 */
	public static function release_metadata() {
		$value = get_option( 'tacnav_devices_release', array() );
		$value = is_array( $value ) ? $value : array();
		$url   = esc_url_raw( isset( $value['download_url'] ) ? (string) $value['download_url'] : '' );
		if ( '' === $url && Release::has_stored_apk() ) {
			$url = Release::public_apk_url();
		}
		return array(
			'package_id'   => Release::PACKAGE_ID,
			'version'      => sanitize_text_field( isset( $value['version'] ) ? (string) $value['version'] : '' ),
			'download_url' => $url,
		);
	}

	/**
	 * Register scripts shared by the handoff response.
	 *
	 * @return void
	 */
	public static function scripts() {
		wp_register_script( 'tacnav-qrcode', TACNAV_MAPS_URL . 'assets/vendor/qrcode.js', array(), TACNAV_MAPS_VERSION, true );
		wp_register_script( 'tacnav-devices-admin', TACNAV_DEVICES_URL . 'assets/admin.js', array( 'tacnav-qrcode' ), TACNAV_DEVICES_VERSION, true );
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
	 * Parse comma-separated identifiers.
	 *
	 * @param string $value Input.
	 * @return array<int, int>
	 */
	private static function csv_ids( $value ) {
		return Validation::ids( preg_split( '/\s*,\s*/', trim( $value ) ) );
	}
}
