DELETE FROM menu_items;

ALTER TABLE menu_items
    ADD COLUMN IF NOT EXISTS location VARCHAR(255);

ALTER TABLE menu_items
    ALTER COLUMN location SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_menu_items_date_location
    ON menu_items (date, location);
