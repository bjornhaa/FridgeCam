# FridgeCam

Ta et bilde av kjøleskapet med mobilen, la AI gjenkjenne innholdet, og få en handleliste basert på hva som mangler.

- **Frontend:** PWA (Vite + React + TypeScript), bruker mobilkameraet
- **Backend:** Kotlin + Spring Boot, Maven
- **AI:** Vision-modell (Anthropic, Gemini m.fl.) som returnerer strukturert liste over varer
- **Lagring:** PostgreSQL (Flyway), delt beholdning og handleliste per husstand
- **Drift:** Fly.io

Se [docs/PLAN.md](docs/PLAN.md) for implementasjonsplanen.
