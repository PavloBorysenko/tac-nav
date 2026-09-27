<?php
/**
 * Player cabinet.
 *
 * @package TacNav_Maps
 *
 * @var string               $notice Notice.
 * @var string               $error  Error.
 * @var bool                 $player Whether the current user is a player.
 * @var array<string, mixed>|null $team Team passport.
 * @var array<int, array<string, string>> $mates Teammates.
 * @var array<int, array<string, mixed>> $maps Published maps.
 * @var \WP_User             $user Current user.
 */

defined( 'ABSPATH' ) || exit;
?><!DOCTYPE html>
<html <?php language_attributes(); ?>>
<head>
	<meta charset="<?php bloginfo( 'charset' ); ?>" />
	<meta name="viewport" content="width=device-width, initial-scale=1" />
	<title><?php esc_html_e( 'Player', 'tacnav-maps' ); ?></title>
	<style>
		body { font-family: sans-serif; margin: 2rem auto; max-width: 40rem; padding: 0 1rem; }
		label { display: block; margin: 0.75rem 0; }
		.tacnav-error { color: #9b1c1c; }
		.tacnav-notice { color: #14532d; }
		ul { padding-left: 1.2rem; }
	</style>
</head>
<body>
	<h1><?php esc_html_e( 'Player', 'tacnav-maps' ); ?></h1>
	<?php if ( $error ) : ?>
		<p class="tacnav-error"><?php echo esc_html( $error ); ?></p>
	<?php endif; ?>
	<?php if ( $notice ) : ?>
		<p class="tacnav-notice"><?php echo esc_html( $notice ); ?></p>
	<?php endif; ?>

	<?php if ( ! is_user_logged_in() ) : ?>
		<h2 id="login"><?php esc_html_e( 'Sign in', 'tacnav-maps' ); ?></h2>
		<form method="post">
			<?php wp_nonce_field( 'tacnav_player', 'tacnav_player_nonce' ); ?>
			<input type="hidden" name="tacnav_player_action" value="login" />
			<label><?php esc_html_e( 'Email', 'tacnav-maps' ); ?> <input type="email" name="email" required /></label>
			<label><?php esc_html_e( 'Password', 'tacnav-maps' ); ?> <input type="password" name="password" required /></label>
			<button type="submit"><?php esc_html_e( 'Sign in', 'tacnav-maps' ); ?></button>
		</form>
		<h2 id="register"><?php esc_html_e( 'Register', 'tacnav-maps' ); ?></h2>
		<form method="post">
			<?php wp_nonce_field( 'tacnav_player', 'tacnav_player_nonce' ); ?>
			<input type="hidden" name="tacnav_player_action" value="register" />
			<label><?php esc_html_e( 'Email', 'tacnav-maps' ); ?> <input type="email" name="email" required /></label>
			<label><?php esc_html_e( 'Nickname', 'tacnav-maps' ); ?> <input type="text" name="nickname" required /></label>
			<label><?php esc_html_e( 'Password', 'tacnav-maps' ); ?> <input type="password" name="password" required minlength="8" /></label>
			<button type="submit"><?php esc_html_e( 'Create account', 'tacnav-maps' ); ?></button>
		</form>
	<?php elseif ( ! $player ) : ?>
		<p><?php esc_html_e( 'This page is for players.', 'tacnav-maps' ); ?></p>
	<?php else : ?>
		<form method="post" enctype="multipart/form-data">
			<?php wp_nonce_field( 'tacnav_player', 'tacnav_player_nonce' ); ?>
			<input type="hidden" name="tacnav_player_action" value="profile" />
			<?php $tacnav_avatar = \TacNav\Maps\Membership::avatar_url( (int) $user->ID ); ?>
			<?php if ( $tacnav_avatar ) : ?>
				<p><img src="<?php echo esc_url( $tacnav_avatar ); ?>" alt="" width="64" height="64" /></p>
			<?php endif; ?>
			<label><?php esc_html_e( 'Nickname', 'tacnav-maps' ); ?> <input type="text" name="nickname" value="<?php echo esc_attr( $user->display_name ); ?>" required /></label>
			<label><?php esc_html_e( 'Avatar', 'tacnav-maps' ); ?> <input type="file" name="avatar" accept="image/jpeg,image/png,image/gif,image/webp" /></label>
			<button type="submit"><?php esc_html_e( 'Save profile', 'tacnav-maps' ); ?></button>
		</form>
		<h2><?php echo esc_html( $team ? $team['title'] : __( 'No team yet', 'tacnav-maps' ) ); ?></h2>
		<?php if ( ! $team ) : ?>
			<p><?php esc_html_e( 'An organizer will assign your team. Until then this list stays empty.', 'tacnav-maps' ); ?></p>
		<?php else : ?>
			<h3><?php esc_html_e( 'Teammates', 'tacnav-maps' ); ?></h3>
			<ul>
				<?php foreach ( $mates as $tacnav_mate ) : ?>
					<li><?php echo esc_html( $tacnav_mate['name'] ); ?></li>
				<?php endforeach; ?>
			</ul>
			<h3><?php esc_html_e( 'Maps', 'tacnav-maps' ); ?></h3>
			<ul>
				<?php foreach ( $maps as $tacnav_map ) : ?>
					<li><a href="<?php echo esc_url( $tacnav_map['url'] ); ?>"><?php echo esc_html( $tacnav_map['title'] ); ?></a></li>
				<?php endforeach; ?>
			</ul>
		<?php endif; ?>
	<?php endif; ?>
</body>
</html>
