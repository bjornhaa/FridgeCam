-- Husstanden velger AI-leverandør og modell selv (#54). Null = appens standard
-- (AI_PROVIDER/AI_MODEL). Modellen sjekkes mot listen i AiModeller i koden.
ALTER TABLE husstand
    ADD COLUMN ai_leverandor TEXT CONSTRAINT husstand_ai_leverandor_gyldig CHECK (ai_leverandor IN ('ANTHROPIC', 'GEMINI')),
    ADD COLUMN ai_modell     TEXT,
    ADD CONSTRAINT husstand_ai_valg_komplett CHECK ((ai_leverandor IS NULL) = (ai_modell IS NULL));
