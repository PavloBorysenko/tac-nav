( function () {
	'use strict';

	const target = document.getElementById( 'tacnav-activation-qr' );
	if ( ! target || typeof window.QRCode === 'undefined' ) {
		return;
	}

	// The vendored QR library renders locally; the activation secret is never sent elsewhere.
	new window.QRCode( target, {
		text: target.dataset.payload,
		width: 256,
		height: 256,
	} );
} )();
