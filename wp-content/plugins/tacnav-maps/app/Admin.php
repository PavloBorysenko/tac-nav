<?php
/**
 * Catalog edit screens.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Meta boxes and save handlers.
 */
class Admin {

	/**
	 * Register meta boxes.
	 *
	 * @return void
	 */
	public static function add_meta_boxes() {
		add_meta_box( 'tacnav_map_passport', __( 'Map view', 'tacnav-maps' ), array( __CLASS__, 'render_map_box' ), Post_Types::MAP, 'normal' );
		add_meta_box( 'tacnav_team_setup', __( 'Team setup', 'tacnav-maps' ), array( __CLASS__, 'render_team_box' ), Post_Types::TEAM, 'normal' );
		add_meta_box( 'tacnav_icon_setup', __( 'Icon setup', 'tacnav-maps' ), array( __CLASS__, 'render_icon_box' ), Post_Types::ICON, 'side' );
	}

	/**
	 * Enable featured images without restricting other post types.
	 *
	 * @return void
	 */
	public static function support_thumbnails() {
		add_theme_support( 'post-thumbnails' );
	}

	/**
	 * Leaflet picker and catalog admin styles.
	 *
	 * @param string $hook Current admin page.
	 * @return void
	 */
	public static function enqueue_admin( $hook ) {
		$screen = function_exists( 'get_current_screen' ) ? get_current_screen() : null;
		if ( ! $screen || ! in_array( $screen->post_type, array( Post_Types::MAP, Post_Types::TEAM, Post_Types::ICON ), true ) ) {
			return;
		}

		wp_enqueue_style(
			'tacnav-maps-admin',
			TACNAV_MAPS_URL . 'assets/admin.css',
			array(),
			TACNAV_MAPS_VERSION
		);

		if ( Post_Types::TEAM === $screen->post_type && in_array( $hook, array( 'post.php', 'post-new.php' ), true ) ) {
			wp_enqueue_script(
				'tacnav-maps-admin-roster',
				TACNAV_MAPS_URL . 'assets/admin-team-roster.js',
				array(),
				TACNAV_MAPS_VERSION,
				true
			);
		}

		if ( Post_Types::MAP !== $screen->post_type || ! in_array( $hook, array( 'post.php', 'post-new.php' ), true ) ) {
			return;
		}

		wp_enqueue_style(
			'tacnav-leaflet',
			'https://unpkg.com/leaflet@1.9.4/dist/leaflet.css',
			array(),
			'1.9.4'
		);
		wp_enqueue_script(
			'tacnav-leaflet',
			'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js',
			array(),
			'1.9.4',
			true
		);
		wp_enqueue_script(
			'tacnav-maps-admin-picker',
			TACNAV_MAPS_URL . 'assets/admin-map-picker.js',
			array( 'tacnav-leaflet' ),
			TACNAV_MAPS_VERSION,
			true
		);
	}

	/**
	 * Send organizers to the Maps list instead of an empty dashboard.
	 *
	 * @return void
	 */
	public static function redirect_organizer_home() {
		if ( ! current_user_can( Roles::CAP_CATALOG ) || current_user_can( 'manage_options' ) ) {
			return;
		}

		wp_safe_redirect( admin_url( 'edit.php?post_type=' . Post_Types::MAP ) );
		exit;
	}

	/**
	 * Classic editor for catalog types.
	 *
	 * @param bool   $use_block_editor Whether the block editor is used.
	 * @param string $type             Post type.
	 * @return bool
	 */
	public static function disable_block_editor( $use_block_editor, $type ) {
		if ( in_array( $type, array( Post_Types::MAP, Post_Types::TEAM, Post_Types::ICON ), true ) ) {
			return false;
		}

		return $use_block_editor;
	}

	/**
	 * Admin bar shortcut to add a map.
	 *
	 * @param \WP_Admin_Bar $wp_admin_bar Toolbar.
	 * @return void
	 */
	public static function admin_bar( $wp_admin_bar ) {
		if ( ! current_user_can( Roles::CAP_CATALOG ) ) {
			return;
		}

		$wp_admin_bar->add_node(
			array(
				'id'    => 'tacnav-maps',
				'title' => __( 'Maps', 'tacnav-maps' ),
				'href'  => admin_url( 'edit.php?post_type=' . Post_Types::MAP ),
			)
		);
		$wp_admin_bar->add_node(
			array(
				'id'     => 'tacnav-new-map',
				'parent' => 'tacnav-maps',
				'title'  => __( 'Add Map', 'tacnav-maps' ),
				'href'   => admin_url( 'post-new.php?post_type=' . Post_Types::MAP ),
			)
		);
		$wp_admin_bar->add_node(
			array(
				'id'     => 'tacnav-new-team',
				'parent' => 'tacnav-maps',
				'title'  => __( 'Add Team', 'tacnav-maps' ),
				'href'   => admin_url( 'post-new.php?post_type=' . Post_Types::TEAM ),
			)
		);
	}

