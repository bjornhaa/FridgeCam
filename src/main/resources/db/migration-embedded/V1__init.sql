-- Samlet skjema for lokal utvikling (local-profilen, flyktig Zonky-base).
-- Skal gi samme skjema som db/migration/ (prod-historikken); FlywaySkjemaTest
-- sjekker det. Ved skjemaendring: ny V{n} i db/migration/, og oppdater denne.
--
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

-- Skanninger (bilde + AI-svar) og husstandens beholdning (#30).

-- Én skanning = ett bilde sendt til AI-en. Tilhører husstanden; brukeren som
-- skannet beholdes som historikk, men skanningen overlever at brukeren slettes.
CREATE TABLE skanning (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    husstand_id BIGINT        NOT NULL REFERENCES husstand (id) ON DELETE CASCADE,
    bruker_id   BIGINT        REFERENCES bruker (id) ON DELETE SET NULL,
    tidspunkt   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- VisionProvider.navn, f.eks. «anthropic/claude-opus-5-5».
    leverandor  TEXT          NOT NULL,
    -- Modellen som faktisk svarte (kan avvike ved fallback). Null når ukjent.
    modell      TEXT,
    tokens_inn  BIGINT,
    tokens_ut   BIGINT,
    -- Anslått ut fra listepris; null når modellen ikke har kjent pris.
    kostnad_usd NUMERIC(12, 6),
    -- AI-ens opprinnelige vareliste (navn, antall, sikkerhet, bokser), urørt.
    -- Sammen med beholdningen gir den forskjellen brukeren rettet.
    ai_varer    JSONB         NOT NULL,
    -- Brukerens rettede liste og når den ble lagret som beholdning (V3).
    -- Null = skanningen ble aldri lagret.
    rettede_varer JSONB,
    lagret        TIMESTAMPTZ
);

-- «Siste skanning for husstanden».
CREATE INDEX skanning_husstand_idx ON skanning (husstand_id, tidspunkt DESC);

-- Bildet i egen tabell, så spørringer mot skanning ikke drar med seg bildedata.
-- Samme bilde som AI-en fikk (maks 1500 px, ~170 kB), så boksene passer.
CREATE TABLE skanning_bilde (
    skanning_id BIGINT PRIMARY KEY REFERENCES skanning (id) ON DELETE CASCADE,
    mime_type   TEXT  NOT NULL,
    data        BYTEA NOT NULL
);

-- Det som er i kjøleskapet nå. En ny skanning erstatter hele beholdningen.
CREATE TABLE beholdning_vare (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    husstand_id BIGINT  NOT NULL REFERENCES husstand (id) ON DELETE CASCADE,
    -- Skanningen varen kom fra. Beholdningen overlever at skanningen slettes.
    skanning_id BIGINT  REFERENCES skanning (id) ON DELETE SET NULL,
    navn        TEXT    NOT NULL CHECK (btrim(navn) <> ''),
    antall      INTEGER NOT NULL CHECK (antall > 0),
    -- Hvor i bildet varen er (fra AI-en); tom for varer brukeren la til selv.
    bokser      JSONB   NOT NULL DEFAULT '[]'
);

CREATE INDEX beholdning_vare_husstand_idx ON beholdning_vare (husstand_id);
