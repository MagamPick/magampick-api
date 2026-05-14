-- Enable PostGIS for location-based queries (stores, addresses).
-- Used by GEOGRAPHY(POINT, 4326) columns + GIST indexes.
CREATE EXTENSION IF NOT EXISTS postgis;
