<?php
/**
 * Device application types.
 *
 * @package TacNav_Devices
 */

namespace TacNav\Devices;

defined( 'ABSPATH' ) || exit;

/**
 * Describes what differs between device application types.
 */
class App_Types {

	const MORTAR = 'mortar';
	const OBJECT = 'object';

	/**
	 * Known types.
	 *
	 * The Mortar values predate this list and must not change: its option, file,
	 * and public addresses already serve a published release.
	 *
	 * @return array<string, array<string, string>>
	 */
	public static function all() {
		return array(
			self::MORTAR => array(
				'label'        => 'Tac Nav Mortar',
				'package_id'   => 'com.tacnav.mortar',
				'option'       => 'tacnav_devices_release',
				'file_name'    => 'tacnav-mortar.apk',
				'slug'         => 'tacnav-mortar',
				'query_prefix' => 'tacnav_mortar',
			),
			self::OBJECT => array(
				'label'        => 'Tac Nav Object',
				'package_id'   => 'com.tacnav.beacon',
				'option'       => 'tacnav_devices_release_object',
				'file_name'    => 'tacnav-object.apk',
				'slug'         => 'tacnav-object',
				'query_prefix' => 'tacnav_object',
			),
		);
	}

	/**
	 * Definition of a type. A stored value other than `object` is Mortar.
	 *
	 * @param string $type Type.
	 * @return array<string, string>
	 */
	public static function get( $type ) {
		return self::all()[ self::canonical( $type ) ];
	}

	/**
	 * Map a stored type to a known type.
	 *
	 * Staff once typed this value freely, so anything that is not an Object
	 * application is treated as a Mortar application.
	 *
	 * @param string $type Stored type.
	 * @return string
	 */
	public static function canonical( $type ) {
		return self::OBJECT === $type ? self::OBJECT : self::MORTAR;
	}

	/**
	 * Decide which type a save stores.
	 *
	 * An update keeps its stored value, including a legacy free-text one, and a
	 * request cannot move a record between application types.
	 *
	 * @param string      $requested Requested type.
	 * @param string|null $stored    Stored type, null for a new record.
	 * @return string|\WP_Error
	 */
	public static function resolve_save_type( $requested, $stored ) {
		if ( null !== $stored ) {
			if ( '' !== $requested && self::canonical( $requested ) !== self::canonical( $stored ) ) {
				return new \WP_Error( 'tacnav_application_type_change', __( 'An application cannot change its type.', 'tacnav-devices' ), array( 'status' => 400 ) );
			}
			return $stored;
		}
		if ( ! isset( self::all()[ $requested ] ) ) {
			return new \WP_Error( 'tacnav_application_type', __( 'Choose a known application type.', 'tacnav-devices' ), array( 'status' => 400 ) );
		}
		return $requested;
	}

	/**
	 * Keep only records shown on one type's tab.
	 *
	 * @param array<int, array<string, mixed>> $rows Application rows.
	 * @param string                           $type Known type.
	 * @return array<int, array<string, mixed>>
	 */
	public static function filter_rows( $rows, $type ) {
		$out = array();
		foreach ( $rows as $row ) {
			if ( self::canonical( (string) $row['application_type'] ) === $type ) {
				$out[] = $row;
			}
		}
		return $out;
	}

	/**
	 * Whether a package may activate an application.
	 *
	 * Tac Nav Mortar has always sent its package id, so an empty value can only
	 * come from an older client and is treated as Mortar.
	 *
	 * @param string $type       Stored application type.
	 * @param string $package_id Requesting package.
	 * @return bool
	 */
	public static function package_matches( $type, $package_id ) {
		$package = '' === $package_id ? self::all()[ self::MORTAR ]['package_id'] : $package_id;
		return self::get( $type )['package_id'] === $package;
	}

	/**
	 * Validate reference settings with the rules of one type.
	 *
	 * @param string $type  Stored type.
	 * @param mixed  $input Submitted values.
	 * @param string $name  Application name, the fallback object name.
	 * @return array<string, mixed>|\WP_Error
	 */
	public static function reference_settings( $type, $input, $name = '' ) {
		if ( self::OBJECT === self::canonical( $type ) ) {
			return Validation::object_reference_settings( $input, $name );
		}
		return Validation::reference_settings( $input );
	}
}
