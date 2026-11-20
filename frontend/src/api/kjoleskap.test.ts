import { afterEach, describe, expect, it, vi } from 'vitest'
import { giKjoleskapNyttNavn, hentKjoleskap, opprettKjoleskap, slettKjoleskap } from './kjoleskap.ts'
import { ApiFeil } from './klient.ts'

function svar(status: number, body?: unknown) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
    new Response(body === undefined ? null : JSON.stringify(body), { status }),
  ))
}

function sendt() {
  const [sti, init] = vi.mocked(fetch).mock.calls[0]
  return { sti, metode: init?.method ?? 'GET', body: init?.body ? JSON.parse(init.body as string) : undefined }
}

afterEach(() => {
  vi.unstubAllGlobals()
})

const liste = [{ id: 1, navn: 'Hjemme', sistSkannet: null }]

describe('kjøleskap', () => {
  it('henter listen', async () => {
    svar(200, liste)
    expect(await hentKjoleskap()).toEqual(liste)
    expect(sendt()).toEqual({ sti: '/api/kjoleskap', metode: 'GET', body: undefined })
  })

  it('oppretter og gir nytt navn med trimmet navn', async () => {
    svar(201, liste)
    await opprettKjoleskap('  Hytta ')
    expect(sendt()).toEqual({ sti: '/api/kjoleskap', metode: 'POST', body: { navn: 'Hytta' } })

    svar(200, liste)
    await giKjoleskapNyttNavn(7, 'Byen ')
    expect(sendt()).toEqual({ sti: '/api/kjoleskap/7', metode: 'PUT', body: { navn: 'Byen' } })
  })

  it('sletter, og viser serverens melding når det siste ikke kan slettes', async () => {
    svar(204)
    await slettKjoleskap(7)
    expect(sendt()).toEqual({ sti: '/api/kjoleskap/7', metode: 'DELETE', body: undefined })

    svar(409, { code: 'siste_kjoleskap', message: 'Husstanden må ha minst ett kjøleskap.' })
    await expect(slettKjoleskap(1)).rejects.toEqual(new ApiFeil('siste_kjoleskap', 'Husstanden må ha minst ett kjøleskap.'))
  })
})
