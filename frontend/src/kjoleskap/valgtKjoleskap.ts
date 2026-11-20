/**
 * Hvilket kjøleskap som sist ble brukt (#63, #64), husket per enhet i
 * localStorage. Skann og Kjøleskapet deler det, så man lander på kjøleskapet
 * man nettopp skannet. localStorage kan kaste (privat modus); da huskes det
 * bare til siden lastes på nytt.
 */
import type { Kjoleskap } from '../api/kjoleskap.ts'

const NOKKEL = 'fridgecam.kjoleskap'

let iMinnet: number | null = null

export function hentSistValgt(): number | null {
  try {
    const verdi = Number(localStorage.getItem(NOKKEL))
    return Number.isInteger(verdi) && verdi > 0 ? verdi : null
  } catch {
    return iMinnet
  }
}

export function lagreSistValgt(id: number) {
  iMinnet = id
  try {
    localStorage.setItem(NOKKEL, String(id))
  } catch {
    // Bare i minnet.
  }
}

/** Det husket kjøleskapet hvis det fortsatt finnes, ellers husstandens standard (det første). */
export function gjeldende(liste: Kjoleskap[], husket: number | null): Kjoleskap | null {
  return liste.find((k) => k.id === husket) ?? liste[0] ?? null
}
