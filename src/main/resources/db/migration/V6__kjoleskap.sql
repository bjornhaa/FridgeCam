-- Flere kjøleskap per husstand (#61): hjemme, hytta … Skanning og beholdning
-- hører til et kjøleskap. Eksisterende data flyttes til ett standard kjøleskap
-- («Hjemme») per husstand.
CREATE TABLE kjoleskap (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    husstand_id BIGINT      NOT NULL REFERENCES husstand (id) ON DELETE CASCADE,
    navn        TEXT        NOT NULL CHECK (btrim(navn) <> ''),
    opprettet   TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Mål for de sammensatte fremmednøklene under: et kjøleskap og raden som peker
    -- på det må høre til samme husstand.
    CONSTRAINT kjoleskap_id_husstand_unik UNIQUE (id, husstand_id)
);

CREATE UNIQUE INDEX kjoleskap_navn_unik ON kjoleskap (husstand_id, lower(btrim(navn)));

INSERT INTO kjoleskap (husstand_id, navn) SELECT id, 'Hjemme' FROM husstand;

ALTER TABLE skanning ADD COLUMN kjoleskap_id BIGINT;
UPDATE skanning s SET kjoleskap_id = k.id FROM kjoleskap k WHERE k.husstand_id = s.husstand_id;
ALTER TABLE skanning
    ALTER COLUMN kjoleskap_id SET NOT NULL,
    ADD CONSTRAINT skanning_kjoleskap_fk FOREIGN KEY (kjoleskap_id, husstand_id)
        REFERENCES kjoleskap (id, husstand_id) ON DELETE CASCADE;
CREATE INDEX skanning_kjoleskap_idx ON skanning (kjoleskap_id, tidspunkt DESC);

ALTER TABLE beholdning_vare ADD COLUMN kjoleskap_id BIGINT;
UPDATE beholdning_vare v SET kjoleskap_id = k.id FROM kjoleskap k WHERE k.husstand_id = v.husstand_id;
ALTER TABLE beholdning_vare
    ALTER COLUMN kjoleskap_id SET NOT NULL,
    ADD CONSTRAINT beholdning_vare_kjoleskap_fk FOREIGN KEY (kjoleskap_id, husstand_id)
        REFERENCES kjoleskap (id, husstand_id) ON DELETE CASCADE;
DROP INDEX beholdning_vare_husstand_idx;
CREATE INDEX beholdning_vare_kjoleskap_idx ON beholdning_vare (kjoleskap_id);
