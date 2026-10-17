<?php
/**
 * Device application validation rules.
 *
 * @package TacNav_Devices
 */

namespace TacNav\Devices;

defined( 'ABSPATH' ) || exit;

/**
 * Pure scope and context validation.
 */
class Validation {

	/**
	 * Normalize positive integer identifiers.
	 *
	 * @param mixed $values Candidate values.
	 * @return array<int, int>
	 */
	public static function ids( $values ) {
		if ( ! is_array( $values ) ) {
			return array();
		}
		return array_values( array_unique( array_filter( array_map( 'intval', $values ) ) ) );
	}

	/**
	 * Validate defaults against allowed scopes.
	 *
	 * @param array<int, int> $maps         Allowed map IDs.
	 * @param array<int, int> $teams        Allowed team IDs.
	 * @param int             $default_map  Default map ID.
	 * @param int             $default_team Default team ID.
	 * @param callable        $team_on_map  Receives map and team IDs.
	 * @return true|\WP_Error
	 */
	public static function defaults( $maps, $teams, $default_map, $default_team, $team_on_map ) {
		if ( $default_map > 0 && ! in_array( $default_map, $maps, true ) ) {
			return new \WP_Error( 'tacnav_default_map_scope', __( 'Default map must be in the allowed map scope.', 'tacnav-devices' ) );
		}
		if ( $default_team > 0 && ! in_array( $default_team, $teams, true ) ) {
			return new \WP_Error( 'tacnav_default_team_scope', __( 'Default team must be in the allowed team scope.', 'tacnav-devices' ) );
		}
		if ( $default_map > 0 && $default_team > 0 && ! call_user_func( $team_on_map, $default_map, $default_team ) ) {
			return new \WP_Error( 'tacnav_default_team_map', __( 'Default team must be listed on the default map.', 'tacnav-devices' ) );
		}
		return true;
	}

	/**
	 * Validate a submitted current map/team without trusting the client.
	 *
	 * @param array<string, mixed> $application Application definition.
	 * @param int                  $map_id      Submitted map.
	 * @param int                  $team_id     Submitted team.
	 * @param callable             $team_on_map Receives map and team IDs.
	 * @return array<string, int>|\WP_Error
	 */
	public static function context( $application, $map_id, $team_id, $team_on_map ) {
		$maps  = isset( $application['allowed_map_ids'] ) ? self::ids( $application['allowed_map_ids'] ) : array();
		$teams = isset( $application['allowed_team_ids'] ) ? self::ids( $application['allowed_team_ids'] ) : array();
		if ( ! in_array( $map_id, $maps, true ) || ! in_array( $team_id, $teams, true ) ) {
			return new \WP_Error( 'tacnav_application_scope', __( 'The selected map or team is outside the activated scope.', 'tacnav-devices' ), array( 'status' => 403 ) );
		}
		if ( ! call_user_func( $team_on_map, $map_id, $team_id ) ) {
			return new \WP_Error( 'tacnav_application_membership', __( 'The selected team is not listed on the selected map.', 'tacnav-devices' ), array( 'status' => 403 ) );
		}
		return array(
			'map_id'  => $map_id,
			'team_id' => $team_id,
		);
	}

	/**
	 * Allowed firing modes and their staff labels.
	 *
	 * @return array<string, string>
	 */
	public static function firing_mode_choices() {
		return array(
			'button'   => __( 'Button', 'tacnav-devices' ),
			'acoustic' => __( 'Acoustic', 'tacnav-devices' ),
		);
	}

	/**
	 * Allowed aiming modes and their staff labels.
	 *
	 * @return array<string, string>
	 */
	public static function aiming_mode_choices() {
		return array(
			'free'                    => __( 'Free', 'tacnav-devices' ),
			'precise_coordinates'     => __( 'Precise coordinates', 'tacnav-devices' ),
			'approximate_coordinates' => __( 'Approximate coordinates', 'tacnav-devices' ),
		);
	}

