<?php
/**
 * Closed registry of geo-object roles.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Lists the roles an object may have and the kinds each role allows.
 *
 * The server stores and filters by role but never acts on it. Applications
 * decide what a role means for their game.
 */
class Object_Roles {

	const FILTER = 'tacnav_geo_object_roles';

	const AREA_KINDS = array( 'polygon', 'circle' );

	/**
	 * Registered roles keyed by slug. The ordinary role is the empty slug.
	 *
	 * @return array<string, array{label: string, kinds: array<int, string>, assignable: bool}>
	 */
	public static function all() {
		$defaults = array(
			''          => array(
				'label'      => __( 'Object', 'tacnav-maps' ),
				'kinds'      => Geometry_Service::KINDS,
				'assignable' => true,
			),
			'minefield' => array(
				'label'      => __( 'Minefield', 'tacnav-maps' ),
				'kinds'      => self::AREA_KINDS,
				'assignable' => true,
			),
			'hint'      => array(
				'label'      => __( 'Hint area', 'tacnav-maps' ),
				'kinds'      => self::AREA_KINDS,
				'assignable' => false,
			),
		);

		/**
		 * Filters the geo-object role registry.
		 *
		 * Each entry is keyed by slug and has label, kinds, and assignable.
		 * The ordinary role '' is always present.
		 *
		 * @param array<string, array<string, mixed>> $defaults Registry.
		 */
		$filtered = apply_filters( 'tacnav_geo_object_roles', $defaults );
		/** @var mixed $filtered A callback may return anything. */
		if ( ! is_array( $filtered ) ) {
			return $defaults;
		}

		$roles = array();
		foreach ( $filtered as $slug => $role ) {
			$slug = sanitize_key( (string) $slug );
			if ( ! is_array( $role ) || strlen( $slug ) > 32 ) {
				continue;
			}
			$kinds          = isset( $role['kinds'] ) && is_array( $role['kinds'] ) ? $role['kinds'] : array();
			$roles[ $slug ] = array(
				'label'      => isset( $role['label'] ) ? (string) $role['label'] : $slug,
				'kinds'      => array_values( array_intersect( Geometry_Service::KINDS, $kinds ) ),
				'assignable' => ! empty( $role['assignable'] ),
			);
		}
		$roles[''] = $defaults[''];

		return $roles;
	}

	/**
	 * Whether a slug is registered.
	 *
	 * @param mixed $slug Role slug from a request.
	 * @return bool
	 */
	public static function exists( $slug ) {
		return is_string( $slug ) && array_key_exists( $slug, self::all() );
	}

	/**
	 * Check a role for a kind.
	 *
	 * @param mixed  $slug Role slug.
	 * @param string $kind Geometry kind.
	 * @return true|\WP_Error
	 */
	public static function validate( $slug, $kind ) {
		$roles = self::all();
		if ( ! is_string( $slug ) || ! array_key_exists( $slug, $roles ) ) {
			return self::error( __( 'Unknown object role.', 'tacnav-maps' ) );
		}
		if ( ! in_array( (string) $kind, $roles[ $slug ]['kinds'], true ) ) {
			return self::error( __( 'This object role is not allowed for this shape.', 'tacnav-maps' ) );
		}
		return true;
	}

	/**
	 * Registry entries for the map studio.
	 *
	 * @return array<int, array{slug: string, label: string, kinds: array<int, string>, assignable: bool}>
	 */
	public static function for_canvas() {
		$out = array();
		foreach ( self::all() as $slug => $role ) {
			$out[] = array(
				'slug'       => (string) $slug,
				'label'      => $role['label'],
				'kinds'      => $role['kinds'],
				'assignable' => $role['assignable'],
			);
		}
		return $out;
	}

	/**
	 * Role refusal.
	 *
	 * @param string $message Message.
	 * @return \WP_Error
	 */
	private static function error( $message ) {
		return new \WP_Error( 'tacnav_geo_role', $message, array( 'status' => 400 ) );
	}
}
