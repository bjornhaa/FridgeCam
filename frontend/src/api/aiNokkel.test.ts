import { afterEach, describe, expect, it, vi } from 'vitest'
import { fjernAiNokkel, hentAiNokkel, lagreAiNokkel } from './aiNokkel.ts'

function svar(status: number, body?: unknown) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
    new Response(body === undefined ? null : JSON.stringify(body), { status }),
  ))
}

afterEach(() => {
  vi.unstubAllGlobals()
})

const status = { leverandor: 'anthropic', kreves: true, nokkel: { sisteTegn: 'WXYZ', endret: '2026-11-17T20:00:00Z', endretAv: 'Test' }, reserve: false }

describe('aiNokkel', () => {
  it('lagrer nøkkelen med PUT, trimmet', async () => {
    svar(200, status)

    const resultat = await lagreAiNokkel('  sk-ant-api03-test-WXYZ \n')

    const [sti, init] = vi.mocked(fetch).mock.calls[0]
    expect(sti).toBe('/api/husstand/ai-nokkel')
    expect(init?.method).toBe('PUT')
    expect(JSON.parse(init?.body as string)).toEqual({ nokkel: 'sk-ant-api03-test-WXYZ' })
    expect(resultat.nokkel?.sisteTegn).toBe('WXYZ')
  })

  it('henter status og fjerner nøkkelen', async () => {
    svar(200, status)
    expect((await hentAiNokkel()).kreves).toBe(true)

    svar(204)
    await fjernAiNokkel()
    expect(vi.mocked(fetch).mock.calls[0][1]?.method).toBe('DELETE')
  })

  it('viser meldingen fra backend når nøkkelen avvises', async () => {
    svar(400, { code: 'ugyldig_foresporsel', message: 'Dette er en admin-nøkkel. Lag en vanlig API-nøkkel i stedet.' })

    await expect(lagreAiNokkel('sk-ant-admin01-x')).rejects.toThrow('admin-nøkkel')
  })
})
