<?php
/**
 * Device application administration.
 *
 * @package TacNav_Devices
 *
 * @var array<int, array<string, mixed>> $applications Application rows.
 * @var string                           $action       Form target.
 * @var array<string, string>            $release      Android release metadata.
 */

defined( 'ABSPATH' ) || exit;
?>
<div class="wrap">
	<h1><?php esc_html_e( 'TacNav device applications', 'tacnav-devices' ); ?></h1>
	<h2><?php esc_html_e( 'Application definition', 'tacnav-devices' ); ?></h2>
	<p><?php esc_html_e( 'Allowed/default map and team IDs are validated against the TacNav Maps catalog when saved.', 'tacnav-devices' ); ?></p>
	<form method="post" action="<?php echo esc_url( $action ); ?>">
		<input type="hidden" name="action" value="tacnav_devices_save_application">
		<?php wp_nonce_field( 'tacnav_devices_save_application' ); ?>
		<table class="form-table">
			<tr><th><label for="tacnav-app-id"><?php esc_html_e( 'Application ID (update)', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-id" name="id" type="number" min="1"></td></tr>
			<tr><th><label for="tacnav-app-type"><?php esc_html_e( 'Type', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-type" name="application_type" value="mortar" required></td></tr>
			<tr><th><label for="tacnav-app-name"><?php esc_html_e( 'Name', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-name" name="name" required></td></tr>
			<tr><th><label for="tacnav-app-maps"><?php esc_html_e( 'Allowed map IDs', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-maps" name="allowed_map_ids" placeholder="12, 34" required></td></tr>
			<tr><th><label for="tacnav-app-teams"><?php esc_html_e( 'Allowed team IDs', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-teams" name="allowed_team_ids" placeholder="56, 78" required></td></tr>
			<tr><th><label for="tacnav-app-default-map"><?php esc_html_e( 'Default map ID', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-default-map" name="default_map_id" type="number" min="1"></td></tr>
			<tr><th><label for="tacnav-app-default-team"><?php esc_html_e( 'Default team ID', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-default-team" name="default_team_id" type="number" min="1"></td></tr>
			<tr><th><label for="tacnav-app-reference"><?php esc_html_e( 'Reference settings JSON', 'tacnav-devices' ); ?></label></th><td><textarea id="tacnav-app-reference" name="reference_settings" rows="8" class="large-text code">{}</textarea></td></tr>
		</table>
		<?php submit_button( __( 'Save application', 'tacnav-devices' ) ); ?>
	</form>

	<h2><?php esc_html_e( 'Existing applications', 'tacnav-devices' ); ?></h2>
	<table class="widefat striped">
		<thead><tr><th><?php esc_html_e( 'ID', 'tacnav-devices' ); ?></th><th><?php esc_html_e( 'Name', 'tacnav-devices' ); ?></th><th><?php esc_html_e( 'Type', 'tacnav-devices' ); ?></th><th><?php esc_html_e( 'Actions', 'tacnav-devices' ); ?></th></tr></thead>
		<tbody>
		<?php foreach ( $applications as $tacnav_application ) : ?>
			<tr>
				<td><?php echo esc_html( (string) $tacnav_application['id'] ); ?></td>
				<td><?php echo esc_html( (string) $tacnav_application['name'] ); ?></td>
				<td><?php echo esc_html( (string) $tacnav_application['application_type'] ); ?></td>
				<td>
					<form class="tacnav-inline-form" method="post" action="<?php echo esc_url( $action ); ?>">
						<input type="hidden" name="action" value="tacnav_devices_create_activation"><input type="hidden" name="id" value="<?php echo esc_attr( (string) $tacnav_application['id'] ); ?>">
						<?php wp_nonce_field( 'tacnav_devices_create_activation' ); ?>
						<?php submit_button( __( 'Activation handoff', 'tacnav-devices' ), 'secondary small', 'submit', false ); ?>
					</form>
					<form class="tacnav-inline-form" method="post" action="<?php echo esc_url( $action ); ?>">
						<input type="hidden" name="action" value="tacnav_devices_revoke"><input type="hidden" name="id" value="<?php echo esc_attr( (string) $tacnav_application['id'] ); ?>">
						<?php wp_nonce_field( 'tacnav_devices_revoke' ); ?>
						<?php submit_button( __( 'Revoke phone', 'tacnav-devices' ), 'delete small', 'submit', false ); ?>
					</form>
				</td>
			</tr>
		<?php endforeach; ?>
		</tbody>
	</table>

	<h2><?php esc_html_e( 'Current Android release', 'tacnav-devices' ); ?></h2>
	<p><?php esc_html_e( 'Upload a built APK to host it on this site, or paste an HTTPS URL if the file is stored elsewhere. Signing keys stay off the site.', 'tacnav-devices' ); ?></p>
	<form method="post" action="<?php echo esc_url( $action ); ?>" enctype="multipart/form-data">
		<input type="hidden" name="action" value="tacnav_devices_save_release"><?php wp_nonce_field( 'tacnav_devices_save_release' ); ?>
		<table class="form-table">
			<tr><th><?php esc_html_e( 'Package', 'tacnav-devices' ); ?></th><td><code>com.tacnav.mortar</code></td></tr>
			<tr><th><label for="tacnav-release-version"><?php esc_html_e( 'Version', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-release-version" name="version" value="<?php echo esc_attr( $release['version'] ); ?>" placeholder="0.1.0"></td></tr>
			<tr>
				<th><label for="tacnav-release-apk"><?php esc_html_e( 'APK file', 'tacnav-devices' ); ?></label></th>
				<td>
					<input id="tacnav-release-apk" name="apk" type="file" accept=".apk,application/vnd.android.package-archive">
					<?php if ( \TacNav\Devices\Release::has_stored_apk() ) : ?>
						<p class="description"><?php esc_html_e( 'A package is already hosted. Uploading another file replaces it.', 'tacnav-devices' ); ?></p>
					<?php endif; ?>
				</td>
			</tr>
			<tr><th><label for="tacnav-release-url"><?php esc_html_e( 'APK URL', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-release-url" class="large-text" type="url" name="download_url" value="<?php echo esc_attr( $release['download_url'] ); ?>"></td></tr>
		</table>
		<?php submit_button( __( 'Publish release', 'tacnav-devices' ) ); ?>
	</form>
	<?php if ( '' !== $release['download_url'] ) : ?>
		<p>
			<a class="button button-primary" href="<?php echo esc_url( $release['download_url'] ); ?>"><?php esc_html_e( 'Download APK', 'tacnav-devices' ); ?></a>
			<a class="button" href="<?php echo esc_url( \TacNav\Devices\Release::public_page_url() ); ?>"><?php esc_html_e( 'Open public download page', 'tacnav-devices' ); ?></a>
		</p>
	<?php endif; ?>
</div>
