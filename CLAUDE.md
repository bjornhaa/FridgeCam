# CLAUDE.md

Veiledning for Claude Code i dette repoet.

## Hva dette er

FridgeCam: PWA der man tar bilde av kjøleskapet, AI lister varene, og (etter hvert)
lages en handleliste ut fra hva husstanden alltid vil ha. Plan og status står i
[docs/PLAN.md](docs/PLAN.md); arbeidet styres med GitHub-issues merket `fase-N`.
Repoet er **public** (bjornhaa/FridgeCam).

Oppsettet følger bevisst talik-ai (`~/dev/talik-ai`, se dens CLAUDE.md). Se der først
når noe nytt skal lages (auth, Fly-deploy, backup osv.).

**Stack:** Kotlin + Spring Boot 4.1 (Java 25), Spring JDBC (ikke JPA), Flyway, Postgres 18
(Zonky embedded lokalt/tester), Vite 8 + React 19 + TypeScript 7 + Tailwind 4, Vitest.
Brukeren vil ha **siste stabile versjoner** av alt. Java 25 fordi Kotlin 2.4 ikke støtter
JVM-target 27 og Java 26 er ute av støtte. ESLint er utelatt fordi typescript-eslint ikke
støtter TypeScript 7.

## Kommandoer

```sh
mvn test                                   # backend-tester (+ ingen frontend)
mvn -B -Pwith-frontend verify              # det CI kjører: frontend-bygg, Vitest, backend-tester
mvn spring-boot:run                        # app på :8080 med local-profil og innebygd Postgres
cd frontend && npm run dev                 # Vite på :5173, proxier /api og /actuator til :8080
cd frontend && npm test                    # Vitest
```

## Arbeidsflyt

- Én gren per issue (eller to små issues sammen), PR med `Closes #N`, CI må være grønn.
  Grener slettes automatisk ved merge. Stablede PR-er: base = forrige gren; flytt til `main` før merge.
- **Før commit/PR: la brukeren teste på mobil.** Start Vite og en tunnel
  (`cloudflared tunnel --url http://localhost:5173`), gi brukeren adressen, vent på
  «det fungerer». Tunnel-DNS kan bruke litt tid; sjekk med `dig @1.1.1.1` + `curl --resolve`.
- **Brukeren kjører backend fra IntelliJ** (run-konfig med `SPRING_PROFILES_ACTIVE=local`
  og AI-variabler). Ikke start egen backend på 8080. Etter Kotlin-/migreringsendringer: be
  brukeren starte backend på nytt, og sjekk at prosessen er nyere enn de kompilerte klassene
  (`ps -o lstart= -p <pid>` mot tidsstempel i `target/classes`).
- **IntelliJ legger nye filer til i git automatisk.** Sjekk `git status` før commit, særlig
  at ingenting fra `testbilder/` eller `.env` er staget.
- Kommunikasjon og dokumentasjon på norsk.

## Arkitektur

### Profiler og konfigurasjon
- `local`-profil: `LocalDataSourceConfig` starter Zonky Postgres på port 55433 (ikke 55432,
  den bruker talik-ai). `.env` i prosjektmappen leses (`spring.config.import`), se `.env.example`.
- Uten profil (prod): `SPRING_DATASOURCE_*`, `AI_PROVIDER` og `JWT_SECRET` må settes; appen stopper ellers.
- Gjør aldri `local` til standardprofil: prod ville da stille brukt en flyktig database.

### Innlogging (`innlogging/`)
- Stateless JWT som i talik-ai: `Authorization: Bearer …`, HS256 via `jjwt`, levetid 30 dager.
  Tokenet har bare bruker-id (`sub`); hentes i controllere med `@AuthenticationPrincipal InnloggetBruker`.
- `POST /api/auth/registrer`, `POST /api/auth/logg-inn`, `GET /api/meg`. Registrering lager en
  husstand («Hjemme») med brukeren som `EIER`, i samme transaksjon.
