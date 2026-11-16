/** Klient for `POST /api/analyze` (#7). */

export type Sikkerhet = 'høy' | 'middels' | 'lav'

export type FunnetVare = {
  navn: string
  antall: number
  sikkerhet: Sikkerhet
}

export type AnalyseSvar = {
  varer: FunnetVare[]
  leverandor: string
}

/** Feil med en melding som kan vises direkte til brukeren. */
export class AnalyseFeil extends Error {
  readonly code: string

  constructor(code: string, message: string) {
    super(message)
    this.code = code
  }
}

const MELDINGER: Record<string, string> = {
  bilde_for_stort: 'Bildet er for stort. Prøv å ta det på nytt.',
  ugyldig_bildeformat: 'Bildeformatet støttes ikke. Prøv å ta bildet på nytt.',
  bilde_mangler: 'Bildet kom ikke fram. Prøv igjen.',
  rate_limit: 'AI-tjenesten har for mye å gjøre akkurat nå. Vent litt og prøv igjen.',
}

export async function analyserBilde(bilde: File, signal?: AbortSignal): Promise<AnalyseSvar> {
  const skjema = new FormData()
  skjema.append('bilde', bilde)

  let respons: Response
  try {
    respons = await fetch('/api/analyze', { method: 'POST', body: skjema, signal })
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') throw err
    throw new AnalyseFeil('nettverk', 'Fikk ikke kontakt med serveren. Sjekk nettet og prøv igjen.')
  }

  if (!respons.ok) {
    const feil = await respons.json().catch(() => null) as { code?: string; message?: string } | null
    const code = feil?.code ?? `http_${respons.status}`
    throw new AnalyseFeil(code, MELDINGER[code] ?? feil?.message ?? 'Noe gikk galt. Prøv igjen.')
  }
  return respons.json() as Promise<AnalyseSvar>
}
