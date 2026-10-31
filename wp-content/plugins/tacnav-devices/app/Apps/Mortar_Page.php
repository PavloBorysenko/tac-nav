<?php
/**
 * Tac Nav Mortar staff page.
 *
 * @package TacNav_Devices
 */

namespace TacNav\Devices\Apps;

use TacNav\Devices\Admin;
use TacNav\Devices\Application_Store;
use TacNav\Devices\Validation;

defined( 'ABSPATH' ) || exit;

/**
 * Mortar application definitions, activation, and release controls.
 */
class Mortar_Page implements App_Page {

	/**
	 * Identifier used in the `app` request parameter.
	 *
	 * @return string
	 */
	public function slug() {
		return 'mortar';
	}

	/**
	 * Tab label.
	 *
	 * @return string
	 */
	public function label() {
		return __( 'Mortar', 'tacnav-devices' );
	}

	/**
	 * Print the Mortar page.
	 *
	 * @return void
	 */
	public function render() {
		$applications = ( new Application_Store() )->all();
		$action       = admin_url( 'admin-post.php' );
		$release      = Admin::release_metadata();
		$tacnav_maps  = \TacNav\Maps\Catalog::get_maps();
		$tacnav_teams = \TacNav\Maps\Catalog::get_teams();
		$tacnav_icons = \TacNav\Maps\Catalog::get_icons();
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
		require TACNAV_DEVICES_DIR . 'templates/apps/mortar.php';
	}
}
