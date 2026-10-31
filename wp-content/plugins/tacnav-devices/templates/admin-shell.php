<?php
/**
 * Device applications page wrapper and tab bar.
 *
 * @package TacNav_Devices
 *
 * @var array<int, \TacNav\Devices\Apps\App_Page> $tacnav_pages   Application pages.
 * @var \TacNav\Devices\Apps\App_Page             $tacnav_current Selected page.
 */

defined( 'ABSPATH' ) || exit;

$tacnav_page_url = admin_url( 'edit.php?post_type=tacnav_map&page=' . \TacNav\Devices\Admin::PAGE );
?>
<div class="wrap">
	<h1><?php esc_html_e( 'TacNav device applications', 'tacnav-devices' ); ?></h1>
	<nav class="nav-tab-wrapper" aria-label="<?php esc_attr_e( 'Device applications', 'tacnav-devices' ); ?>">
		<?php foreach ( $tacnav_pages as $tacnav_page ) : ?>
			<?php $tacnav_is_current = $tacnav_page->slug() === $tacnav_current->slug(); ?>
			<a class="nav-tab<?php echo $tacnav_is_current ? ' nav-tab-active' : ''; ?>" href="<?php echo esc_url( add_query_arg( 'app', $tacnav_page->slug(), $tacnav_page_url ) ); ?>"<?php echo $tacnav_is_current ? ' aria-current="page"' : ''; ?>><?php echo esc_html( $tacnav_page->label() ); ?></a>
		<?php endforeach; ?>
	</nav>
	<?php $tacnav_current->render(); ?>
</div>
