# Hooks and APIs

## Purpose

Name the access hooks other code may attach to without opening the resolver class.

## Responsibilities and boundaries

`tacnav_geo_object_visible` receives the default boolean, the object, the viewer, and the request (`surface`, `map_id`). `tacnav_geo_object_editable` uses the same signature for edit and delete. `tacnav_geo_objects_query` filters listing args (including `include_expired`) with viewer and request.

Default staff viewers see non-expired objects. Empty visibility means all authorized viewers. Team members may edit only `origin=team` objects whose belonging is their team. Those defaults are owned here as hook contracts; storage stays on Data model.

## Implementation references

- `wp-content/plugins/tacnav-maps/app/Access.php` — resolvers and filters
- `wp-content/plugins/tacnav-maps/app/Geo_Store.php` — `tacnav_geo_objects_query`
