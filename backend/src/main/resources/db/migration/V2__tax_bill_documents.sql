-- Where each bill's generated PDF lives in object storage (Amazon S3). All three are NULL until the PDF is
-- first generated. Columns on tax_bills rather than a new table: the schema stays at nine tables.
ALTER TABLE tax_bills
    ADD COLUMN document_key           VARCHAR(255) UNIQUE,
    ADD COLUMN document_size_bytes    BIGINT CHECK (document_size_bytes > 0),
    ADD COLUMN document_generated_at  TIMESTAMPTZ,
    ADD CONSTRAINT tax_bills_document_all_or_none CHECK (
        (document_key IS NULL) = (document_size_bytes IS NULL)
        AND (document_key IS NULL) = (document_generated_at IS NULL));
