<?php
/**
 * Catalog helpers for maps, teams, and icons.
 *
 * @package TacNav_Maps
 */

namespace TacNav\Maps;

defined( 'ABSPATH' ) || exit;

/**
 * Reads catalog meta for the studio.
 */
class Catalog {

	/**
	 * Map passport.
	 *
	 * @param int $map_id Map post ID.
	 * @return array<string, mixed>|null
	 */
	public static function get_map( $map_id ) {
		$post = get_post( $map_id );
		if ( ! $post || Post_Types::MAP !== $post->post_type ) {
			return null;
		}

		return array(
			'id'          => (int) $post->ID,
			'title'       => get_the_title( $post ),
			'description' => $post->post_content,
			'center_lat'  => (float) get_post_meta( $post->ID, 'tacnav_center_lat', true ),
			'center_lng'  => (float) get_post_meta( $post->ID, 'tacnav_center_lng', true ),
			'zoom'        => (int) get_post_meta( $post->ID, 'tacnav_zoom', true ),
			'basemap'     => self::sanitize_basemap( (string) get_post_meta( $post->ID, 'tacnav_basemap', true ) ),
			'status'      => (string) $post->post_status,
			'team_ids'    => self::team_ids( (int) $post->ID ),
		);
	}

	/**
	 * Teams listed on a map.
	 *
	 * @param int $map_id Map ID.
	 * @return array<int, int>
	 */
	public static function team_ids( $map_id ) {
		$raw = get_post_meta( (int) $map_id, 'tacnav_team_ids', true );
		if ( ! is_array( $raw ) ) {
			return array();
		}
		return array_values( array_unique( array_filter( array_map( 'intval', $raw ) ) ) );
	}

	/**
	 * Catalog teams that are listed on a map.
	 *
	 * @param array<string, mixed> $map Map passport.
	 * @return array<int, array<string, mixed>>
	 */
	public static function teams_on_map( $map ) {
		$ids = isset( $map['team_ids'] ) && is_array( $map['team_ids'] ) ? $map['team_ids'] : array();
		$out = array();
		foreach ( self::get_teams() as $team ) {
			if ( in_array( (int) $team['id'], $ids, true ) ) {
				$out[] = $team;
			}
		}
		return $out;
	}

	/**
	 * All teams.
	 *
	 * @return array<int, array<string, mixed>>
	 */
	public static function get_teams() {
		$posts = get_posts(
			array(
				'post_type'      => Post_Types::TEAM,
				'post_status'    => 'publish',
				'posts_per_page' => 50,
				'orderby'        => 'title',
				'order'          => 'ASC',
			)
		);

		$out = array();
		foreach ( $posts as $post ) {
			$out[] = self::get_team( $post->ID );
		}

		return array_values( array_filter( $out ) );
	}

	/**
	 * One team.
	 *
	 * @param int $team_id Team post ID.
	 * @return array<string, mixed>|null
	 */
	public static function get_team( $team_id ) {
		$post = get_post( $team_id );
		if ( ! $post || Post_Types::TEAM !== $post->post_type ) {
			return null;
		}

		$palette = get_post_meta( $post->ID, 'tacnav_palette_icon_ids', true );
		$presets = get_post_meta( $post->ID, 'tacnav_quick_presets', true );
		$thumb   = get_the_post_thumbnail_url( $post, 'thumbnail' );
		$full    = get_the_post_thumbnail_url( $post, 'full' );

		return array(
			'id'               => (int) $post->ID,
			'title'            => get_the_title( $post ),
			'description'      => $post->post_content,
			'color'            => self::sanitize_color( (string) get_post_meta( $post->ID, 'tacnav_color', true ) ),
			'icon_url'         => $full ? $full : '',
			'icon_thumb'       => $thumb ? $thumb : '',
			'palette_icon_ids' => is_array( $palette ) ? array_values( array_map( 'intval', $palette ) ) : array(),
			'quick_presets'    => is_array( $presets ) ? array_values( $presets ) : array(),
		);
	}

	/**
	 * All icons.
	 *
	 * @return array<int, array<string, mixed>>
	 */
	public static function get_icons() {
		$posts = get_posts(
			array(
				'post_type'      => Post_Types::ICON,
				'post_status'    => 'publish',
				'posts_per_page' => 100,
				'orderby'        => 'title',
				'order'          => 'ASC',
			)
		);

		$out = array();
		foreach ( $posts as $post ) {
			$out[] = self::get_icon( $post->ID );
		}

		return array_values( array_filter( $out ) );
	}

	/**
	 * One icon.
	 *
	 * @param int $icon_id Icon post ID.
	 * @return array<string, mixed>|null
	 */
	public static function get_icon( $icon_id ) {
		$post = get_post( $icon_id );
		if ( ! $post || Post_Types::ICON !== $post->post_type ) {
			return null;
		}

		$url = get_the_post_thumbnail_url( $post, 'full' );

		return array(
			'id'           => (int) $post->ID,
			'title'        => get_the_title( $post ),
			'url'          => $url ? $url : '',
			'availability' => self::sanitize_availability( (string) get_post_meta( $post->ID, 'tacnav_availability', true ) ),
		);
	}

	/**
	 * Allowed basemap keys.
	 *
	 * @param string $basemap Raw.
	 * @return string
	 */
	public static function sanitize_basemap( $basemap ) {
		return 'satellite-labels' === $basemap ? 'satellite-labels' : 'satellite';
	}

	/**
	 * Hex color or default gray.
	 *
	 * @param string $color Raw.
	 * @return string
	 */
	public static function sanitize_color( $color ) {
		$color = sanitize_hex_color( $color );
		return $color ? $color : '#6b7280';
	}

	/**
	 * Allowed availability keys and labels.
	 *
	 * @return array<string, string>
	 */
	public static function availability_choices() {
		return array(
			'both'   => __( 'Staff and players', 'tacnav-maps' ),
			'staff'  => __( 'Staff only', 'tacnav-maps' ),
			'player' => __( 'Players only', 'tacnav-maps' ),
		);
	}

	/**
	 * Icon availability.
	 *
	 * @param string $value Raw.
	 * @return string
	 */
	public static function sanitize_availability( $value ) {
		$keys = array_keys( self::availability_choices() );
		return in_array( $value, $keys, true ) ? $value : 'both';
	}
}
