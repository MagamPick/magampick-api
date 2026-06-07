ALTER TABLE stores
    ADD COLUMN representative_name VARCHAR(30) NOT NULL DEFAULT '',
    ADD COLUMN open_date           DATE        NOT NULL DEFAULT '1970-01-01';

ALTER TABLE stores
    ALTER COLUMN representative_name DROP DEFAULT,
    ALTER COLUMN open_date           DROP DEFAULT;
