import type { Boks } from '../api/analyse.ts'

/** Koordinatene fra AI-en går fra 0 til denne verdien langs hver akse. */
export const BOKS_MAKS = 1000

/**
 * Gjør en boks om til CSS-posisjon i prosent av bildet. Prosent gjør at boksen
 * havner riktig uansett hvor stort bildet vises på skjermen.
 */
export function boksStil(boks: Boks): { left: string; top: string; width: string; height: string } {
  const prosent = (v: number) => `${(Math.min(Math.max(v, 0), BOKS_MAKS) / BOKS_MAKS) * 100}%`
  return {
    left: prosent(boks.x1),
    top: prosent(boks.y1),
    width: prosent(boks.x2 - boks.x1),
    height: prosent(boks.y2 - boks.y1),
  }
}
