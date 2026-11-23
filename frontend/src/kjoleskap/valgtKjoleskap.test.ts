import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gjeldende, hentSistValgt, lagreSistValgt } from './valgtKjoleskap.ts'

const hjemme = { id: 1, navn: 'Hjemme', sistSkannet: null }
const hytta = { id: 2, navn: 'Hytta', sistSkannet: null }

// Testene kjører i Node, uten localStorage.
beforeEach(() => {
  const lager = new Map<string, string>()
  vi.stubGlobal('localStorage', {
    getItem: (k: string) => lager.get(k) ?? null,
    setItem: (k: string, v: string) => void lager.set(k, v),
  })
})

afterEach(() => vi.unstubAllGlobals())

describe('valgt kjøleskap', () => {
  it('bruker det huskede når det finnes, ellers det første', () => {
    expect(gjeldende([hjemme, hytta], 2)).toBe(hytta)
    expect(gjeldende([hjemme, hytta], 99)).toBe(hjemme)
    expect(gjeldende([hjemme, hytta], null)).toBe(hjemme)
    expect(gjeldende([], 2)).toBeNull()
  })

  it('huskes mellom besøk, og tull i lagringen ignoreres', () => {
    expect(hentSistValgt()).toBeNull()
    lagreSistValgt(2)
    expect(hentSistValgt()).toBe(2)
    localStorage.setItem('fridgecam.kjoleskap', 'tull')
    expect(hentSistValgt()).toBeNull()
  })
})
