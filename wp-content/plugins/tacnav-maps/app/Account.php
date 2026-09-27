<?php
/**
 * Player sign-in, registration, and cabinet.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Front-end account page.
 */
class Account {

	/**
	 * Register the cabinet route.
	 *
	 * @return void
	 */
	public static function register_rewrite() {
		add_rewrite_rule( '^tacnav-player/?$', 'index.php?tacnav_player=1', 'top' );
	}

	/**
	 * Query vars.
	 *
	 * @param array<int, string> $vars Vars.
	 * @return array<int, string>
	 */
	public static function query_vars( $vars ) {
		$vars[] = 'tacnav_player';
		return $vars;
	}

	/**
	 * Cabinet URL.
	 *
	 * @return string
	 */
	public static function url() {
		return home_url( '/tacnav-player/' );
	}

	/**
	 * Render the account page.
	 *
	 * @return void
	 */
	public static function template_redirect() {
		if ( ! get_query_var( 'tacnav_player' ) ) {
			return;
		}

		$notice = self::notice_from_query();
		$error  = '';
		if ( 'POST' === ( isset( $_SERVER['REQUEST_METHOD'] ) ? sanitize_text_field( wp_unslash( $_SERVER['REQUEST_METHOD'] ) ) : '' ) ) {
			$result = self::handle_post();
			if ( is_wp_error( $result ) ) {
				$error = $result->get_error_message();
			} else {
				$notice = (string) $result;
			}
		}

		status_header( 200 );
		nocache_headers();
		$user    = wp_get_current_user();
		$player  = $user->exists() && Membership::is_player( (int) $user->ID );
		$team_id = $player ? Membership::team_id( (int) $user->ID ) : 0;
		$team    = $team_id ? Catalog::get_team( $team_id ) : null;
		$mates   = array();
		$maps    = array();
		if ( $team ) {
			foreach ( Membership::player_ids_for_team( $team_id ) as $mate_id ) {
				if ( $mate_id === (int) $user->ID ) {
					continue;
				}
				$mate = get_userdata( $mate_id );
				if ( $mate ) {
					$mates[] = array(
						'name'   => $mate->display_name,
						'avatar' => Membership::avatar_url( $mate_id ),
					);
				}
			}
			foreach ( self::published_maps_for_team( $team_id ) as $map ) {
				$maps[] = $map;
			}
		}

		include TACNAV_MAPS_DIR . 'templates/player-account.php';
		exit;
	}

	/**
	 * Published maps that list a team.
	 *
	 * @param int $team_id Team ID.
	 * @return array<int, array<string, mixed>>
	 */
	public static function published_maps_for_team( $team_id ) {
		$posts = get_posts(
			array(
				'post_type'      => Post_Types::MAP,
				'post_status'    => 'publish',
				'posts_per_page' => 50,
				'orderby'        => 'title',
				'order'          => 'ASC',
			)
		);
		$out   = array();
		foreach ( $posts as $post ) {
			$map = Catalog::get_map( (int) $post->ID );
			if ( $map && in_array( (int) $team_id, $map['team_ids'], true ) ) {
				$map['url'] = Canvas::url( (int) $map['id'] );
				$out[]      = $map;
			}
		}
		return $out;
	}

	/**
	 * Handle registration, sign-in, or profile save.
	 *
	 * @return string|\WP_Error
	 */
	private static function handle_post() {
		if ( ! isset( $_POST['tacnav_player_nonce'] ) || ! wp_verify_nonce( sanitize_text_field( wp_unslash( $_POST['tacnav_player_nonce'] ) ), 'tacnav_player' ) ) {
			return new \WP_Error( 'tacnav_player_nonce', __( 'The form expired. Try again.', 'tacnav-maps' ) );
		}

		$action   = isset( $_POST['tacnav_player_action'] ) ? sanitize_key( wp_unslash( $_POST['tacnav_player_action'] ) ) : '';
		$email    = isset( $_POST['email'] ) ? sanitize_email( wp_unslash( $_POST['email'] ) ) : '';
		$password = isset( $_POST['password'] ) ? (string) wp_unslash( $_POST['password'] ) : '';
		$nickname = isset( $_POST['nickname'] ) ? sanitize_text_field( wp_unslash( $_POST['nickname'] ) ) : '';
		if ( 'register' === $action ) {
			$result = self::register( $email, $password, $nickname );
			if ( is_wp_error( $result ) ) {
				return $result;
			}
			self::redirect_cabinet( 'new' );
		}
		if ( 'login' === $action ) {
			$result = self::login( $email, $password );
			if ( is_wp_error( $result ) ) {
				return $result;
			}
			self::redirect_cabinet( 'in' );
		}
		if ( 'profile' === $action ) {
			$file = isset( $_FILES['avatar'] ) && is_array( $_FILES['avatar'] ) ? $_FILES['avatar'] : array();
			return self::save_profile( $nickname, $file );
		}
		return new \WP_Error( 'tacnav_player_action', __( 'Unknown action.', 'tacnav-maps' ) );
	}

