CREATE TABLE IF NOT EXISTS CAMEL_UNIQUE_DIGEST_PER_FILENAME (processorName VARCHAR(255), digest VARCHAR(255), fileName varchar(255),createdAt TIMESTAMP);
--CREATE UNIQUE INDEX IF NOT EXISTS  CAMEL_UNIQUE_DIGEST_PER_FILENAME_IDX ON CAMEL_UNIQUE_DIGEST_PER_FILENAME (processorName,digest,fileName); --CREATE INDEX IF NOT EXISTS NOT SUPPORTED in 9.4, requires postgres 9.5..

