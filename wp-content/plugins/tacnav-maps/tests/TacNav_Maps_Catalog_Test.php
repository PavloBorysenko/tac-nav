<?php
/**
 * Icon catalog listing tests.
 *
 * @package TacNav_Maps
 */

use PHPUnit\Framework\TestCase;
use TacNav\Maps\Catalog;
use TacNav\Maps\Post_Types;

require_once __DIR__ . '/bootstrap.php';

if ( ! function_exists( 'get_posts' ) ) {
	/**
	 * Return stored icons, honouring the requested page size.
	 *
	 * @param array<string, mixed> $args Query args.
	 * @return array<int, object>
	 */
	function get_posts( $args ) {
		$posts = $GLOBALS['tacnav_test_icons'];
		$limit = (int) $args['posts_per_page'];
		return $limit < 0 ? $posts : array_slice( $posts, 0, $limit );
	}
}

if ( ! function_exists( 'get_post' ) ) {
	/**
	 * Find a stored icon.
	 *
	 * @param int $post_id Post ID.
	 * @return object|null
	 */
	function get_post( $post_id ) {
		foreach ( $GLOBALS['tacnav_test_icons'] as $post ) {
			if ( $post->ID === $post_id ) {
				return $post;
			}
		}
		return null;
	}
}

if ( ! function_exists( 'get_the_post_thumbnail_url' ) ) {
	/**
	 * Thumbnail stub.
	 *
	 * @param object $post Post.
	 * @return string
	 */
	function get_the_post_thumbnail_url( $post ) {
		return 'https://example.test/icon-' . $post->ID . '.png';
	}
}

if ( ! function_exists( 'get_the_title' ) ) {
	/**
	 * Title stub.
	 *
	 * @param object $post Post.
	 * @return string
	 */
	function get_the_title( $post ) {
		return 'Icon ' . $post->ID;
	}
}

if ( ! function_exists( 'get_post_meta' ) ) {
	/**
	 * Meta stub.
	 *
	 * @return string
	 */
	function get_post_meta() {
		return 'both';
	}
}

/**
 * @covers \TacNav\Maps\Catalog
 */
class TacNav_Maps_Catalog_Test extends TestCase {

	/**
	 * Every published icon is listed, however many the catalog holds.
	 */
	public function test_every_icon_is_listed() {
		$GLOBALS['tacnav_test_icons'] = array();
		for ( $id = 1; $id <= 150; $id++ ) {
			$GLOBALS['tacnav_test_icons'][] = (object) array(
				'ID'        => $id,
				'post_type' => Post_Types::ICON,
			);
		}

		$icons = Catalog::get_icons();

		$this->assertCount( 150, $icons );
		$this->assertSame( 150, $icons[149]['id'] );
	}
}
