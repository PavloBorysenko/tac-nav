<?php
/**
 * Delete cascades.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Removes geo objects when catalog items are deleted.
 */
class Cascades {

	/**
	 * Hook before a catalog post is deleted.
	 *
	 * @param int     $post_id Post ID.
	 * @param \WP_Post $post Post.
	 * @return void
	 */
	public static function before_delete_post( $post_id, $post ) {
		$store = new Geo_Store();

		if ( Post_Types::MAP === $post->post_type ) {
			$store->delete_for_map( (int) $post_id );
			return;
		}

		if ( Post_Types::TEAM === $post->post_type ) {
			$store->delete_for_team( (int) $post_id );
			Membership::clear_team( (int) $post_id );
			return;
		}

		if ( Post_Types::ICON === $post->post_type ) {
			$store->clear_icon( (int) $post_id );
		}
	}
}
