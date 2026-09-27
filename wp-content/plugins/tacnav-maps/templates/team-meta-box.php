<?php
/**
 * Team setup meta box.
 *
 * @package TacNav_Maps
 *
 * @var string             $color Team color.
 * @var array<int, int>    $palette Palette icon IDs.
 * @var array<int, mixed>  $presets Quick-add presets.
 * @var array<int, mixed>  $icons Icon catalog.
 * @var array<int, string> $kinds Geometry kinds.
 * @var array<int, int>    $selected Player IDs on this team.
 * @var array<int, \WP_User> $users Players.
 */

defined( 'ABSPATH' ) || exit;
?>
<p><?php esc_html_e( 'Set the team badge with the Featured image box. That image is the unique team icon.', 'tacnav-maps' ); ?></p>
<p>
	<label for="tacnav_color"><?php esc_html_e( 'Color', 'tacnav-maps' ); ?></label><br />
	<input type="color" id="tacnav_color" name="tacnav_color" value="<?php echo esc_attr( $color ); ?>" />
</p>
<h3><?php esc_html_e( 'Icon palette', 'tacnav-maps' ); ?></h3>
<p><?php esc_html_e( 'Icons members of this team may use on the canvas.', 'tacnav-maps' ); ?></p>
<div class="tacnav-palette-grid">
	<?php foreach ( $icons as $tacnav_icon ) : ?>
		<label class="tacnav-palette-item">
			<input type="checkbox" name="tacnav_palette_icon_ids[]" value="<?php echo esc_attr( (string) $tacnav_icon['id'] ); ?>" <?php checked( in_array( (int) $tacnav_icon['id'], $palette, true ) ); ?> />
			<?php if ( ! empty( $tacnav_icon['url'] ) ) : ?>
				<img src="<?php echo esc_url( $tacnav_icon['url'] ); ?>" alt="" width="32" height="32" />
			<?php else : ?>
				<span class="tacnav-palette-fallback">●</span>
			<?php endif; ?>
			<span><?php echo esc_html( $tacnav_icon['title'] ); ?></span>
		</label>
	<?php endforeach; ?>
</div>
<h3><?php esc_html_e( 'Quick-add presets', 'tacnav-maps' ); ?></h3>
<p><?php esc_html_e( 'All fields except coordinates. Default time to live is 3 minutes. Used by team members in a later slice.', 'tacnav-maps' ); ?></p>
<table class="widefat">
	<thead>
		<tr>
			<th><?php esc_html_e( 'Kind', 'tacnav-maps' ); ?></th>
			<th><?php esc_html_e( 'Icon', 'tacnav-maps' ); ?></th>
			<th><?php esc_html_e( 'Title', 'tacnav-maps' ); ?></th>
			<th><?php esc_html_e( 'TTL minutes', 'tacnav-maps' ); ?></th>
			<th><?php esc_html_e( 'Description', 'tacnav-maps' ); ?></th>
		</tr>
	</thead>
	<tbody>
	<?php
	for ( $tacnav_i = 0; $tacnav_i < 6; $tacnav_i++ ) {
		$tacnav_row = isset( $presets[ $tacnav_i ] ) && is_array( $presets[ $tacnav_i ] ) ? $presets[ $tacnav_i ] : array();
		$tacnav_ttl = array_key_exists( 'ttl_minutes', $tacnav_row ) ? (string) $tacnav_row['ttl_minutes'] : '3';
		?>
		<tr>
			<td>
				<select name="tacnav_presets[<?php echo esc_attr( (string) $tacnav_i ); ?>][kind]">
					<option value=""><?php esc_html_e( '—', 'tacnav-maps' ); ?></option>
					<?php foreach ( $kinds as $tacnav_kind ) : ?>
						<option value="<?php echo esc_attr( $tacnav_kind ); ?>" <?php selected( isset( $tacnav_row['kind'] ) ? $tacnav_row['kind'] : '', $tacnav_kind ); ?>><?php echo esc_html( $tacnav_kind ); ?></option>
					<?php endforeach; ?>
				</select>
			</td>
			<td>
				<select name="tacnav_presets[<?php echo esc_attr( (string) $tacnav_i ); ?>][icon_id]">
					<option value="0"><?php esc_html_e( 'Default', 'tacnav-maps' ); ?></option>
					<?php foreach ( $icons as $tacnav_icon ) : ?>
						<option value="<?php echo esc_attr( (string) $tacnav_icon['id'] ); ?>" <?php selected( isset( $tacnav_row['icon_id'] ) ? (int) $tacnav_row['icon_id'] : 0, (int) $tacnav_icon['id'] ); ?>><?php echo esc_html( $tacnav_icon['title'] ); ?></option>
					<?php endforeach; ?>
				</select>
			</td>
			<td><input type="text" name="tacnav_presets[<?php echo esc_attr( (string) $tacnav_i ); ?>][title]" value="<?php echo esc_attr( isset( $tacnav_row['title'] ) ? (string) $tacnav_row['title'] : '' ); ?>" /></td>
			<td><input type="number" min="0" name="tacnav_presets[<?php echo esc_attr( (string) $tacnav_i ); ?>][ttl_minutes]" value="<?php echo esc_attr( $tacnav_ttl ); ?>" /></td>
			<td><input type="text" name="tacnav_presets[<?php echo esc_attr( (string) $tacnav_i ); ?>][description]" value="<?php echo esc_attr( isset( $tacnav_row['description'] ) ? (string) $tacnav_row['description'] : '' ); ?>" /></td>
		</tr>
		<?php
	}
	?>
	</tbody>
