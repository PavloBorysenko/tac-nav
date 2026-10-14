<?php
/**
 * Durable application mailbox.
 *
 * @package TacNav_Devices
 */

namespace TacNav\Devices;

defined( 'ABSPATH' ) || exit;

/**
 * Stores source events and recipient deliveries.
 */
class Mailbox {

	const PENDING_SECONDS = 86400;
	const ACK_SECONDS     = 3600;

	/**
	 * Submit an idempotent same-map event.
	 *
	 * @param array<string, mixed> $context Authenticated context.
	 * @param array<string, mixed> $input   Request input.
	 * @return array<string, mixed>|\WP_Error
	 */
	public function submit( $context, $input ) {
		global $wpdb;

		$client_id = isset( $input['client_event_id'] ) ? sanitize_text_field( (string) $input['client_event_id'] ) : '';
		$type      = isset( $input['type'] ) ? sanitize_key( (string) $input['type'] ) : '';
		$objects   = Validation::ids( isset( $input['recipient_object_ids'] ) ? $input['recipient_object_ids'] : array() );
		if ( '' === $client_id || '' === $type || empty( $objects ) || strlen( $client_id ) > 190 ) {
			return new \WP_Error( 'tacnav_event_invalid', __( 'Event id, type, and recipients are required.', 'tacnav-devices' ), array( 'status' => 400 ) );
		}

		$map_id = (int) $context['map_id'];
		$geo    = new \TacNav\Maps\Geo_Store();
		foreach ( $objects as $object_id ) {
			$object = $geo->get( $object_id );
			if ( ! $object || (int) $object['map_id'] !== $map_id ) {
				return new \WP_Error( 'tacnav_recipient_map', __( 'Every recipient must exist on the selected map.', 'tacnav-devices' ), array( 'status' => 403 ) );
			}
		}

		$tables = Activator::tables();
		$now    = current_time( 'mysql', true );
		$wpdb->query( 'START TRANSACTION' ); // phpcs:ignore WordPress.DB.DirectDatabaseQuery
		$event_id = (int) $wpdb->get_var(
			$wpdb->prepare(
				'SELECT id FROM %i WHERE source_application_id = %d AND client_event_id = %s',
				$tables['events'],
				(int) $context['application_id'],
				$client_id
			)
		);
		if ( $event_id <= 0 ) {
			$ok = $wpdb->insert(
				$tables['events'],
				array(
					'source_application_id' => (int) $context['application_id'],
					'source_team_id'        => (int) $context['team_id'],
					'map_id'                => $map_id,
					'client_event_id'       => $client_id,
					'event_type'            => $type,
					'payload'               => wp_json_encode( isset( $input['payload'] ) ? $input['payload'] : array() ),
					'accepted_at'           => $now,
				)
			);
			if ( false === $ok ) {
				$wpdb->query( 'ROLLBACK' ); // phpcs:ignore WordPress.DB.DirectDatabaseQuery
				return new \WP_Error( 'tacnav_event_store', __( 'Could not store the event.', 'tacnav-devices' ), array( 'status' => 500 ) );
			}
			$event_id = (int) $wpdb->insert_id;
		}

		$delivery_ids = array();
		foreach ( $objects as $object_id ) {
			$binding = $this->active_binding( $object_id, $map_id, $now );
			if ( ! $binding ) {
				continue;
			}
			$wpdb->query(
				$wpdb->prepare(
					'INSERT IGNORE INTO %i
					(event_id,source_application_id,client_event_id,recipient_object_id,recipient_application_id,recipient_installation_id,created_at,expires_at)
					VALUES (%d,%d,%s,%d,%d,%d,%s,%s)',
					$tables['deliveries'],
					$event_id,
					(int) $context['application_id'],
					$client_id,
					$object_id,
					(int) $binding['application_id'],
					(int) $binding['installation_id'],
					$now,
					gmdate( 'Y-m-d H:i:s', time() + self::PENDING_SECONDS )
				)
			);
			$delivery_id = (int) $wpdb->get_var(
				$wpdb->prepare(
					'SELECT id FROM %i WHERE source_application_id = %d AND client_event_id = %s AND recipient_object_id = %d',
					$tables['deliveries'],
					(int) $context['application_id'],
					$client_id,
					$object_id
				)
			);
			if ( $delivery_id > 0 ) {
				$delivery_ids[] = $delivery_id;
			}
		}
		$wpdb->query( 'COMMIT' ); // phpcs:ignore WordPress.DB.DirectDatabaseQuery

		foreach ( array_unique( $delivery_ids ) as $delivery_id ) {
			Firebase::wake_delivery( $delivery_id );
		}
		$this->maybe_cleanup();
		return array(
			'event_id'     => $event_id,
			'delivery_ids' => array_values( array_unique( $delivery_ids ) ),
		);
	}