	/**
	 * Create a Player with no team.
	 *
	 * @param string $email    Email.
	 * @param string $password Password.
	 * @param string $nickname Nickname.
	 * @return string|\WP_Error
	 */
	private static function register( $email, $password, $nickname ) {
		if ( ! is_email( $email ) || strlen( $password ) < 8 || '' === $nickname ) {
			return new \WP_Error( 'tacnav_player_register', __( 'Enter an email, a nickname, and a password of at least 8 characters.', 'tacnav-maps' ) );
		}
		if ( email_exists( $email ) ) {
			return new \WP_Error( 'tacnav_player_register', __( 'That email is already registered.', 'tacnav-maps' ) );
		}

		$user_id = wp_insert_user(
			array(
				'user_login'   => $email,
				'user_email'   => $email,
				'user_pass'    => $password,
				'display_name' => $nickname,
				'nickname'     => $nickname,
				'role'         => Roles::ROLE_PLAYER,
			)
		);
		if ( is_wp_error( $user_id ) ) {
			return $user_id;
		}
		delete_user_meta( (int) $user_id, Membership::TEAM_META );
		wp_set_current_user( (int) $user_id );
		wp_set_auth_cookie( (int) $user_id );
		return '';
	}

	/**
	 * Sign in.
	 *
	 * @param string $email    Email.
	 * @param string $password Password.
	 * @return string|\WP_Error
	 */
	private static function login( $email, $password ) {
		$user = wp_signon(
			array(
				'user_login'    => $email,
				'user_password' => $password,
				'remember'      => true,
			),
			is_ssl()
		);
		if ( is_wp_error( $user ) ) {
			return new \WP_Error( 'tacnav_player_login', __( 'Those sign-in details were not recognized.', 'tacnav-maps' ) );
		}
		return '';
	}

	/**
	 * Send the user to the cabinet after sign-in or registration.
	 *
	 * @param string $code Notice code, in or new.
	 * @return never
	 */
	private static function redirect_cabinet( $code ) {
		wp_safe_redirect( add_query_arg( 'tacnav_notice', $code, self::url() ) );
		exit;
	}

	/**
	 * Notice carried by the cabinet redirect.
	 *
	 * @return string
	 */
	private static function notice_from_query() {
		// phpcs:ignore WordPress.Security.NonceVerification.Recommended -- Display flag after a same-site redirect, not a form.
		$code = isset( $_GET['tacnav_notice'] ) ? sanitize_key( wp_unslash( $_GET['tacnav_notice'] ) ) : '';
		if ( 'new' === $code ) {
			return __( 'Account created. A map will appear here after an organizer assigns your team.', 'tacnav-maps' );
		}
		if ( 'in' === $code ) {
			return __( 'Signed in.', 'tacnav-maps' );
		}
		return '';
	}

	/**
	 * Save nickname and avatar for the current player.
	 *
	 * @param string               $nickname Nickname.
	 * @param array<string, mixed> $file     Upload field.
	 * @return string|\WP_Error
	 */
	private static function save_profile( $nickname, $file ) {
		$user_id = get_current_user_id();
		if ( ! Membership::is_player( $user_id ) ) {
			return new \WP_Error( 'tacnav_player_profile', __( 'Only a player can edit this cabinet.', 'tacnav-maps' ) );
		}
		if ( '' === $nickname ) {
			return new \WP_Error( 'tacnav_player_profile', __( 'Enter a nickname.', 'tacnav-maps' ) );
		}
		wp_update_user(
			array(
				'ID'           => $user_id,
				'display_name' => $nickname,
				'nickname'     => $nickname,
			)
		);

		if ( ! empty( $file['name'] ) ) {
			$saved = self::save_avatar( $user_id, $file );
			if ( is_wp_error( $saved ) ) {
				return $saved;
			}
		}
		return __( 'Profile saved.', 'tacnav-maps' );
	}

	/**
	 * Store an image as the player avatar.
	 *
	 * @param int                  $user_id User ID.
	 * @param array<string, mixed> $file    Upload field.
	 * @return true|\WP_Error
	 */
	private static function save_avatar( $user_id, $file ) {
		require_once ABSPATH . 'wp-admin/includes/file.php';
		require_once ABSPATH . 'wp-admin/includes/media.php';
		require_once ABSPATH . 'wp-admin/includes/image.php';

		$uploaded = wp_handle_upload(
			$file,
			array(
				'test_form' => false,
				'mimes'     => array(
					'jpg|jpeg|jpe' => 'image/jpeg',
					'gif'          => 'image/gif',
					'png'          => 'image/png',
					'webp'         => 'image/webp',
				),
			)
		);
		if ( isset( $uploaded['error'] ) ) {
			return new \WP_Error( 'tacnav_avatar', (string) $uploaded['error'] );
		}

		$attachment = wp_insert_attachment(
			array(
				'post_mime_type' => $uploaded['type'],
				'post_title'     => sanitize_file_name( basename( $uploaded['file'] ) ),
				'post_status'    => 'inherit',
				'post_author'    => $user_id,
			),
			$uploaded['file']
		);
		if ( $attachment <= 0 ) {
			return new \WP_Error( 'tacnav_avatar', __( 'Could not save the avatar.', 'tacnav-maps' ) );
		}
		wp_update_attachment_metadata( $attachment, wp_generate_attachment_metadata( $attachment, $uploaded['file'] ) );
		update_user_meta( $user_id, Membership::AVATAR, (int) $attachment );
		return true;
	}
}
