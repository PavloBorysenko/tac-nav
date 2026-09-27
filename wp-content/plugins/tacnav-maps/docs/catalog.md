# TacNav Maps Documentation

Read only the links whose condition matches the current task.

## Overview

- [Architecture](architecture.md) — read only when the task changes plugin disable or activation schema, rewrite `tacnav-canvas`, or whether this component registers post types or REST routes
- [Data model](data-model.md) — read only when changing `tacnav_map`, `tacnav_team`, `tacnav_icon`, table `tacnav_geo_objects`, or registered `tacnav_*` post-meta keys
- [Development](development.md) — read only when changing `assets/canvas.js`, `assets/canvas.css`, `data-tacnav-canvas`, or Leaflet enqueue
- [Hooks and APIs](hooks-and-apis.md) — read only when changing `tacnav_geo_object_visible`, `tacnav_geo_objects_query`, or `tacnav_geo_object_editable`
- [Security](security.md) — read only when changing `tacnav_manage_catalog`, `tacnav_manage_geo`, `tacnav_view_canvas`, `tacnav_purge_expired`, or REST namespace `tacnav/v1`
