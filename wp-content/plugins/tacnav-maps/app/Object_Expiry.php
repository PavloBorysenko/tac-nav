<?php
/**
 * Geo-object expiry from a chosen duration.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Turns ttl_minutes into an absolute expiry.
 */
class Object_Expiry {

	/**
	 * Apply a duration to a write payload.
	 *
	 * An update that does not choose a duration omits expires_at so the stored
	 * value remains. A client-supplied expires_at is never kept.
	 *
	 * @param array<string, mixed> $payload Payload that may include ttl_minutes.
	 * @param bool                 $create  Creating.
	 * @param int                  $now     Unix time the server accepts the save.
	 * @return array<string, mixed>
	 */
	public static function apply_to_payload( $payload, $create, $now ) {
		$minutes = null;
		if ( array_key_exists( 'ttl_minutes', $payload ) && null !== $payload['ttl_minutes'] ) {
			$minutes = (int) $payload['ttl_minutes'];
		}
		unset( $payload['ttl_minutes'] );
		unset( $payload['expires_at'] );

		if ( null === $minutes ) {
			if ( $create ) {
				$payload['expires_at'] = null;
			}
			return $payload;
		}

		if ( $minutes > 0 ) {
			$payload['expires_at'] = gmdate( 'Y-m-d H:i:s', $now + ( $minutes * 60 ) );
			return $payload;
		}

		$payload['expires_at'] = null;
		return $payload;
	}
}
