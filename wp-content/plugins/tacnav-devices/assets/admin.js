( function () {
	'use strict';

	var target = document.getElementById( 'tacnav-activation-qr' );
	if ( ! target || typeof window.qrcode !== 'function' ) {
		return;
	}

	var payload = target.getAttribute( 'data-payload' ) || '';
	if ( ! payload ) {
		return;
	}

	// The vendored QR library renders locally; the activation secret is never sent elsewhere.
	var qr = window.qrcode( 0, 'M' );
	qr.addData( payload );
	qr.make();
	target.innerHTML = qr.createSvgTag( 4, 8 );
}() );
