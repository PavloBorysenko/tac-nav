<?php
/**
 * Tac Nav Object staff page.
 *
 * @package TacNav_Devices
 */

namespace TacNav\Devices\Apps;

use TacNav\Devices\Admin;
use TacNav\Devices\App_Types;
use TacNav\Devices\Application_Store;
use TacNav\Devices\Validation;

defined( 'ABSPATH' ) || exit;

/**
 * Object application definitions, activation, and release controls.
 */
class Object_Page implements App_Page {

	/**
	 * Identifier used in the `app` request parameter.
	 *
	 * @return string
	 */
	public function slug() {
		return App_Types::OBJECT;
	}

	/**
	 * Tab label.
	 *
	 * @return string
	 */
	public function label() {
		return __( 'Object', 'tacnav-devices' );
	}

	/**
	 * Print the Object page.
	 *
	 * @return void
	 */
	public function render() {
		$store        = new Application_Store();
		$applications = $store->all( App_Types::OBJECT );
		$action       = admin_url( 'admin-post.php' );
		$release      = Admin::release_metadata( App_Types::OBJECT );
		$tacnav_maps  = \TacNav\Maps\Catalog::get_maps();
		$tacnav_teams = \TacNav\Maps\Catalog::get_teams();
		$tacnav_icons = \TacNav\Maps\Catalog::get_icons();
		// phpcs:disable WordPress.Security.NonceVerification.Recommended -- staff list filter after capability check.
		$edit_id = isset( $_GET['edit'] ) ? (int) wp_unslash( $_GET['edit'] ) : 0;
		// phpcs:enable WordPress.Security.NonceVerification.Recommended
		$tacnav_draft = $edit_id > 0 ? $store->get( $edit_id ) : null;
		if ( $tacnav_draft && App_Types::OBJECT !== App_Types::canonical( (string) $tacnav_draft['application_type'] ) ) {
			$tacnav_draft = null;
		}
		$tacnav_reference = is_array( $tacnav_draft ) && is_array( $tacnav_draft['reference_settings'] )
			? array_merge( Validation::object_reference_defaults(), $tacnav_draft['reference_settings'] )
			: Validation::object_reference_defaults();
		require TACNAV_DEVICES_DIR . 'templates/apps/object.php';
	}
}
