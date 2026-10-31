<?php
/**
 * Firebase HTTP v1 wake-up transport.
 *
 * @package TacNav_Devices
 */

namespace TacNav\Devices;

defined( 'ABSPATH' ) || exit;

/**
 * Signs OAuth JWTs with an external least-privilege service account.
 */
class Firebase {

	/**
	 * Wake data key read by the Android application.
	 */
	const DATA_DELIVERY_REFERENCE = 'delivery_ref';

	/**
	 * External service-account path.
	 *
	 * @return string
	 */
	public static function credential_path() {
		$path = defined( 'TACNAV_FIREBASE_SERVICE_ACCOUNT_FILE' ) ? TACNAV_FIREBASE_SERVICE_ACCOUNT_FILE : getenv( 'TACNAV_FIREBASE_SERVICE_ACCOUNT_FILE' );
		$path = is_string( $path ) ? trim( $path ) : '';
		if ( '' === $path || 0 === strpos( wp_normalize_path( $path ), wp_normalize_path( ABSPATH ) ) ) {
			return '';
		}
		return $path;
	}

	/**
	 * Send a post-commit wake reference for one delivery.
	 *
	 * @param int $delivery_id Delivery cursor.
	 * @return true|\WP_Error
	 */
	public static function wake_delivery( $delivery_id ) {
		global $wpdb;

		$account = self::service_account();
		if ( is_wp_error( $account ) ) {
			return $account;
		}
		$tables = Activator::tables();
		$row    = $wpdb->get_row(
			$wpdb->prepare(
				'SELECT i.id AS installation_id, i.fcm_token
				FROM %i d INNER JOIN %i i ON i.id = d.recipient_installation_id
				WHERE d.id = %d AND d.acknowledged_at IS NULL AND i.revoked_at IS NULL',
				$tables['deliveries'],
				$tables['installations'],
				(int) $delivery_id
			),
			ARRAY_A
		);
		if ( ! $row || empty( $row['fcm_token'] ) ) {
			return true;
		}
		$token = self::access_token( $account );
		if ( is_wp_error( $token ) ) {
			return $token;
		}

		$url      = 'https://fcm.googleapis.com/v1/projects/' . rawurlencode( (string) $account['project_id'] ) . '/messages:send';
		$response = wp_remote_post(
			$url,
			array(
				'timeout' => 10,
				'headers' => array(
					'Authorization' => 'Bearer ' . $token,
					'Content-Type'  => 'application/json',
				),
				'body'    => wp_json_encode( self::wake_message( (string) $row['fcm_token'], $delivery_id ) ),
			)
		);
		if ( is_wp_error( $response ) ) {
			return $response;
		}
		$status = (int) wp_remote_retrieve_response_code( $response );
		if ( $status >= 200 && $status < 300 ) {
			return true;
		}
		$response_body = (string) wp_remote_retrieve_body( $response );
		$stale_token   = 404 === $status || 410 === $status || ( 400 === $status && false !== strpos( $response_body, 'UNREGISTERED' ) );
		if ( $stale_token ) {
			$wpdb->update(
				$tables['installations'],
				array(
					'fcm_token'      => null,
					'fcm_token_hash' => null,
				),
				array( 'id' => (int) $row['installation_id'] )
			);
		}
		return new \WP_Error( 'tacnav_fcm_failed', __( 'Firebase wake-up failed; the mailbox remains pending.', 'tacnav-devices' ) );
	}

	/**
	 * Build the HTTP v1 wake message for one delivery.
	 *
	 * Data-only, so Android always calls the app's onMessageReceived, which
	 * syncs the mailbox and shows the user-visible notification itself. A
	 * notification block would be shown by the system instead while the app is
	 * in the background, skipping the sync.
	 *
	 * @param string $fcm_token   Installation registration token.
	 * @param int    $delivery_id Delivery cursor.
	 * @return array<string, mixed>
	 */
	public static function wake_message( $fcm_token, $delivery_id ) {
		return array(
			'message' => array(
				'token'   => $fcm_token,
				'data'    => array( self::DATA_DELIVERY_REFERENCE => (string) (int) $delivery_id ),
				'android' => array( 'priority' => 'high' ),
			),
		);
	}

