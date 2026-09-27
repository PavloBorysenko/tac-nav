<?php
/**
 * Standalone map canvas.
 *
 * @package TacNav_Maps
 *
 * @var array<string, mixed> $map  Map passport.
 * @var string               $mode staff|player|guest.
 */

defined( 'ABSPATH' ) || exit;
?><!DOCTYPE html>
<html <?php language_attributes(); ?>>
<head>
	<meta charset="<?php bloginfo( 'charset' ); ?>" />
	<meta name="viewport" content="width=device-width, initial-scale=1" />
	<title><?php echo esc_html( $map['title'] ); ?></title>
	<?php wp_head(); ?>
</head>
<body class="tacnav-canvas-body">
	<div id="tacnav-canvas-app" class="tacnav-canvas-app" data-tacnav-canvas="1" data-tacnav-mode="<?php echo esc_attr( $mode ); ?>">
		<div id="tacnav-map" class="tacnav-map" data-tacnav-map="1"></div>
		<div class="tacnav-modebar" data-tacnav-modebar hidden></div>
		<div class="tacnav-maptools">
			<button type="button" class="tacnav-btn" data-tacnav-home></button>
			<button type="button" class="tacnav-btn" data-tacnav-locate></button>
			<?php if ( 'player' === $mode ) : ?>
				<button type="button" class="tacnav-btn" data-tacnav-mark></button>
			<?php endif; ?>
			<?php if ( 'staff' === $mode ) : ?>
				<button type="button" class="tacnav-btn" data-tacnav-live></button>
			<?php endif; ?>
		</div>
		<div class="tacnav-toolbar" data-tacnav-toolbar>
			<?php if ( 'guest' !== $mode ) : ?>
			<div class="tacnav-add-wrap">
				<button type="button" class="tacnav-btn" data-tacnav-add>+</button>
				<div class="tacnav-add-menu" data-tacnav-add-menu hidden></div>
			</div>
			<?php endif; ?>
			<button type="button" class="tacnav-btn" data-tacnav-tool="measure"></button>
			<button type="button" class="tacnav-btn" data-tacnav-tool="coords"></button>
			<button type="button" class="tacnav-btn" data-tacnav-titles></button>
			<?php if ( 'staff' === $mode ) : ?>
			<button type="button" class="tacnav-btn" data-tacnav-open="filters"></button>
			<button type="button" class="tacnav-btn" data-tacnav-open="layers"></button>
			<?php endif; ?>
		</div>
		<div class="tacnav-saving" data-tacnav-saving hidden><span class="tacnav-spinner" aria-hidden="true"></span></div>
		<aside class="tacnav-sheet" data-tacnav-sheet="filters" hidden></aside>
		<aside class="tacnav-sheet" data-tacnav-sheet="layers" hidden></aside>
		<aside class="tacnav-sheet" data-tacnav-sheet="inspector" hidden></aside>
		<aside class="tacnav-sheet" data-tacnav-sheet="popup" hidden></aside>
	</div>
	<?php wp_footer(); ?>
</body>
</html>
