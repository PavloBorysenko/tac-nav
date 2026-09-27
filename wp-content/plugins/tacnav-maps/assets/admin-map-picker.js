( function () {
	'use strict';

	if ( ! window.L ) {
		return;
	}

	var el = document.getElementById( 'tacnav-map-picker' );
	var latInput = document.getElementById( 'tacnav_center_lat' );
	var lngInput = document.getElementById( 'tacnav_center_lng' );
	var zoomInput = document.getElementById( 'tacnav_zoom' );
	var baseSelect = document.getElementById( 'tacnav_basemap' );
	if ( ! el || ! latInput || ! lngInput || ! zoomInput ) {
		return;
	}

	var lat = parseFloat( latInput.value );
	var lng = parseFloat( lngInput.value );
	if ( isNaN( lat ) || isNaN( lng ) || ( 0 === lat && 0 === lng ) ) {
		lat = 50.4501;
		lng = 30.5234;
	}
	var zoom = parseInt( zoomInput.value, 10 ) || 13;

	var map = window.L.map( el, { maxZoom: 22 } ).setView( [ lat, lng ], zoom );
	var satellite = window.L.tileLayer(
		'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
		{ attribution: 'Tiles &copy; Esri', maxZoom: 22, maxNativeZoom: 19 }
	);
	var labels = window.L.tileLayer(
		'https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}',
		{ attribution: 'Tiles &copy; Esri', maxZoom: 22, maxNativeZoom: 19 }
	);
	satellite.addTo( map );

	function syncLabels() {
		var show = baseSelect && 'satellite-labels' === baseSelect.value;
		if ( show && ! map.hasLayer( labels ) ) {
			labels.addTo( map );
		}
		if ( ! show && map.hasLayer( labels ) ) {
			map.removeLayer( labels );
		}
	}

	function persist() {
		var center = map.getCenter();
		latInput.value = center.lat.toFixed( 6 );
		lngInput.value = center.lng.toFixed( 6 );
		zoomInput.value = String( map.getZoom() );
	}

	syncLabels();
	if ( baseSelect ) {
		baseSelect.addEventListener( 'change', syncLabels );
	}
	map.on( 'moveend', persist );
	map.on( 'zoomend', persist );
	persist();
	window.setTimeout( function () {
		map.invalidateSize();
	}, 250 );
}() );
