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
  - Ett migreringstre (`db/migration/`) – vi har ingen prod-historikk å konsolidere
- **Auth:** Stateless JWT (som talik-ai)
- **Frontend:** Vite + React + TypeScript + Tailwind, PWA. Bygges inn i jar-en via `with-frontend`-profil
- **Drift:** Dockerfile (to-trinns), `fly.toml` (region `arn`), GitHub Actions: test → deploy på push til `main`

## Datamodell (utkast)

Husstand er med i modellen fra dag én, selv om invitasjon av andre brukere kommer senere.
Da slipper vi en datamigrering når flere skal dele kjøleskap.

- `bruker` – innlogging
- `husstand` – en delt beholdning og handleliste
- `husstand_medlem` – kobler bruker ↔ husstand (rolle: eier / medlem)
- `skanning` – ett bilde-opptak (tidspunkt, hvem, AI-leverandør/modell, råsvar)
- `beholdning_vare` – nåværende varer i husstandens kjøleskap
- `fast_vare` – varer husstanden alltid vil ha, med ønsket minimumsantall
- `handleliste_vare` – genererte og manuelt lagt til varer, med avhuking

Ved første registrering opprettes automatisk en husstand med brukeren som eier.

## Fase 0 – Oppsett
- [x] Opprette public GitHub-repo
- [ ] Spring Boot-skjelett i Maven-prosjektet (Kotlin, Java 25, actuator health)
- [ ] Postgres: Zonky embedded lokalt (`local`-profil), Flyway, første migrering
- [ ] Vite/React-skall som serveres fra backend (`with-frontend`-profil)
- [ ] GitHub Actions: bygg og test på push/PR

## Fase 1 – Bilde → vareliste
- [ ] PWA: kameraknapp (`<input capture="environment">`), forhåndsvisning
- [ ] Krympe/komprimere bilde i nettleseren før opplasting
- [ ] Backend: `POST /api/analyze` som sender bildet til AI og returnerer JSON
- [ ] `VisionProvider`-grensesnitt slik at AI-leverandør kan byttes (valgt via `AI_PROVIDER`)
- [ ] Implementasjon: Anthropic (offisiell Java SDK, modell konfigurerbar via `AI_MODEL`)
- [ ] Implementasjon: Google Gemini (gratisnivå, for testing/utvikling)
- [ ] Felles prompt + JSON-skjema for strukturert svar (navn, antall, sikkerhet), validert i backend
- [ ] Vise resultatlisten i appen
- [ ] API-nøkler via miljøvariabler / Fly secrets, aldri i frontend

## Fase 2 – Brukere, husstand og lagring
- [ ] Registrering/innlogging med JWT
- [ ] Tabeller: bruker, husstand, husstand_medlem (husstand opprettes ved registrering)
- [ ] Lagre skanninger og beholdning per husstand
- [ ] Redigerbar liste etter skanning før lagring
- [ ] Normalisering av varenavn (f.eks. «lettmelk» ≈ «melk»)

## Fase 3 – Handleliste
- [ ] Administrere faste varer (med ønsket minimumsantall)
- [ ] Generere handleliste = faste varer − beholdning
- [ ] Manuelt legge til / huke av varer i handlelisten
- [ ] Dele handlelisten (kopiere tekst / del-knapp via Web Share API)

## Fase 4 – PWA og drift
- [ ] `manifest.webmanifest`, ikoner, service worker (installerbar, offline-visning av handleliste)
- [ ] Dockerfile + `fly.toml`, Fly Postgres, deploy til Fly.io (HTTPS inkludert)
- [ ] Deploy fra GitHub Actions ved push til `main`
- [ ] Backup av Postgres

## Fase 5 – Flere brukere i husstanden
- [ ] Invitere andre til husstanden (invitasjonslenke/kode)
- [ ] Felles handleliste som oppdateres for alle medlemmer
- [ ] Forlate / fjerne medlem

## Fase 6 – Åpne for andre
- [ ] Flere AI-leverandører (f.eks. OpenAI) bak samme grensesnitt
- [ ] Husstand velger leverandør og legger inn egen API-nøkkel (kryptert med AES-256-GCM, som Spotify-tokens i talik-ai)
- [ ] Begrensning av antall kall per husstand (kostnadskontroll)

## Senere / ideer
- Flere bilder per skanning (dører, skuffer)
- Utløpsdatoer og varsler
- Oppskriftsforslag basert på beholdning

## Beslutninger
- **AI:** Flere leverandører bak et felles grensesnitt. Anthropic som hovedvalg, Gemini (gratisnivå) som gratis alternativ.
- **Hosting:** Fly.io
- **Database:** PostgreSQL fra start (Zonky lokalt, Fly Postgres i prod)
- **Stack:** Samme som talik-ai (Spring Boot + Spring JDBC + Flyway + JWT, Vite/React)
- **Brukere:** Husstand med flere brukere; datamodellen støtter det fra start, invitasjon kommer i fase 5
