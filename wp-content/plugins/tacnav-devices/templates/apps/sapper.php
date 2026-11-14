<?php
/**
 * Sapper application administration.
 *
 * @package TacNav_Devices
 *
 * @var array<int, array<string, mixed>> $applications     Application rows.
 * @var string                           $action           Form target.
 * @var array<string, string>            $release          Android release metadata.
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
$tacnav_edit_url     = admin_url( \TacNav\Devices\Admin::page_query( 'sapper' ) );
$tacnav_numbers      = array(
	array( 'detect_radius_m', __( 'Detection radius (m)', 'tacnav-devices' ), '0.1', __( 'One beep when a minefield is this close inside the heading sector, two beeps at half this distance.', 'tacnav-devices' ) ),
	array( 'detect_sector_deg', __( 'Heading sector (degrees)', 'tacnav-devices' ), '1', __( 'Width of the cone in front of the phone, from 1 to 360.', 'tacnav-devices' ) ),
	array( 'mark_hold_seconds', __( 'Mark minefield hold (seconds)', 'tacnav-devices' ), '1', __( 'Holding inside another team’s minefield for this long shows it to the sapper’s team.', 'tacnav-devices' ) ),
	array( 'clear_hold_seconds', __( 'Clear mines hold (seconds)', 'tacnav-devices' ), '1', __( 'Total hold time that removes a minefield.', 'tacnav-devices' ) ),
	array( 'lay_hold_seconds', __( 'Lay mines hold (seconds)', 'tacnav-devices' ), '1', __( 'Total hold time that places a minefield.', 'tacnav-devices' ) ),
	array( 'minefield_length_m', __( 'Minefield length (m)', 'tacnav-devices' ), '0.1', __( 'Along the phone top when laying starts.', 'tacnav-devices' ) ),
	array( 'minefield_width_m', __( 'Minefield width (m)', 'tacnav-devices' ), '0.1', __( 'Across the phone top.', 'tacnav-devices' ) ),
	array( 'max_minefields', __( 'Minefields per activation', 'tacnav-devices' ), '1', __( '0 means unlimited.', 'tacnav-devices' ) ),
	array( 'max_no_mines_markers', __( 'No mines markers per activation', 'tacnav-devices' ), '1', __( '0 means unlimited.', 'tacnav-devices' ) ),
	array( 'max_location_accuracy_m', __( 'GPS accuracy limit (m)', 'tacnav-devices' ), '0.1', __( 'Laying, marking, clearing, and No mines wait for a fix at least this accurate.', 'tacnav-devices' ) ),
);
$tacnav_icon_fields  = array(
	array( 'minefield_icon_id', __( 'Minefield icon', 'tacnav-devices' ) ),
	array( 'no_mines_icon_id', __( 'No mines icon', 'tacnav-devices' ) ),
);
?>
	<h2><?php echo $tacnav_draft ? esc_html__( 'Edit application definition', 'tacnav-devices' ) : esc_html__( 'Application definition', 'tacnav-devices' ); ?></h2>
	<p><?php esc_html_e( 'These values become the phone’s starting settings at the next activation. Later changes under the PIN stay on that phone.', 'tacnav-devices' ); ?></p>
	<form method="post" action="<?php echo esc_url( $action ); ?>">
		<input type="hidden" name="action" value="tacnav_devices_save_application">
		<input type="hidden" name="application_type" value="sapper">
		<?php wp_nonce_field( 'tacnav_devices_save_application' ); ?>
		<?php if ( $tacnav_draft ) : ?>
			<input type="hidden" name="id" value="<?php echo esc_attr( (string) $tacnav_draft['id'] ); ?>">
		<?php endif; ?>
		<table class="form-table">
			<tr><th><label for="tacnav-sapper-app-name"><?php esc_html_e( 'Application name', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-sapper-app-name" name="name" value="<?php echo esc_attr( $tacnav_draft ? (string) $tacnav_draft['name'] : '' ); ?>" required></td></tr>
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
				<th><label for="tacnav-sapper-default-map"><?php esc_html_e( 'Default map', 'tacnav-devices' ); ?></label></th>
				<td>
					<select id="tacnav-sapper-default-map" name="default_map_id">
						<option value=""><?php esc_html_e( 'Select a map', 'tacnav-devices' ); ?></option>
						<?php foreach ( $tacnav_maps as $tacnav_map ) : ?>
							<option value="<?php echo esc_attr( (string) (int) $tacnav_map['id'] ); ?>" <?php selected( $tacnav_default_map, (int) $tacnav_map['id'] ); ?>><?php echo esc_html( (string) $tacnav_map['title'] ); ?></option>
						<?php endforeach; ?>
					</select>
				</td>
			</tr>
			<tr>
				<th><label for="tacnav-sapper-default-team"><?php esc_html_e( 'Default team', 'tacnav-devices' ); ?></label></th>
				<td>
					<select id="tacnav-sapper-default-team" name="default_team_id">
						<option value=""><?php esc_html_e( 'Select a team', 'tacnav-devices' ); ?></option>
						<?php foreach ( $tacnav_teams as $tacnav_team ) : ?>
							<option value="<?php echo esc_attr( (string) (int) $tacnav_team['id'] ); ?>" <?php selected( $tacnav_default_team, (int) $tacnav_team['id'] ); ?>><?php echo esc_html( (string) $tacnav_team['title'] ); ?></option>
						<?php endforeach; ?>
					</select>
				</td>
			</tr>
			<?php foreach ( $tacnav_numbers as $tacnav_field ) : ?>
				<tr>
					<th><label for="tacnav-sapper-<?php echo esc_attr( $tacnav_field[0] ); ?>"><?php echo esc_html( $tacnav_field[1] ); ?></label></th>
					<td>
						<input id="tacnav-sapper-<?php echo esc_attr( $tacnav_field[0] ); ?>" name="<?php echo esc_attr( $tacnav_field[0] ); ?>" type="number" min="0" step="<?php echo esc_attr( $tacnav_field[2] ); ?>" value="<?php echo esc_attr( (string) $tacnav_reference[ $tacnav_field[0] ] ); ?>">
						<p class="description"><?php echo esc_html( $tacnav_field[3] ); ?></p>
					</td>
				</tr>
			<?php endforeach; ?>
			<?php foreach ( $tacnav_icon_fields as $tacnav_field ) : ?>
				<tr>
					<th><label for="tacnav-sapper-<?php echo esc_attr( $tacnav_field[0] ); ?>"><?php echo esc_html( $tacnav_field[1] ); ?></label></th>
					<td>
						<select id="tacnav-sapper-<?php echo esc_attr( $tacnav_field[0] ); ?>" name="<?php echo esc_attr( $tacnav_field[0] ); ?>">
							<option value="0"><?php esc_html_e( 'Default marker', 'tacnav-devices' ); ?></option>
							<?php foreach ( $tacnav_icons as $tacnav_icon ) : ?>
								<option value="<?php echo esc_attr( (string) (int) $tacnav_icon['id'] ); ?>" <?php selected( (int) $tacnav_reference[ $tacnav_field[0] ], (int) $tacnav_icon['id'] ); ?>><?php echo esc_html( (string) $tacnav_icon['title'] ); ?></option>
							<?php endforeach; ?>
						</select>
					</td>
				</tr>
			<?php endforeach; ?>
			<tr>
				<th><label for="tacnav-sapper-pin"><?php esc_html_e( 'Settings PIN', 'tacnav-devices' ); ?></label></th>
				<td>
					<input id="tacnav-sapper-pin" name="settings_pin" inputmode="numeric" pattern="[0-9]{4,}" value="<?php echo esc_attr( (string) $tacnav_reference['settings_pin'] ); ?>">
					<p class="description"><?php esc_html_e( 'Set a game PIN. Anyone who knows the default 4321 can change the settings and reset the connection.', 'tacnav-devices' ); ?></p>
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
						<input type="hidden" name="action" value="tacnav_devices_revoke"><input type="hidden" name="app" value="sapper"><input type="hidden" name="id" value="<?php echo esc_attr( (string) $tacnav_application['id'] ); ?>">
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
		<input type="hidden" name="action" value="tacnav_devices_save_release"><input type="hidden" name="app" value="sapper"><?php wp_nonce_field( 'tacnav_devices_save_release' ); ?>
		<table class="form-table">
			<tr><th><?php esc_html_e( 'Package', 'tacnav-devices' ); ?></th><td><code><?php echo esc_html( $release['package_id'] ); ?></code></td></tr>
			<tr><th><label for="tacnav-sapper-release-version"><?php esc_html_e( 'Version', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-sapper-release-version" name="version" value="<?php echo esc_attr( $release['version'] ); ?>" placeholder="0.1.0"></td></tr>
			<tr>
				<th><label for="tacnav-sapper-release-apk"><?php esc_html_e( 'APK file', 'tacnav-devices' ); ?></label></th>
				<td>
					<input id="tacnav-sapper-release-apk" name="apk" type="file" accept=".apk,application/vnd.android.package-archive">
					<?php if ( \TacNav\Devices\Release::has_stored_apk( 'sapper' ) ) : ?>
						<p class="description"><?php esc_html_e( 'A package is already hosted. Uploading another file replaces it.', 'tacnav-devices' ); ?></p>
					<?php endif; ?>
				</td>
			</tr>
			<tr><th><label for="tacnav-sapper-release-url"><?php esc_html_e( 'APK URL', 'tacnav-devices' ); ?></label></th><td><input id="tacnav-sapper-release-url" class="large-text" type="url" name="download_url" value="<?php echo esc_attr( $release['download_url'] ); ?>"></td></tr>
		</table>
		<?php submit_button( __( 'Publish release', 'tacnav-devices' ) ); ?>
	</form>
	<?php if ( '' !== $release['download_url'] ) : ?>
		<p>
			<a class="button button-primary" href="<?php echo esc_url( $release['download_url'] ); ?>"><?php esc_html_e( 'Download APK', 'tacnav-devices' ); ?></a>
			<a class="button" href="<?php echo esc_url( \TacNav\Devices\Release::public_page_url( 'sapper' ) ); ?>"><?php esc_html_e( 'Open public download page', 'tacnav-devices' ); ?></a>
		</p>
	<?php endif; ?>
