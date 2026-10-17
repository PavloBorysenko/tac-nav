<?php
/**
 * Device application administration.
 *
 * @package TacNav_Devices
 *
 * @var array<int, array<string, mixed>> $applications Application rows.
 * @var string                           $action       Form target.
 * @var array<string, string>            $release      Android release metadata.
 * @var array<string, mixed>|null        $tacnav_draft     Application being edited.
 * @var array<string, mixed>             $tacnav_reference Normalized reference settings.
 * @var array<int, array<string, mixed>> $tacnav_maps      Published maps.
 * @var array<int, array<string, mixed>> $tacnav_teams     Published teams.
 * @var array<int, array<string, mixed>> $tacnav_icons     Published icons.
 */

defined( 'ABSPATH' ) || exit;

$tacnav_firing       = $tacnav_reference['firing_modes'];
$tacnav_aiming       = $tacnav_reference['aiming_modes'];
$tacnav_map_ids      = $tacnav_draft && isset( $tacnav_draft['allowed_map_ids'] ) ? array_map( 'intval', (array) $tacnav_draft['allowed_map_ids'] ) : array();
$tacnav_team_ids     = $tacnav_draft && isset( $tacnav_draft['allowed_team_ids'] ) ? array_map( 'intval', (array) $tacnav_draft['allowed_team_ids'] ) : array();
$tacnav_default_map  = $tacnav_draft && ! empty( $tacnav_draft['default_map_id'] ) ? (int) $tacnav_draft['default_map_id'] : 0;
$tacnav_default_team = $tacnav_draft && ! empty( $tacnav_draft['default_team_id'] ) ? (int) $tacnav_draft['default_team_id'] : 0;
$tacnav_edit_url     = admin_url( 'edit.php?post_type=tacnav_map&page=' . \TacNav\Devices\Admin::PAGE );
?>
<style>
	.tacnav-inline-form { display: inline; margin-left: 0.35rem; }
