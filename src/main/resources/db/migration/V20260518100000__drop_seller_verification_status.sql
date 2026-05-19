ALTER TABLE sellers DROP CONSTRAINT IF EXISTS chk_sellers_verification_status;
ALTER TABLE sellers DROP COLUMN verification_status;
