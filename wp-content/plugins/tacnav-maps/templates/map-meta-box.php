<?php
/**
 * Map passport meta box.
 *
 * @package TacNav_Maps
 *
 * @var WP_Post $post Post.
 * @var mixed   $lat Latitude.
 * @var mixed   $lng Longitude.
 * @var mixed   $zoom Zoom.
 * @var string  $basemap Basemap key.
 * @var string               $canvas Canvas URL.
 * @var array<int, int>      $team_ids Teams saved on this map.
 * @var array<int, array<string, mixed>> $teams Catalog teams.
 */

defined( 'ABSPATH' ) || exit;
?>
<p><?php esc_html_e( 'Pan and zoom this preview. The saved view is the center and scale players will open.', 'tacnav-maps' ); ?></p>
<div id="tacnav-map-picker" class="tacnav-map-picker"></div>
<input type="hidden" id="tacnav_center_lat" name="tacnav_center_lat" value="<?php echo esc_attr( (string) $lat ); ?>" />
<input type="hidden" id="tacnav_center_lng" name="tacnav_center_lng" value="<?php echo esc_attr( (string) $lng ); ?>" />
<input type="hidden" id="tacnav_zoom" name="tacnav_zoom" value="<?php echo esc_attr( $zoom ? (string) $zoom : '13' ); ?>" />
<p>
	<label for="tacnav_basemap"><?php esc_html_e( 'Default overlay', 'tacnav-maps' ); ?></label><br />
	<select id="tacnav_basemap" name="tacnav_basemap">
		<option value="satellite" <?php selected( $basemap, 'satellite' ); ?>><?php esc_html_e( 'Satellite only', 'tacnav-maps' ); ?></option>
		<option value="satellite-labels" <?php selected( $basemap, 'satellite-labels' ); ?>><?php esc_html_e( 'Satellite with place names', 'tacnav-maps' ); ?></option>
	</select>
</p>
<?php if ( 'auto-draft' !== $post->post_status ) : ?>
	<p><a class="button button-primary" href="<?php echo esc_url( $canvas ); ?>"><?php esc_html_e( 'Open map canvas', 'tacnav-maps' ); ?></a></p>
<?php endif; ?>
<h3><?php esc_html_e( 'Teams on this map', 'tacnav-maps' ); ?></h3>
<?php if ( array() === $teams ) : ?>
	<p><?php esc_html_e( 'Create a team before adding it to this map.', 'tacnav-maps' ); ?></p>
<?php else : ?>
	<ul class="tacnav-map-teams">
		<?php foreach ( $teams as $tacnav_team ) : ?>
			<li>
				<label>
					<input type="checkbox" name="tacnav_team_ids[]" value="<?php echo esc_attr( (string) $tacnav_team['id'] ); ?>" <?php checked( in_array( (int) $tacnav_team['id'], $team_ids, true ) ); ?> />
					<?php echo esc_html( $tacnav_team['title'] ); ?>
				</label>
				<?php if ( 'publish' === $post->post_status && in_array( (int) $tacnav_team['id'], $team_ids, true ) ) : ?>
					<a href="<?php echo esc_url( \TacNav\Maps\Guest_Link::page_url( (int) $post->ID, (int) $tacnav_team['id'] ) ); ?>" target="_blank" rel="noopener noreferrer"><?php esc_html_e( 'Guest link', 'tacnav-maps' ); ?></a>
				<?php endif; ?>
			</li>
		<?php endforeach; ?>
	</ul>
<?php endif; ?>
