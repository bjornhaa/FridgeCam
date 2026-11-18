-- Husstandens egen AI-nøkkel (#42): hver husstand betaler for egne AI-kall.
-- Nøkkelen er kryptert med AES-256-GCM (NokkelKryptering) og knyttet til
-- husstand og leverandør, så en kryptert verdi ikke kan flyttes til en annen rad.
-- siste_tegn er de fire siste tegnene, for visning uten å dekryptere.
CREATE TABLE husstand_ai_nokkel (
    husstand_id     BIGINT      NOT NULL REFERENCES husstand (id) ON DELETE CASCADE,
    leverandor      TEXT        NOT NULL CHECK (leverandor IN ('ANTHROPIC', 'GEMINI')),
    nokkel_kryptert TEXT        NOT NULL,
    siste_tegn      TEXT        NOT NULL,
    endret          TIMESTAMPTZ NOT NULL DEFAULT now(),
    endret_av       BIGINT      REFERENCES bruker (id) ON DELETE SET NULL,
    PRIMARY KEY (husstand_id, leverandor)
);
