/**
 * Innloggings-tokenet (JWT) lagres i localStorage, så innloggingen huskes
 * mellom besøk til tokenet utløper (30 dager, satt av backend).
 *
 * localStorage kan kaste (f.eks. privat modus i enkelte nettlesere); da
 * holdes tokenet bare i minnet, og brukeren må logge inn igjen neste gang.
 */
const NOKKEL = 'fridgecam.token'

let iMinnet: string | null = null

export function hentToken(): string | null {
  try {
    return localStorage.getItem(NOKKEL)
  } catch {
    return iMinnet
  }
}

export function lagreToken(token: string) {
  iMinnet = token
  try {
    localStorage.setItem(NOKKEL, token)
  } catch {
    // Bare i minnet.
  }
}

export function slettToken() {
  iMinnet = null
  try {
    localStorage.removeItem(NOKKEL)
  } catch {
    // Ingenting å slette.
  }
}
