<?php
/**
 * Organizer role and map-staff capabilities.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Capability registry.
 */
class Roles {

	const ROLE        = 'tacnav_organizer';
	const ROLE_PLAYER = 'tacnav_player';

	const CAP_CATALOG = 'tacnav_manage_catalog';
	const CAP_GEO     = 'tacnav_manage_geo';
	const CAP_CANVAS  = 'tacnav_view_canvas';
	const CAP_PURGE   = 'tacnav_purge_expired';

	/**
	 * All staff capabilities.
	 *
	 * @return array<int, string>
	 */
	public static function caps() {
		return array(
			self::CAP_CATALOG,
			self::CAP_GEO,
			self::CAP_CANVAS,
			self::CAP_PURGE,
		);
	}

	/**
	 * Create the Organizer role and grant caps to Administrator.
	 *
	 * @return void
	 */
	public static function install() {
		$caps = array(
			'read'         => true,
			'upload_files' => true,
		);
		foreach ( self::caps() as $cap ) {
			$caps[ $cap ] = true;
		}

		$role = get_role( self::ROLE );
		if ( ! $role ) {
			add_role( self::ROLE, __( 'Organizer', 'tacnav-maps' ), $caps );
		} else {
			foreach ( $caps as $cap => $grant ) {
				$role->add_cap( $cap );
			}
		}

		$admin = get_role( 'administrator' );
		if ( $admin ) {
			foreach ( self::caps() as $cap ) {
				$admin->add_cap( $cap );
			}
		}

		$player = get_role( self::ROLE_PLAYER );
		if ( ! $player ) {
			add_role( self::ROLE_PLAYER, __( 'Player', 'tacnav-maps' ), array( 'read' => true ) );
		} else {
			foreach ( self::caps() as $cap ) {
				$player->remove_cap( $cap );
			}
		}
	}

	/**
	 * Whether the user is map staff.
	 *
	 * @param int|null $user_id User ID. Current user when null.
	 * @return bool
	 */
	public static function is_staff( $user_id = null ) {
		if ( null === $user_id ) {
			return current_user_can( self::CAP_GEO ) && current_user_can( self::CAP_CANVAS );
		}

		$user = get_userdata( (int) $user_id );
		return $user && user_can( $user, self::CAP_GEO ) && user_can( $user, self::CAP_CANVAS );
	}
}
