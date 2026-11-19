import { afterEach, describe, expect, it, vi } from 'vitest'
import { fjernAiNokkel, formaterPris, hentAi, lagreAiNokkel, velgModell } from './ai.ts'

function svar(status: number, body?: unknown) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
    new Response(body === undefined ? null : JSON.stringify(body), { status }),
  ))
}

function sendt() {
  const [sti, init] = vi.mocked(fetch).mock.calls[0]
  return { sti, metode: init?.method, body: init?.body ? JSON.parse(init.body as string) : undefined }
}

afterEach(() => {
  vi.unstubAllGlobals()
})

const innstillinger = {
  kreves: true,
  valg: { leverandor: 'anthropic', modell: 'claude-opus-5-5', egetValg: false },
  leverandorer: [],
}

describe('ai', () => {
  it('lagrer nøkkelen for en leverandør med PUT, trimmet', async () => {
    svar(200, innstillinger)

    await lagreAiNokkel('anthropic', '  sk-ant-api03-test-WXYZ \n')

    expect(sendt()).toEqual({
      sti: '/api/husstand/ai/nokkel',
      metode: 'PUT',
      body: { leverandor: 'anthropic', nokkel: 'sk-ant-api03-test-WXYZ' },
    })
  })

  it('velger modell, henter innstillinger og fjerner nøkkel', async () => {
    svar(200, innstillinger)
    await velgModell('gemini', 'gemini-3.8-flash')
    expect(sendt()).toEqual({ sti: '/api/husstand/ai/valg', metode: 'PUT', body: { leverandor: 'gemini', modell: 'gemini-3.8-flash' } })

    svar(200, innstillinger)
    expect((await hentAi()).kreves).toBe(true)

    svar(204)
    await fjernAiNokkel('gemini')
    expect(sendt()).toMatchObject({ sti: '/api/husstand/ai/nokkel/gemini', metode: 'DELETE' })
  })

  it('viser meldingen fra backend når nøkkelen avvises', async () => {
    svar(400, { code: 'ugyldig_foresporsel', message: 'Dette er en admin-nøkkel. Lag en vanlig API-nøkkel i stedet.' })

    await expect(lagreAiNokkel('anthropic', 'sk-ant-admin01-x')).rejects.toThrow('admin-nøkkel')
  })

  it('formaterer pris per bilde', () => {
    expect(formaterPris(0.056)).toBe('ca. 0,06 USD per bilde')
    expect(formaterPris(0.0014)).toBe('under 0,01 USD per bilde')
    expect(formaterPris(null)).toBeNull()
  })
})