	/**
	 * Read pending deliveries after a monotonic cursor.
	 *
	 * @param array<string, mixed> $context Authenticated context.
	 * @param int                  $cursor  Last applied delivery ID.
	 * @param int                  $limit   Maximum rows.
	 * @return array<string, mixed>
	 */
	public function inbox( $context, $cursor, $limit = 100 ) {
		global $wpdb;

		$tables = Activator::tables();
		$limit  = max( 1, min( 100, (int) $limit ) );
		$rows   = $wpdb->get_results(
			$wpdb->prepare(
				'SELECT d.id, d.recipient_object_id, e.event_type, e.payload, e.source_application_id, e.source_team_id, e.map_id, e.client_event_id, e.accepted_at
				FROM %i d INNER JOIN %i e ON e.id = d.event_id
				INNER JOIN %i b ON b.object_id = d.recipient_object_id
				WHERE d.recipient_application_id = %d AND d.recipient_installation_id = %d
				AND b.application_id = %d AND b.installation_id = %d
				AND (b.expires_at IS NULL OR b.expires_at > %s)
				AND d.id > %d AND d.acknowledged_at IS NULL AND d.expires_at > %s
				ORDER BY d.id ASC LIMIT %d',
				$tables['deliveries'],
				$tables['events'],
				$tables['bindings'],
				(int) $context['application_id'],
				(int) $context['installation_id'],
				(int) $context['application_id'],
				(int) $context['installation_id'],
				current_time( 'mysql', true ),
				max( 0, (int) $cursor ),
				current_time( 'mysql', true ),
				$limit
			),
			ARRAY_A
		);
		$items  = array();
		foreach ( (array) $rows as $row ) {
			$row['id']                    = (int) $row['id'];
			$row['recipient_object_id']   = (int) $row['recipient_object_id'];
			$row['source_application_id'] = (int) $row['source_application_id'];
			$row['source_team_id']        = (int) $row['source_team_id'];
			$row['map_id']                = (int) $row['map_id'];
			$row['payload']               = json_decode( (string) $row['payload'], true );
			$items[]                      = $row;
		}
		$this->maybe_cleanup();
		return array(
			'items'       => $items,
			'next_cursor' => empty( $items ) ? max( 0, (int) $cursor ) : (int) $items[ count( $items ) - 1 ]['id'],
		);
	}

	/**
	 * Idempotently acknowledge deliveries belonging to this active phone.
	 *
	 * @param array<string, mixed> $context Authenticated context.
	 * @param array<int, int>      $ids     Delivery IDs.
	 * @return int
	 */
	public function acknowledge( $context, $ids ) {
		global $wpdb;

		$count = 0;
		$now   = current_time( 'mysql', true );
		foreach ( Validation::ids( $ids ) as $id ) {
			$changed = $wpdb->query(
				$wpdb->prepare(
					'UPDATE %i SET acknowledged_at = COALESCE(acknowledged_at,%s)
					WHERE id = %d AND recipient_application_id = %d AND recipient_installation_id = %d',
					Activator::tables()['deliveries'],
					$now,
					$id,
					(int) $context['application_id'],
					(int) $context['installation_id']
				)
			);
			if ( false !== $changed ) {
				++$count;
			}
		}
		$this->maybe_cleanup();
		return $count;
	}

