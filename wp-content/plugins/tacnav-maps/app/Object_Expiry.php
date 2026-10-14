<?php
/**
 * Geo-object expiry from a chosen duration.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Turns ttl_seconds into an absolute expiry.
 */
class Object_Expiry {

	/**
	 * Apply a duration to a write payload.
	 *
	 * An update that does not choose a duration omits expires_at so the stored
	 * value remains. A client-supplied expires_at is never kept.
	 *
	 * @param array<string, mixed> $payload Payload that may include ttl_seconds.
	 * @param bool                 $create  Creating.
	 * @param int                  $now     Unix time the server accepts the save.
	 * @return array<string, mixed>|\WP_Error
	 */
	public static function apply_to_payload( $payload, $create, $now ) {
		if ( array_key_exists( 'ttl_minutes', $payload ) ) {
			return new \WP_Error( 'tacnav_ttl_minutes_removed', __( 'ttl_minutes is no longer accepted; use ttl_seconds.', 'tacnav-maps' ), array( 'status' => 400 ) );
		}

		$seconds = null;
		if ( array_key_exists( 'ttl_seconds', $payload ) && null !== $payload['ttl_seconds'] ) {
			if ( ! is_numeric( $payload['ttl_seconds'] ) || (int) $payload['ttl_seconds'] < 0 ) {
				return new \WP_Error( 'tacnav_ttl_seconds', __( 'ttl_seconds must be a non-negative whole number.', 'tacnav-maps' ), array( 'status' => 400 ) );
			}
			$seconds = (int) $payload['ttl_seconds'];
		}
		unset( $payload['ttl_seconds'] );
		unset( $payload['expires_at'] );

		if ( null === $seconds ) {
			if ( $create ) {
				$payload['expires_at'] = null;
			}
			return $payload;
		}

		if ( $seconds > 0 ) {
			$payload['expires_at'] = gmdate( 'Y-m-d H:i:s', $now + $seconds );
			return $payload;
		}

		$payload['expires_at'] = null;
		return $payload;
	}
}
