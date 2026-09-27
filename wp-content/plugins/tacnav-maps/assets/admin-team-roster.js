( function () {
	'use strict';

	var root = document.querySelector( '[data-tacnav-roster]' );
	if ( ! root ) {
		return;
	}

	var search = root.querySelector( '[data-tacnav-roster-search]' );
	var reset = root.querySelector( '[data-tacnav-roster-reset]' );
	var count = root.querySelector( '[data-tacnav-roster-count]' );
	var empty = root.querySelector( '[data-tacnav-roster-empty]' );
	var rows = root.querySelectorAll( '[data-tacnav-roster-row]' );
	var countLabel = count ? count.getAttribute( 'data-tacnav-count-label' ) : '';

	function paint() {
		var query = search ? search.value.trim().toLowerCase() : '';
		var visible = 0;
		var selected = 0;
		var index;

		for ( index = 0; index < rows.length; index++ ) {
			var row = rows[ index ];
			var box = row.querySelector( 'input[type="checkbox"]' );
			var name = row.querySelector( '.tacnav-roster-name' );
			var text = name && name.textContent ? name.textContent.toLowerCase() : '';
			var show = '' === query || text.indexOf( query ) !== -1;

			row.hidden = ! show;
			if ( show ) {
				visible++;
			}
			if ( box && box.checked ) {
				selected++;
				row.classList.add( 'is-selected' );
			} else {
				row.classList.remove( 'is-selected' );
			}
		}

		if ( count && countLabel ) {
			count.textContent = countLabel.replace( '%d', String( selected ) );
		}
		if ( empty ) {
			empty.hidden = 0 !== visible;
		}
	}

	if ( search ) {
		search.addEventListener( 'input', paint );
	}
	if ( reset ) {
		reset.addEventListener( 'click', function () {
			if ( search ) {
				search.value = '';
			}
			paint();
		} );
	}
	root.addEventListener( 'change', function ( event ) {
		if ( event.target && event.target.matches( 'input[type="checkbox"]' ) ) {
			paint();
		}
	} );
	paint();
}() );
