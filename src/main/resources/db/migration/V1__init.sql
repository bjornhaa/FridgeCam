-- Grunntabeller: brukere og husstander.
--
-- Beholdning, faste varer og handleliste hører til en husstand, ikke en
-- bruker. Husstanden er med fra start slik at flere brukere senere kan dele
-- kjøleskap uten at data må flyttes.

CREATE TABLE bruker (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    epost        TEXT        NOT NULL,
    navn         TEXT        NOT NULL,
    passord_hash TEXT        NOT NULL,
    opprettet    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- E-post er unik uavhengig av store/små bokstaver.
CREATE UNIQUE INDEX bruker_epost_unik ON bruker (lower(epost));

CREATE TABLE husstand (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    navn      TEXT        NOT NULL,
    opprettet TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE husstand_medlem (
    husstand_id BIGINT      NOT NULL REFERENCES husstand (id) ON DELETE CASCADE,
    bruker_id   BIGINT      NOT NULL REFERENCES bruker (id) ON DELETE CASCADE,
    rolle       TEXT        NOT NULL CHECK (rolle IN ('EIER', 'MEDLEM')),
    lagt_til    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (husstand_id, bruker_id)
);

-- Oppslag «hvilke husstander er denne brukeren med i».
CREATE INDEX husstand_medlem_bruker_idx ON husstand_medlem (bruker_id);
