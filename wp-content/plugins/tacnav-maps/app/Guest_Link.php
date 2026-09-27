<?php
/**
 * Staff page that reveals a guest map link.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Guest-link handoff page.
 */
class Guest_Link {

	/**
	 * Register the route.
	 *
	 * @return void
	 */
	public static function register_rewrite() {
		add_rewrite_rule( '^tacnav-guest-link/([0-9]+)/([0-9]+)/?$', 'index.php?tacnav_guest_map=$matches[1]&tacnav_guest_team=$matches[2]', 'top' );
	}

	/**
	 * Query vars.
	 *
	 * @param array<int, string> $vars Vars.
	 * @return array<int, string>
	 */
	public static function query_vars( $vars ) {
		$vars[] = 'tacnav_guest_map';
		$vars[] = 'tacnav_guest_team';
		return $vars;
	}

	/**
	 * Staff page URL.
	 *
	 * @param int $map_id  Map ID.
	 * @param int $team_id Team ID.
	 * @return string
	 */
	public static function page_url( $map_id, $team_id ) {
		return home_url( '/tacnav-guest-link/' . (int) $map_id . '/' . (int) $team_id . '/' );
	}

	/**
	 * Canvas URL with a guest token.
	 *
	 * @param int $map_id  Map ID.
	 * @param int $team_id Team ID.
	 * @return string
	 */
	public static function canvas_url( $map_id, $team_id ) {
		$token = Guest_Token::token( (int) $map_id, (int) $team_id, wp_salt( 'auth' ) );
		return add_query_arg( 't', $token, Canvas::url( (int) $map_id ) );
	}

	/**
	 * Render or refuse.
	 *
	 * @return void
	 */
	public static function template_redirect() {
		$map_id = (int) get_query_var( 'tacnav_guest_map' );
		if ( $map_id <= 0 ) {
			return;
		}

		if ( ! is_user_logged_in() || ! current_user_can( Roles::CAP_CANVAS ) ) {
			wp_die( esc_html__( 'You cannot open this guest link.', 'tacnav-maps' ), esc_html__( 'Forbidden', 'tacnav-maps' ), array( 'response' => 403 ) );
		}

		$team_id = (int) get_query_var( 'tacnav_guest_team' );
		$map     = Catalog::get_map( $map_id );
		$team    = Catalog::get_team( $team_id );
		if ( ! $map || ! $team || 'publish' !== $map['status'] || ! in_array( $team_id, $map['team_ids'], true ) ) {
			wp_die( esc_html__( 'This guest link is not available.', 'tacnav-maps' ), '', array( 'response' => 404 ) );
		}

		$url = self::canvas_url( $map_id, $team_id );
		/*
		 * Block themes print the import map in wp_head, and only for modules
		 * already discovered. Render the template parts first so header and
		 * footer blocks enqueue their styles and script modules in time.
		 */
		ob_start();
		block_header_area();
		$header_html = ob_get_clean();
		ob_start();
		block_footer_area();
		$footer_html = ob_get_clean();
		$header_html = is_string( $header_html ) ? $header_html : '';
		$footer_html = is_string( $footer_html ) ? $footer_html : '';
		wp_enqueue_style(
			'tacnav-guest-link',
			TACNAV_MAPS_URL . 'assets/guest-link.css',
			array(),
			TACNAV_MAPS_VERSION
		);
		wp_enqueue_script(
			'tacnav-qrcode',
			TACNAV_MAPS_URL . 'assets/vendor/qrcode.js',
			array(),
			TACNAV_MAPS_VERSION,
			true
		);
		wp_add_inline_script(
			'tacnav-qrcode',
			"(function(){var input=document.getElementById('tacnav-guest-url');var copy=document.getElementById('tacnav-guest-copy');if(!input||!copy){return;}copy.addEventListener('click',function(){input.select();if(navigator.clipboard){navigator.clipboard.writeText(input.value);}});if(window.qrcode){var qr=window.qrcode(0,'M');qr.addData(input.value);qr.make();document.getElementById('tacnav-qr').innerHTML=qr.createSvgTag(4,0);}})();"
		);
		status_header( 200 );
		nocache_headers();
		include TACNAV_MAPS_DIR . 'templates/guest-link.php';
		exit;
	}
}
