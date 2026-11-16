import { hentToken, slettToken } from './token.ts'

/** Feil fra API-et med en melding som kan vises direkte til brukeren. */
export class ApiFeil extends Error {
  readonly code: string

  constructor(code: string, message: string) {
    super(message)
    this.code = code
  }
}

/** Meldinger for feilkoder som kan komme fra alle endepunkter. */
const FELLES_MELDINGER: Record<string, string> = {
  ikke_innlogget: 'Du er logget ut. Logg inn igjen.',
  intern_feil: 'Noe gikk galt hos oss. Prøv igjen.',
}

const utloggingslyttere = new Set<() => void>()

/**
 * Kalles når serveren avviser tokenet (utløpt eller ugyldig). Innloggingen
 * lytter på dette og sender brukeren til innloggingssiden.
 */
export function vedUtlogging(lytter: () => void): () => void {
  utloggingslyttere.add(lytter)
  return () => utloggingslyttere.delete(lytter)
}

/**
 * `fetch` mot API-et: legger på `Authorization: Bearer …`, gjør feilsvar
 * (`{code, message}`) om til [ApiFeil] med norsk melding, og logger ut ved 401.
 *
 * @param meldinger egne meldinger for feilkoder fra dette endepunktet
 */
export async function apiKall<T>(
  sti: string,
  init: RequestInit = {},
  meldinger: Record<string, string> = {},
): Promise<T> {
  const token = hentToken()
  const headers = new Headers(init.headers)
  if (token) headers.set('Authorization', `Bearer ${token}`)

  let respons: Response
  try {
    respons = await fetch(sti, { ...init, headers })
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') throw err
    throw new ApiFeil('nettverk', 'Fikk ikke kontakt med serveren. Sjekk nettet og prøv igjen.')
  }

  if (respons.status === 401 && token) {
    // Tokenet er utløpt eller ugyldig. (Uten token er 401 et vanlig svar,
    // f.eks. feil passord, og håndteres som andre feil under.)
    slettToken()
    utloggingslyttere.forEach((lytter) => lytter())
  }

  if (!respons.ok) {
    const feil = await respons.json().catch(() => null) as { code?: string; message?: string } | null
    const code = feil?.code ?? `http_${respons.status}`
    throw new ApiFeil(
      code,
      meldinger[code] ?? FELLES_MELDINGER[code] ?? feil?.message ?? 'Noe gikk galt. Prøv igjen.',
    )
  }
  return await respons.json() as T
}

/** JSON-body til `apiKall`. */
export function json(body: unknown): RequestInit {
  return {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }
}
