-- V7 capped every OpenSky reference column at a guessed width (e.g. manufacturer_icao VARCHAR(10),
-- on the assumption real ICAO manufacturer codes are short). Real data proved that wrong within
-- two consecutive import attempts - the OpenSky CSV is explicitly "unlicensed... offered as is, no
-- support or guarantees" (see docs/plans/aircraft-picker.md), so a fixed width on any of these
-- fields is just a crash waiting to happen on whatever row is next. TEXT has no length limit and
-- is not slower than VARCHAR(n) in Postgres - the two are stored identically; VARCHAR(n) is purely
-- an application-level constraint, which isn't wanted here for data this repo doesn't control the
-- shape of. registration/make/model are untouched: pre-existing fields (from before this plan,
-- not sourced from this same untrusted CSV column set) that haven't shown this problem.
ALTER TABLE aircraft ALTER COLUMN manufacturer_icao TYPE TEXT;
ALTER TABLE aircraft ALTER COLUMN type_code TYPE TEXT;
ALTER TABLE aircraft ALTER COLUMN serial_number TYPE TEXT;
ALTER TABLE aircraft ALTER COLUMN operator TYPE TEXT;
ALTER TABLE aircraft ALTER COLUMN owner TYPE TEXT;
ALTER TABLE aircraft ALTER COLUMN engines TYPE TEXT;
ALTER TABLE aircraft ALTER COLUMN category_description TYPE TEXT;
