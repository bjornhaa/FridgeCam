# FridgeCam

Ta et bilde av kjøleskapet med mobilen, la AI gjenkjenne innholdet, og få en handleliste basert på hva som mangler.

- **Frontend:** PWA (Vite + React + TypeScript), bruker mobilkameraet
- **Backend:** Kotlin + Spring Boot, Maven
- **AI:** Vision-modell (Anthropic, Gemini m.fl.) som returnerer strukturert liste over varer
- **Lagring:** PostgreSQL (Flyway), delt beholdning og handleliste per husstand
- **Drift:** Fly.io

Se [docs/PLAN.md](docs/PLAN.md) for implementasjonsplanen.

## Kjøre lokalt

Krever Java 25+ og Maven. Ingen Docker eller ekstern database trengs.

```sh
mvn spring-boot:run     # starter appen på :8080 med innebygd Postgres (local-profil)
mvn test                # alle tester
```

`local`-profilen starter en innebygd Postgres 18 (Zonky) på port **55433**, og
Flyway kjører migreringene i `src/main/resources/db/migration/` ved oppstart.
Basen er flyktig, så alle data forsvinner når appen stopper.

Koble til fra IntelliJ: `jdbc:postgresql://127.0.0.1:55433/postgres`, bruker
`postgres`, tomt passord. Porten kan endres med `FRIDGECAM_LOCAL_DB_PORT`
(0 = tilfeldig ledig port).

Uten profil (prod) leses databasen fra `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME` og `SPRING_DATASOURCE_PASSWORD`.

### Konfigurasjon og API-nøkler

Appen styres med miljøvariabler:

| Variabel | Verdier | Standard |
|---|---|---|
| `AI_PROVIDER` | `fake`, `anthropic`, `gemini` | `fake` lokalt; må settes i prod |
| `AI_MODEL` | f.eks. `claude-sonnet-5-5` | `claude-opus-5-5` / `gemini-4-hydrogen` |
| `AI_EFFORT` | `low`, `medium`, `high` | `medium` |
| `ANTHROPIC_API_KEY` | fra [Claude Console](https://platform.claude.com) → Settings → API Keys | – |
| `GEMINI_API_KEY` | fra [Google AI Studio](https://aistudio.google.com) → Get API key | – |

Appen stopper ved oppstart med en tydelig melding hvis valgt leverandør mangler nøkkel.

**Lokalt:** kopier `.env.example` til `.env` og fyll inn. `.env` leses automatisk
med `local`-profilen og er lagt i `.gitignore`. Alternativt kan variablene settes i
run-konfigurasjonen i IntelliJ (de vinner over `.env`). Kjøres appen fra IntelliJ, må
`local`-profilen være aktiv: *Active profiles: `local`* eller `SPRING_PROFILES_ACTIVE=local`.

**Produksjon (Fly.io):** `.env` brukes ikke. Sett verdiene som secrets:

```sh
fly secrets set AI_PROVIDER=anthropic ANTHROPIC_API_KEY=sk-ant-...
```

Nøklene skal aldri i frontend, i git eller i logger. Backend logger bare HTTP-status
ved avvist nøkkel, og actuator eksponerer bare `health` og `info`.

### Frontend

Frontenden ligger i `frontend/` (Vite + React + TypeScript + Tailwind).

```sh
cd frontend
npm install             # første gang
npm run dev             # Vite på :5173, proxier /api og /actuator til :8080
npm run build           # produksjonsbygg til frontend/dist/
```

`mvn -Pwith-frontend package` bygger frontenden (installerer Node selv i
`target/`) og legger den inn i jar-en, slik at Spring Boot serverer alt fra
én prosess. Vanlig `mvn package` / `mvn test` tar ikke med frontenden.

### Test på mobil (HTTPS-tunnel)

Kameraet krever HTTPS på mobil. Kjør Vite og en tunnel:

```sh
cd frontend && npm run dev
cloudflared tunnel --url http://localhost:5173
```

Åpne `https://….trycloudflare.com`-adressen cloudflared skriver ut, på
telefonen. Backend på :8080 trengs bare for sider som kaller API-et.

### Sammenligne AI-oppsett

`SammenligningTest` kjører testbilder gjennom flere AI-oppsett og teller treff,
bomskudd og manglende varer mot en fasit. Bilder, fasit, råsvar og rapporter
ligger i `testbilder/`, som holdes utenfor git (repoet er offentlig).

```
testbilder/
  kjoleskap-1.jpg             # krympet slik appen sender det (1500 px, JPEG)
  kjoleskap-1.fasit.json      # hva som faktisk er der, med alias
  kjoringer-<tid>.json        # råsvar fra hver kjøring
  rapport-<tid>.md            # resultat
```

Testen bruker ekte API-kall (Claude koster penger) og kjører derfor bare når
`FRIDGECAM_SAMMENLIGNING=1` er satt:

```sh
FRIDGECAM_SAMMENLIGNING=1 ANTHROPIC_API_KEY=… GEMINI_API_KEY=… \
  mvn test -Dtest=SammenligningTest -Dsurefire.failIfNoSpecifiedTests=false
```

- `FRIDGECAM_KJORINGER`: kjøringer per oppsett (standard 5)
- `FRIDGECAM_OPPSETT`: f.eks. `claude-opus-medium,claude-sonnet-medium`
- `FRIDGECAM_REVURDER=testbilder/kjoringer-….json`: tell på nytt fra lagrede
  råsvar uten API-kall, f.eks. etter at fasiten er rettet