	/**
	 * Bind a TacNav Maps object after an authenticated application write.
	 *
	 * @param int                  $object_id Geo object ID.
	 * @param array<string, mixed> $context   Authenticated context.
	 * @return bool
	 */
	public static function bind_object( $object_id, $context ) {
		global $wpdb;

		$application_id = isset( $context['application_id'] ) ? (int) $context['application_id'] : 0;
		if ( $application_id <= 0 ) {
			return false;
		}
		$tables          = Activator::tables();
		$installation_id = isset( $context['installation_id'] ) ? (int) $context['installation_id'] : 0;
		if ( $installation_id <= 0 ) {
			$installation_id = (int) $wpdb->get_var(
				$wpdb->prepare(
					'SELECT id FROM %i WHERE application_id = %d AND revoked_at IS NULL ORDER BY id DESC LIMIT 1',
					$tables['installations'],
					$application_id
				)
			);
		}
		if ( $installation_id <= 0 ) {
			return false;
		}
		$ok = $wpdb->replace(
			$tables['bindings'],
			array(
				'object_id'       => (int) $object_id,
				'application_id'  => $application_id,
				'installation_id' => $installation_id,
				'map_id'          => (int) $context['map_id'],
				'created_at'      => current_time( 'mysql', true ),
			)
		);
		return false !== $ok;
	}

	/**
	 * Purge expired pending and old acknowledged deliveries.
	 *
	 * @param int|null $timestamp Time override for tests/jobs.
	 * @return int
	 */
	public static function cleanup( $timestamp = null ) {
		global $wpdb;

		$now     = null === $timestamp ? time() : (int) $timestamp;
		$deleted = $wpdb->query(
			$wpdb->prepare(
				'DELETE FROM %i WHERE (acknowledged_at IS NULL AND expires_at <= %s)
				OR (acknowledged_at IS NOT NULL AND acknowledged_at <= %s)',
				Activator::tables()['deliveries'],
				gmdate( 'Y-m-d H:i:s', $now ),
				gmdate( 'Y-m-d H:i:s', $now - self::ACK_SECONDS )
			)
		);
		return false === $deleted ? 0 : (int) $deleted;
	}

	/**
	 * Active recipient route for an object.
	 *
	 * @param int    $object_id Object ID.
	 * @param int    $map_id    Map ID.
	 * @param string $now       UTC timestamp.
	 * @return array<string, mixed>|null
	 */
	private function active_binding( $object_id, $map_id, $now ) {
		global $wpdb;

		$tables = Activator::tables();
		$row    = $wpdb->get_row(
			$wpdb->prepare(
				'SELECT b.application_id, b.installation_id
				FROM %i b INNER JOIN %i i ON i.id = b.installation_id
				INNER JOIN %i a ON a.id = b.application_id
				WHERE b.object_id = %d AND b.map_id = %d
				AND (b.expires_at IS NULL OR b.expires_at > %s)
				AND i.revoked_at IS NULL AND a.revoked_at IS NULL',
				$tables['bindings'],
				$tables['installations'],
				$tables['applications'],
				(int) $object_id,
				(int) $map_id,
				$now
			),
			ARRAY_A
		);
		return $row ? $row : null;
	}

	/**
	 * Opportunistic cleanup at most once per five minutes.
	 *
	 * @return void
	 */
	private function maybe_cleanup() {
		if ( get_transient( 'tacnav_devices_cleanup_lock' ) ) {
			return;
		}
		set_transient( 'tacnav_devices_cleanup_lock', 1, 5 * MINUTE_IN_SECONDS );
		self::cleanup();
	}
}
