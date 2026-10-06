<?php
/**
 * Front-end map canvas.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Capability-gated studio page.
 */
class Canvas {

	/**
	 * Register rewrite and query var.
	 *
	 * @return void
	 */
	public static function register_rewrite() {
		add_rewrite_rule( '^tacnav-canvas/([0-9]+)/?$', 'index.php?tacnav_canvas=$matches[1]', 'top' );
	}

	/**
	 * Query vars.
	 *
	 * @param array<int, string> $vars Vars.
	 * @return array<int, string>
	 */
	public static function query_vars( $vars ) {
		$vars[] = 'tacnav_canvas';
		return $vars;
	}

	/**
	 * Stop Chrome from logging skipped same-document view transitions.
	 *
	 * @return void
	 */
	public static function disable_view_transitions() {
		wp_print_inline_script_tag(
			'(function () {'
			. 'function swallow(vt) { if (vt && vt.finished) { vt.finished.catch(function () {}); } }'
			. 'if (document.startViewTransition) {'
			. 'var orig = document.startViewTransition.bind(document);'
			. 'document.startViewTransition = function (cb) { var vt = orig(cb); swallow(vt); return vt; };'
			. '}'
			. 'window.addEventListener("pagereveal", function (e) { swallow(e.viewTransition); });'
			. 'window.addEventListener("pageswap", function (e) { swallow(e.viewTransition); });'
			. '}());'
		);
	}

	/**
	 * Canvas URL.
	 *
	 * @param int $map_id Map ID.
	 * @return string
	 */
	public static function url( $map_id ) {
		return home_url( '/tacnav-canvas/' . (int) $map_id . '/' );
	}

	/**
	 * Render or refuse.
	 *
	 * @return void
	 */
	public static function template_redirect() {
		$map_id = (int) get_query_var( 'tacnav_canvas' );
		if ( $map_id <= 0 ) {
			return;
		}

		$map = Catalog::get_map( $map_id );
		if ( ! $map ) {
			wp_die( esc_html__( 'Map not found.', 'tacnav-maps' ), '', array( 'response' => 404 ) );
		}

		$token    = isset( $_GET['t'] ) ? sanitize_text_field( wp_unslash( $_GET['t'] ) ) : ''; // phpcs:ignore WordPress.Security.NonceVerification.Recommended -- Public map token, not a submitted form.
		$resolved = Viewer::resolve( $map, $token );
		if ( '' === $resolved['kind'] ) {
			wp_die( esc_html__( 'You cannot open this map canvas.', 'tacnav-maps' ), esc_html__( 'Forbidden', 'tacnav-maps' ), array( 'response' => 403 ) );
		}

		status_header( 200 );
		nocache_headers();
		self::enqueue( $map, $resolved );
		self::render( $map, $resolved );
		exit;
	}

