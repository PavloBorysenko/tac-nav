<?php
/**
 * Guest link handoff.
 *
 * @package TacNav_Maps
 *
 * @var array<string, mixed> $map         Map passport.
 * @var array<string, mixed> $team        Team passport.
 * @var string               $url         Guest canvas URL.
 * @var string               $header_html Rendered header template part.
 * @var string               $footer_html Rendered footer template part.
 */

defined( 'ABSPATH' ) || exit;
?><!DOCTYPE html>
<html <?php language_attributes(); ?>>
<head>
	<meta charset="<?php bloginfo( 'charset' ); ?>" />
	<meta name="viewport" content="width=device-width, initial-scale=1" />
	<title><?php echo esc_html( $map['title'] . ' — ' . $team['title'] ); ?></title>
	<?php wp_head(); ?>
</head>
<body <?php body_class( 'tacnav-guest-link-page' ); ?>>
<?php wp_body_open(); ?>
<div class="wp-site-blocks">
	<header class="wp-block-template-part"><?php echo $header_html; // phpcs:ignore WordPress.Security.EscapeOutput.OutputNotEscaped -- Block markup is escaped when the template part renders. ?></header>
	<main id="wp--skip-link--target" class="wp-block-group is-layout-constrained wp-block-group-is-layout-constrained has-global-padding">
		<h1 class="wp-block-heading"><?php echo esc_html( $map['title'] ); ?></h1>
		<p><?php echo esc_html( $team['title'] ); ?></p>
		<div class="tacnav-guest-card">
			<div class="tacnav-guest-url-row">
				<input id="tacnav-guest-url" type="text" readonly value="<?php echo esc_attr( $url ); ?>" />
				<button type="button" class="wp-element-button" id="tacnav-guest-copy"><?php esc_html_e( 'Copy link', 'tacnav-maps' ); ?></button>
			</div>
			<div id="tacnav-qr"></div>
		</div>
	</main>
	<footer class="wp-block-template-part"><?php echo $footer_html; // phpcs:ignore WordPress.Security.EscapeOutput.OutputNotEscaped -- Block markup is escaped when the template part renders. ?></footer>
</div>
<?php wp_footer(); ?>
</body>
</html>
