<?php
/**
 * Staff settings page contract for one device application.
 *
 * @package TacNav_Devices
 */

namespace TacNav\Devices\Apps;

defined( 'ABSPATH' ) || exit;

/**
 * One device application's tab on the staff page.
 */
interface App_Page {

	/**
	 * Identifier used in the `app` request parameter.
	 *
	 * @return string
	 */
	public function slug();

	/**
	 * Tab label.
	 *
	 * @return string
	 */
	public function label();

	/**
	 * Print the page content inside the staff page wrapper.
	 *
	 * @return void
	 */
	public function render();
}
