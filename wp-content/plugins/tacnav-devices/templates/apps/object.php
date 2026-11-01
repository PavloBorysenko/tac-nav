<?php
/**
 * Object application administration.
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

$tacnav_map_ids      = $tacnav_draft && isset( $tacnav_draft['allowed_map_ids'] ) ? array_map( 'intval', (array) $tacnav_draft['allowed_map_ids'] ) : array();
$tacnav_team_ids     = $tacnav_draft && isset( $tacnav_draft['allowed_team_ids'] ) ? array_map( 'intval', (array) $tacnav_draft['allowed_team_ids'] ) : array();
$tacnav_default_map  = $tacnav_draft && ! empty( $tacnav_draft['default_map_id'] ) ? (int) $tacnav_draft['default_map_id'] : 0;
$tacnav_default_team = $tacnav_draft && ! empty( $tacnav_draft['default_team_id'] ) ? (int) $tacnav_draft['default_team_id'] : 0;
$tacnav_edit_url     = admin_url( \TacNav\Devices\Admin::page_query( 'object' ) );
$tacnav_number       = static function ( $key ) use ( $tacnav_reference ) {
	$value = $tacnav_reference[ $key ];
	return null === $value || 0.0 === $value ? '' : (string) $value;
};
?>
	<h2><?php echo $tacnav_draft ? esc_html__( 'Edit application definition', 'tacnav-devices' ) : esc_html__( 'Application definition', 'tacnav-devices' ); ?></h2>
	<p><?php esc_html_e( 'These values become the phone’s starting settings at the next activation. Later changes under the PIN stay on that phone.', 'tacnav-devices' ); ?></p>
	<form method="post" action="<?php echo esc_url( $action ); ?>">
		<input type="hidden" name="action" value="tacnav_devices_save_application">
		<input type="hidden" name="application_type" value="object">
		<?php wp_nonce_field( 'tacnav_devices_save_application' ); ?>
		<?php if ( $tacnav_draft ) : ?>
			<input type="hidden" name="id" value="<?php echo esc_attr( (string) $tacnav_draft['id'] ); ?>">
		<?php endif; ?>
		<table class="form-table">
			<tr><th><label for="tacnav-object-app-name"><?php esc_html_e( 'Application name', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-object-app-name" name="name" value="<?php echo esc_attr( $tacnav_draft ? (string) $tacnav_draft['name'] : '' ); ?>" required></td></tr>
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
				<th><label for="tacnav-object-default-map"><?php esc_html_e( 'Default map', 'tacnav-devices' ); ?></label></th>
				<td>
					<select id="tacnav-object-default-map" name="default_map_id">
						<option value=""><?php esc_html_e( 'Select a map', 'tacnav-devices' ); ?></option>
						<?php foreach ( $tacnav_maps as $tacnav_map ) : ?>
							<option value="<?php echo esc_attr( (string) (int) $tacnav_map['id'] ); ?>" <?php selected( $tacnav_default_map, (int) $tacnav_map['id'] ); ?>><?php echo esc_html( (string) $tacnav_map['title'] ); ?></option>
						<?php endforeach; ?>
					</select>
				</td>
			</tr>
			<tr>
				<th><label for="tacnav-object-default-team"><?php esc_html_e( 'Default team', 'tacnav-devices' ); ?></label></th>
				<td>
					<select id="tacnav-object-default-team" name="default_team_id">
						<option value=""><?php esc_html_e( 'Select a team', 'tacnav-devices' ); ?></option>
						<?php foreach ( $tacnav_teams as $tacnav_team ) : ?>
							<option value="<?php echo esc_attr( (string) (int) $tacnav_team['id'] ); ?>" <?php selected( $tacnav_default_team, (int) $tacnav_team['id'] ); ?>><?php echo esc_html( (string) $tacnav_team['title'] ); ?></option>
						<?php endforeach; ?>
					</select>
				</td>
			</tr>
			<tr>
				<th><label for="tacnav-object-name"><?php esc_html_e( 'Object name', 'tacnav-devices' ); ?></label></th>
				<td>
					<input id="tacnav-object-name" name="object_name" value="<?php echo esc_attr( (string) $tacnav_reference['object_name'] ); ?>">
					<p class="description"><?php esc_html_e( 'Shown on the map. Empty uses the application name.', 'tacnav-devices' ); ?></p>
				</td>
			</tr>
			<tr><th><label for="tacnav-object-description"><?php esc_html_e( 'Description', 'tacnav-devices' ); ?></label></th><td><textarea id="tacnav-object-description" name="description" class="large-text" rows="2"><?php echo esc_textarea( (string) $tacnav_reference['description'] ); ?></textarea></td></tr>
			<tr>
				<th><label for="tacnav-object-icon"><?php esc_html_e( 'Icon', 'tacnav-devices' ); ?></label></th>
				<td>
					<select id="tacnav-object-icon" name="icon_id">
						<option value="0"><?php esc_html_e( 'Default marker', 'tacnav-devices' ); ?></option>
						<?php foreach ( $tacnav_icons as $tacnav_icon ) : ?>
							<option value="<?php echo esc_attr( (string) (int) $tacnav_icon['id'] ); ?>" <?php selected( (int) $tacnav_reference['icon_id'], (int) $tacnav_icon['id'] ); ?>><?php echo esc_html( (string) $tacnav_icon['title'] ); ?></option>
						<?php endforeach; ?>
					</select>
				</td>
			</tr>
			<tr>
				<th><label for="tacnav-object-destroyed-icon"><?php esc_html_e( 'Destroyed icon', 'tacnav-devices' ); ?></label></th>
				<td>
					<select id="tacnav-object-destroyed-icon" name="destroyed_icon_id">
						<option value="0"><?php esc_html_e( 'Default marker', 'tacnav-devices' ); ?></option>
						<?php foreach ( $tacnav_icons as $tacnav_icon ) : ?>
							<option value="<?php echo esc_attr( (string) (int) $tacnav_icon['id'] ); ?>" <?php selected( (int) $tacnav_reference['destroyed_icon_id'], (int) $tacnav_icon['id'] ); ?>><?php echo esc_html( (string) $tacnav_icon['title'] ); ?></option>
						<?php endforeach; ?>
					</select>
				</td>
			</tr>
			<tr>
				<th><label for="tacnav-object-hits"><?php esc_html_e( 'Hits to destroy', 'tacnav-devices' ); ?></label></th>
				<td>
					<input id="tacnav-object-hits" name="hits_to_destroy" type="number" min="0" step="1" value="<?php echo esc_attr( (string) (int) $tacnav_reference['hits_to_destroy'] ); ?>">
					<p class="description"><?php esc_html_e( '0 means hits never destroy it.', 'tacnav-devices' ); ?></p>
				</td>
			</tr>
			<tr>
				<th><?php esc_html_e( 'Size (m)', 'tacnav-devices' ); ?></th>
				<td>
					<label for="tacnav-object-size-a"><?php esc_html_e( 'Along the phone top', 'tacnav-devices' ); ?></label>
					<input id="tacnav-object-size-a" name="size_a_m" type="number" min="0" step="0.1" value="<?php echo esc_attr( $tacnav_number( 'size_a_m' ) ); ?>">
					<label for="tacnav-object-size-b"><?php esc_html_e( 'Across it', 'tacnav-devices' ); ?></label>
					<input id="tacnav-object-size-b" name="size_b_m" type="number" min="0" step="0.1" value="<?php echo esc_attr( $tacnav_number( 'size_b_m' ) ); ?>">
					<p class="description"><?php esc_html_e( 'Both empty: a point. One filled: a circle with that radius. Both filled: a rectangle with those sides. The phone is at the centre.', 'tacnav-devices' ); ?></p>
				</td>
			</tr>
			<tr><th><label for="tacnav-object-deploy"><?php esc_html_e( 'Deployment seconds', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-object-deploy" name="activation_seconds" type="number" min="0" step="1" value="<?php echo esc_attr( (string) (int) $tacnav_reference['activation_seconds'] ); ?>"><p class="description"><?php esc_html_e( '0 activates at once. During deployment it can already be hit, and only its own team and staff see it.', 'tacnav-devices' ); ?></p></td></tr>
			<tr>
				<th><label for="tacnav-object-visibility"><?php esc_html_e( 'Visibility', 'tacnav-devices' ); ?></label></th>
				<td>
					<select id="tacnav-object-visibility" name="visibility">
						<option value="team" <?php selected( (string) $tacnav_reference['visibility'], 'team' ); ?>><?php esc_html_e( 'Own team', 'tacnav-devices' ); ?></option>
						<option value="all" <?php selected( (string) $tacnav_reference['visibility'], 'all' ); ?>><?php esc_html_e( 'Everyone', 'tacnav-devices' ); ?></option>
					</select>
					<p class="description"><?php esc_html_e( 'With a random area, opponents see only the area whatever this says.', 'tacnav-devices' ); ?></p>
				</td>
			</tr>
			<tr>
				<th><label for="tacnav-object-area"><?php esc_html_e( 'Random area', 'tacnav-devices' ); ?></label></th>
				<td>
					<label><input id="tacnav-object-area" name="random_area_enabled" type="checkbox" value="1" <?php checked( ! empty( $tacnav_reference['random_area_enabled'] ) ); ?>> <?php esc_html_e( 'Show everyone a circle around the object, not centred on it.', 'tacnav-devices' ); ?></label><br>
					<label for="tacnav-object-area-radius"><?php esc_html_e( 'Radius (m)', 'tacnav-devices' ); ?></label>
					<input id="tacnav-object-area-radius" name="random_area_radius_m" type="number" min="0" step="0.1" value="<?php echo esc_attr( $tacnav_number( 'random_area_radius_m' ) ); ?>">
				</td>
			</tr>
			<tr>
				<th><label for="tacnav-object-gps"><?php esc_html_e( 'GPS accuracy limit (m)', 'tacnav-devices' ); ?></label></th>
				<td>
					<input id="tacnav-object-gps" name="max_location_accuracy_m" type="number" min="0.1" step="0.1" value="<?php echo esc_attr( (string) $tacnav_reference['max_location_accuracy_m'] ); ?>">
					<p class="description"><?php esc_html_e( 'The phone cannot activate while its accuracy is worse than this.', 'tacnav-devices' ); ?></p>
				</td>
			</tr>
			<tr>
				<th><label for="tacnav-object-deactivate"><?php esc_html_e( 'Automatic deactivation (m)', 'tacnav-devices' ); ?></label></th>
				<td>
					<input id="tacnav-object-deactivate" name="auto_deactivate_m" type="number" min="0" step="0.1" value="<?php echo esc_attr( $tacnav_number( 'auto_deactivate_m' ) ); ?>">
					<p class="description"><?php esc_html_e( 'Deactivates after moving this far. Empty turns it off. Must be at least twice the GPS accuracy limit, or GPS drift causes false triggers.', 'tacnav-devices' ); ?></p>
				</td>
			</tr>
			<tr>
				<th><label for="tacnav-object-auto"><?php esc_html_e( 'Automatic activation', 'tacnav-devices' ); ?></label></th>
				<td>
					<label><input id="tacnav-object-auto" name="auto_activate_enabled" type="checkbox" value="1" <?php checked( ! empty( $tacnav_reference['auto_activate_enabled'] ) ); ?>> <?php esc_html_e( 'Activate after the phone stays still.', 'tacnav-devices' ); ?></label><br>
					<label for="tacnav-object-auto-seconds"><?php esc_html_e( 'Still seconds', 'tacnav-devices' ); ?></label>
					<input id="tacnav-object-auto-seconds" name="auto_activate_seconds" type="number" min="0" step="1" value="<?php echo esc_attr( (string) (int) $tacnav_reference['auto_activate_seconds'] ); ?>">
				</td>
			</tr>
			<tr>
				<th><label for="tacnav-object-downtime"><?php esc_html_e( 'Downtime allowance (minutes)', 'tacnav-devices' ); ?></label></th>
				<td>
					<input id="tacnav-object-downtime" name="max_downtime_minutes" type="number" min="0" step="1" value="<?php echo esc_attr( $tacnav_number( 'max_downtime_minutes' ) ); ?>">
					<p class="description"><?php esc_html_e( 'After the first activation, the phone counts down whenever the object is off and is destroyed at zero. Empty turns it off.', 'tacnav-devices' ); ?></p>
				</td>
			</tr>
			<tr>
				<th><label for="tacnav-object-reset"><?php esc_html_e( 'Downtime reset (minutes)', 'tacnav-devices' ); ?></label></th>
				<td>
					<input id="tacnav-object-reset" name="downtime_reset_minutes" type="number" min="0" step="1" value="<?php echo esc_attr( null === $tacnav_reference['downtime_reset_minutes'] ? '' : (string) (int) $tacnav_reference['downtime_reset_minutes'] ); ?>">
					<p class="description"><?php esc_html_e( 'Unbroken active time that restores the full allowance. Shorter activity only pauses the countdown. Empty uses the allowance value; 0 restores it at once.', 'tacnav-devices' ); ?></p>
				</td>
			</tr>
			<tr>
				<th><label for="tacnav-object-pin"><?php esc_html_e( 'Settings PIN', 'tacnav-devices' ); ?></label></th>
				<td>
					<input id="tacnav-object-pin" name="settings_pin" inputmode="numeric" pattern="[0-9]{4,}" value="<?php echo esc_attr( (string) $tacnav_reference['settings_pin'] ); ?>">
					<p class="description"><?php esc_html_e( 'Set a game PIN. Anyone who knows the default 4321 can reset a destroyed object and change its settings.', 'tacnav-devices' ); ?></p>
				</td>
			</tr>
		</table>
		<?php submit_button( $tacnav_draft ? __( 'Update application', 'tacnav-devices' ) : __( 'Save application', 'tacnav-devices' ) ); ?>
	</form>

	<h2><?php esc_html_e( 'Existing applications', 'tacnav-devices' ); ?></h2>
	<table class="widefat striped">
		<thead><tr><th><?php esc_html_e( 'ID', 'tacnav-devices' ); ?></th><th><?php esc_html_e( 'Name', 'tacnav-devices' ); ?></th><th><?php esc_html_e( 'Actions', 'tacnav-devices' ); ?></th></tr></thead>
		<tbody>
		<?php foreach ( $applications as $tacnav_application ) : ?>
			<tr>
				<td><?php echo esc_html( (string) $tacnav_application['id'] ); ?></td>
				<td><?php echo esc_html( (string) $tacnav_application['name'] ); ?></td>
				<td>
					<a class="button button-small" href="<?php echo esc_url( add_query_arg( 'edit', (int) $tacnav_application['id'], $tacnav_edit_url ) ); ?>"><?php esc_html_e( 'Edit', 'tacnav-devices' ); ?></a>
					<form class="tacnav-inline-form" method="post" action="<?php echo esc_url( $action ); ?>">
						<input type="hidden" name="action" value="tacnav_devices_create_activation"><input type="hidden" name="id" value="<?php echo esc_attr( (string) $tacnav_application['id'] ); ?>">
						<?php wp_nonce_field( 'tacnav_devices_create_activation' ); ?>
						<?php submit_button( __( 'Activation handoff', 'tacnav-devices' ), 'secondary small', 'submit', false ); ?>
					</form>
					<form class="tacnav-inline-form" method="post" action="<?php echo esc_url( $action ); ?>">
						<input type="hidden" name="action" value="tacnav_devices_revoke"><input type="hidden" name="app" value="object"><input type="hidden" name="id" value="<?php echo esc_attr( (string) $tacnav_application['id'] ); ?>">
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
		<input type="hidden" name="action" value="tacnav_devices_save_release"><input type="hidden" name="app" value="object"><?php wp_nonce_field( 'tacnav_devices_save_release' ); ?>
		<table class="form-table">
			<tr><th><?php esc_html_e( 'Package', 'tacnav-devices' ); ?></th><td><code><?php echo esc_html( $release['package_id'] ); ?></code></td></tr>
			<tr><th><label for="tacnav-object-release-version"><?php esc_html_e( 'Version', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-object-release-version" name="version" value="<?php echo esc_attr( $release['version'] ); ?>" placeholder="0.1.0"></td></tr>
			<tr>
				<th><label for="tacnav-object-release-apk"><?php esc_html_e( 'APK file', 'tacnav-devices' ); ?></label></th>
				<td>
					<input id="tacnav-object-release-apk" name="apk" type="file" accept=".apk,application/vnd.android.package-archive">
					<?php if ( \TacNav\Devices\Release::has_stored_apk( 'object' ) ) : ?>
						<p class="description"><?php esc_html_e( 'A package is already hosted. Uploading another file replaces it.', 'tacnav-devices' ); ?></p>
					<?php endif; ?>
				</td>
			</tr>
			<tr><th><label for="tacnav-object-release-url"><?php esc_html_e( 'APK URL', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-object-release-url" class="large-text" type="url" name="download_url" value="<?php echo esc_attr( $release['download_url'] ); ?>"></td></tr>
		</table>
		<?php submit_button( __( 'Publish release', 'tacnav-devices' ) ); ?>
	</form>
	<?php if ( '' !== $release['download_url'] ) : ?>
		<p>
			<a class="button button-primary" href="<?php echo esc_url( $release['download_url'] ); ?>"><?php esc_html_e( 'Download APK', 'tacnav-devices' ); ?></a>
			<a class="button" href="<?php echo esc_url( \TacNav\Devices\Release::public_page_url( 'object' ) ); ?>"><?php esc_html_e( 'Open public download page', 'tacnav-devices' ); ?></a>
		</p>
	<?php endif; ?>
