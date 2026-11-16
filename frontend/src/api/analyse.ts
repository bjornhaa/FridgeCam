/** Klient for `POST /api/analyze` (#7). */
import { apiKall } from './klient.ts'

export type Sikkerhet = 'høy' | 'middels' | 'lav'

/** Rektangel i bildet, normaliserte koordinater 0–1000 med origo øverst til venstre. */
export type Boks = {
  x1: number
  y1: number
  x2: number
  y2: number
}

export type FunnetVare = {
  navn: string
  antall: number
  sikkerhet: Sikkerhet
  bokser: Boks[]
}

export type AnalyseSvar = {
  varer: FunnetVare[]
  leverandor: string
}

const MELDINGER: Record<string, string> = {
  bilde_for_stort: 'Bildet er for stort. Prøv å ta det på nytt.',
  ugyldig_bildeformat: 'Bildeformatet støttes ikke. Prøv å ta bildet på nytt.',
  bilde_mangler: 'Bildet kom ikke fram. Prøv igjen.',
  rate_limit: 'AI-tjenesten har for mye å gjøre akkurat nå. Vent litt og prøv igjen.',
  ai_utilgjengelig: 'AI-tjenesten er utilgjengelig akkurat nå. Prøv igjen om litt.',
  ai_avviste: 'AI-en kunne ikke analysere dette bildet. Prøv et annet bilde.',
  ai_feil: 'Noe gikk galt hos AI-tjenesten. Prøv igjen.',
}

export async function analyserBilde(bilde: File, signal?: AbortSignal): Promise<AnalyseSvar> {
  const skjema = new FormData()
  skjema.append('bilde', bilde)
  return normaliser(await apiKall('/api/analyze', { method: 'POST', body: skjema, signal }, MELDINGER))
}

/**
 * Sørger for at svaret har formen appen forventer, også når et felt mangler,
 * f.eks. `bokser` fra en backend eller leverandør som ikke oppgir plassering.
 * Uten dette krasjer visningen på `undefined.map` og siden blir hvit.
 */
function normaliser(data: unknown): AnalyseSvar {
  const svar = (data ?? {}) as Partial<AnalyseSvar>
  const varer = Array.isArray(svar.varer) ? svar.varer : []
  return {
    leverandor: typeof svar.leverandor === 'string' ? svar.leverandor : 'ukjent',
    varer: varer.map((v) => ({
      ...v,
      bokser: Array.isArray(v.bokser) ? v.bokser : [],
    })),
  }
}
