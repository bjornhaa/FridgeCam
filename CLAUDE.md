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

## Drift (Fly.io, #44)

- App `fridgecam` (https://fridgecam.fly.dev) og database `fridgecam-db` i `arn`, personlig org.
  Push til `main` deployer automatisk etter grønne tester (CI-jobben `deploy`, `FLY_API_TOKEN` er et
  deploy-token bare for `fridgecam`). Manuelt: `fly deploy --remote-only --ha=false --app fridgecam`
  (ingen Docker lokalt; bygges hos Fly). Strategi `bluegreen`: starter ikke den nye versjonen (f.eks. feilet
  migrering), rulles deployen tilbake og den gamle kjører videre (testet med ugyldig `AI_PROVIDER`).
- **Begge sover når de ikke brukes:** appen stoppes av Fly-proxyen etter noen minutter uten trafikk
  (`auto_stop_machines`), databasen stopper selv etter en time uten tilkoblinger (`FLY_SCALE_TO_ZERO=1h`
  på maskinen, autostart via `fridgecam-db.flycast`). Første forespørsel etter en pause tar 10–20 s.
  Hikari venter på databasen (timeouts i `fly.toml`). `shared-cpu-2x`/1 GB gir kaldstart på ~11 s (mot 19 s
  med 1x/512 MB) og koster bare mens maskinen er våken. Kostnad: i praksis bare disk (< $1/mnd).
- `fridgecam-db` er Flys umanagede Postgres (`flyio/postgres-flex:18.1`, 256 MB, 1 GB volum), samme type
  som talik-ai. Fly vedlikeholder den ikke lenger; daglige volum-snapshots (5 dager) er eneste backup.
- Secrets (kan ikke leses ut igjen; lag nye ved behov): `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`,
  `JWT_SECRET` (ny = alle logges ut), `FRIDGECAM_NOKKEL_KRYPTERING` (ny = alle husstander må legge inn
  AI-nøkkelen på nytt). `AI_PROVIDER` står i `fly.toml`; ingen AI-nøkkel på serveren.
- Dockerfile: to trinn; byggetrinnet trenger `libatomic1` for Node fra frontend-maven-plugin.
  `.dockerignore` holder `.env` og `testbilder/` utenfor imaget.

## Arbeidsflyt

- Én gren per issue (eller to små issues sammen), PR med `Closes #N`, CI må være grønn.
  Grener slettes automatisk ved merge. **Merge PR-en (når CI er grønn og brukeren har godkjent)
  før neste issue startes**; unngå stabler av åpne PR-er. Må det likevel stables: base = forrige
  gren, og flytt til `main` før merge.
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
- Uten profil (prod): `SPRING_DATASOURCE_*`, `AI_PROVIDER`, `JWT_SECRET` og `FRIDGECAM_NOKKEL_KRYPTERING`
  må settes; appen stopper ellers.
- Gjør aldri `local` til standardprofil: prod ville da stille brukt en flyktig database.

### Migreringer (som i talik-ai)
- To trær: `db/migration/` er prod-historikken (`V1`, `V2`, …). `db/migration-embedded/` brukes av
  `local` (og testene som starter appen): `V1__init.sql` er det samlede skjemaet, resten er testdata.
- **Ved skjemaendring: endre begge.** Ny `V{n}__….sql` i `db/migration/`, og oppdater
  `db/migration-embedded/V1__init.sql`. Legg ikke versjonerte skjemaendringer i embedded-treet.
- `FlywayFilnavnTest` (navn, duplikater, hull) og `FlywaySkjemaTest` (kjører begge trærne og
  sammenligner tabeller, kolonner, constraints og indekser) passer på at de ikke glir fra hverandre.
- Testbruker lokalt (`V2__dev_seed.sql`, som i talik-ai): `bjorn@haakenstad.com` / `qwer1234`,
  eier av husstanden «Hjemme».

### Innlogging (`innlogging/`)
- Stateless JWT som i talik-ai: `Authorization: Bearer …`, HS256 via `jjwt`, levetid 30 dager.
  Tokenet har bare bruker-id (`sub`); hentes i controllere med `@AuthenticationPrincipal InnloggetBruker`.
- `POST /api/auth/registrer`, `POST /api/auth/logg-inn`, `GET /api/meg`. Registrering lager en
  husstand («Hjemme») med brukeren som `EIER`, i samme transaksjon.
- Alt under `/api/` krever innlogging (unntatt de to over), og avvisning gir 401 `ikke_innlogget`
  fra `SecurityConfig`. `/actuator/health`, frontenden og dype lenker er åpne.
- Registrering er åpen; hver husstand bruker egen AI-nøkkel (se under), så fremmede ikke bruker
  appeierens kreditt. `JWT_SECRET` (minst 32 tegn) kreves i prod; `local` har en utviklingsverdi.
- Frontend: `api/klient.ts` (`apiKall`) legger på token fra `api/token.ts` (localStorage) og gjør
  `{code, message}` om til `ApiFeil` med norsk melding. 401 *med* token = utlogget (lyttere varsles);
  401 uten token er en vanlig feil (feil passord). `innlogging/Innlogging.tsx` holder status, og
  `KreverInnlogging` sender til `/logg-inn`. Nye API-kall skal gå via `apiKall`.
- `JwtFilter` er bevisst ikke en bean (ellers registreres den også som vanlig servlet-filter).
  `@WebMvcTest` for beskyttede controllere: `@Import(SecurityConfig, JwtService)` og ekte token.

### Skanning og beholdning (`skanning/`, `beholdning/`, `husstand/`)
- `/api/analyze` lagrer skanningen med en gang (bilde, AI-svar, forbruk) og returnerer `skanningId`.
  «Lagre i kjøleskapet» sender bare den rettede listen til `POST /api/skanninger/{id}/beholdning`;
  boksene hentes fra AI-svaret via `aiIndeks`, ikke fra klienten. Den rettede listen lagres også på
  skanningen (`rettede_varer`), for senere måling av AI-kvalitet. `GET /api/beholdning` gir kjøleskapet,
  `GET /api/skanninger/{id}/bilde` bildet. Frontend henter bilder med `apiBlob` (et `<img src>` kan
  ikke sende tokenet) og viser dem med `BildeMedBokser`.
- Flere kjøleskap per husstand (#61, `kjoleskap/`): skanning og beholdning har `kjoleskap_id` (sammensatt
  fremmednøkkel med `husstand_id`, så de ikke kan peke på et kjøleskap i en annen husstand). Registrering lager
  «Hjemme». `GET /api/kjoleskap`; `/api/analyze` tar skjemafeltet `kjoleskapId` og `GET /api/beholdning`
  `?kjoleskap=`, begge med husstandens eldste kjøleskap som standard. `HusstandTilgang.kjoleskapFor` sjekker
  tilgang (404). En ny skanning erstatter bare beholdningen i sitt kjøleskap.
- Kjøleskap administreres under Profil (#62, `KjoleskapService`): `POST /api/kjoleskap`, `PUT`/`DELETE
  /api/kjoleskap/{id}`; endringer svarer med hele listen. Navn unikt i husstanden uansett store/små bokstaver
  (`kjoleskap_finnes`), høyst 20 (`for_mange_kjoleskap`), det siste kan ikke slettes (`siste_kjoleskap`;
  husstandsraden låses så to samtidige slettinger ikke tar begge). Sletting tar med skanninger, bilde og varer.
- Frontend (#63, #64): `useKjoleskap()` gir listen og valgt kjøleskap, husket per enhet i localStorage
  (`kjoleskap/valgtKjoleskap.ts`), delt av Skann og Kjøleskapet. Med flere enn ett vises `KjoleskapVelger` (knapper) på Skann,
  og overskriften i Kjøleskapet er en nedtrekksliste (`KjoleskapOverskrift`).
  Valget låses etter analysen, siden skanningen allerede hører til et kjøleskap.
- Bare siste bilde lagres (#43), per kjøleskap: ved ny skanning og ved lagring som beholdning slettes
  kjøleskapets bilder unntatt den nyeste skanningens og beholdningens (som regel samme). Ingen bildehistorikk; selve
  skanningene (AI-svar, rettet liste, kostnad) beholdes. Kjøleskapet viser bare listen uten bilde.
- All tilgang til husstandsdata går via `HusstandTilgang`: `husstandFor(bruker)` og
  `sjekkMedlem(...)`, som gir 404 `ikke_funnet` (ikke 403) for andres data.

### AI-valg og -nøkler per husstand (`ainokkel/`, #42, #54)
- **Nøklene må aldri komme på avveie.** De lagres bare kryptert (`husstand_ai_nokkel`), dekrypteres bare
  rett før et AI-kall, og logges eller returneres aldri. Alle klasser som holder en nøkkel (`AiNokkel`,
  `AiNokkelForesporsel`, `AiProperties`) har `toString` som skjuler den; hold det slik, og bruk aldri
  `data class` for slike klasser. Tester sjekker DB-innhold, API-svar og logg (`AiNokkelFlytTest`).
- `NokkelKryptering`: AES-256-GCM, hovednøkkel fra `FRIDGECAM_NOKKEL_KRYPTERING` (base64, 32 byte),
  husstand + leverandør som AAD, format `v1:…`. `local` har en nullnøkkel som avvises utenfor `local`.
  Mistes hovednøkkelen, må alle husstander legge inn nøkkelen på nytt.
- Husstanden velger leverandør og modell selv (`husstand.ai_leverandor`/`ai_modell`, null = standard fra
  `AI_PROVIDER`/`AI_MODEL`). Bare modeller i `AiModeller` kan velges (navn, beskrivelse, pris per bilde).
  Én nøkkel per leverandør; skanning bruker nøkkelen for valgt leverandør.
- API (`AiInnstillingerController`): `GET /api/husstand/ai`, `PUT /api/husstand/ai/valg`,
  `PUT /api/husstand/ai/nokkel` (`{leverandor, nokkel}`), `DELETE /api/husstand/ai/nokkel/{leverandor}`.
  Alle i husstanden kan endre; svaret har bare `sisteTegn`. Anthropic-nøkler må begynne med `sk-ant-`, og
  admin-nøkler (`sk-ant-admin…`) avvises.
- `VisionProviderFabrikk.lag(valg, nokkel)` lager leverandøren per kall (lukkes etterpå). Med `fake` brukes
  fake-leverandøren uansett valg, og ingen nøkkel trengs.
- Feilkoder: `mangler_ai_nokkel`, `ugyldig_ai_nokkel`, `ai_mangler_kreditt`; Skann lenker da til Profil.
- `ANTHROPIC_API_KEY`/`GEMINI_API_KEY` er bare lokal reserve (koblet til i `application-local.yml`, og
  koden sjekker `local`-profilen). Testene blanker dem, så ekte nøkler fra `.env` aldri lastes.

### AI-analyse (`analyse/`)
- `VisionProvider` → `Analyseresultat(varer, forbruk)`, laget per kall av `VisionProviderFabrikk`. Implementasjoner: `FakeVisionProvider`,
  `AnthropicVisionProvider` (offisiell Java SDK, beta-endepunkt pga. server-side fallbacks),
  `GeminiVisionProvider` (Interactions-API over REST med `RestClient`).
- Valg: `AI_PROVIDER` (fake/anthropic/gemini), `AI_MODEL`, `AI_EFFORT` (low/medium/high).
  Standard: Claude Opus 5.5 på `medium` (valgt etter måling, se #11).
  `AI_MODEL` må høre til leverandøren (`claude-…` / `gemini-…`); ellers stopper appen ved oppstart.
  Til utvikling er `claude-haiku-5-5` med `low` omtrent 20 ganger billigere, men finner færre varer.
- `AnalysePrompt`: felles prompt, JSON-skjema og opprydding i svaret. Bokser lagres som
  `Boks(x1,y1,x2,y2)` normalisert 0–1000. **Claude oppgir piksler** selv når den bes om 0–1000 (#58),
  så den får bildets størrelse (`Bildeformat.storrelse`, fra filhodet) og formatet `PIKSLER`;
  backend regner om. `HJORNER` (0–1000) brukes bare når størrelsen ikke kan leses. Gemini får
  `BOX_2D` ([ymin,xmin,ymax,xmax], 0–1000), som den er trent på.
- Feilkoder via `ApiException` → `{code, message}`: `rate_limit`, `ai_utilgjengelig`,
  `ai_avviste`, `ai_feil`, `mangler_ai_nokkel`, `ugyldig_ai_nokkel`, `ai_mangler_kreditt`, `bilde_mangler`, `ugyldig_bildeformat`, `bilde_for_stort`; for innlogging
  `ikke_innlogget`, `ugyldig_innlogging`, `epost_i_bruk`, `ikke_funnet`,
  `ingen_husstand`, og `ugyldig_foresporsel` for valideringsfeil (meldingen er fra feltet).
  Frontend (`frontend/src/api/analyse.ts`) har norske meldinger for hver kode.
- `GlobalExceptionHandler` er avgrenset med `basePackages`; ellers fanger den Springs 404 for
  statiske filer og dype lenker gir 500. `MaxUploadSizeExceededException` håndteres i egen,
  uavgrenset advice. `server.tomcat.max-swallow-size: 20MB` trengs for at 413 når klienten.
- Bildet krympes i nettleseren (`frontend/src/bilde/krympBilde.ts`: 1500 px, JPEG 0,8, EXIF-rotasjon).

### PWA (#47)
- `frontend/public/manifest.webmanifest`, ikoner laget av `scripts/lag-ikoner.py` (ren Python, samme figur
  som `favicon.svg`; kjør på nytt ved endring), iOS-tagger i `index.html`.
- `public/sw.js` registreres bare i produksjonsbygget. `/api/` og `/actuator/` caches aldri; sider hentes
  fra nettet først (cache bare uten nett), så en ny versjon tas i bruk med en gang; `/assets/` caches først
  og ryddes mot siste `index.html`. Endres strategien, bytt `CACHE`-navnet.
- Teste lokalt: `npm run build && npx vite preview --port 4173` + tunnel mot 4173 (service worker krever HTTPS).

### Måling av AI-kvalitet (`src/test/.../sammenligning/`)
- `SammenligningTest` kjører testbilder mot ekte API-er og teller mot en fasit. Kjører bare med
  `FRIDGECAM_SAMMENLIGNING=1`. **Koster penger (Claude); spør brukeren først.**
- `testbilder/` (bilder, `*.fasit.json`, råsvar, rapporter) er i `.gitignore`: private bilder
  i et offentlig repo. `FRIDGECAM_REVURDER=<kjoringer-….json>` teller på nytt uten API-kall.
- Prompt-eksempler må ikke være varer fra testbildene (da måles fasiten, ikke modellen).

## Konvensjoner
- Ingen JPA: `NamedParameterJdbcTemplate`, `BIGINT GENERATED ALWAYS AS IDENTITY`, `INSERT … RETURNING`.
- JSON i Postgres (`JSONB`): skriv med appens `JsonMapper` og `CAST(:x AS jsonb)`, les med `json.readValue`.
  `skanning.ai_varer` er AI-ens urørte svar; `beholdning_vare` er den rettede listen.
- Norske kolonnenavn og norske domenenavn i Kotlin (`FunnetVare`, `Sikkerhet`, `Boks`).
- Tester: `@WebMvcTest` for controllere, `AppTest` som base for tester mot hele appen (deler
  kontekst og database, så bruk `unikEpost()`), `DatabaseTest` for repositories (ekte
  transaksjoner; `nyEier()` gir egen bruker og husstand per test), Zonky for databasetester, JDK `HttpServer` som falsk
  API-server for AI-leverandørene. Frontend: Vitest med `vi.stubGlobal('fetch', …)`.
