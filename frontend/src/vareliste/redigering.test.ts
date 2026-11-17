import { describe, expect, it } from 'vitest'
import type { FunnetVare } from '../api/analyse.ts'
import { bekreft, endre, erUsikker, fjern, fraAnalyse, leggTil, MAKS_ANTALL, utfylte } from './redigering.ts'

const ai: FunnetVare[] = [
  { navn: 'melk', antall: 2, sikkerhet: 'høy', bokser: [{ x1: 1, y1: 2, x2: 3, y2: 4 }] },
  { navn: 'brunost', antall: 1, sikkerhet: 'lav', bokser: [] },
  { navn: 'smør', antall: 1, sikkerhet: 'middels', bokser: [] },
  { navn: 'yoghurt', antall: 3, sikkerhet: 'lav', bokser: [] },
]

describe('fraAnalyse', () => {
  it('legger usikre varer først og beholder ellers rekkefølgen', () => {
    expect(fraAnalyse(ai).map((v) => v.navn)).toEqual(['brunost', 'yoghurt', 'melk', 'smør'])
  })

  it('beholder bokser og sikkerhet, og gir unike id-er', () => {
    const varer = fraAnalyse(ai)
    expect(varer.find((v) => v.navn === 'melk')?.bokser).toHaveLength(1)
    expect(new Set(varer.map((v) => v.id)).size).toBe(varer.length)
    expect(varer.filter(erUsikker).map((v) => v.navn)).toEqual(['brunost', 'yoghurt'])
  })

  it('holder antall innenfor 1 til maks', () => {
    const varer = fraAnalyse([
      { navn: 'egg', antall: 0, sikkerhet: 'høy', bokser: [] },
      { navn: 'øl', antall: 500, sikkerhet: 'høy', bokser: [] },
    ])
    expect(varer.map((v) => v.antall)).toEqual([1, MAKS_ANTALL])
  })
})

describe('redigering', () => {
  const varer = fraAnalyse(ai)
  const melk = varer.find((v) => v.navn === 'melk')!
  const brunost = varer.find((v) => v.navn === 'brunost')!

  it('endrer antall, men aldri under 1', () => {
    expect(endre(varer, melk.id, { antall: 3 }).find((v) => v.id === melk.id)?.antall).toBe(3)
    expect(endre(varer, melk.id, { antall: 0 }).find((v) => v.id === melk.id)?.antall).toBe(1)
  })

  it('nytt navn på en usikker vare regnes som sjekket', () => {
    const rettet = endre(varer, brunost.id, { navn: 'geitost' }).find((v) => v.id === brunost.id)!
    expect(rettet.navn).toBe('geitost')
    expect(erUsikker(rettet)).toBe(false)
  })

  it('bare antall gjør ikke en usikker vare sjekket', () => {
    expect(erUsikker(endre(varer, brunost.id, { antall: 2 }).find((v) => v.id === brunost.id)!)).toBe(true)
  })

  it('bekreft fjerner usikker-merket', () => {
    expect(erUsikker(bekreft(varer, brunost.id).find((v) => v.id === brunost.id)!)).toBe(false)
  })

  it('fjerner bare den valgte varen', () => {
    expect(fjern(varer, melk.id).map((v) => v.navn)).toEqual(['brunost', 'yoghurt', 'smør'])
  })

  it('ny vare legges nederst uten bokser og uten AI-sikkerhet', () => {
    const { varer: nye, id } = leggTil(varer)
    const ny = nye.at(-1)!
    expect(ny).toMatchObject({ id, navn: '', antall: 1, sikkerhet: null, bokser: [] })
    expect(leggTil(nye).id).not.toBe(id)
  })

  it('varer uten navn tas ikke med, og navn trimmes', () => {
    const { varer: medTom } = leggTil(endre(varer, melk.id, { navn: '  lettmelk ' }))
    expect(utfylte(medTom).map((v) => v.navn)).toEqual(['brunost', 'yoghurt', 'lettmelk', 'smør'])
  })
})