	/**
	 * Load and validate the external service account.
	 *
	 * @return array<string, mixed>|\WP_Error
	 */
	private static function service_account() {
		$path = self::credential_path();
		if ( '' === $path || ! is_readable( $path ) ) {
			return new \WP_Error( 'tacnav_firebase_unconfigured', __( 'Firebase service credentials are not configured.', 'tacnav-devices' ) );
		}
		$json = file_get_contents( $path ); // phpcs:ignore WordPress.WP.AlternativeFunctions.file_get_contents_file_get_contents -- External local credential file.
		$data = json_decode( (string) $json, true );
		if ( ! is_array( $data ) || empty( $data['client_email'] ) || empty( $data['private_key'] ) || empty( $data['project_id'] ) ) {
			return new \WP_Error( 'tacnav_firebase_credentials', __( 'Firebase service credentials are invalid.', 'tacnav-devices' ) );
		}
		return $data;
	}

	/**
	 * Exchange a signed JWT for a short-lived OAuth token.
	 *
	 * @param array<string, mixed> $account Service account.
	 * @return string|\WP_Error
	 */
	private static function access_token( $account ) {
		$cache_key = 'tacnav_firebase_oauth_' . substr( hash( 'sha256', (string) $account['client_email'] ), 0, 16 );
		$cached    = get_transient( $cache_key );
		if ( is_string( $cached ) && '' !== $cached ) {
			return $cached;
		}
		$now       = time();
		$header    = self::base64url(
			wp_json_encode(
				array(
					'alg' => 'RS256',
					'typ' => 'JWT',
				)
			)
		);
		$claims    = self::base64url(
			wp_json_encode(
				array(
					'iss'   => (string) $account['client_email'],
					'scope' => 'https://www.googleapis.com/auth/firebase.messaging',
					'aud'   => 'https://oauth2.googleapis.com/token',
					'iat'   => $now,
					'exp'   => $now + 3600,
				)
			)
		);
		$unsigned  = $header . '.' . $claims;
		$signature = '';
		$signed    = openssl_sign( $unsigned, $signature, (string) $account['private_key'], OPENSSL_ALGO_SHA256 );
		if ( ! $signed ) {
			return new \WP_Error( 'tacnav_firebase_signing', __( 'Could not sign the Firebase authorization request.', 'tacnav-devices' ) );
		}
		$assertion = $unsigned . '.' . self::base64url( $signature );
		$response  = wp_remote_post(
			'https://oauth2.googleapis.com/token',
			array(
				'timeout' => 10,
				'body'    => array(
					'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
					'assertion'  => $assertion,
				),
			)
		);
		if ( is_wp_error( $response ) ) {
			return $response;
		}
		$body = json_decode( (string) wp_remote_retrieve_body( $response ), true );
		if ( (int) wp_remote_retrieve_response_code( $response ) !== 200 || ! is_array( $body ) || empty( $body['access_token'] ) ) {
			return new \WP_Error( 'tacnav_firebase_oauth', __( 'Firebase authorization failed.', 'tacnav-devices' ) );
		}
		$lifetime = isset( $body['expires_in'] ) ? max( 60, (int) $body['expires_in'] - 120 ) : 3300;
		set_transient( $cache_key, (string) $body['access_token'], $lifetime );
		return (string) $body['access_token'];
	}

	/**
	 * URL-safe unpadded base64.
	 *
	 * @param string $value Raw value.
	 * @return string
	 */
	private static function base64url( $value ) {
		return rtrim( strtr( base64_encode( $value ), '+/', '-_' ), '=' ); // phpcs:ignore WordPress.PHP.DiscouragedPHPFunctions.obfuscation_base64_encode -- JWT base64url encoding, not obfuscation.
	}
}
