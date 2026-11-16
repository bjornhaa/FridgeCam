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
