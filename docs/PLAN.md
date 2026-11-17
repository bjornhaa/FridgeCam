# Implementasjonsplan

Planen tas steg for steg. Hver fase blir til GitHub-issues etter hvert som den blir aktuell.

## Kjerneidé

1. Brukeren tar bilde av kjøleskapet.
2. AI returnerer en liste over varer den ser.
3. Brukeren retter opp listen (legger til / fjerner / justerer antall).
4. Listen lagres som *nåværende beholdning* for husstanden.
5. Beholdningen sammenlignes med *faste varer* (det husstanden alltid vil ha) → **handleliste**.

## Stack (samme oppsett som talik-ai)

- **Backend:** Kotlin + Spring Boot 4 (Java 25), Spring JDBC med `NamedParameterJdbcTemplate` (ikke JPA)
- **Database:** PostgreSQL fra start, Flyway-migreringer
  - Lokalt og i tester: Zonky embedded Postgres (ingen Docker nødvendig)
  - Prod: Fly Postgres (samme hovedversjon som lokalt, Postgres 18), koblet til via `SPRING_DATASOURCE_*`-secrets
  - To migreringstrær som i talik-ai: `db/migration/` (prod-historikk) og `db/migration-embedded/`
    (samlet skjema + testbruker for lokal utvikling), holdt like av `FlywaySkjemaTest`
- **Auth:** Stateless JWT (som talik-ai)
- **Frontend:** Vite + React + TypeScript + Tailwind, PWA. Bygges inn i jar-en via `with-frontend`-profil
- **Drift:** Dockerfile (to-trinns), `fly.toml` (region `arn`), GitHub Actions: test → deploy på push til `main`

## Datamodell (utkast)

Husstand er med i modellen fra dag én, selv om invitasjon av andre brukere kommer senere.
Da slipper vi en datamigrering når flere skal dele kjøleskap.

- `bruker` – innlogging
- `husstand` – en delt beholdning og handleliste
- `husstand_medlem` – kobler bruker ↔ husstand (rolle: eier / medlem)
- `skanning` – ett bilde-opptak (tidspunkt, hvem, AI-leverandør/modell, tokens/kostnad, AI-ens opprinnelige liste)
- `skanning_bilde` – bildet som ble sendt til AI-en (maks 1500 px, ~170 kB), egen tabell
- `beholdning_vare` – nåværende varer i husstandens kjøleskap
- `fast_vare` – varer husstanden alltid vil ha, med ønsket minimumsantall
- `handleliste_vare` – genererte og manuelt lagt til varer, med avhuking
- API-nøkkel per husstand (kryptert), fra fase 4

Ved første registrering opprettes automatisk en husstand med brukeren som eier.

## Fase 0 – Oppsett
- [x] Opprette public GitHub-repo
- [x] Spring Boot-skjelett i Maven-prosjektet (Kotlin, Java 25, actuator health)
- [x] Postgres: Zonky embedded lokalt (`local`-profil), Flyway, første migrering
- [x] Vite/React-skall som serveres fra backend (`with-frontend`-profil)
- [x] GitHub Actions: bygg og test på push/PR

## Fase 1 – Bilde → vareliste
- [x] PWA: kameraknapp (`<input capture="environment">`), forhåndsvisning
- [x] Krympe/komprimere bilde i nettleseren før opplasting
- [x] Backend: `POST /api/analyze` som sender bildet til AI og returnerer JSON
- [x] `VisionProvider`-grensesnitt slik at AI-leverandør kan byttes (valgt via `AI_PROVIDER`)
- [x] Implementasjon: Anthropic (offisiell Java SDK, modell konfigurerbar via `AI_MODEL`)
- [x] Implementasjon: Google Gemini (gratisnivå, for testing/utvikling)
- [x] Felles prompt + JSON-skjema for strukturert svar (navn, antall, sikkerhet), validert i backend
- [x] Vise resultatlisten i appen
- [x] API-nøkler via miljøvariabler / Fly secrets, aldri i frontend

