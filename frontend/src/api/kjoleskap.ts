/** Klient for beholdningen: lagre en rettet skanning (#32) og hente kjøleskapet. */
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
  varer: BeholdningVare[]
  /** Skanningen beholdningen kom fra, eller null før første lagring. */
  skanning: { id: number; tidspunkt: string } | null
}

/** Erstatter husstandens beholdning med den rettede listen. Varer uten navn tas ikke med. */
export async function lagreIKjoleskapet(skanningId: number, varer: RedigerbarVare[]): Promise<void> {
  await apiKall(
    `/api/skanninger/${skanningId}/beholdning`,
    json({ varer: utfylte(varer).map(({ navn, antall, aiIndeks }) => ({ navn, antall, aiIndeks })) }),
  )
}

export function hentBeholdning(): Promise<Beholdning> {
  return apiKall('/api/beholdning')
}

/** Bildet fra skanningen (samme som AI-en fikk, så boksene passer). */
export function hentSkanningsbilde(skanningId: number): Promise<Blob> {
  return apiBlob(`/api/skanninger/${skanningId}/bilde`)
}
