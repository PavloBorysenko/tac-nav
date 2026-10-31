<?php
/**
 * Device application pages available to staff.
 *
 * @package TacNav_Devices
 */

namespace TacNav\Devices\Apps;

defined( 'ABSPATH' ) || exit;

/**
 * Lists application pages and selects the requested tab.
 */
class App_Pages {

	/**
	 * Application pages in tab order. The first page is the default tab.
	 *
	 * @return array<int, App_Page>
	 */
	public static function all() {
		return array(
			new Mortar_Page(),
		);
	}

	/**
	 * Select the requested page, or the first page when none matches.
	 *
	 * @param array<int, App_Page> $pages     Application pages.
	 * @param string               $requested Requested slug.
	 * @return App_Page
	 */
	public static function resolve( $pages, $requested ) {
		foreach ( $pages as $page ) {
			if ( $page->slug() === $requested ) {
				return $page;
			}
		}
		return $pages[0];
	}
}
