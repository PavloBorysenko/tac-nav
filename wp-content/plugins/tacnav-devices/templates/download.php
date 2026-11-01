<?php
/**
 * Public Tac Nav Mortar download page for players.
 *
 * @package TacNav_Devices
 *
 * @var array<string, string> $release     Android release metadata.
 * @var string                $tacnav_type Application type.
 */

defined( 'ABSPATH' ) || exit;

$tacnav_app_name     = \TacNav\Devices\App_Types::get( $tacnav_type )['label'];
$tacnav_has_apk      = \TacNav\Devices\Release::has_stored_apk( $tacnav_type ) || '' !== $release['download_url'];
$tacnav_download_url = '' !== $release['download_url']
	? $release['download_url']
	: \TacNav\Devices\Release::public_apk_url( $tacnav_type );
$tacnav_version      = '' !== $release['version'] ? $release['version'] : '';
?>
<!doctype html>
<html <?php language_attributes(); ?>>
<head>
	<meta charset="<?php bloginfo( 'charset' ); ?>">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title><?php echo esc_html( sprintf( /* translators: %s: app name */ __( 'Get %s', 'tacnav-devices' ), $tacnav_app_name ) ); ?></title>
	<?php wp_print_styles( 'tacnav-devices-download' ); ?>
</head>
<body class="tacnav-mortar-download">
	<main class="tacnav-mortar-download__main">
		<p class="tacnav-mortar-download__kicker"><?php echo esc_html( get_bloginfo( 'name' ) ); ?></p>
		<h1><?php echo esc_html( sprintf( /* translators: %s: app name */ __( 'Get %s', 'tacnav-devices' ), $tacnav_app_name ) ); ?></h1>
		<p><?php esc_html_e( 'This is the current Android app for the game. Download it here, then ask an organizer for a one-time activation code.', 'tacnav-devices' ); ?></p>
		<?php if ( $tacnav_has_apk ) : ?>
			<p class="tacnav-mortar-download__version"><?php echo esc_html( sprintf( /* translators: %s: Android package id */ __( 'Package %s', 'tacnav-devices' ), $release['package_id'] ) ); ?></p>
			<?php if ( '' !== $tacnav_version ) : ?>
				<p class="tacnav-mortar-download__version"><?php echo esc_html( sprintf( /* translators: %s: app version */ __( 'Latest version %s', 'tacnav-devices' ), $tacnav_version ) ); ?></p>
			<?php endif; ?>
			<p>
				<a class="tacnav-mortar-download__button" href="<?php echo esc_url( $tacnav_download_url ); ?>">
					<?php esc_html_e( 'Download the app', 'tacnav-devices' ); ?>
				</a>
			</p>
			<ol>
				<li><?php esc_html_e( 'Tap Download the app and open the file.', 'tacnav-devices' ); ?></li>
				<li><?php esc_html_e( 'If Android asks, allow installs from this browser.', 'tacnav-devices' ); ?></li>
				<li><?php echo esc_html( sprintf( /* translators: %s: app name */ __( 'Open %s and scan the organizer QR code, or type the server address and one-time code.', 'tacnav-devices' ), $tacnav_app_name ) ); ?></li>
			</ol>
			<p class="tacnav-mortar-download__note"><?php esc_html_e( 'Android 9 or newer with Google Play Services is required. Replacing this file on the site gives every player the same latest version.', 'tacnav-devices' ); ?></p>
		<?php else : ?>
			<p class="tacnav-mortar-download__empty"><?php esc_html_e( 'The organizer has not published the app yet. Check back after the next release.', 'tacnav-devices' ); ?></p>
		<?php endif; ?>
		<p><a href="<?php echo esc_url( home_url( '/' ) ); ?>"><?php esc_html_e( 'Back to the site', 'tacnav-devices' ); ?></a></p>
	</main>
</body>
</html>