</table>
<h3><?php esc_html_e( 'Players', 'tacnav-maps' ); ?></h3>
<p><?php esc_html_e( 'A player belongs to one team. Choosing them here moves them from any other team.', 'tacnav-maps' ); ?></p>
<?php if ( array() === $users ) : ?>
	<p><?php esc_html_e( 'No players have registered yet.', 'tacnav-maps' ); ?></p>
<?php else : ?>
	<?php
	$tacnav_selected_count = 0;
	foreach ( $users as $tacnav_user ) {
		if ( in_array( (int) $tacnav_user->ID, $selected, true ) ) {
			++$tacnav_selected_count;
		}
	}
	/* translators: %d: number of selected players. */
	$tacnav_count_label = __( 'Selected: %d', 'tacnav-maps' );
	?>
	<div class="tacnav-roster" data-tacnav-roster>
		<p class="tacnav-roster-count" data-tacnav-roster-count data-tacnav-count-label="<?php echo esc_attr( $tacnav_count_label ); ?>">
			<?php echo esc_html( sprintf( $tacnav_count_label, $tacnav_selected_count ) ); ?>
		</p>
		<p class="tacnav-roster-filter">
			<label for="tacnav-roster-search"><?php esc_html_e( 'Filter players', 'tacnav-maps' ); ?></label>
			<input type="search" id="tacnav-roster-search" class="regular-text" data-tacnav-roster-search />
			<button type="button" class="button" data-tacnav-roster-reset><?php esc_html_e( 'Reset', 'tacnav-maps' ); ?></button>
		</p>
		<p class="tacnav-roster-empty" data-tacnav-roster-empty hidden><?php esc_html_e( 'No players match.', 'tacnav-maps' ); ?></p>
		<ul class="tacnav-player-list">
			<?php foreach ( $users as $tacnav_user ) : ?>
				<?php
				$tacnav_checked = in_array( (int) $tacnav_user->ID, $selected, true );
				$tacnav_avatar  = \TacNav\Maps\Membership::avatar_url( (int) $tacnav_user->ID );
				?>
				<li class="tacnav-roster-row<?php echo $tacnav_checked ? ' is-selected' : ''; ?>" data-tacnav-roster-row>
					<label>
						<input type="checkbox" name="tacnav_player_ids[]" value="<?php echo esc_attr( (string) $tacnav_user->ID ); ?>" <?php checked( $tacnav_checked ); ?> />
						<?php if ( '' !== $tacnav_avatar ) : ?>
							<img class="tacnav-roster-avatar" src="<?php echo esc_url( $tacnav_avatar ); ?>" alt="" width="32" height="32" />
						<?php else : ?>
							<span class="tacnav-roster-avatar tacnav-roster-avatar-empty" aria-hidden="true"></span>
						<?php endif; ?>
						<span class="tacnav-roster-name"><?php echo esc_html( $tacnav_user->display_name ); ?></span>
					</label>
				</li>
			<?php endforeach; ?>
		</ul>
	</div>
<?php endif; ?>
