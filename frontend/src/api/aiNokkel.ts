/**
 * Klient for husstandens AI-nøkkel (#42). Nøkkelen sendes bare én vei: den kan
 * legges inn, men API-et gir aldri mer enn de fire siste tegnene tilbake.
 * Den lagres aldri i nettleseren.
 */
import { apiKall } from './klient.ts'

export type AiNokkelStatus = {
  /** `anthropic` eller `gemini`. */
  leverandor: string
  /** False når backend kjører med fake-AI (lokalt): da trengs ingen nøkkel. */
  kreves: boolean
  nokkel: { sisteTegn: string; endret: string; endretAv: string | null } | null
  /** Bare lokalt: ingen nøkkel lagt inn, men nøkkelen fra .env brukes. */
  reserve: boolean
}

export function hentAiNokkel(): Promise<AiNokkelStatus> {
  return apiKall('/api/husstand/ai-nokkel')
}

export function lagreAiNokkel(nokkel: string): Promise<AiNokkelStatus> {
  return apiKall('/api/husstand/ai-nokkel', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nokkel: nokkel.trim() }),
  })
}

export async function fjernAiNokkel(): Promise<void> {
  await apiKall('/api/husstand/ai-nokkel', { method: 'DELETE' })
}

/** Feilkoder fra skanning som betyr at brukeren må gjøre noe med nøkkelen under Profil. */
export const NOKKELFEIL = new Set(['mangler_ai_nokkel', 'ugyldig_ai_nokkel', 'ai_mangler_kreditt'])
