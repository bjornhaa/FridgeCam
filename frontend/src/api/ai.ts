/**
 * Klient for husstandens AI-innstillinger: valg av leverandør og modell (#54) og
 * nøkkel per leverandør (#42). En nøkkel sendes bare én vei: API-et gir aldri mer
 * enn de fire siste tegnene tilbake, og den lagres aldri i nettleseren.
 */
import { apiKall } from './klient.ts'

export type NokkelInfo = { sisteTegn: string; endret: string; endretAv: string | null }

export type Modell = { id: string; navn: string; beskrivelse: string; prisPerBildeUsd: number | null }

export type Leverandor = {
  /** `anthropic` eller `gemini`. */
  id: string
  navn: string
  nokkel: NokkelInfo | null
  /** Bare lokalt: ingen nøkkel lagt inn, men nøkkelen fra .env brukes. */
  reserve: boolean
  modeller: Modell[]
}

export type AiInnstillinger = {
  /** False når backend kjører med fake-AI (lokalt): da trengs ingen nøkkel. */
  kreves: boolean
  valg: { leverandor: string; modell: string; egetValg: boolean }
  leverandorer: Leverandor[]
}

export function hentAi(): Promise<AiInnstillinger> {
  return apiKall('/api/husstand/ai')
}

function put(body: unknown): RequestInit {
  return { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
}

export function velgModell(leverandor: string, modell: string): Promise<AiInnstillinger> {
  return apiKall('/api/husstand/ai/valg', put({ leverandor, modell }))
}

export function lagreAiNokkel(leverandor: string, nokkel: string): Promise<AiInnstillinger> {
  return apiKall('/api/husstand/ai/nokkel', put({ leverandor, nokkel: nokkel.trim() }))
}

export async function fjernAiNokkel(leverandor: string): Promise<void> {
  await apiKall(`/api/husstand/ai/nokkel/${encodeURIComponent(leverandor)}`, { method: 'DELETE' })
}

/** «ca. 0,06 USD per bilde», til visning ved modellvalget. */
export function formaterPris(usd: number | null): string | null {
  if (usd === null) return null
  if (usd < 0.01) return 'under 0,01 USD per bilde'
  return `ca. ${usd.toLocaleString('nb-NO', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} USD per bilde`
}

/** Feilkoder fra skanning som betyr at brukeren må gjøre noe under Profil. */
export const NOKKELFEIL = new Set(['mangler_ai_nokkel', 'ugyldig_ai_nokkel', 'ai_mangler_kreditt'])
