-- Den rettede varelisten lagres på skanningen (#32). Beholdningen erstattes
-- ved neste skanning, men AI-ens liste mot brukerens rettede liste trengs
-- senere for å måle AI-kvaliteten. Null = skanningen ble aldri lagret.
ALTER TABLE skanning
    ADD COLUMN rettede_varer JSONB,
    ADD COLUMN lagret        TIMESTAMPTZ;