	/**
	 * Enqueue canvas assets.
	 *
	 * @param array<string, mixed> $map      Map passport.
	 * @param array<string, mixed> $resolved Viewer kind.
	 * @return void
	 */
	private static function enqueue( $map, $resolved ) {
		add_action( 'wp_head', array( self::class, 'disable_view_transitions' ), 0 );

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
		wp_enqueue_style(
			'tacnav-maps-canvas',
			TACNAV_MAPS_URL . 'assets/canvas.css',
			array( 'tacnav-leaflet' ),
			TACNAV_MAPS_VERSION
		);
		wp_enqueue_script(
			'tacnav-maps-canvas',
			TACNAV_MAPS_URL . 'assets/canvas.js',
			array( 'tacnav-leaflet' ),
			TACNAV_MAPS_VERSION,
			true
		);

		$objects = REST::visible_objects( (int) $map['id'], false, $resolved );
		$teams   = Catalog::teams_on_map( $map );
		$icons   = Catalog::get_icons();
		$presets = array();
		if ( 'player' === $resolved['kind'] ) {
			$team = Catalog::get_team( (int) $resolved['team_id'] );
			if ( $team ) {
				$presets = $team['quick_presets'];
				$allowed = $team['palette_icon_ids'];
				$icons   = array_values(
					array_filter(
						$icons,
						static function ( $icon ) use ( $allowed ) {
							return in_array( (int) $icon['id'], $allowed, true );
						}
					)
				);
			}
		}

		if ( $map['zoom'] <= 0 ) {
			$map['zoom'] = 13;
		}

		wp_localize_script(
			'tacnav-maps-canvas',
			'tacnavMapsCanvas',
			array(
				'restUrl'    => esc_url_raw( rest_url( REST::NS . '/maps/' . (int) $map['id'] ) ),
				'nonce'      => wp_create_nonce( 'wp_rest' ),
				'mode'       => $resolved['kind'],
				'map'        => $map,
				'teams'      => $teams,
				'icons'      => $icons,
				'presets'    => $presets,
				'objects'    => $objects,
				'pictureRev' => (int) get_post_meta( (int) $map['id'], Picture_Version::META, true ),
				'canPurge'   => 'staff' === $resolved['kind'] && current_user_can( Roles::CAP_PURGE ),
				'kinds'      => Geo_Store::KINDS,
				'strings'    => array(
					'add'           => __( 'Add', 'tacnav-maps' ),
					'marker'        => __( 'Point', 'tacnav-maps' ),
					'polyline'      => __( 'Line', 'tacnav-maps' ),
					'polygon'       => __( 'Polygon', 'tacnav-maps' ),
					'circle'        => __( 'Circle', 'tacnav-maps' ),
					'measure'       => __( 'Measure', 'tacnav-maps' ),
					'coords'        => __( 'Coordinates', 'tacnav-maps' ),
					'done'          => __( 'Done', 'tacnav-maps' ),
					'cancel'        => __( 'Cancel', 'tacnav-maps' ),
					'stop'          => __( 'Stop', 'tacnav-maps' ),
					'clear'         => __( 'Clear', 'tacnav-maps' ),
					'save'          => __( 'Save', 'tacnav-maps' ),
					'delete'        => __( 'Delete', 'tacnav-maps' ),
					'edit'          => __( 'Edit', 'tacnav-maps' ),
					'title'         => __( 'Title', 'tacnav-maps' ),
					'description'   => __( 'Description', 'tacnav-maps' ),
					'belonging'     => __( 'Belonging', 'tacnav-maps' ),
					'visibility'    => __( 'Visibility', 'tacnav-maps' ),
					'neutral'       => __( 'Neutral / all', 'tacnav-maps' ),
					'ttl'           => __( 'Time to live', 'tacnav-maps' ),
					'icon'          => __( 'Icon', 'tacnav-maps' ),
					'expired'       => __( 'Show expired', 'tacnav-maps' ),
					'labels'        => __( 'Show titles', 'tacnav-maps' ),
					'mark'          => __( 'Mark myself', 'tacnav-maps' ),
					'purge'         => __( 'Delete expired', 'tacnav-maps' ),
					'layers'        => __( 'Layers', 'tacnav-maps' ),
					'satellite'     => __( 'Satellite', 'tacnav-maps' ),
					'filters'       => __( 'Filters', 'tacnav-maps' ),
					'none'          => __( 'None', 'tacnav-maps' ),
					'forever'       => __( 'Does not expire', 'tacnav-maps' ),
					'keep'          => __( 'Don\'t change', 'tacnav-maps' ),
					'objectId'      => __( 'ID', 'tacnav-maps' ),
					'saving'        => __( 'Saving...', 'tacnav-maps' ),
					'remaining'     => __( 'Visible for', 'tacnav-maps' ),
					'home'          => __( 'Center', 'tacnav-maps' ),
					'locate'        => __( 'Me', 'tacnav-maps' ),
					'live'          => __( 'Live', 'tacnav-maps' ),
					'conflict'      => __( 'Someone else saved this object. Close the form to see their version.', 'tacnav-maps' ),
					'labelsOverlay' => __( 'Place names', 'tacnav-maps' ),
					'tapRadius'     => __( 'Tap center, move to set radius, tap again to lock, then Done', 'tacnav-maps' ),
					'adjust'        => __( 'Drag points to edit. Line and polygon: tap to continue.', 'tacnav-maps' ),
					'locateFail'    => __( 'Could not read your location.', 'tacnav-maps' ),
					'removeVertex'  => __( 'Remove point', 'tacnav-maps' ),
					'close'         => __( 'Close', 'tacnav-maps' ),
				),
			)
		);
	}

	/**
	 * Print the canvas document.
	 *
	 * @param array<string, mixed> $map      Map.
	 * @param array<string, mixed> $resolved Viewer kind.
	 * @return void
	 */
	private static function render( $map, $resolved ) {
		$mode       = (string) $resolved['kind'];
		$logo_id    = (int) get_theme_mod( 'custom_logo' );
		$logo_src   = $logo_id ? wp_get_attachment_image_url( $logo_id, 'full' ) : false;
		$logo_url   = is_string( $logo_src ) ? $logo_src : '';
		$site_name  = (string) get_bloginfo( 'name' );
		$site_front = home_url( '/' );
		include TACNAV_MAPS_DIR . 'templates/canvas.php';
	}
}