	/**
	 * Default mortar reference used when staff omit a field.
	 *
	 * @return array<string, mixed>
	 */
	public static function reference_defaults() {
		return array(
			'mortar_name'                 => 'Mortar 1',
			'firing_modes'                => array( 'button' ),
			'aiming_modes'                => array( 'free' ),
			'approximate_radius_m'        => 30.0,
			'elevation_reference_degrees' => 60.0,
			'impact_icon_id'              => 0,
			'min_elevation_degrees'       => 60.0,
			'max_elevation_degrees'       => 85.0,
			'max_distance_m'              => 200.0,
			'min_distance_m'              => 10.0,
			'damage_radius_m'             => 5.0,
			'edge_probability'            => 0.5,
			'flight_duration_seconds'     => 20,
			'impact_ttl_seconds'          => 30,
			'max_location_accuracy_m'     => 10.0,
			'max_location_age_seconds'    => 5,
			'acoustic_threshold_dbfs'     => -20.0,
			'acoustic_impulse_ms'         => 80,
			'acoustic_debounce_ms'        => 400,
		);
	}

	/**
	 * Normalize staff reference settings for storage and activation.
	 *
	 * @param mixed $input Submitted values.
	 * @return array<string, mixed>|\WP_Error
	 */
	public static function reference_settings( $input ) {
		$defaults = self::reference_defaults();
		$source   = is_array( $input ) ? $input : array();
		$firing   = self::choice_set(
			isset( $source['firing_modes'] ) ? $source['firing_modes'] : $defaults['firing_modes'],
			array_keys( self::firing_mode_choices() )
		);
		$aiming   = self::choice_set(
			isset( $source['aiming_modes'] ) ? $source['aiming_modes'] : $defaults['aiming_modes'],
			array_keys( self::aiming_mode_choices() )
		);
		if ( array() === $firing ) {
			return new \WP_Error( 'tacnav_firing_required', __( 'Select at least one firing mode.', 'tacnav-devices' ) );
		}
		if ( array() === $aiming ) {
			return new \WP_Error( 'tacnav_aiming_required', __( 'Select at least one aiming mode.', 'tacnav-devices' ) );
		}

		$name = isset( $source['mortar_name'] ) ? trim( (string) $source['mortar_name'] ) : $defaults['mortar_name'];
		if ( '' === $name ) {
			return new \WP_Error( 'tacnav_mortar_name', __( 'Mortar name is required.', 'tacnav-devices' ) );
		}

		$merged = array(
			'mortar_name'                 => $name,
			'firing_modes'                => $firing,
			'aiming_modes'                => $aiming,
			'approximate_radius_m'        => self::positive_float( $source, 'approximate_radius_m', $defaults['approximate_radius_m'] ),
			'elevation_reference_degrees' => self::bounded_float( $source, 'elevation_reference_degrees', $defaults['elevation_reference_degrees'], 0, 90 ),
			'impact_icon_id'              => isset( $source['impact_icon_id'] ) ? (int) $source['impact_icon_id'] : 0,
			'min_elevation_degrees'       => self::bounded_float( $source, 'min_elevation_degrees', $defaults['min_elevation_degrees'], 0, 90 ),
			'max_elevation_degrees'       => self::bounded_float( $source, 'max_elevation_degrees', $defaults['max_elevation_degrees'], 0, 90 ),
			'max_distance_m'              => self::positive_float( $source, 'max_distance_m', $defaults['max_distance_m'] ),
			'min_distance_m'              => self::positive_float( $source, 'min_distance_m', $defaults['min_distance_m'] ),
			'damage_radius_m'             => self::positive_float( $source, 'damage_radius_m', $defaults['damage_radius_m'] ),
			'edge_probability'            => self::bounded_float( $source, 'edge_probability', $defaults['edge_probability'], 0, 1 ),
			'flight_duration_seconds'     => self::positive_int( $source, 'flight_duration_seconds', $defaults['flight_duration_seconds'] ),
			'impact_ttl_seconds'          => self::non_negative_int( $source, 'impact_ttl_seconds', $defaults['impact_ttl_seconds'] ),
			'max_location_accuracy_m'     => self::positive_float( $source, 'max_location_accuracy_m', $defaults['max_location_accuracy_m'] ),
			'max_location_age_seconds'    => self::positive_int( $source, 'max_location_age_seconds', $defaults['max_location_age_seconds'] ),
			'acoustic_threshold_dbfs'     => self::float_value( $source, 'acoustic_threshold_dbfs', $defaults['acoustic_threshold_dbfs'] ),
			'acoustic_impulse_ms'         => self::positive_int( $source, 'acoustic_impulse_ms', $defaults['acoustic_impulse_ms'] ),
			'acoustic_debounce_ms'        => self::positive_int( $source, 'acoustic_debounce_ms', $defaults['acoustic_debounce_ms'] ),
		);
		if ( $merged['min_elevation_degrees'] >= $merged['max_elevation_degrees'] ) {
			return new \WP_Error( 'tacnav_elevation_range', __( 'Minimum elevation must be below maximum elevation.', 'tacnav-devices' ) );
		}
		if ( $merged['min_distance_m'] >= $merged['max_distance_m'] ) {
			return new \WP_Error( 'tacnav_distance_range', __( 'Minimum distance must be below maximum distance.', 'tacnav-devices' ) );
		}
		if ( $merged['impact_icon_id'] < 0 ) {
			$merged['impact_icon_id'] = 0;
		}
		return $merged;
	}