- Alt under `/api/` krever innlogging (unntatt de to over), og avvisning gir 401 `ikke_innlogget`
  fra `SecurityConfig`. `/actuator/health`, frontenden og dype lenker er åpne.
- Registrering krever `FRIDGECAM_REGISTRERINGSKODE`; uten satt kode er registrering stengt.
  `JWT_SECRET` (minst 32 tegn) kreves i prod; `local` har en utviklingsverdi.
- `JwtFilter` er bevisst ikke en bean (ellers registreres den også som vanlig servlet-filter).
  `@WebMvcTest` for beskyttede controllere: `@Import(SecurityConfig, JwtService)` og ekte token.

### AI-analyse (`analyse/`)
- `VisionProvider` → `Analyseresultat(varer, forbruk)`. Implementasjoner: `FakeVisionProvider`,
  `AnthropicVisionProvider` (offisiell Java SDK, beta-endepunkt pga. server-side fallbacks),
  `GeminiVisionProvider` (Interactions-API over REST med `RestClient`).
- Valg: `AI_PROVIDER` (fake/anthropic/gemini), `AI_MODEL`, `AI_EFFORT` (low/medium/high).
  Standard: Claude Opus 5.5 på `medium` (valgt etter måling, se #11).
- `AnalysePrompt`: felles prompt, JSON-skjema og opprydding i svaret. Bokser i to formater:
  `HJORNER` (x1,y1,x2,y2) for Claude, `BOX_2D` ([ymin,xmin,ymax,xmax]) for Gemini; begge
  normalisert 0–1000 og oversatt til `Boks(x1,y1,x2,y2)`.
- Feilkoder via `ApiException` → `{code, message}`: `rate_limit`, `ai_utilgjengelig`,
  `ai_avviste`, `ai_feil`, `bilde_mangler`, `ugyldig_bildeformat`, `bilde_for_stort`; for innlogging
  `ikke_innlogget`, `ugyldig_innlogging`, `epost_i_bruk`, `ugyldig_registreringskode`,
  `registrering_stengt`, og `ugyldig_foresporsel` for valideringsfeil (meldingen er fra feltet).
  Frontend (`frontend/src/api/analyse.ts`) har norske meldinger for hver kode.
- `GlobalExceptionHandler` er avgrenset med `basePackages`; ellers fanger den Springs 404 for
  statiske filer og dype lenker gir 500. `MaxUploadSizeExceededException` håndteres i egen,
  uavgrenset advice. `server.tomcat.max-swallow-size: 20MB` trengs for at 413 når klienten.
- Bildet krympes i nettleseren (`frontend/src/bilde/krympBilde.ts`: 1500 px, JPEG 0,8, EXIF-rotasjon).

### Måling av AI-kvalitet (`src/test/.../sammenligning/`)
- `SammenligningTest` kjører testbilder mot ekte API-er og teller mot en fasit. Kjører bare med
  `FRIDGECAM_SAMMENLIGNING=1`. **Koster penger (Claude); spør brukeren først.**
- `testbilder/` (bilder, `*.fasit.json`, råsvar, rapporter) er i `.gitignore`: private bilder
  i et offentlig repo. `FRIDGECAM_REVURDER=<kjoringer-….json>` teller på nytt uten API-kall.
- Prompt-eksempler må ikke være varer fra testbildene (da måles fasiten, ikke modellen).

## Konvensjoner
- Ingen JPA: `NamedParameterJdbcTemplate`, `BIGINT GENERATED ALWAYS AS IDENTITY`, `INSERT … RETURNING`.
- Norske kolonnenavn og norske domenenavn i Kotlin (`FunnetVare`, `Sikkerhet`, `Boks`).
- Tester: `@WebMvcTest` for controllere, `AppTest` som base for tester mot hele appen (deler
  kontekst og database, så bruk `unikEpost()`), Zonky for databasetester, JDK `HttpServer` som falsk
  API-server for AI-leverandørene. Frontend: Vitest med `vi.stubGlobal('fetch', …)`.