</style>
<div class="wrap">
	<h1><?php esc_html_e( 'TacNav device applications', 'tacnav-devices' ); ?></h1>
	<h2><?php echo $tacnav_draft ? esc_html__( 'Edit application definition', 'tacnav-devices' ) : esc_html__( 'Application definition', 'tacnav-devices' ); ?></h2>
	<p><?php esc_html_e( 'Choose published maps and teams from the catalog. Gameplay fields become the phone’s starting settings at the next activation.', 'tacnav-devices' ); ?></p>
	<form method="post" action="<?php echo esc_url( $action ); ?>">
		<input type="hidden" name="action" value="tacnav_devices_save_application">
		<?php wp_nonce_field( 'tacnav_devices_save_application' ); ?>
		<?php if ( $tacnav_draft ) : ?>
			<input type="hidden" name="id" value="<?php echo esc_attr( (string) $tacnav_draft['id'] ); ?>">
		<?php endif; ?>
		<table class="form-table">
			<?php if ( ! $tacnav_draft ) : ?>
				<tr><th><label for="tacnav-app-id"><?php esc_html_e( 'Application ID (update)', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-id" name="id" type="number" min="1"></td></tr>
			<?php endif; ?>
			<tr><th><label for="tacnav-app-type"><?php esc_html_e( 'Type', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-type" name="application_type" value="<?php echo esc_attr( $tacnav_draft ? (string) $tacnav_draft['application_type'] : 'mortar' ); ?>" required></td></tr>
			<tr><th><label for="tacnav-app-name"><?php esc_html_e( 'Name', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-name" name="name" value="<?php echo esc_attr( $tacnav_draft ? (string) $tacnav_draft['name'] : '' ); ?>" required></td></tr>
			<tr>
				<th><?php esc_html_e( 'Allowed maps', 'tacnav-devices' ); ?></th>
				<td>
					<?php if ( $tacnav_maps ) : ?>
						<?php foreach ( $tacnav_maps as $tacnav_map ) : ?>
							<label><input type="checkbox" name="allowed_map_ids[]" value="<?php echo esc_attr( (string) (int) $tacnav_map['id'] ); ?>" <?php checked( in_array( (int) $tacnav_map['id'], $tacnav_map_ids, true ) ); ?>> <?php echo esc_html( (string) $tacnav_map['title'] ); ?></label><br>
						<?php endforeach; ?>
					<?php else : ?>
						<p class="description"><?php esc_html_e( 'Publish a map first.', 'tacnav-devices' ); ?></p>
					<?php endif; ?>
				</td>
			</tr>
			<tr>
				<th><?php esc_html_e( 'Allowed teams', 'tacnav-devices' ); ?></th>
				<td>
					<?php if ( $tacnav_teams ) : ?>
						<?php foreach ( $tacnav_teams as $tacnav_team ) : ?>
							<label><input type="checkbox" name="allowed_team_ids[]" value="<?php echo esc_attr( (string) (int) $tacnav_team['id'] ); ?>" <?php checked( in_array( (int) $tacnav_team['id'], $tacnav_team_ids, true ) ); ?>> <?php echo esc_html( (string) $tacnav_team['title'] ); ?></label><br>
						<?php endforeach; ?>
					<?php else : ?>
						<p class="description"><?php esc_html_e( 'Publish a team first.', 'tacnav-devices' ); ?></p>
					<?php endif; ?>
				</td>
			</tr>
			<tr>
				<th><label for="tacnav-app-default-map"><?php esc_html_e( 'Default map', 'tacnav-devices' ); ?></label></th>
				<td>
					<select id="tacnav-app-default-map" name="default_map_id">
						<option value=""><?php esc_html_e( 'Select a map', 'tacnav-devices' ); ?></option>
						<?php foreach ( $tacnav_maps as $tacnav_map ) : ?>
							<option value="<?php echo esc_attr( (string) (int) $tacnav_map['id'] ); ?>" <?php selected( $tacnav_default_map, (int) $tacnav_map['id'] ); ?>><?php echo esc_html( (string) $tacnav_map['title'] ); ?></option>
						<?php endforeach; ?>
					</select>
				</td>
			</tr>
			<tr>
				<th><label for="tacnav-app-default-team"><?php esc_html_e( 'Default team', 'tacnav-devices' ); ?></label></th>
				<td>
					<select id="tacnav-app-default-team" name="default_team_id">
						<option value=""><?php esc_html_e( 'Select a team', 'tacnav-devices' ); ?></option>
						<?php foreach ( $tacnav_teams as $tacnav_team ) : ?>
							<option value="<?php echo esc_attr( (string) (int) $tacnav_team['id'] ); ?>" <?php selected( $tacnav_default_team, (int) $tacnav_team['id'] ); ?>><?php echo esc_html( (string) $tacnav_team['title'] ); ?></option>
						<?php endforeach; ?>
					</select>
				</td>
			</tr>
			<tr><th><label for="tacnav-app-mortar-name"><?php esc_html_e( 'Mortar name', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-mortar-name" name="mortar_name" value="<?php echo esc_attr( (string) $tacnav_reference['mortar_name'] ); ?>" required></td></tr>
			<tr>
				<th><?php esc_html_e( 'Firing modes', 'tacnav-devices' ); ?></th>
				<td>
					<?php foreach ( \TacNav\Devices\Validation::firing_mode_choices() as $tacnav_mode => $tacnav_label ) : ?>
						<label><input type="checkbox" name="firing_modes[]" value="<?php echo esc_attr( $tacnav_mode ); ?>" <?php checked( in_array( $tacnav_mode, (array) $tacnav_firing, true ) ); ?>> <?php echo esc_html( $tacnav_label ); ?></label><br>
					<?php endforeach; ?>
					<p class="description"><?php esc_html_e( 'At least one firing mode is required.', 'tacnav-devices' ); ?></p>
				</td>
			</tr>
			<tr>
				<th><?php esc_html_e( 'Aiming modes', 'tacnav-devices' ); ?></th>
				<td>
					<?php foreach ( \TacNav\Devices\Validation::aiming_mode_choices() as $tacnav_mode => $tacnav_label ) : ?>
						<label><input type="checkbox" name="aiming_modes[]" value="<?php echo esc_attr( $tacnav_mode ); ?>" <?php checked( in_array( $tacnav_mode, (array) $tacnav_aiming, true ) ); ?>> <?php echo esc_html( $tacnav_label ); ?></label><br>
					<?php endforeach; ?>
					<p class="description"><?php esc_html_e( 'At least one aiming mode is required.', 'tacnav-devices' ); ?></p>
				</td>
			</tr>
			<tr><th><label for="tacnav-app-approx"><?php esc_html_e( 'Approximate radius (m)', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-approx" name="approximate_radius_m" type="number" min="0.1" step="0.1" value="<?php echo esc_attr( (string) $tacnav_reference['approximate_radius_m'] ); ?>"></td></tr>
			<tr><th><label for="tacnav-app-elev-ref"><?php esc_html_e( 'Elevation reference (degrees)', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-elev-ref" name="elevation_reference_degrees" type="number" min="0" max="90" step="0.1" value="<?php echo esc_attr( (string) $tacnav_reference['elevation_reference_degrees'] ); ?>"></td></tr>
			<tr><th><label for="tacnav-app-min-elev"><?php esc_html_e( 'Minimum elevation (degrees)', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-min-elev" name="min_elevation_degrees" type="number" min="0" max="90" step="0.1" value="<?php echo esc_attr( (string) $tacnav_reference['min_elevation_degrees'] ); ?>"></td></tr>
			<tr><th><label for="tacnav-app-max-elev"><?php esc_html_e( 'Maximum elevation (degrees)', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-max-elev" name="max_elevation_degrees" type="number" min="0" max="90" step="0.1" value="<?php echo esc_attr( (string) $tacnav_reference['max_elevation_degrees'] ); ?>"></td></tr>
			<tr><th><label for="tacnav-app-max-dist"><?php esc_html_e( 'Maximum distance (m)', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-max-dist" name="max_distance_m" type="number" min="0.1" step="0.1" value="<?php echo esc_attr( (string) $tacnav_reference['max_distance_m'] ); ?>"></td></tr>
			<tr><th><label for="tacnav-app-min-dist"><?php esc_html_e( 'Minimum distance (m)', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-min-dist" name="min_distance_m" type="number" min="0.1" step="0.1" value="<?php echo esc_attr( (string) $tacnav_reference['min_distance_m'] ); ?>"></td></tr>
			<tr><th><label for="tacnav-app-damage"><?php esc_html_e( 'Damage radius (m)', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-damage" name="damage_radius_m" type="number" min="0.1" step="0.1" value="<?php echo esc_attr( (string) $tacnav_reference['damage_radius_m'] ); ?>"></td></tr>
			<tr><th><label for="tacnav-app-pedge"><?php esc_html_e( 'Edge probability', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-pedge" name="edge_probability" type="number" min="0" max="1" step="0.01" value="<?php echo esc_attr( (string) $tacnav_reference['edge_probability'] ); ?>"></td></tr>
			<tr><th><label for="tacnav-app-flight"><?php esc_html_e( 'Flight duration (seconds)', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-flight" name="flight_duration_seconds" type="number" min="1" step="1" value="<?php echo esc_attr( (string) $tacnav_reference['flight_duration_seconds'] ); ?>"></td></tr>
			<tr><th><label for="tacnav-app-ttl"><?php esc_html_e( 'Impact TTL (seconds)', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-ttl" name="impact_ttl_seconds" type="number" min="0" step="1" value="<?php echo esc_attr( (string) $tacnav_reference['impact_ttl_seconds'] ); ?>"></td></tr>
			<tr><th><label for="tacnav-app-gps-acc"><?php esc_html_e( 'Max location accuracy (m)', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-gps-acc" name="max_location_accuracy_m" type="number" min="0.1" step="0.1" value="<?php echo esc_attr( (string) $tacnav_reference['max_location_accuracy_m'] ); ?>"></td></tr>
			<tr><th><label for="tacnav-app-gps-age"><?php esc_html_e( 'Max location age (seconds)', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-gps-age" name="max_location_age_seconds" type="number" min="1" step="1" value="<?php echo esc_attr( (string) $tacnav_reference['max_location_age_seconds'] ); ?>"></td></tr>
			<tr><th><label for="tacnav-app-acoustic-db"><?php esc_html_e( 'Acoustic threshold (dBFS)', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-acoustic-db" name="acoustic_threshold_dbfs" type="number" step="0.1" value="<?php echo esc_attr( (string) $tacnav_reference['acoustic_threshold_dbfs'] ); ?>"></td></tr>
			<tr><th><label for="tacnav-app-acoustic-impulse"><?php esc_html_e( 'Acoustic impulse (ms)', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-acoustic-impulse" name="acoustic_impulse_ms" type="number" min="1" step="1" value="<?php echo esc_attr( (string) $tacnav_reference['acoustic_impulse_ms'] ); ?>"></td></tr>
			<tr><th><label for="tacnav-app-acoustic-debounce"><?php esc_html_e( 'Acoustic debounce (ms)', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-app-acoustic-debounce" name="acoustic_debounce_ms" type="number" min="1" step="1" value="<?php echo esc_attr( (string) $tacnav_reference['acoustic_debounce_ms'] ); ?>"></td></tr>
			<tr>
				<th><label for="tacnav-app-icon"><?php esc_html_e( 'Impact icon', 'tacnav-devices' ); ?></label></th>
				<td>
					<select id="tacnav-app-icon" name="impact_icon_id">
						<option value="0"><?php esc_html_e( 'Phone default marker', 'tacnav-devices' ); ?></option>
						<?php foreach ( $tacnav_icons as $tacnav_icon ) : ?>
							<option value="<?php echo esc_attr( (string) (int) $tacnav_icon['id'] ); ?>" <?php selected( (int) $tacnav_reference['impact_icon_id'], (int) $tacnav_icon['id'] ); ?>><?php echo esc_html( (string) $tacnav_icon['title'] ); ?></option>
						<?php endforeach; ?>
					</select>
				</td>
			</tr>
		</table>
		<?php submit_button( $tacnav_draft ? __( 'Update application', 'tacnav-devices' ) : __( 'Save application', 'tacnav-devices' ) ); ?>
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
					<a class="button button-small" href="<?php echo esc_url( add_query_arg( 'edit', (int) $tacnav_application['id'], $tacnav_edit_url ) ); ?>"><?php esc_html_e( 'Edit', 'tacnav-devices' ); ?></a>
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