## Fase 2 – Brukere, husstand og lagring
- [x] Tabeller: bruker, husstand, husstand_medlem (V1)
- [x] Innlogging i backend med JWT; husstand opprettes ved registrering (#26)
- [x] ~~Registreringskode fra miljøvariabel (#27)~~ – fjernet igjen; erstattet av API-nøkkel per husstand i fase 4
- [x] Egne Flyway-migreringer for lokal utvikling med testbruker, som i talik-ai
- [x] Innlogging i frontend (#28)
- [ ] Navigasjon: Skann / Kjøleskapet / Profil (#29)
- [x] Datamodell for skanning, bilde og beholdning (#30)
- [ ] Redigerbar vareliste etter skanning (#31)
- [ ] Lagre skanning som beholdning; ny skanning erstatter beholdningen (#32)
- [ ] Vis kjøleskapet med siste bilde og bokser (#33)

## Fase 3 – Handleliste
- [ ] Normalisering av varenavn (f.eks. «lettmelk» ≈ «melk»), så beholdning kan sammenlignes med faste varer
- [ ] Administrere faste varer (med ønsket minimumsantall)
- [ ] Generere handleliste = faste varer − beholdning
- [ ] Manuelt legge til / huke av varer i handlelisten
- [ ] Dele handlelisten (kopiere tekst / del-knapp via Web Share API)

## Fase 4 – PWA og drift
- [ ] `manifest.webmanifest`, ikoner, service worker (installerbar, offline-visning av handleliste)
- [ ] Dockerfile + `fly.toml`, Fly Postgres, deploy til Fly.io (HTTPS inkludert)
  - Maskinen kan gå i dvale (`auto_stop_machines`, `min_machines_running = 0`); kaldstart på noen sekunder er greit
  - Kostnadsvurdering: appmaskin i dvale + Postgres-maskin og volum (bildene ligger i Postgres)
- [ ] API-nøkkel per husstand, kryptert med AES-256-GCM (som Spotify-tokens i talik-ai), før appen legges ut
  - Hver husstand betaler for egne AI-kall, så åpen registrering koster ikke appeieren AI-kreditt
  - Ingen felles nøkkel fra miljøet i prod; uten nøkkel ber appen husstanden legge inn en
  - Alle medlemmer i husstanden deler nøkkelen
- [ ] Grense for lagrede bilder per husstand (åpen registrering skal ikke kunne fylle Postgres-volumet)
- [ ] Deploy fra GitHub Actions ved push til `main`
- [ ] Backup av Postgres

## Fase 5 – Flere brukere i husstanden
- [ ] Invitere andre til husstanden (invitasjonslenke/kode)
- [ ] Felles handleliste som oppdateres for alle medlemmer
- [ ] Forlate / fjerne medlem

## Fase 6 – Åpne for andre
- [ ] Flere AI-leverandører (f.eks. OpenAI) bak samme grensesnitt
- [ ] Husstand velger leverandør (nøkkel per husstand finnes allerede fra fase 4)
- [ ] Begrensning av antall kall per husstand (kostnadskontroll)

## Senere / ideer
- Flere bilder per skanning (dører, skuffer); da kan nye skanninger slås sammen med beholdningen
- Høyere bildeoppløsning hvis etiketter på små pakker skal leses (måles med `SammenligningTest`)
- Flytte bilder til objektlagring (Tigris) hvis Postgres-volumet blir for lite
- Utløpsdatoer og varsler
- Oppskriftsforslag basert på beholdning

## Beslutninger
- **AI:** Flere leverandører bak et felles grensesnitt. Claude Opus 5.5 (`medium`) som standard etter måling mot fasit (#11); Sonnet 5.5 som raskere/billigere alternativ. Gemini fungerer, men gratisnivået avviser mange kall.
- **Hosting:** Fly.io
- **Database:** PostgreSQL fra start (Zonky lokalt, Fly Postgres i prod)
- **Stack:** Samme som talik-ai (Spring Boot + Spring JDBC + Flyway + JWT, Vite/React)
- **Brukere:** Husstand med flere brukere; datamodellen støtter det fra start, invitasjon kommer i fase 5
- **Innlogging:** JWT som `Authorization: Bearer …`, som i talik-ai. Registrering er åpen; i stedet for registreringskode legger hver husstand inn egen API-nøkkel før appen legges ut (fase 4)
- **Bilder:** Lagres i samme størrelse som sendes til AI-en (maks 1500 px, ~170 kB), i Postgres. Boksene passer da til bildet
- **Beholdning:** En ny skanning erstatter hele beholdningen (bildet er et øyeblikksbilde)
- **Drift:** Fly.io med maskin som kan gå i dvale
