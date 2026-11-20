import { afterEach, describe, expect, it, vi } from 'vitest'
import { analyserBilde } from './analyse.ts'
import { ApiFeil } from './klient.ts'

const bilde = new File([new Uint8Array([0xff, 0xd8, 0xff])], 'kjoleskap.jpg', { type: 'image/jpeg' })

function svar(status: number, body: unknown) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
    new Response(typeof body === 'string' ? body : JSON.stringify(body), { status }),
  ))
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('analyserBilde', () => {
  it('sender bildet som multipart-feltet «bilde» og returnerer svaret', async () => {
    svar(200, { skanningId: 7, varer: [{ navn: 'melk', antall: 1, sikkerhet: 'høy' }], leverandor: 'fake' })

    const resultat = await analyserBilde(bilde)

    expect(resultat.varer[0].navn).toBe('melk')
    expect(resultat.skanningId).toBe(7)
    const [url, init] = vi.mocked(fetch).mock.calls[0]
    expect(url).toBe('/api/analyze')
    expect((init?.body as FormData).get('bilde')).toBeInstanceOf(File)
    expect((init?.body as FormData).has('kjoleskapId')).toBe(false)
  })

  it('sender valgt kjøleskap som skjemafeltet «kjoleskapId»', async () => {
    svar(200, { skanningId: 7, varer: [], leverandor: 'fake' })

    await analyserBilde(bilde, 3)

    const [, init] = vi.mocked(fetch).mock.calls[0]
    expect((init?.body as FormData).get('kjoleskapId')).toBe('3')
  })

  it('gir tom liste med bokser når feltet mangler i svaret', async () => {
    svar(200, { varer: [{ navn: 'brunost', antall: 1, sikkerhet: 'høy' }], leverandor: 'anthropic' })

    const resultat = await analyserBilde(bilde)

    expect(resultat.varer[0].bokser).toEqual([])
  })

  it('tåler svar uten varer', async () => {
    svar(200, {})

    expect((await analyserBilde(bilde)).varer).toEqual([])
  })

  it('gjør kjent feilkode om til norsk melding', async () => {
    svar(413, { code: 'bilde_for_stort', message: 'Bildet er for stort.' })

    await expect(analyserBilde(bilde)).rejects.toMatchObject({
      code: 'bilde_for_stort',
      message: 'Bildet er for stort. Prøv å ta det på nytt.',
    })
  })

  it('bruker serverens melding for ukjente feilkoder', async () => {
    svar(400, { code: 'noe_nytt', message: 'Noe nytt gikk galt.' })

    await expect(analyserBilde(bilde)).rejects.toMatchObject({ code: 'noe_nytt', message: 'Noe nytt gikk galt.' })
  })

  it('tåler feilsvar som ikke er JSON', async () => {
    svar(502, '<html>Bad gateway</html>')

    await expect(analyserBilde(bilde)).rejects.toMatchObject({ code: 'http_502', message: 'Noe gikk galt. Prøv igjen.' })
  })

  it('gir egen melding når nettet er borte', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    const feil = await analyserBilde(bilde).catch((e: unknown) => e)

    expect(feil).toBeInstanceOf(ApiFeil)
    expect((feil as ApiFeil).code).toBe('nettverk')
  })
})
