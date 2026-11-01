<?php
/**
 * One-time Android activation handoff.
 *
 * @package TacNav_Devices
 *
 * @var string                $code        One-time activation code.
 * @var string                $payload     QR payload JSON.
 * @var array<string, string> $release     Android release metadata.
 * @var string                $tacnav_type Application type.
 */

defined( 'ABSPATH' ) || exit;

$tacnav_app_title = sprintf(
	/* translators: %s: app name */
	__( '%s activation', 'tacnav-devices' ),
	\TacNav\Devices\App_Types::get( $tacnav_type )['label']
);
?>
<!doctype html>
<html <?php language_attributes(); ?>>
<head>
	<meta charset="<?php bloginfo( 'charset' ); ?>">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title><?php echo esc_html( $tacnav_app_title ); ?></title>
	<?php wp_print_styles( 'tacnav-devices-activation' ); ?>
</head>
<body class="tacnav-activation">
	<main class="tacnav-activation__card">
		<h1><?php echo esc_html( $tacnav_app_title ); ?></h1>
		<p class="tacnav-activation__warning"><strong><?php esc_html_e( 'This code expires in 15 minutes and is shown only on this response.', 'tacnav-devices' ); ?></strong></p>
		<div id="tacnav-activation-qr" class="tacnav-activation__qr" data-payload="<?php echo esc_attr( $payload ); ?>"></div>
		<dl>
			<dt><?php esc_html_e( 'Server URL', 'tacnav-devices' ); ?></dt>
			<dd><code><?php echo esc_html( home_url( '/' ) ); ?></code></dd>
			<dt><?php esc_html_e( 'Manual code', 'tacnav-devices' ); ?></dt>
			<dd><code class="tacnav-activation__code"><?php echo esc_html( $code ); ?></code></dd>
		</dl>
		<h2><?php esc_html_e( 'Android application', 'tacnav-devices' ); ?></h2>
		<dl>
			<dt><?php esc_html_e( 'Package', 'tacnav-devices' ); ?></dt>
			<dd><code><?php echo esc_html( $release['package_id'] ); ?></code></dd>
			<dt><?php esc_html_e( 'Version', 'tacnav-devices' ); ?></dt>
			<dd><?php echo esc_html( $release['version'] ); ?></dd>
		</dl>
		<div class="tacnav-activation__actions">
			<?php $tacnav_download_url = '' !== $release['download_url'] ? $release['download_url'] : \TacNav\Devices\Release::public_apk_url( $tacnav_type ); ?>
			<a class="tacnav-activation__button tacnav-activation__button--primary" href="<?php echo esc_url( $tacnav_download_url ); ?>"><?php esc_html_e( 'Download APK', 'tacnav-devices' ); ?></a>
			<a class="tacnav-activation__button" href="<?php echo esc_url( \TacNav\Devices\Release::public_page_url( $tacnav_type ) ); ?>"><?php esc_html_e( 'Public download page', 'tacnav-devices' ); ?></a>
			<a class="tacnav-activation__button" href="<?php echo esc_url( admin_url( \TacNav\Devices\Admin::page_query( $tacnav_type ) ) ); ?>"><?php esc_html_e( 'Back', 'tacnav-devices' ); ?></a>
		</div>
	</main>
	<?php wp_print_scripts( array( 'tacnav-qrcode', 'tacnav-devices-admin' ) ); ?>
</body>
</html>
