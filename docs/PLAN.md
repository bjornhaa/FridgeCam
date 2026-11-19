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
- `kjoleskap` – et kjøleskap i husstanden (hjemme, hytta …), fra fase 4
- `beholdning_vare` – nåværende varer i et kjøleskap
- `fast_vare` – varer som alltid skal være i et kjøleskap, med ønsket minimumsantall
- `handleliste_vare` – genererte og manuelt lagt til varer, med avhuking
- API-nøkkel per husstand (kryptert), fra fase 3

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
- [x] ~~Registreringskode fra miljøvariabel (#27)~~ – fjernet igjen; erstattet av API-nøkkel per husstand i fase 3
- [x] Egne Flyway-migreringer for lokal utvikling med testbruker, som i talik-ai
- [x] Innlogging i frontend (#28)
- [x] Navigasjon: Skann / Kjøleskapet / Profil (#29)
- [x] Datamodell for skanning, bilde og beholdning (#30)
- [x] Redigerbar vareliste etter skanning (#31)
- [x] Lagre skanning som beholdning; ny skanning erstatter beholdningen (#32)
- [x] Vis kjøleskapet med siste bilde og bokser (#33)

## Fase 3 – Drift og PWA
Tatt før handlelisten (bestemt 2026-11-17): appen tas i bruk på eget kjøleskap, så
ekte skanninger og rettelser viser hva AI-en bommer på før handlelisten bygges, og
mobiltesting slipper midlertidige tunneler. Rekkefølgen under er den issuene tas i.
- [x] API-nøkkel per husstand, kryptert med AES-256-GCM (som Spotify-tokens i talik-ai), før appen legges ut (#42)
  - Hver husstand betaler for egne AI-kall, så åpen registrering koster ikke appeieren AI-kreditt
  - Ingen felles nøkkel fra miljøet i prod; uten nøkkel ber appen husstanden legge inn en
  - Alle medlemmer i husstanden deler nøkkelen
- [x] Bare siste bilde lagres per husstand, så åpen registrering ikke kan fylle Postgres-volumet (#43)
- [x] Dockerfile + `fly.toml`, Fly Postgres, deploy til Fly.io (HTTPS inkludert) (#44)
  - Både app og database sover når de ikke brukes (database: `FLY_SCALE_TO_ZERO=1h`); kaldstart på 10–20 s er greit
  - Kostnad i praksis bare disk (< $1/mnd) så lenge det er få brukere; utvides ved behov
- [x] Deploy fra GitHub Actions ved push til `main`, blue-green så en app som ikke starter ikke tar ned prod (#45)
- [x] `manifest.webmanifest`, ikoner, service worker (installerbar på hjemskjermen) (#47)
- [x] Husstanden velger AI-leverandør og modell under Profil (#54), flyttet fra fase 7 (2026-11-19)

## Fase 4 – Flere kjøleskap
En husstand kan ha flere kjøleskap, f.eks. hjemme, på fjellhytta og på hytta på Sørlandet.
Man husker sjelden hva som står igjen i hvert av dem; appen skal vise det hjemmefra.
Tatt før handlelisten, så den bygges per kjøleskap fra starten (hytta trenger annet enn hjemme).
- [ ] `kjoleskap` som egen tabell under husstanden (navn); skanning og beholdning hører til et kjøleskap
  - Eksisterende skanninger og beholdning flyttes til ett standard kjøleskap per husstand, i samme migrering
- [ ] Velge kjøleskap når man skanner (det sist brukte er forhåndsvalgt)
- [ ] «Kjøleskapet»-fanen viser ett kjøleskap om gangen, med enkel veksling og «sist skannet» for hvert
- [ ] Legge til, gi nytt navn og slette kjøleskap (Profil)
- [ ] En ny skanning erstatter beholdningen i det valgte kjøleskapet, ikke i de andre

## Fase 5 – Handleliste
- [ ] Undersøke Odas offisielle MCP-server (`https://oda.com/mcp`): vilkår for bruk fra en tredjepartsapp,
  og hva produktsøk, kategoritre og kjøpshistorikk gir i praksis (prøves fra Claude Code med egen Oda-konto)
- [ ] Normalisering av varenavn (f.eks. «lettmelk» ≈ «melk»), så beholdning kan sammenlignes med faste varer
  - Mulig hjelp fra Odas produkter og kategoritre
- [ ] Administrere faste varer per kjøleskap (med ønsket minimumsantall); kanskje forslag fra kjøpshistorikk hos Oda
- [ ] Generere handleliste = faste varer − beholdning, for ett kjøleskap (f.eks. før man drar på hytta)
- [ ] Manuelt legge til / huke av varer i handlelisten
- [ ] Dele handlelisten (kopiere tekst / del-knapp via Web Share API)
- [ ] Kanskje: legge handlelisten i Oda-handlekurven eller en lagret liste (betaling skjer på oda.com)
- [ ] Offline-visning av handlelisten (service worker fra fase 3)

## Fase 6 – Flere brukere i husstanden
- [ ] Invitere andre til husstanden (invitasjonslenke/kode)
- [ ] Felles handleliste som oppdateres for alle medlemmer
- [ ] Forlate / fjerne medlem

## Fase 7 – Åpne for andre
- [ ] Flere AI-leverandører (f.eks. OpenAI) bak samme grensesnitt
- [ ] Begrensning av antall kall per husstand (kostnadskontroll)

## Senere / ideer
- Flere bilder per skanning (dører, skuffer); da kan nye skanninger slås sammen med beholdningen
- Høyere bildeoppløsning hvis etiketter på små pakker skal leses (måles med `SammenligningTest`)
- Flytte bilder til objektlagring (Tigris) hvis Postgres-volumet blir for lite
- Utløpsdatoer og varsler
- Backup av Postgres med ferskhetskontroll og varsling, som i talik-ai (#46, lagt bort 2026-11-19: ikke nødvendig nå)
- Oppskriftsforslag basert på beholdning
- Bruke kjøpshistorikk fra Oda i prompten, så AI-en gjenkjenner merkene husstanden faktisk kjøper

## Beslutninger
- **AI:** Flere leverandører bak et felles grensesnitt. Claude Opus 5.5 (`medium`) som standard etter måling mot fasit (#11); Sonnet 5.5 som raskere/billigere alternativ. Gemini fungerer, men gratisnivået avviser mange kall.
- **Hosting:** Fly.io
- **Database:** PostgreSQL fra start (Zonky lokalt, Fly Postgres i prod)
- **Stack:** Samme som talik-ai (Spring Boot + Spring JDBC + Flyway + JWT, Vite/React)
- **Brukere:** Husstand med flere brukere; datamodellen støtter det fra start, invitasjon kommer i fase 6
- **Innlogging:** JWT som `Authorization: Bearer …`, som i talik-ai. Registrering er åpen; i stedet for registreringskode legger hver husstand inn egen API-nøkkel før appen legges ut (fase 3)
- **Bilder:** Lagres i samme størrelse som sendes til AI-en (maks 1500 px, ~170 kB), i Postgres. Boksene passer da til bildet. Bare siste bilde beholdes (ingen historikk), bestemt 2026-11-19
- **Beholdning:** En ny skanning erstatter hele beholdningen i kjøleskapet som ble skannet (bildet er et øyeblikksbilde)
- **Kjøleskap:** En husstand kan ha flere kjøleskap (hjem og hytter); skanning, beholdning, faste varer og handleliste hører til et kjøleskap (fase 4)
- **Drift:** Fly.io med maskin som kan gå i dvale
