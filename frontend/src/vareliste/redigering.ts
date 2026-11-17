import type { Boks, FunnetVare, Sikkerhet } from '../api/analyse.ts'

/**
 * En vare i listen brukeren retter før den lagres (#31).
 *
 * `sikkerhet` er AI-ens, eller null for varer brukeren har lagt til selv.
 * `bekreftet` settes når brukeren har sjekket en usikker vare.
 */
export type RedigerbarVare = {
  id: string
  navn: string
  antall: number
  sikkerhet: Sikkerhet | null
  bekreftet: boolean
  bokser: Boks[]
}

export const MAKS_ANTALL = 99

/** Usikre varer først, så de sjekkes før resten; ellers AI-ens rekkefølge. */
export function fraAnalyse(varer: FunnetVare[]): RedigerbarVare[] {
  const redigerbare = varer.map((v, i) => ({
    id: `ai-${i}`,
    navn: v.navn,
    antall: Math.min(Math.max(v.antall, 1), MAKS_ANTALL),
    sikkerhet: v.sikkerhet,
    bekreftet: false,
    bokser: v.bokser,
  }))
  return [...redigerbare.filter(erUsikker), ...redigerbare.filter((v) => !erUsikker(v))]
}

/** Usikker og ikke sjekket av brukeren ennå. */
export function erUsikker(vare: RedigerbarVare): boolean {
  return vare.sikkerhet === 'lav' && !vare.bekreftet
}

export function endre(varer: RedigerbarVare[], id: string, endring: Partial<Pick<RedigerbarVare, 'navn' | 'antall'>>): RedigerbarVare[] {
  return varer.map((v) => {
    if (v.id !== id) return v
    const antall = endring.antall === undefined ? v.antall : Math.min(Math.max(Math.round(endring.antall), 1), MAKS_ANTALL)
    // Har brukeren rettet navnet, har den også sjekket varen.
    const bekreftet = v.bekreftet || (endring.navn !== undefined && endring.navn !== v.navn)
    return { ...v, ...endring, antall, bekreftet }
  })
}

export function bekreft(varer: RedigerbarVare[], id: string): RedigerbarVare[] {
  return varer.map((v) => (v.id === id ? { ...v, bekreftet: true } : v))
}

export function fjern(varer: RedigerbarVare[], id: string): RedigerbarVare[] {
  return varer.filter((v) => v.id !== id)
}

let nesteId = 0

/** Ny, tom vare nederst i listen. Returnerer listen og id-en til den nye varen. */
export function leggTil(varer: RedigerbarVare[]): { varer: RedigerbarVare[]; id: string } {
  const id = `ny-${++nesteId}`
  return {
    varer: [...varer, { id, navn: '', antall: 1, sikkerhet: null, bekreftet: true, bokser: [] }],
    id,
  }
}

/** Varer uten navn tas ikke med når listen lagres. */
export function utfylte(varer: RedigerbarVare[]): RedigerbarVare[] {
  return varer
    .map((v) => ({ ...v, navn: v.navn.trim() }))
    .filter((v) => v.navn !== '')
}