	/**
	 * Map passport fields.
	 *
	 * @param \WP_Post $post Post.
	 * @return void
	 */
	public static function render_map_box( $post ) {
		wp_nonce_field( 'tacnav_map_meta', 'tacnav_map_nonce' );
		$lat      = get_post_meta( $post->ID, 'tacnav_center_lat', true );
		$lng      = get_post_meta( $post->ID, 'tacnav_center_lng', true );
		$zoom     = get_post_meta( $post->ID, 'tacnav_zoom', true );
		$basemap  = Catalog::sanitize_basemap( (string) get_post_meta( $post->ID, 'tacnav_basemap', true ) );
		$canvas   = Canvas::url( (int) $post->ID );
		$team_ids = Catalog::team_ids( (int) $post->ID );
		$teams    = Catalog::get_teams();
		include TACNAV_MAPS_DIR . 'templates/map-meta-box.php';
	}

	/**
	 * Team palette and presets.
	 *
	 * @param \WP_Post $post Post.
	 * @return void
	 */
	public static function render_team_box( $post ) {
		wp_nonce_field( 'tacnav_team_meta', 'tacnav_team_nonce' );
		$color    = Catalog::sanitize_color( (string) get_post_meta( $post->ID, 'tacnav_color', true ) );
		$palette  = get_post_meta( $post->ID, 'tacnav_palette_icon_ids', true );
		$presets  = get_post_meta( $post->ID, 'tacnav_quick_presets', true );
		$palette  = is_array( $palette ) ? array_map( 'intval', $palette ) : array();
		$presets  = is_array( $presets ) ? $presets : array();
		$icons    = Catalog::get_icons();
		$kinds    = Geo_Store::KINDS;
		$selected = Membership::player_ids_for_team( (int) $post->ID );
		$users    = get_users(
			array(
				'role'    => Roles::ROLE_PLAYER,
				'orderby' => 'display_name',
				'order'   => 'ASC',
				'number'  => 500,
			)
		);
		include TACNAV_MAPS_DIR . 'templates/team-meta-box.php';
	}

	/**
	 * Icon availability.
	 *
	 * @param \WP_Post $post Post.
	 * @return void
	 */
	public static function render_icon_box( $post ) {
		wp_nonce_field( 'tacnav_icon_meta', 'tacnav_icon_nonce' );
		$value = Catalog::sanitize_availability( (string) get_post_meta( $post->ID, 'tacnav_availability', true ) );
		include TACNAV_MAPS_DIR . 'templates/icon-meta-box.php';
	}

	/**
	 * Save catalog meta.
	 *
	 * @param int $post_id Post ID.
	 * @return void
	 */
	public static function save_post( $post_id ) {
		if ( defined( 'DOING_AUTOSAVE' ) && DOING_AUTOSAVE ) {
			return;
		}
		if ( ! current_user_can( Roles::CAP_CATALOG ) ) {
			return;
		}

		$post = get_post( $post_id );
		if ( ! $post ) {
			return;
		}

		if ( Post_Types::MAP === $post->post_type ) {
			self::save_map( $post_id );
		} elseif ( Post_Types::TEAM === $post->post_type ) {
			self::save_team( $post_id );
		} elseif ( Post_Types::ICON === $post->post_type ) {
			self::save_icon( $post_id );
		}
	}

	/**
	 * Row action to open the canvas.
	 *
	 * @param array<string, string> $actions Actions.
	 * @param \WP_Post               $post    Post.
	 * @return array<string, string>
	 */
	public static function row_actions( $actions, $post ) {
		if ( Post_Types::MAP !== $post->post_type || ! current_user_can( Roles::CAP_CANVAS ) ) {
			return $actions;
		}

		$actions['tacnav_canvas'] = '<a href="' . esc_url( Canvas::url( (int) $post->ID ) ) . '">' . esc_html__( 'Canvas', 'tacnav-maps' ) . '</a>';
		return $actions;
	}

