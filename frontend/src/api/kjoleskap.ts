/** Klient for kjøleskapene (#61, #62) og beholdningen: lagre en rettet skanning (#32) og hente kjøleskapet. */
import type { Boks } from './analyse.ts'
import { apiBlob, apiKall, json } from './klient.ts'
import type { RedigerbarVare } from '../vareliste/redigering.ts'
import { utfylte } from '../vareliste/redigering.ts'

export type BeholdningVare = {
  navn: string
  antall: number
  bokser: Boks[]
}

export type Beholdning = {
  kjoleskap: { id: number; navn: string }
  varer: BeholdningVare[]
  /** Skanningen beholdningen kom fra, eller null før første lagring. */
  skanning: { id: number; tidspunkt: string } | null
}

/** Erstatter beholdningen i skanningens kjøleskap med den rettede listen. Varer uten navn tas ikke med. */
export async function lagreIKjoleskapet(skanningId: number, varer: RedigerbarVare[]): Promise<void> {
  await apiKall(
    `/api/skanninger/${skanningId}/beholdning`,
    json({ varer: utfylte(varer).map(({ navn, antall, aiIndeks }) => ({ navn, antall, aiIndeks })) }),
  )
}

/** Beholdningen i kjøleskapet; uten id husstandens standard kjøleskap. */
export function hentBeholdning(kjoleskapId?: number): Promise<Beholdning> {
  return apiKall(kjoleskapId === undefined ? '/api/beholdning' : `/api/beholdning?kjoleskap=${kjoleskapId}`)
}

/** Bildet fra skanningen (samme som AI-en fikk, så boksene passer). */
export function hentSkanningsbilde(skanningId: number): Promise<Blob> {
  return apiBlob(`/api/skanninger/${skanningId}/bilde`)
}

/** Et kjøleskap i husstanden. Listen kommer eldste (standard) først. */
export type Kjoleskap = {
  id: number
  navn: string
  /** Siste skanning, eller null hvis det aldri er skannet. */
  sistSkannet: string | null
}

function navnBody(metode: string, navn: string): RequestInit {
  return { ...json({ navn: navn.trim() }), method: metode }
}

export function hentKjoleskap(): Promise<Kjoleskap[]> {
  return apiKall('/api/kjoleskap')
}

export function opprettKjoleskap(navn: string): Promise<Kjoleskap[]> {
  return apiKall('/api/kjoleskap', navnBody('POST', navn))
}

export function giKjoleskapNyttNavn(id: number, navn: string): Promise<Kjoleskap[]> {
  return apiKall(`/api/kjoleskap/${id}`, navnBody('PUT', navn))
}

/** Sletter kjøleskapet med varelisten og bildet. Det siste kan ikke slettes. */
export async function slettKjoleskap(id: number): Promise<void> {
  await apiKall(`/api/kjoleskap/${id}`, { method: 'DELETE' })
}
