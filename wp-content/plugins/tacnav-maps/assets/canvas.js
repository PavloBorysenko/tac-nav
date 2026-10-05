( function () {
	'use strict';

	var cfg = window.tacnavMapsCanvas;
	if ( ! cfg || ! window.L ) {
		return;
	}

	var strings = cfg.strings || {};
	var kinds = cfg.kinds || [];

	function escapeHtml( value ) {
		return String( value == null ? '' : value )
			.replace( /&/g, '&amp;' )
			.replace( /</g, '&lt;' )
			.replace( />/g, '&gt;' )
			.replace( /"/g, '&quot;' );
	}

	var mapEl = document.getElementById( 'tacnav-map' );
	var app = document.getElementById( 'tacnav-canvas-app' );
	if ( ! mapEl || ! app ) {
		return;
	}

	var ttlTimer = null;
	var state = {
		mode: 'pan',
		draftKind: '',
		formObject: null,
		vertices: [],
		vertexMarkers: [],
		preview: null,
		previewType: '',
		draftIcon: null,
		circleRadius: 0,
		measureStopped: false,
		coordMarker: null,
		showExpired: false,
		showLabels: ( function () {
			try {
				return window.sessionStorage.getItem( 'tacnav-show-labels' ) === '1';
			} catch ( err ) {
				return false;
			}
		}() ),
		preset: null,
		hiddenTeams: {},
		layers: {},
		objects: cfg.objects || [],
		saving: false,
		liveTimer: null,
		liveOn: false,
		quietPolls: 0,
		pictureRev: cfg.pictureRev == null ? '0' : String( cfg.pictureRev ),
		labelsOn: cfg.map.basemap === 'satellite-labels',
	};

	var center = [ cfg.map.center_lat || 0, cfg.map.center_lng || 0 ];
	var homeZoom = cfg.map.zoom || 13;
	var map = window.L.map( mapEl, {
		zoomControl: true,
		fadeAnimation: false,
		markerZoomAnimation: false,
		maxZoom: 22,
	} ).setView( center, homeZoom );

	var satellite = window.L.tileLayer(
		'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
		{ attribution: 'Tiles &copy; Esri', maxZoom: 22, maxNativeZoom: 19 }
	);
	var labels = window.L.tileLayer(
		'https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}',
		{ attribution: 'Tiles &copy; Esri', maxZoom: 22, maxNativeZoom: 19 }
	);
	satellite.addTo( map );
	if ( state.labelsOn ) {
		labels.addTo( map );
	}

	function teamById( id ) {
		return ( cfg.teams || [] ).find( function ( team ) {
			return Number( team.id ) === Number( id );
		} );
	}

	function iconById( id ) {
		return ( cfg.icons || [] ).find( function ( icon ) {
			return Number( icon.id ) === Number( id );
		} );
	}

	function teamColor( object ) {
		var team = object.owner_team_id ? teamById( object.owner_team_id ) : null;
		return team && team.color ? team.color : '#6b7280';
	}

	function isExpired( object ) {
		if ( ! object.expires_at ) {
			return false;
		}
		return Date.parse( object.expires_at.replace( ' ', 'T' ) + 'Z' ) <= Date.now();
	}

	function formatRemaining( object ) {
		if ( ! object.expires_at ) {
			return strings.forever;
		}
		var ms = Date.parse( object.expires_at.replace( ' ', 'T' ) + 'Z' ) - Date.now();
		if ( isNaN( ms ) || ms <= 0 ) {
			return strings.expired;
		}
		var total = Math.floor( ms / 1000 );
		var mins = Math.floor( total / 60 );
		var secs = total % 60;
		return strings.remaining + ': ' + mins + ' min ' + secs + ' s';
	}

	function objectVisibleOnCanvas( object ) {
		if ( isExpired( object ) && ! state.showExpired ) {
			return false;
		}
		if ( object.owner_team_id && state.hiddenTeams[ object.owner_team_id ] ) {
			return false;
		}
		if ( ! object.owner_team_id && state.hiddenTeams.neutral ) {
			return false;
		}
		return true;
	}

	function iconHtml( object ) {
		var color = teamColor( object );
		var icon = object.icon_id ? iconById( object.icon_id ) : null;
		var inner;
		if ( object.self_point && object.avatar_url ) {
			inner = '<img src="' + escapeHtml( object.avatar_url ) + '" alt="" style="width:22px;height:22px;border-radius:50%;" />';
		} else if ( icon && icon.url ) {
			inner = '<img src="' + escapeHtml( icon.url ) + '" alt="" style="width:22px;height:22px;border-radius:50%;" />';
		} else {
			inner = '<span style="color:' + escapeHtml( color ) + ';font-size:14px;">●</span>';
		}
		return '<div class="tacnav-halo-icon" style="width:34px;height:34px;border-radius:50%;border:3px solid ' + escapeHtml( color ) + ';background:rgba(255,255,255,0.22);">' + inner + '</div>';
	}

	function makeDivIcon( object ) {
		return window.L.divIcon( {
			className: 'tacnav-object-icon',
			html: iconHtml( object ),
			iconSize: [ 34, 34 ],
			iconAnchor: [ 17, 17 ],
		} );
	}

	function toLatLng( pt ) {
		if ( ! pt ) {
			return null;
		}
		if ( pt.lat != null ) {
			return window.L.latLng( pt.lat, pt.lng );
		}
		return window.L.latLng( pt[ 0 ], pt[ 1 ] );
	}

	function eastOf( origin, meters ) {
		var cos = Math.cos( origin.lat * Math.PI / 180 );
		var dLng = meters / ( 111320 * ( cos || 0.01 ) );
		return window.L.latLng( origin.lat, origin.lng + dLng );
	}

	function pathLatLngs( geom ) {
		return ( geom.path || [] ).map( toLatLng ).filter( Boolean );
	}

	function iconLatLng( object ) {
		var geom = object.geometry || {};
		if ( object.kind === 'marker' || object.kind === 'circle' ) {
			if ( geom.lat == null ) {
				return null;
			}
			return window.L.latLng( geom.lat, geom.lng );
		}
		var path = pathLatLngs( geom );
		if ( ! path.length ) {
			return null;
		}
		if ( object.kind === 'polygon' ) {
			return window.L.polygon( path ).getBounds().getCenter();
		}
		var total = 0;
		var i;
		for ( i = 1; i < path.length; i++ ) {
			total += path[ i - 1 ].distanceTo( path[ i ] );
		}
		if ( total <= 0 ) {
			return path[ 0 ];
		}
		var half = total / 2;
		var acc = 0;
		for ( i = 1; i < path.length; i++ ) {
			var dist = path[ i - 1 ].distanceTo( path[ i ] );
			if ( acc + dist >= half || i === path.length - 1 ) {
				var t = dist ? ( half - acc ) / dist : 0;
				return window.L.latLng(
					path[ i - 1 ].lat + ( path[ i ].lat - path[ i - 1 ].lat ) * t,
					path[ i - 1 ].lng + ( path[ i ].lng - path[ i - 1 ].lng ) * t
				);
			}
			acc += dist;
		}
		return path[ 0 ];
	}

	function rest( path, options ) {
		return window.fetch( cfg.restUrl + path, Object.assign( {
			headers: {
				'Content-Type': 'application/json',
				'X-WP-Nonce': cfg.nonce,
			},
			credentials: 'same-origin',
		}, options ) ).then( function ( res ) {
			return res.json().then( function ( body ) {
				if ( ! res.ok ) {
					throw body;
				}
				return body;
			} );
		} );
	}

	function clearObjectLayers() {
		Object.keys( state.layers ).forEach( function ( id ) {
			map.removeLayer( state.layers[ id ] );
		} );
		state.layers = {};
	}

	function styleFor( object ) {
		var color = teamColor( object );
		return {
			color: color,
			weight: 3,
			fillColor: color,
			fillOpacity: object.kind === 'polygon' || object.kind === 'circle' ? 0.25 : 0,
		};
	}

	function bindLayer( object, layer ) {
		layer.on( 'click', function ( event ) {
			window.L.DomEvent.stopPropagation( event );
			if ( state.mode !== 'pan' ) {
				handleMapClick( event );
				return;
			}
			openPopup( object );
		} );
	}

	function addObjectLayer( object ) {
		if ( ! objectVisibleOnCanvas( object ) ) {
			return;
		}
		if ( state.formObject && state.formObject.id && Number( object.id ) === Number( state.formObject.id ) ) {
			return;
		}
		var geom = object.geometry || {};
		var group = window.L.featureGroup();
		var shape = null;
		if ( object.kind === 'circle' ) {
			shape = window.L.circle( [ geom.lat, geom.lng ], Object.assign( { radius: geom.radius }, styleFor( object ) ) );
		} else if ( object.kind === 'polyline' ) {
			shape = window.L.polyline( geom.path || [], styleFor( object ) );
		} else if ( object.kind === 'polygon' ) {
			shape = window.L.polygon( geom.path || [], styleFor( object ) );
		}
		if ( shape ) {
			shape.addTo( group );
		}
		var pin = iconLatLng( object );
		if ( pin ) {
			var marker = window.L.marker( pin, { icon: makeDivIcon( object ) } );
			marker.addTo( group );
			if ( state.showLabels && object.title ) {
				marker.bindTooltip( escapeHtml( object.title ), { permanent: true, className: 'tacnav-label', direction: 'top', escape: false } );
			}
		}
		group.addTo( map );
		bindLayer( object, group );
		state.layers[ object.id ] = group;
	}

	function redrawObjects() {
		clearObjectLayers();
		state.objects.forEach( addObjectLayer );
	}

	function setModeBar( text, actions ) {
		var bar = app.querySelector( '[data-tacnav-modebar]' );
		if ( ! text ) {
			bar.hidden = true;
			bar.textContent = '';
			return;
		}
		bar.hidden = false;
		bar.textContent = '';
		var label = document.createElement( 'span' );
		label.textContent = text;
		bar.appendChild( label );
		( actions || [] ).forEach( function ( action ) {
			var btn = document.createElement( 'button' );
			btn.type = 'button';
			btn.className = 'tacnav-btn';
			btn.textContent = action.label;
			btn.addEventListener( 'click', action.onClick );
			bar.appendChild( btn );
		} );
	}

	function stopTtlClock() {
		if ( ttlTimer ) {
			window.clearInterval( ttlTimer );
			ttlTimer = null;
		}
	}

	function closeSheets() {
		stopTtlClock();
		app.querySelectorAll( '[data-tacnav-sheet]' ).forEach( function ( sheet ) {
			sheet.hidden = true;
			sheet.innerHTML = '';
		} );
	}

	function dismissSheet( name ) {
		if ( name === 'inspector' ) {
			cancelInspect();
			return;
		}
		closeSheets();
	}

	function openSheet( name, html ) {
		closeSheets();
		var sheet = app.querySelector( '[data-tacnav-sheet="' + name + '"]' );
		sheet.hidden = false;
		sheet.innerHTML = '<button type="button" class="tacnav-sheet-close" data-close-x aria-label="' + escapeHtml( strings.close || strings.cancel ) + '">×</button>' + html;
		var closeX = sheet.querySelector( '[data-close-x]' );
		if ( closeX ) {
			closeX.addEventListener( 'click', function () {
				dismissSheet( name );
			} );
		}
		return sheet;
	}

	function hideAddMenu() {
		var menu = app.querySelector( '[data-tacnav-add-menu]' );
		if ( menu ) {
			menu.hidden = true;
		}
	}

	function setSaving( on ) {
		state.saving = on;
		var overlay = app.querySelector( '[data-tacnav-saving]' );
		if ( overlay ) {
			overlay.hidden = ! on;
		}
		var btn = app.querySelector( '[data-save]' );
		if ( btn ) {
			btn.disabled = on;
			btn.textContent = on ? strings.saving : strings.save;
		}
		app.classList.toggle( 'is-saving', on );
	}

	function refreshDraftIcon( object ) {
		if ( state.draftIcon ) {
			map.removeLayer( state.draftIcon );
			state.draftIcon = null;
		}
		if ( ! object || object.kind === 'marker' ) {
			return;
		}
		var pin = iconLatLng( object );
		if ( ! pin ) {
			return;
		}
		state.draftIcon = window.L.marker( pin, { icon: makeDivIcon( object ), interactive: false } ).addTo( map );
	}

	function ensurePreview( type, factory ) {
		if ( state.preview && state.previewType !== type ) {
			map.removeLayer( state.preview );
			state.preview = null;
		}
		if ( ! state.preview ) {
			state.preview = factory().addTo( map );
			state.previewType = type;
		}
	}

	function syncPreview( mouseLatLng ) {
		var kind = state.draftKind;
		var verts = state.vertices;
		var follow = mouseLatLng && ! state.measureStopped;

		if ( state.mode === 'measure' ) {
			if ( ! verts.length ) {
				return;
			}
			var measurePts = follow ? verts.concat( [ mouseLatLng ] ) : verts.slice();
			ensurePreview( 'polyline', function () {
				return window.L.polyline( measurePts, { color: '#f59e0b', dashArray: '6 4', weight: 3, interactive: false } );
			} );
			state.preview.setLatLngs( measurePts );
			if ( ! state.measureStopped ) {
				setModeBar( strings.measure + ' ' + distanceLabel( chainDistance( measurePts ) ), measureActions() );
			}
			return;
		}

		if ( kind === 'circle' ) {
			if ( ! verts.length ) {
				return;
			}
			var edge = verts[ 1 ] || ( follow && state.mode === 'add-circle' ? mouseLatLng : null );
			if ( ! edge ) {
				return;
			}
			var radius = map.distance( verts[ 0 ], edge );
			state.circleRadius = radius;
			ensurePreview( 'circle', function () {
				return window.L.circle( verts[ 0 ], {
					radius: radius,
					color: '#2563eb',
					fillColor: '#6b7280',
					fillOpacity: 0.15,
					weight: 2,
					interactive: false,
				} );
			} );
			if ( typeof state.preview.setLatLng === 'function' ) {
				state.preview.setLatLng( verts[ 0 ] );
			}
			state.preview.setRadius( radius );
			if ( state.mode === 'add-circle' ) {
				setModeBar( strings.circle + ' ' + distanceLabel( radius ) + ' — ' + strings.tapRadius, drawActions() );
			}
			return;
		}

		if ( kind === 'polyline' || kind === 'polygon' ) {
			if ( ! verts.length ) {
				return;
			}
			var pts = verts.slice();
			if ( follow && ( state.mode === 'add-polyline' || state.mode === 'add-polygon' ) ) {
				pts = pts.concat( [ mouseLatLng ] );
			}
			if ( kind === 'polygon' ) {
				ensurePreview( 'polygon', function () {
					return window.L.polygon( pts, {
						color: '#6b7280',
						weight: 2,
						fillColor: '#6b7280',
						fillOpacity: 0.2,
						dashArray: '6 4',
						interactive: false,
					} );
				} );
			} else {
				ensurePreview( 'polyline', function () {
					return window.L.polyline( pts, { color: '#2563eb', dashArray: '6 4', weight: 3, interactive: false } );
				} );
			}
			state.preview.setLatLngs( pts );
		}
	}

	function currentGeometry() {
		var kind = state.draftKind;
		var verts = state.vertices;
		if ( kind === 'marker' ) {
			if ( verts.length < 1 ) {
				return null;
			}
			return { lat: verts[ 0 ].lat, lng: verts[ 0 ].lng };
		}
		if ( kind === 'circle' ) {
			if ( verts.length < 1 || state.circleRadius < 1 ) {
				return null;
			}
			return { lat: verts[ 0 ].lat, lng: verts[ 0 ].lng, radius: state.circleRadius };
		}
		if ( kind === 'polyline' ) {
			if ( verts.length < 2 ) {
				return null;
			}
			return {
				path: verts.map( function ( pt ) {
					return [ pt.lat, pt.lng ];
				} ),
			};
		}
		if ( kind === 'polygon' ) {
			if ( verts.length < 3 ) {
				return null;
			}
			return {
				path: verts.map( function ( pt ) {
					return [ pt.lat, pt.lng ];
				} ),
			};
		}
		return null;
	}

	function clearDraft() {
		state.vertices = [];
		state.vertexMarkers.forEach( function ( marker ) {
			map.removeLayer( marker );
		} );
		state.vertexMarkers = [];
		if ( state.preview ) {
			map.removeLayer( state.preview );
			state.preview = null;
		}
		state.previewType = '';
		if ( state.draftIcon ) {
			map.removeLayer( state.draftIcon );
			state.draftIcon = null;
		}
		state.circleRadius = 0;
	}

	function resetTools() {
		state.mode = 'pan';
		state.draftKind = '';
		state.formObject = null;
		state.preset = null;
		state.measureStopped = false;
		clearDraft();
		if ( state.coordMarker ) {
			map.removeLayer( state.coordMarker );
			state.coordMarker = null;
		}
		setModeBar( '' );
		app.querySelectorAll( '[data-tacnav-tool]' ).forEach( function ( btn ) {
			btn.classList.remove( 'is-active' );
		} );
		redrawObjects();
	}

	function cancelInspect() {
		resetTools();
		closeSheets();
	}

	function distanceLabel( meters ) {
		if ( meters >= 1000 ) {
			return ( meters / 1000 ).toFixed( 2 ) + ' km';
		}
		return Math.round( meters ) + ' m';
	}

	function chainDistance( points ) {
		var total = 0;
		for ( var i = 1; i < points.length; i++ ) {
			total += map.distance( points[ i - 1 ], points[ i ] );
		}
		return total;
	}

	function vertexIcon( showDelete ) {
		var html = '<span class="tacnav-vertex-dot"></span>';
		if ( showDelete ) {
			html += '<button type="button" class="tacnav-vertex-del" aria-label="' + escapeHtml( strings.removeVertex || '' ) + '">×</button>';
		}
		return window.L.divIcon( {
			className: 'tacnav-vertex-wrap',
			html: html,
			iconSize: [ 32, 32 ],
			iconAnchor: [ 16, 16 ],
		} );
	}

	function usesVertexChrome() {
		return state.draftKind === 'polyline' || state.draftKind === 'polygon';
	}

	function bindVertexDelete( marker ) {
		var el = marker.getElement();
		if ( ! el ) {
			return;
		}
		var btn = el.querySelector( '.tacnav-vertex-del' );
		if ( ! btn ) {
			return;
		}
		window.L.DomEvent.disableClickPropagation( btn );
		window.L.DomEvent.on( btn, 'mousedown', window.L.DomEvent.stop );
		window.L.DomEvent.on( btn, 'click', function ( event ) {
			window.L.DomEvent.stop( event );
			removeVertexAt( state.vertexMarkers.indexOf( marker ) );
		} );
	}

	function refreshVertexDeletes() {
		if ( ! usesVertexChrome() ) {
			return;
		}
		state.vertexMarkers.forEach( function ( marker, idx ) {
			if ( typeof marker.setIcon !== 'function' ) {
				return;
			}
			var show = state.draftKind === 'polygon' || idx === state.vertexMarkers.length - 1;
			marker.setIcon( vertexIcon( show ) );
			bindVertexDelete( marker );
		} );
	}

	function removeVertexAt( idx ) {
		if ( idx < 0 || idx >= state.vertices.length ) {
			return;
		}
		var marker = state.vertexMarkers[ idx ];
		if ( marker ) {
			map.removeLayer( marker );
		}
		state.vertices.splice( idx, 1 );
		state.vertexMarkers.splice( idx, 1 );
		refreshVertexDeletes();
		syncPreview();
		if ( state.formObject ) {
			var geom = currentGeometry();
			if ( geom ) {
				state.formObject.geometry = geom;
			}
			refreshDraftIcon( state.formObject );
		}
	}

	function addVertex( latlng ) {
		state.vertices.push( latlng );
		var options = { draggable: true, zIndexOffset: 800 };
		if ( usesVertexChrome() ) {
			options.icon = vertexIcon( false );
		}
		var marker = window.L.marker( latlng, options ).addTo( map );
		marker.on( 'drag', function () {
			var idx = state.vertexMarkers.indexOf( marker );
			if ( idx < 0 ) {
				return;
			}
			state.vertices[ idx ] = marker.getLatLng();
			if ( state.draftKind === 'circle' && idx === 0 && state.vertexMarkers[ 1 ] && state.circleRadius ) {
				var moved = eastOf( state.vertices[ 0 ], state.circleRadius );
				state.vertices[ 1 ] = moved;
				state.vertexMarkers[ 1 ].setLatLng( moved );
			}
			if ( state.draftKind === 'circle' && idx === 1 ) {
				state.circleRadius = map.distance( state.vertices[ 0 ], state.vertices[ 1 ] );
			}
			syncPreview();
			if ( state.formObject ) {
				var geom = currentGeometry();
				if ( geom ) {
					state.formObject.geometry = geom;
					refreshDraftIcon( state.formObject );
				}
			}
		} );
		state.vertexMarkers.push( marker );
		refreshVertexDeletes();
	}

	function drawActions() {
		return [
			{ label: strings.cancel, onClick: cancelInspect },
			{ label: strings.done, onClick: finishDraw },
		];
	}

	function measureActions() {
		return [
			{ label: strings.stop, onClick: stopMeasure },
			{ label: strings.clear, onClick: resetTools },
		];
	}

	function saveDrawn( payload ) {
		setSaving( true );
		rest( '/objects', { method: 'POST', body: JSON.stringify( payload ) } ).then( function ( saved ) {
			adoptSaved( saved );
			setSaving( false );
			resetTools();
			closeSheets();
		} ).catch( function () {
			setSaving( false );
			return null;
		} );
	}

	function finishDraw() {
		var geom = currentGeometry();
		if ( ! geom ) {
			return;
		}
		if ( state.preset ) {
			saveDrawn( {
				kind: state.preset.kind,
				geometry: geom,
				title: state.preset.title || '',
				description: state.preset.description || '',
				icon_id: Number( state.preset.icon_id ) || null,
				ttl_minutes: Number( state.preset.ttl_minutes ) || 0,
			} );
			return;
		}
		state.mode = 'inspect';
		setModeBar( strings.adjust, [ { label: strings.cancel, onClick: cancelInspect } ] );
		var object = {
			kind: state.draftKind,
			geometry: geom,
			owner_team_id: null,
			visible_team_ids: [],
			icon_id: null,
			title: '',
			description: '',
			ttl_minutes: 0,
		};
		state.formObject = object;
		refreshDraftIcon( object );
		openInspector( object, true );
	}

	function stopMeasure() {
		state.measureStopped = true;
		syncPreview();
		if ( state.preview ) {
			map.removeLayer( state.preview );
			state.preview = null;
			state.previewType = '';
		}
		if ( state.vertices.length >= 2 ) {
			var line = window.L.polyline( state.vertices, { color: '#f59e0b', weight: 3 } ).addTo( map );
			state.vertexMarkers.push( line );
		}
		setModeBar( strings.measure + ' ' + distanceLabel( chainDistance( state.vertices ) ), [
			{ label: strings.clear, onClick: resetTools },
		] );
	}

	function startDraw( kind, preset ) {
		resetTools();
		closeSheets();
		state.preset = preset || null;
		state.mode = 'add-' + kind;
		state.draftKind = kind;
		var hint = kind === 'circle' ? strings.tapRadius : ( strings[ kind ] || kind );
		setModeBar( hint, drawActions() );
		hideAddMenu();
	}

	function fillAddMenu() {
		var menu = app.querySelector( '[data-tacnav-add-menu]' );
		if ( ! menu ) {
			return;
		}
		menu.textContent = '';
		( cfg.presets || [] ).forEach( function ( preset ) {
			if ( ! preset.kind ) {
				return;
			}
			var presetBtn = document.createElement( 'button' );
			presetBtn.type = 'button';
			presetBtn.textContent = preset.title || strings[ preset.kind ] || preset.kind;
			presetBtn.addEventListener( 'click', function () {
				startDraw( preset.kind, preset );
			} );
			menu.appendChild( presetBtn );
		} );
		kinds.forEach( function ( kind ) {
			var btn = document.createElement( 'button' );
			btn.type = 'button';
			btn.textContent = strings[ kind ] || kind;
			btn.addEventListener( 'click', function () {
				startDraw( kind );
			} );
			menu.appendChild( btn );
		} );
	}

	function beginGeomEdit( object ) {
		clearDraft();
		state.draftKind = object.kind;
		state.mode = 'inspect';
		state.formObject = object;
		var geom = object.geometry || {};
		if ( object.kind === 'marker' ) {
			addVertex( window.L.latLng( geom.lat, geom.lng ) );
		} else if ( object.kind === 'circle' ) {
			var circleCenter = window.L.latLng( geom.lat, geom.lng );
			addVertex( circleCenter );
			addVertex( eastOf( circleCenter, geom.radius || 50 ) );
			state.circleRadius = geom.radius || 50;
		} else {
			pathLatLngs( geom ).forEach( addVertex );
		}
		syncPreview();
		refreshDraftIcon( object );
		setModeBar( strings.adjust, [ { label: strings.cancel, onClick: cancelInspect } ] );
		redrawObjects();
	}

	function iconPickerHtml( selectedId ) {
		var html = '<label>' + escapeHtml( strings.icon ) + '</label><div class="tacnav-icon-grid">';
		html += '<button type="button" class="tacnav-icon-choice' + ( ! selectedId ? ' is-selected' : '' ) + '" data-icon-id="0">' + escapeHtml( strings.none ) + '</button>';
		( cfg.icons || [] ).forEach( function ( icon ) {
			var selected = Number( selectedId ) === Number( icon.id ) ? ' is-selected' : '';
			var img = icon.url ? '<img src="' + escapeHtml( icon.url ) + '" alt="" />' : '';
			html += '<button type="button" class="tacnav-icon-choice' + selected + '" data-icon-id="' + escapeHtml( icon.id ) + '">' + img + '<span>' + escapeHtml( icon.title ) + '</span></button>';
		} );
		html += '</div><input type="hidden" data-f="icon_id" value="' + escapeHtml( selectedId || 0 ) + '" />';
		return html;
	}

	function adoptSaved( saved ) {
		if ( typeof saved.can_edit === 'undefined' ) {
			saved.can_edit = true;
		}
		var exists = state.objects.some( function ( item ) {
			return Number( item.id ) === Number( saved.id );
		} );
		if ( exists ) {
			state.objects = state.objects.map( function ( item ) {
				return Number( item.id ) === Number( saved.id ) ? saved : item;
			} );
			return;
		}
		state.objects.push( saved );
	}

	function openInspector( object, isNew ) {
		var teams = cfg.teams || [];
		var html = '<h2>' + escapeHtml( isNew ? strings.add : strings.edit ) + '</h2>';
		html += '<label>' + escapeHtml( strings.title ) + '</label><input data-f="title" value="' + escapeHtml( object.title || '' ) + '" />';
		html += '<label>' + escapeHtml( strings.description ) + '</label><textarea data-f="description">' + escapeHtml( object.description || '' ) + '</textarea>';
		html += iconPickerHtml( object.icon_id );
		if ( cfg.mode !== 'player' ) {
			html += '<label>' + escapeHtml( strings.belonging ) + '</label><select data-f="owner_team_id"><option value="0">' + escapeHtml( strings.neutral ) + '</option>';
			teams.forEach( function ( team ) {
				html += '<option value="' + escapeHtml( team.id ) + '"' + ( Number( object.owner_team_id ) === Number( team.id ) ? ' selected' : '' ) + '>' + escapeHtml( team.title ) + '</option>';
			} );
			html += '</select><p>' + escapeHtml( strings.visibility ) + '</p>';
			teams.forEach( function ( team ) {
				var checked = ( object.visible_team_ids || [] ).map( Number ).indexOf( Number( team.id ) ) !== -1;
				html += '<label><input type="checkbox" data-vis="' + escapeHtml( team.id ) + '"' + ( checked ? ' checked' : '' ) + ' /> ' + escapeHtml( team.title ) + '</label>';
			} );
		}
		html += '<label>' + escapeHtml( strings.ttl ) + '</label><select data-f="ttl_minutes">';
		[
			[ 0, strings.forever ],
			[ 3, '3' ],
			[ 10, '10' ],
			[ 60, '60' ],
		].forEach( function ( pair ) {
			html += '<option value="' + pair[ 0 ] + '"' + ( Number( object.ttl_minutes ) === pair[ 0 ] ? ' selected' : '' ) + '>' + pair[ 1 ] + '</option>';
		} );
		html += '</select>';
		if ( ! isNew ) {
			html += '<p class="tacnav-ttl" data-ttl-remaining></p>';
		}
		html += '<div class="tacnav-sheet-actions"><button type="button" class="tacnav-btn" data-save>' + strings.save + '</button>';
		if ( ! isNew ) {
			html += '<button type="button" class="tacnav-btn" data-delete>' + strings.delete + '</button>';
		}
		html += '<button type="button" class="tacnav-btn" data-close>' + strings.cancel + '</button></div>';

		var sheet = openSheet( 'inspector', html );
		sheet.querySelectorAll( '[data-icon-id]' ).forEach( function ( btn ) {
			btn.addEventListener( 'click', function () {
				sheet.querySelectorAll( '[data-icon-id]' ).forEach( function ( other ) {
					other.classList.remove( 'is-selected' );
				} );
				btn.classList.add( 'is-selected' );
				sheet.querySelector( '[data-f="icon_id"]' ).value = btn.getAttribute( 'data-icon-id' );
				if ( state.formObject ) {
					state.formObject.icon_id = Number( btn.getAttribute( 'data-icon-id' ) ) || null;
					refreshDraftIcon( state.formObject );
				}
			} );
		} );
		var ttlEl = sheet.querySelector( '[data-ttl-remaining]' );
		if ( ttlEl ) {
			var tick = function () {
				ttlEl.textContent = formatRemaining( object );
			};
			tick();
			ttlTimer = window.setInterval( tick, 1000 );
		}
		sheet.querySelector( '[data-save]' ).addEventListener( 'click', function () {
			var visible = [];
			sheet.querySelectorAll( '[data-vis]' ).forEach( function ( box ) {
				if ( box.checked ) {
					visible.push( Number( box.getAttribute( 'data-vis' ) ) );
				}
			} );
			var geom = currentGeometry() || object.geometry;
			if ( ! geom ) {
				return;
			}
			var payload = {
				kind: object.kind,
				geometry: geom,
				title: sheet.querySelector( '[data-f="title"]' ).value,
				description: sheet.querySelector( '[data-f="description"]' ).value,
				icon_id: Number( sheet.querySelector( '[data-f="icon_id"]' ).value ) || null,
				owner_team_id: sheet.querySelector( '[data-f="owner_team_id"]' ) ? Number( sheet.querySelector( '[data-f="owner_team_id"]' ).value ) || null : null,
				visible_team_ids: visible,
				ttl_minutes: Number( sheet.querySelector( '[data-f="ttl_minutes"]' ).value ),
				updated_at: object.updated_at || '',
			};
			setSaving( true );
			var req = isNew
				? rest( '/objects', { method: 'POST', body: JSON.stringify( payload ) } )
				: rest( '/objects/' + object.id, { method: 'PUT', body: JSON.stringify( payload ) } );
			req.then( function ( saved ) {
				adoptSaved( saved );
				setSaving( false );
				resetTools();
				closeSheets();
			} ).catch( function ( err ) {
				setSaving( false );
				if ( err && err.code === 'tacnav_geo_conflict' ) {
					var note = sheet.querySelector( '[data-conflict]' );
					if ( ! note ) {
						note = document.createElement( 'p' );
						note.setAttribute( 'data-conflict', '1' );
						sheet.appendChild( note );
					}
					note.textContent = strings.conflict || '';
				}
				return null;
			} );
		} );
		if ( ! isNew ) {
			sheet.querySelector( '[data-delete]' ).addEventListener( 'click', function () {
				setSaving( true );
				rest( '/objects/' + object.id, { method: 'DELETE' } ).then( function () {
					state.objects = state.objects.filter( function ( item ) {
						return item.id !== object.id;
					} );
					setSaving( false );
					resetTools();
					closeSheets();
				} ).catch( function () {
					setSaving( false );
					return null;
				} );
			} );
		}
		sheet.querySelector( '[data-close]' ).addEventListener( 'click', cancelInspect );
	}

	function openPopup( object ) {
		var team = object.owner_team_id ? teamById( object.owner_team_id ) : null;
		var html = '<h2>' + escapeHtml( object.title || strings[ object.kind ] || '' ) + '</h2>';
		if ( object.description ) {
			html += '<p>' + escapeHtml( object.description ) + '</p>';
		}
		html += '<p>' + escapeHtml( strings.belonging ) + ': ' + escapeHtml( team ? team.title : strings.neutral ) + '</p>';
		if ( object.expires_at ) {
			html += '<p class="tacnav-ttl">' + escapeHtml( formatRemaining( object ) ) + '</p>';
		}
		html += '<div class="tacnav-sheet-actions">';
		if ( cfg.mode !== 'guest' && object.can_edit ) {
			html += '<button type="button" class="tacnav-btn" data-edit>' + strings.edit + '</button>';
			html += '<button type="button" class="tacnav-btn" data-delete>' + strings.delete + '</button>';
		}
		html += '<button type="button" class="tacnav-btn" data-close>' + strings.cancel + '</button></div>';
		var sheet = openSheet( 'popup', html );
		if ( cfg.mode !== 'guest' && object.can_edit ) {
			sheet.querySelector( '[data-edit]' ).addEventListener( 'click', function () {
				beginGeomEdit( object );
				openInspector( object, false );
			} );
			sheet.querySelector( '[data-delete]' ).addEventListener( 'click', function () {
				setSaving( true );
				rest( '/objects/' + object.id, { method: 'DELETE' } ).then( function () {
					state.objects = state.objects.filter( function ( item ) {
						return item.id !== object.id;
					} );
					setSaving( false );
					closeSheets();
					redrawObjects();
				} ).catch( function () {
					setSaving( false );
					return null;
				} );
			} );
		}
		sheet.querySelector( '[data-close]' ).addEventListener( 'click', closeSheets );
	}

	function openFilters() {
		hideAddMenu();
		var html = '<h2>' + strings.filters + '</h2>';
		html += '<label><input type="checkbox" data-expired ' + ( state.showExpired ? 'checked' : '' ) + ' /> ' + strings.expired + '</label>';
		html += '<p>' + strings.belonging + '</p>';
		html += '<label><input type="checkbox" data-team="neutral" ' + ( ! state.hiddenTeams.neutral ? 'checked' : '' ) + ' /> ' + strings.neutral + '</label>';
		( cfg.teams || [] ).forEach( function ( team ) {
			html += '<label><input type="checkbox" data-team="' + escapeHtml( team.id ) + '" ' + ( ! state.hiddenTeams[ team.id ] ? 'checked' : '' ) + ' /> ' + escapeHtml( team.title ) + '</label>';
		} );
		if ( cfg.canPurge ) {
			html += '<div class="tacnav-sheet-actions"><button type="button" class="tacnav-btn" data-purge>' + strings.purge + '</button></div>';
		}
		var sheet = openSheet( 'filters', html );
		sheet.querySelector( '[data-expired]' ).addEventListener( 'change', function ( event ) {
			state.showExpired = event.target.checked;
			reloadObjects();
		} );
		sheet.querySelectorAll( '[data-team]' ).forEach( function ( box ) {
			box.addEventListener( 'change', function () {
				var key = box.getAttribute( 'data-team' );
				if ( box.checked ) {
					delete state.hiddenTeams[ key ];
				} else {
					state.hiddenTeams[ key ] = true;
				}
				redrawObjects();
			} );
		} );
		if ( cfg.canPurge ) {
			sheet.querySelector( '[data-purge]' ).addEventListener( 'click', function () {
				rest( '/objects/purge-expired', { method: 'POST' } ).then( reloadObjects );
			} );
		}
	}

	function openLayers() {
		hideAddMenu();
		var html = '<h2>' + strings.layers + '</h2>';
		html += '<p>' + escapeHtml( strings.satellite ) + '</p>';
		html += '<label><input type="checkbox" data-labels-overlay ' + ( state.labelsOn ? 'checked' : '' ) + ' /> ' + strings.labelsOverlay + '</label>';
		var sheet = openSheet( 'layers', html );
		sheet.querySelector( '[data-labels-overlay]' ).addEventListener( 'change', function ( event ) {
			state.labelsOn = event.target.checked;
			if ( state.labelsOn ) {
				labels.addTo( map );
			} else {
				map.removeLayer( labels );
			}
		} );
	}

	function guestPath( path ) {
		if ( cfg.mode !== 'guest' ) {
			return path;
		}
		var token = '';
		try {
			token = new URLSearchParams( window.location.search ).get( 't' ) || '';
		} catch ( err ) {
			token = '';
		}
		return path + ( path.indexOf( '?' ) === -1 ? '?' : '&' ) + 't=' + encodeURIComponent( token );
	}

	function objectStamp( object ) {
		return JSON.stringify( {
			kind: object.kind,
			geometry: object.geometry,
			title: object.title,
			description: object.description,
			icon_id: object.icon_id,
			owner_team_id: object.owner_team_id,
			visible_team_ids: object.visible_team_ids,
			expires_at: object.expires_at,
			origin: object.origin,
			can_edit: object.can_edit,
			self_point: object.self_point,
			avatar_url: object.avatar_url,
		} );
	}

	function upsertObject( row ) {
		if ( state.formObject && state.formObject.id && Number( state.formObject.id ) === Number( row.id ) ) {
			return;
		}
		var layer = state.layers[ row.id ];
		if ( ! objectVisibleOnCanvas( row ) ) {
			if ( layer ) {
				map.removeLayer( layer );
				delete state.layers[ row.id ];
			}
			return;
		}
		var stamp = objectStamp( row );
		if ( layer && layer._tacnavStamp === stamp ) {
			return;
		}
		if ( layer ) {
			map.removeLayer( layer );
			delete state.layers[ row.id ];
		}
		addObjectLayer( row );
		if ( state.layers[ row.id ] ) {
			state.layers[ row.id ]._tacnavStamp = stamp;
		}
	}

	function applyPicture( rows ) {
		var next = {};
		rows.forEach( function ( row ) {
			next[ row.id ] = row;
		} );
		var formId = state.formObject && state.formObject.id;
		Object.keys( state.layers ).forEach( function ( id ) {
			if ( next[ id ] ) {
				return;
			}
			if ( formId && Number( formId ) === Number( id ) ) {
				return;
			}
			map.removeLayer( state.layers[ id ] );
			delete state.layers[ id ];
		} );
		state.objects = rows.map( function ( row ) {
			if ( formId && Number( formId ) === Number( row.id ) ) {
				return state.formObject;
			}
			return row;
		} );
		if ( formId && ! next[ formId ] ) {
			state.objects.push( state.formObject );
		}
		rows.forEach( upsertObject );
	}

	function refreshPicture() {
		var path = state.showExpired ? '/objects?include_expired=1' : '/objects';
		var headers = {
			'Content-Type': 'application/json',
			'X-WP-Nonce': cfg.nonce,
		};
		if ( state.pictureRev != null && state.pictureRev !== '' ) {
			headers[ 'If-None-Match' ] = '"' + state.pictureRev + '"';
		}
		return window.fetch( cfg.restUrl + guestPath( path ), {
			method: 'GET',
			headers: headers,
			credentials: 'same-origin',
		} ).then( function ( res ) {
			if ( res.status === 304 ) {
				return { status: 304 };
			}
			return res.json().then( function ( body ) {
				if ( ! res.ok ) {
					throw body;
				}
				var etag = res.headers.get( 'ETag' );
				if ( etag ) {
					state.pictureRev = etag.replace( /^W\//, '' ).replace( /"/g, '' );
				}
				return { status: 200, objects: body };
			} );
		} );
	}

	function liveDelay() {
		if ( document.hidden ) {
			return 30000;
		}
		if ( state.quietPolls >= 2 ) {
			return 6000;
		}
		return 3000;
	}

	function scheduleLive() {
		if ( state.liveTimer ) {
			window.clearTimeout( state.liveTimer );
		}
		state.liveTimer = window.setTimeout( function () {
			state.liveTimer = null;
			if ( ! state.liveOn ) {
				return;
			}
			if ( state.saving ) {
				scheduleLive();
				return;
			}
			refreshPicture().then( function ( result ) {
				if ( result && result.status === 200 && result.objects ) {
					state.quietPolls = 0;
					applyPicture( result.objects );
				} else if ( result && result.status === 304 ) {
					state.quietPolls += 1;
				}
				if ( state.liveOn ) {
					scheduleLive();
				}
			} ).catch( function () {
				if ( state.liveOn ) {
					scheduleLive();
				}
			} );
		}, liveDelay() );
	}

	function reloadObjects() {
		state.pictureRev = null;
		refreshPicture().then( function ( result ) {
			if ( result && result.status === 200 && result.objects ) {
				state.quietPolls = 0;
				applyPicture( result.objects );
			}
		} ).catch( function () {
			return null;
		} );
	}

	function setLive( on ) {
		state.liveOn = !! on;
		if ( state.liveTimer ) {
			window.clearTimeout( state.liveTimer );
			state.liveTimer = null;
		}
		var btn = app.querySelector( '[data-tacnav-live]' );
		if ( btn ) {
			btn.classList.toggle( 'is-active', state.liveOn );
		}
		if ( state.liveOn ) {
			state.quietPolls = 0;
			scheduleLive();
		}
	}

	function dropExpiredMarkers() {
		if ( cfg.mode === 'staff' && state.showExpired ) {
			return;
		}
		state.objects = state.objects.filter( function ( object ) {
			if ( ! isExpired( object ) ) {
				return true;
			}
			if ( state.formObject && Number( state.formObject.id ) === Number( object.id ) ) {
				return true;
			}
			if ( state.layers[ object.id ] ) {
				map.removeLayer( state.layers[ object.id ] );
				delete state.layers[ object.id ];
			}
			return false;
		} );
	}

	function handleMapClick( event ) {
		hideAddMenu();
		if ( ! event || ! event.latlng ) {
			return;
		}
		if ( state.mode === 'add-marker' ) {
			addVertex( event.latlng );
			finishDraw();
			return;
		}
		if ( state.mode === 'add-circle' && state.vertices.length === 0 ) {
			addVertex( event.latlng );
			return;
		}
		if ( state.mode === 'add-circle' && state.vertices.length === 1 ) {
			addVertex( event.latlng );
			syncPreview();
			return;
		}
		if ( state.mode === 'add-polyline' || state.mode === 'add-polygon' ) {
			addVertex( event.latlng );
			syncPreview();
			return;
		}
		if ( state.mode === 'inspect' && ( state.draftKind === 'polyline' || state.draftKind === 'polygon' ) ) {
			addVertex( event.latlng );
			syncPreview();
			if ( state.formObject ) {
				var geom = currentGeometry();
				if ( geom ) {
					state.formObject.geometry = geom;
					refreshDraftIcon( state.formObject );
				}
			}
			return;
		}
		if ( state.mode === 'measure' && ! state.measureStopped ) {
			addVertex( event.latlng );
			syncPreview();
			return;
		}
		if ( state.mode === 'coords' ) {
			if ( state.coordMarker ) {
				state.coordMarker.setLatLng( event.latlng );
			} else {
				state.coordMarker = window.L.marker( event.latlng ).addTo( map );
			}
			openSheet( 'popup', '<h2>' + escapeHtml( strings.coords ) + '</h2><p>' + escapeHtml( event.latlng.lat.toFixed( 6 ) + ', ' + event.latlng.lng.toFixed( 6 ) ) + '</p>' );
		}
	}

	map.on( 'click', handleMapClick );

	map.on( 'mousemove', function ( event ) {
		updatePreviewSafe( event.latlng );
	} );

	function updatePreviewSafe( latlng ) {
		if ( state.mode === 'measure' && state.measureStopped ) {
			return;
		}
		syncPreview( latlng );
	}

	fillAddMenu();
	var addBtn = app.querySelector( '[data-tacnav-add]' );
	if ( addBtn ) {
		addBtn.addEventListener( 'click', function () {
			var menu = app.querySelector( '[data-tacnav-add-menu]' );
			menu.hidden = ! menu.hidden;
		} );
	}
	app.querySelector( '[data-tacnav-tool="measure"]' ).textContent = strings.measure;
	app.querySelector( '[data-tacnav-tool="coords"]' ).textContent = strings.coords;
	var filtersBtn = app.querySelector( '[data-tacnav-open="filters"]' );
	if ( filtersBtn ) {
		filtersBtn.textContent = strings.filters;
	}
	var layersBtn = app.querySelector( '[data-tacnav-open="layers"]' );
	if ( layersBtn ) {
		layersBtn.textContent = strings.layers;
	}
	app.querySelector( '[data-tacnav-home]' ).textContent = strings.home;
	app.querySelector( '[data-tacnav-locate]' ).textContent = strings.locate;
	var liveBtn = app.querySelector( '[data-tacnav-live]' );
	if ( liveBtn ) {
		liveBtn.textContent = strings.live;
	}
	var titlesBtn = app.querySelector( '[data-tacnav-titles]' );
	if ( titlesBtn ) {
		titlesBtn.textContent = strings.labels;
		titlesBtn.classList.toggle( 'is-active', state.showLabels );
	}
	var markBtn = app.querySelector( '[data-tacnav-mark]' );
	if ( markBtn ) {
		markBtn.textContent = strings.mark;
	}
	app.querySelector( '[data-tacnav-tool="measure"]' ).addEventListener( 'click', function () {
		resetTools();
		closeSheets();
		state.mode = 'measure';
		this.classList.add( 'is-active' );
		setModeBar( strings.measure, measureActions() );
	} );
	app.querySelector( '[data-tacnav-tool="coords"]' ).addEventListener( 'click', function () {
		resetTools();
		closeSheets();
		state.mode = 'coords';
		this.classList.add( 'is-active' );
		setModeBar( strings.coords, [ { label: strings.cancel, onClick: resetTools } ] );
	} );
	app.querySelector( '[data-tacnav-home]' ).addEventListener( 'click', function () {
		map.setView( [ cfg.map.center_lat || 0, cfg.map.center_lng || 0 ], homeZoom );
	} );
	app.querySelector( '[data-tacnav-locate]' ).addEventListener( 'click', function () {
		if ( ! navigator.geolocation ) {
			openSheet( 'popup', '<p>' + escapeHtml( strings.locateFail ) + '</p>' );
			return;
		}
		navigator.geolocation.getCurrentPosition(
			function ( pos ) {
				map.setView( [ pos.coords.latitude, pos.coords.longitude ], Math.max( map.getZoom(), 15 ) );
			},
			function () {
				openSheet( 'popup', '<p>' + escapeHtml( strings.locateFail ) + '</p>' );
			}
		);
	} );
	if ( liveBtn ) {
		liveBtn.addEventListener( 'click', function () {
			setLive( ! state.liveOn );
		} );
	}
	if ( titlesBtn ) {
		titlesBtn.addEventListener( 'click', function () {
			state.showLabels = ! state.showLabels;
			titlesBtn.classList.toggle( 'is-active', state.showLabels );
			try {
				window.sessionStorage.setItem( 'tacnav-show-labels', state.showLabels ? '1' : '0' );
			} catch ( err ) {
				/* Storage can be blocked; the toggle still applies for this page. */
			}
			redrawObjects();
		} );
	}
	if ( markBtn ) {
		markBtn.addEventListener( 'click', function () {
			if ( ! navigator.geolocation ) {
				openSheet( 'popup', '<p>' + escapeHtml( strings.locateFail ) + '</p>' );
				return;
			}
			navigator.geolocation.getCurrentPosition(
				function ( pos ) {
					setSaving( true );
					rest( '/objects/self', {
						method: 'POST',
						body: JSON.stringify( {
							lat: pos.coords.latitude,
							lng: pos.coords.longitude,
						} ),
					} ).then( function ( saved ) {
						state.objects = state.objects.filter( function ( item ) {
							return ! item.self_point || Number( item.created_by_user_id ) !== Number( saved.created_by_user_id );
						} );
						adoptSaved( saved );
						setSaving( false );
						redrawObjects();
					} ).catch( function () {
						setSaving( false );
						return null;
					} );
				},
				function () {
					openSheet( 'popup', '<p>' + escapeHtml( strings.locateFail ) + '</p>' );
				}
			);
		} );
	}
	if ( filtersBtn ) {
		filtersBtn.addEventListener( 'click', openFilters );
	}
	if ( layersBtn ) {
		layersBtn.addEventListener( 'click', openLayers );
	}

	redrawObjects();
	state.objects.forEach( function ( object ) {
		if ( state.layers[ object.id ] ) {
			state.layers[ object.id ]._tacnavStamp = objectStamp( object );
		}
	} );
	window.setInterval( dropExpiredMarkers, 1000 );
	if ( cfg.mode === 'player' || cfg.mode === 'guest' ) {
		setLive( true );
	}
}() );
