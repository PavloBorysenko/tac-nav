<?php
/**
 * Device application tab selection.
 *
 * @package TacNav_Devices
 */

use PHPUnit\Framework\TestCase;
use TacNav\Devices\Admin;
use TacNav\Devices\Apps\App_Page;
use TacNav\Devices\Apps\App_Pages;

require_once __DIR__ . '/bootstrap.php';

/**
 * Application page test double.
 */
class TacNav_Devices_App_Page_Double implements App_Page {

	/**
	 * Page slug.
	 *
	 * @var string
	 */
	private $slug;

	/**
	 * Create a page with a slug.
	 *
	 * @param string $slug Page slug.
	 */
	public function __construct( $slug ) {
		$this->slug = $slug;
	}

	/**
	 * Identifier used in the `app` request parameter.
	 *
	 * @return string
	 */
	public function slug() {
		return $this->slug;
	}

	/**
	 * Tab label.
	 *
	 * @return string
	 */
	public function label() {
		return $this->slug;
	}

	/**
	 * Print nothing.
	 *
	 * @return void
	 */
	public function render() {
	}
}

/**
 * @covers \TacNav\Devices\Apps\App_Pages
 */
class TacNav_Devices_App_Pages_Test extends TestCase {

	/**
	 * Mortar is the default tab.
	 */
	public function test_mortar_is_the_first_page() {
		$this->assertSame( 'mortar', App_Pages::all()[0]->slug() );
	}

	/**
	 * Object is a tab after Mortar and resolves from its slug.
	 */
	public function test_object_is_a_tab_after_mortar() {
		$pages = App_Pages::all();

		$this->assertCount( 2, $pages );
		$this->assertSame( 'object', $pages[1]->slug() );
		$this->assertSame( 'Object', $pages[1]->label() );
		$this->assertSame( $pages[1], App_Pages::resolve( $pages, 'object' ) );
		$this->assertSame( $pages[0], App_Pages::resolve( $pages, '' ) );
	}

	/**
	 * A user without the staff permission gets no tab or page content.
	 */
	public function test_non_staff_user_cannot_open_the_page() {
		$GLOBALS['tacnav_test_is_staff'] = false;
		$_GET['app']                     = 'object';

		try {
			Admin::render();
			$this->fail( 'Non-staff users must be refused.' );
		} catch ( RuntimeException $error ) {
			$this->assertSame( 'You are not allowed to manage device applications.', $error->getMessage() );
		} finally {
			unset( $_GET['app'] );
		}
	}

	/**
	 * A known application opens its own page.
	 */
	public function test_known_slug_selects_its_page() {
		$pages = $this->pages();

		$this->assertSame( $pages[1], App_Pages::resolve( $pages, 'sapper' ) );
	}

	/**
	 * Existing links without a tab land on the default page.
	 */
	public function test_missing_slug_selects_the_first_page() {
		$pages = $this->pages();

		$this->assertSame( $pages[0], App_Pages::resolve( $pages, '' ) );
	}

	/**
	 * An unknown value cannot select anything but an available page.
	 */
	public function test_unknown_slug_selects_the_first_page() {
		$pages = $this->pages();

		$this->assertSame( $pages[0], App_Pages::resolve( $pages, 'unknown' ) );
	}

	/**
	 * Two application pages.
	 *
	 * @return array<int, App_Page>
	 */
	private function pages() {
		return array(
			new TacNav_Devices_App_Page_Double( 'mortar' ),
			new TacNav_Devices_App_Page_Double( 'sapper' ),
		);
	}
}
