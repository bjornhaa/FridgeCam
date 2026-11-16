# Implementasjonsplan

Planen tas steg for steg. Hver fase blir til GitHub-issues etter hvert som den blir aktuell.

## Kjerneidé

1. Brukeren tar bilde av kjøleskapet.
2. AI returnerer en liste over varer den ser.
3. Brukeren retter opp listen (legger til / fjerner / justerer antall).
4. Listen lagres som *nåværende beholdning*.
5. Beholdningen sammenlignes med *faste varer* (det man alltid vil ha) → **handleliste**.

## Fase 0 – Oppsett
- [x] Opprette public GitHub-repo
- [ ] Ktor-backend i Maven-prosjektet (health-endepunkt, kjører lokalt)
- [ ] Serve statisk PWA-skall fra backenden
- [ ] GitHub Actions: bygg og test på push

## Fase 1 – Bilde → vareliste (uten lagring)
- [ ] PWA: kameraknapp (`<input capture="environment">`), forhåndsvisning
- [ ] Krympe/komprimere bilde i nettleseren før opplasting
- [ ] Backend: `POST /api/analyze` som sender bildet til AI og returnerer JSON
- [ ] Prompt + JSON-skjema for strukturert svar (navn, antall, sikkerhet)
- [ ] Vise resultatlisten i appen
- [ ] API-nøkkel via miljøvariabel, aldri i frontend

## Fase 2 – Redigering og lagring
- [ ] Velge database (forslag: PostgreSQL, ev. SQLite for start)
- [ ] Datamodell: `Item`, `Inventory`/`Scan`, `StapleItem` (faste varer), `ShoppingListItem`
- [ ] Redigerbar liste etter skanning før lagring
- [ ] Lagre skanning som nåværende beholdning
- [ ] Normalisering av varenavn (f.eks. «lettmelk» ≈ «melk»)

## Fase 3 – Handleliste
- [ ] Administrere faste varer (med ønsket minimumsantall)
- [ ] Generere handleliste = faste varer − beholdning
- [ ] Manuelt legge til / huke av varer i handlelisten
- [ ] Dele handlelisten (kopiere tekst / del-knapp via Web Share API)

## Fase 4 – PWA og drift
- [ ] `manifest.json`, ikoner, service worker (installerbar, offline-visning av handleliste)
- [ ] Hosting med HTTPS (forslag: Fly.io / Railway / Render)
- [ ] Enkel autentisering (hvis flere brukere / husstand)

## Senere / ideer
- Flere bilder per skanning (dører, skuffer)
- Utløpsdatoer og varsler
- Oppskriftsforslag basert på beholdning
- Deling av handleliste i husstanden

## Åpne spørsmål
- Hvilken AI-leverandør / modell?
- Én bruker eller husstand med flere brukere?
- Hvor skal det hostes?