	/**
	 * Save map meta.
	 *
	 * @param int $post_id Post ID.
	 * @return void
	 */
	private static function save_map( $post_id ) {
		if ( ! isset( $_POST['tacnav_map_nonce'] ) || ! wp_verify_nonce( sanitize_text_field( wp_unslash( $_POST['tacnav_map_nonce'] ) ), 'tacnav_map_meta' ) ) {
			return;
		}

		$lat  = isset( $_POST['tacnav_center_lat'] ) ? (float) wp_unslash( $_POST['tacnav_center_lat'] ) : 0;
		$lng  = isset( $_POST['tacnav_center_lng'] ) ? (float) wp_unslash( $_POST['tacnav_center_lng'] ) : 0;
		$zoom = isset( $_POST['tacnav_zoom'] ) ? (int) wp_unslash( $_POST['tacnav_zoom'] ) : 13;
		$base = isset( $_POST['tacnav_basemap'] ) ? Catalog::sanitize_basemap( sanitize_text_field( wp_unslash( $_POST['tacnav_basemap'] ) ) ) : 'satellite';

		update_post_meta( $post_id, 'tacnav_center_lat', $lat );
		update_post_meta( $post_id, 'tacnav_center_lng', $lng );
		update_post_meta( $post_id, 'tacnav_zoom', max( 1, min( 22, $zoom ) ) );
		update_post_meta( $post_id, 'tacnav_basemap', $base );

		$teams = array();
		if ( isset( $_POST['tacnav_team_ids'] ) && is_array( $_POST['tacnav_team_ids'] ) ) {
			$teams = array_values( array_unique( array_filter( array_map( 'intval', wp_unslash( $_POST['tacnav_team_ids'] ) ) ) ) );
		}
		update_post_meta( $post_id, 'tacnav_team_ids', $teams );
	}

	/**
	 * Save team meta.
	 *
	 * @param int $post_id Post ID.
	 * @return void
	 */
	private static function save_team( $post_id ) {
		if ( ! isset( $_POST['tacnav_team_nonce'] ) || ! wp_verify_nonce( sanitize_text_field( wp_unslash( $_POST['tacnav_team_nonce'] ) ), 'tacnav_team_meta' ) ) {
			return;
		}

		$color = isset( $_POST['tacnav_color'] ) ? Catalog::sanitize_color( sanitize_text_field( wp_unslash( $_POST['tacnav_color'] ) ) ) : '#6b7280';
		update_post_meta( $post_id, 'tacnav_color', $color );

		$palette = array();
		if ( isset( $_POST['tacnav_palette_icon_ids'] ) && is_array( $_POST['tacnav_palette_icon_ids'] ) ) {
			$palette = array_values( array_unique( array_map( 'intval', wp_unslash( $_POST['tacnav_palette_icon_ids'] ) ) ) );
		}
		update_post_meta( $post_id, 'tacnav_palette_icon_ids', $palette );

		$presets = array();
		if ( isset( $_POST['tacnav_presets'] ) && is_array( $_POST['tacnav_presets'] ) ) {
			foreach ( wp_unslash( $_POST['tacnav_presets'] ) as $row ) {
				if ( ! is_array( $row ) || empty( $row['kind'] ) ) {
					continue;
				}
				$kind = sanitize_key( (string) $row['kind'] );
				if ( ! in_array( $kind, Geo_Store::KINDS, true ) ) {
					continue;
				}
				$presets[] = array(
					'kind'        => $kind,
					'icon_id'     => isset( $row['icon_id'] ) ? (int) $row['icon_id'] : 0,
					'title'       => isset( $row['title'] ) ? sanitize_text_field( (string) $row['title'] ) : '',
					'description' => isset( $row['description'] ) ? sanitize_text_field( (string) $row['description'] ) : '',
					'ttl_minutes' => isset( $row['ttl_minutes'] ) && '' !== $row['ttl_minutes'] ? max( 0, (int) $row['ttl_minutes'] ) : 3,
				);
			}
		}
		update_post_meta( $post_id, 'tacnav_quick_presets', $presets );

		$players = array();
		if ( isset( $_POST['tacnav_player_ids'] ) && is_array( $_POST['tacnav_player_ids'] ) ) {
			$players = array_map( 'intval', wp_unslash( $_POST['tacnav_player_ids'] ) );
		}
		Membership::save_roster( $post_id, $players );
	}

	/**
	 * Save icon meta.
	 *
	 * @param int $post_id Post ID.
	 * @return void
	 */
	private static function save_icon( $post_id ) {
		if ( ! isset( $_POST['tacnav_icon_nonce'] ) || ! wp_verify_nonce( sanitize_text_field( wp_unslash( $_POST['tacnav_icon_nonce'] ) ), 'tacnav_icon_meta' ) ) {
			return;
		}

		$value = isset( $_POST['tacnav_availability'] ) ? Catalog::sanitize_availability( sanitize_text_field( wp_unslash( $_POST['tacnav_availability'] ) ) ) : 'both';
		update_post_meta( $post_id, 'tacnav_availability', $value );
	}
}
