<?php
/**
 * Icon availability meta box.
 *
 * @package TacNav_Maps
 *
 * @var string $value Availability.
 */

defined( 'ABSPATH' ) || exit;

$tacnav_choices = \TacNav\Maps\Catalog::availability_choices();
?>
<p><?php esc_html_e( 'Set a featured image as the icon file.', 'tacnav-maps' ); ?></p>
<p>
	<label for="tacnav_availability"><?php esc_html_e( 'Availability', 'tacnav-maps' ); ?></label><br />
	<select id="tacnav_availability" name="tacnav_availability">
		<?php foreach ( $tacnav_choices as $tacnav_key => $tacnav_label ) : ?>
			<option value="<?php echo esc_attr( $tacnav_key ); ?>" <?php selected( $value, $tacnav_key ); ?>><?php echo esc_html( $tacnav_label ); ?></option>
		<?php endforeach; ?>
	</select>
</p>
