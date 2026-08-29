-- Pilot/account split (see docs/plans/pilot-account-split.md). Expand only, per this repo's own
-- migration-compatibility rule - drops nothing, so the previous release's code (which only ever
-- reads/writes pilot.email/disabled_at directly) keeps working unaffected during a deploy.

CREATE TABLE account (
    pilot_id     UUID PRIMARY KEY REFERENCES pilot(id),
    email        VARCHAR(255) NOT NULL,
    disabled_at  TIMESTAMP WITH TIME ZONE,
    CONSTRAINT account_email_unique UNIQUE (email)
);

-- Backfill: every pilot with an auth_identity row today is, today, exactly "has an account."
INSERT INTO account (pilot_id, email, disabled_at)
SELECT p.id, p.email, p.disabled_at
FROM pilot p
WHERE p.email IS NOT NULL
  AND EXISTS (SELECT 1 FROM auth_identity ai WHERE ai.pilot_id = p.id);

ALTER TABLE pilot ADD COLUMN created_by UUID REFERENCES pilot(id);
ALTER TABLE referral_code ADD COLUMN claims_pilot_id UUID REFERENCES pilot(id);