	/**
	 * Keep only allowed string choices.
	 *
	 * @param mixed              $values  Submitted values.
	 * @param array<int, string> $allowed Allowed values.
	 * @return array<int, string>
	 */
	private static function choice_set( $values, $allowed ) {
		if ( ! is_array( $values ) ) {
			$values = array( $values );
		}
		$out = array();
		foreach ( $values as $value ) {
			$key = is_string( $value ) ? $value : '';
			if ( in_array( $key, $allowed, true ) && ! in_array( $key, $out, true ) ) {
				$out[] = $key;
			}
		}
		return $out;
	}

	/**
	 * Positive float or default.
	 *
	 * @param array<string, mixed> $source  Input.
	 * @param string               $name    Key.
	 * @param float                $fallback Default.
	 * @return float
	 */
	private static function positive_float( $source, $name, $fallback ) {
		$value = self::float_value( $source, $name, $fallback );
		return $value > 0 ? $value : $fallback;
	}

	/**
	 * Float clamped to a closed interval.
	 *
	 * @param array<string, mixed> $source  Input.
	 * @param string               $name    Key.
	 * @param float                $fallback Default.
	 * @param float                $min      Minimum.
	 * @param float                $max      Maximum.
	 * @return float
	 */
	private static function bounded_float( $source, $name, $fallback, $min, $max ) {
		$value = self::float_value( $source, $name, $fallback );
		if ( $value < $min || $value > $max ) {
			return $fallback;
		}
		return $value;
	}

	/**
	 * Float from input.
	 *
	 * @param array<string, mixed> $source  Input.
	 * @param string               $name    Key.
	 * @param float                $fallback Default.
	 * @return float
	 */
	private static function float_value( $source, $name, $fallback ) {
		if ( ! isset( $source[ $name ] ) || '' === $source[ $name ] ) {
			return $fallback;
		}
		return (float) $source[ $name ];
	}

	/**
	 * Positive integer or default.
	 *
	 * @param array<string, mixed> $source  Input.
	 * @param string               $name    Key.
	 * @param int                  $fallback Default.
	 * @return int
	 */
	private static function positive_int( $source, $name, $fallback ) {
		$value = self::non_negative_int( $source, $name, $fallback );
		return $value > 0 ? $value : $fallback;
	}

	/**
	 * Non-negative integer or default.
	 *
	 * @param array<string, mixed> $source  Input.
	 * @param string               $name    Key.
	 * @param int                  $fallback Default.
	 * @return int
	 */
	private static function non_negative_int( $source, $name, $fallback ) {
		if ( ! isset( $source[ $name ] ) || '' === $source[ $name ] ) {
			return $fallback;
		}
		$value = (int) $source[ $name ];
		return $value >= 0 ? $value : $fallback;
	}
}
