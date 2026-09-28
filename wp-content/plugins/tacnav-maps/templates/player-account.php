<?php
/**
 * Player cabinet.
 *
 * @package TacNav_Maps
 *
 * @var string               $notice      Notice.
 * @var string               $error       Error.
 * @var bool                 $player      Whether the current user is a player.
 * @var array<string, mixed>|null $team   Team passport.
 * @var array<int, array<string, string>> $mates Teammates.
 * @var array<int, array<string, mixed>> $maps Published maps.
 * @var \WP_User             $user        Current user.
 * @var string               $header_html Rendered header template part.
 * @var string               $footer_html Rendered footer template part.
 */

defined( 'ABSPATH' ) || exit;
?><!DOCTYPE html>
<html <?php language_attributes(); ?>>
<head>
	<meta charset="<?php bloginfo( 'charset' ); ?>" />
	<meta name="viewport" content="width=device-width, initial-scale=1" />
	<title><?php esc_html_e( 'Player', 'tacnav-maps' ); ?></title>
	<?php wp_head(); ?>
</head>
<body <?php body_class( 'tacnav-player-account-page' ); ?>>
<?php wp_body_open(); ?>
<div class="wp-site-blocks">
	<header class="wp-block-template-part"><?php echo $header_html; // phpcs:ignore WordPress.Security.EscapeOutput.OutputNotEscaped -- Block markup is escaped when the template part renders. ?></header>
	<main id="wp--skip-link--target" class="wp-block-group is-layout-constrained wp-block-group-is-layout-constrained has-global-padding">
		<h1 class="wp-block-heading"><?php esc_html_e( 'Player', 'tacnav-maps' ); ?></h1>
		<?php if ( $error ) : ?>
			<p class="tacnav-error"><?php echo esc_html( $error ); ?></p>
		<?php endif; ?>
		<?php if ( $notice ) : ?>
			<p class="tacnav-notice"><?php echo esc_html( $notice ); ?></p>
		<?php endif; ?>

		<?php if ( ! is_user_logged_in() ) : ?>
			<section class="tacnav-account-card">
				<h2 id="login"><?php esc_html_e( 'Sign in', 'tacnav-maps' ); ?></h2>
				<form method="post">
					<?php wp_nonce_field( 'tacnav_player', 'tacnav_player_nonce' ); ?>
					<input type="hidden" name="tacnav_player_action" value="login" />
					<label><?php esc_html_e( 'Email', 'tacnav-maps' ); ?> <input type="email" name="email" required /></label>
					<label><?php esc_html_e( 'Password', 'tacnav-maps' ); ?> <input type="password" name="password" required /></label>
					<button type="submit" class="wp-element-button"><?php esc_html_e( 'Sign in', 'tacnav-maps' ); ?></button>
				</form>
			</section>
			<section class="tacnav-account-card">
				<h2 id="register"><?php esc_html_e( 'Register', 'tacnav-maps' ); ?></h2>
				<form method="post">
					<?php wp_nonce_field( 'tacnav_player', 'tacnav_player_nonce' ); ?>
					<input type="hidden" name="tacnav_player_action" value="register" />
					<label><?php esc_html_e( 'Email', 'tacnav-maps' ); ?> <input type="email" name="email" required /></label>
					<label><?php esc_html_e( 'Nickname', 'tacnav-maps' ); ?> <input type="text" name="nickname" required /></label>
					<label><?php esc_html_e( 'Password', 'tacnav-maps' ); ?> <input type="password" name="password" required minlength="8" /></label>
					<button type="submit" class="wp-element-button"><?php esc_html_e( 'Create account', 'tacnav-maps' ); ?></button>
				</form>
			</section>
		<?php elseif ( ! $player ) : ?>
			<p><?php esc_html_e( 'This page is for players.', 'tacnav-maps' ); ?></p>
		<?php else : ?>
			<section class="tacnav-account-card">
				<h2><?php esc_html_e( 'Profile', 'tacnav-maps' ); ?></h2>
				<form method="post" enctype="multipart/form-data">
					<?php wp_nonce_field( 'tacnav_player', 'tacnav_player_nonce' ); ?>
					<input type="hidden" name="tacnav_player_action" value="profile" />
					<?php $tacnav_avatar = \TacNav\Maps\Membership::avatar_url( (int) $user->ID ); ?>
					<?php if ( $tacnav_avatar ) : ?>
						<p><img class="tacnav-account-avatar" src="<?php echo esc_url( $tacnav_avatar ); ?>" alt="" width="64" height="64" /></p>
					<?php else : ?>
						<p><span class="tacnav-account-avatar tacnav-account-avatar-empty" aria-hidden="true"></span></p>
					<?php endif; ?>
					<label><?php esc_html_e( 'Nickname', 'tacnav-maps' ); ?> <input type="text" name="nickname" value="<?php echo esc_attr( $user->display_name ); ?>" required /></label>
					<label><?php esc_html_e( 'Avatar', 'tacnav-maps' ); ?> <input type="file" name="avatar" accept="image/jpeg,image/png,image/gif,image/webp" /></label>
					<button type="submit" class="wp-element-button"><?php esc_html_e( 'Save profile', 'tacnav-maps' ); ?></button>
				</form>
			</section>
			<section class="tacnav-account-card">
				<h2><?php echo esc_html( $team ? $team['title'] : __( 'No team yet', 'tacnav-maps' ) ); ?></h2>
				<?php if ( ! $team ) : ?>
					<p><?php esc_html_e( 'An organizer will assign your team. Until then this list stays empty.', 'tacnav-maps' ); ?></p>
				<?php else : ?>
					<h3><?php esc_html_e( 'Teammates', 'tacnav-maps' ); ?></h3>
					<ul class="tacnav-account-mates">
						<?php foreach ( $mates as $tacnav_mate ) : ?>
							<li class="tacnav-account-mate">
								<?php if ( $tacnav_mate['avatar'] ) : ?>
									<img class="tacnav-account-avatar" src="<?php echo esc_url( $tacnav_mate['avatar'] ); ?>" alt="" width="40" height="40" />
								<?php else : ?>
									<span class="tacnav-account-avatar tacnav-account-avatar-empty" aria-hidden="true"></span>
								<?php endif; ?>
								<span><?php echo esc_html( $tacnav_mate['name'] ); ?></span>
							</li>
						<?php endforeach; ?>
					</ul>
				<?php endif; ?>
			</section>
			<section class="tacnav-account-card">
				<h2><?php esc_html_e( 'Maps', 'tacnav-maps' ); ?></h2>
				<ul class="tacnav-account-maps">
					<?php foreach ( $maps as $tacnav_map ) : ?>
						<li><a href="<?php echo esc_url( $tacnav_map['url'] ); ?>"><?php echo esc_html( $tacnav_map['title'] ); ?></a></li>
					<?php endforeach; ?>
				</ul>
			</section>
		<?php endif; ?>
	</main>
	<footer class="wp-block-template-part"><?php echo $footer_html; // phpcs:ignore WordPress.Security.EscapeOutput.OutputNotEscaped -- Block markup is escaped when the template part renders. ?></footer>
</div>
<?php wp_footer(); ?>
</body>
</html>
