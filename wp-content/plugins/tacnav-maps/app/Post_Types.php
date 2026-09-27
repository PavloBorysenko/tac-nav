<?php
/**
 * Catalog post types.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Registers maps, teams, and icons.
 */
class Post_Types {

	const MAP  = 'tacnav_map';
	const TEAM = 'tacnav_team';
	const ICON = 'tacnav_icon';

	/**
	 * Register CPTs.
	 *
	 * @return void
	 */
	public static function register() {
		$caps = self::catalog_caps();

		register_post_type(
			self::MAP,
			array(
				'labels'              => array(
					'name'          => __( 'Maps', 'tacnav-maps' ),
					'singular_name' => __( 'Map', 'tacnav-maps' ),
					'add_new_item'  => __( 'Add Map', 'tacnav-maps' ),
					'edit_item'     => __( 'Edit Map', 'tacnav-maps' ),
				),
				'public'              => false,
				'show_ui'             => true,
				'show_in_menu'        => true,
				'menu_icon'           => 'dashicons-location-alt',
				'menu_position'       => 26,
				'show_in_admin_bar'   => true,
				'supports'            => array( 'title', 'editor' ),
				'capability_type'     => 'post',
				'capabilities'        => $caps,
				'map_meta_cap'        => false,
				'exclude_from_search' => true,
			)
		);

		register_post_type(
			self::TEAM,
			array(
				'labels'              => array(
					'name'          => __( 'Teams', 'tacnav-maps' ),
					'singular_name' => __( 'Team', 'tacnav-maps' ),
					'add_new_item'  => __( 'Add Team', 'tacnav-maps' ),
					'edit_item'     => __( 'Edit Team', 'tacnav-maps' ),
				),
				'public'              => false,
				'show_ui'             => true,
				'show_in_menu'        => 'edit.php?post_type=' . self::MAP,
				'supports'            => array( 'title', 'editor', 'thumbnail' ),
				'capability_type'     => 'post',
				'capabilities'        => $caps,
				'map_meta_cap'        => false,
				'exclude_from_search' => true,
			)
		);

		register_post_type(
			self::ICON,
			array(
				'labels'              => array(
					'name'          => __( 'Icons', 'tacnav-maps' ),
					'singular_name' => __( 'Icon', 'tacnav-maps' ),
					'add_new_item'  => __( 'Add Icon', 'tacnav-maps' ),
					'edit_item'     => __( 'Edit Icon', 'tacnav-maps' ),
				),
				'public'              => false,
				'show_ui'             => true,
				'show_in_menu'        => 'edit.php?post_type=' . self::MAP,
				'supports'            => array( 'title', 'thumbnail' ),
				'capability_type'     => 'post',
				'capabilities'        => $caps,
				'map_meta_cap'        => false,
				'exclude_from_search' => true,
			)
		);
	}

	/**
	 * Map all catalog CPT caps to the staff catalog capability.
	 *
	 * @return array<string, string>
	 */
	private static function catalog_caps() {
		$cap = Roles::CAP_CATALOG;

		return array(
			'edit_post'              => $cap,
			'read_post'              => $cap,
			'delete_post'            => $cap,
			'edit_posts'             => $cap,
			'edit_others_posts'      => $cap,
			'delete_posts'           => $cap,
			'publish_posts'          => $cap,
			'read_private_posts'     => $cap,
			'create_posts'           => $cap,
			'delete_others_posts'    => $cap,
			'delete_private_posts'   => $cap,
			'delete_published_posts' => $cap,
			'edit_private_posts'     => $cap,
			'edit_published_posts'   => $cap,
		);
	}
}
