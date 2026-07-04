ALTER TABLE novels
    ADD COLUMN source_book_id VARCHAR(255);

CREATE INDEX idx_novels_user_site_book ON novels (user_id, site_name, source_book_id);
