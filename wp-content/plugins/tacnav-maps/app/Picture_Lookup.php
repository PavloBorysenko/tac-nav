<?php
/**
 * Read a stored picture or report that it is unchanged.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Resolves a refresh without querying geo rows on its own.
 */
class Picture_Lookup {

	/**
	 * Store used for this lookup.
	 *
	 * @var Picture_Store
	 */
	private $store;

	/**
	 * Bind a store.
	 *
	 * @param Picture_Store $store Picture store.
	 */
	public function __construct( Picture_Store $store ) {
		$this->store = $store;
	}

	/**
	 * Match the client's version or return the stored list.
	 *
	 * The loader runs only when the version changed and this store has no body yet.
	 *
	 * @param int      $map_id         Map ID.
	 * @param string   $audience       Audience key.
	 * @param string|null $client_version Version the client sent, or null.
	 * @param int      $current_version Current map version.
	 * @param callable $load           Builds the list from the database.
	 * @return array<string, mixed>
	 */
	public function read( $map_id, $audience, $client_version, $current_version, $load ) {
		$current = (int) $current_version;
		if ( Picture_Version::matches( $client_version, $current ) ) {
			return array(
				'status'  => 304,
				'objects' => null,
				'version' => $current,
			);
		}

		$objects = $this->store->get( (int) $map_id, (string) $audience, $current );
		if ( null === $objects ) {
			$loaded  = call_user_func( $load );
			$objects = is_array( $loaded ) ? $loaded : array();
			$this->store->put( (int) $map_id, (string) $audience, $current, $objects );
		}

		return array(
			'status'  => 200,
			'objects' => $objects,
			'version' => $current,
		);
	}
}
