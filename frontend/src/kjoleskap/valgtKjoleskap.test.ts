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

describe('kortDato', () => {
  it('gir i dag, i går eller kort dato', async () => {
    const { kortDato } = await import('./KjoleskapVelger.tsx')
    const na = new Date(2026, 10, 20, 12, 0)
    expect(kortDato(new Date(2026, 10, 20, 8, 0).toISOString(), na)).toBe('i dag')
    expect(kortDato(new Date(2026, 10, 19, 23, 0).toISOString(), na)).toBe('i går')
    expect(kortDato(new Date(2026, 10, 2, 9, 0).toISOString(), na)).toMatch(/^2\. nov/)
    expect(kortDato(new Date(2025, 6, 2, 9, 0).toISOString(), na)).toMatch(/2025/)
  })
})
