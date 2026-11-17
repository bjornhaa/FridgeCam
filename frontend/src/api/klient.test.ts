import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiBlob, apiKall, ApiFeil, vedUtlogging } from './klient.ts'
import { loggInn } from './innlogging.ts'
import { hentToken, lagreToken } from './token.ts'

function lagMinneLagring(): Storage {
  const data = new Map<string, string>()
  return {
    get length() { return data.size },
    clear: () => data.clear(),
    getItem: (k) => data.get(k) ?? null,
    key: (i) => [...data.keys()][i] ?? null,
    removeItem: (k) => { data.delete(k) },
    setItem: (k, v) => { data.set(k, v) },
  }
}

function svar(status: number, body: unknown) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status })))
}

function sendtHeader(navn: string): string | null {
  const [, init] = vi.mocked(fetch).mock.calls[0]
  return new Headers(init?.headers).get(navn)
}

beforeEach(() => {
  vi.stubGlobal('localStorage', lagMinneLagring())
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('apiKall', () => {
  it('sender tokenet som Bearer', async () => {
    lagreToken('abc.def.ghi')
    svar(200, { ok: true })

    await apiKall('/api/meg')

    expect(sendtHeader('Authorization')).toBe('Bearer abc.def.ghi')
  })

  it('sender ingen Authorization uten token', async () => {
    svar(200, { ok: true })

    await apiKall('/api/meg')

    expect(sendtHeader('Authorization')).toBeNull()
  })

  it('beholder egne headere ved siden av tokenet', async () => {
    lagreToken('t')
    svar(200, {})

    await apiKall('/api/x', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' })

    expect(sendtHeader('Content-Type')).toBe('application/json')
    expect(sendtHeader('Authorization')).toBe('Bearer t')
  })

  it('logger ut når serveren avviser tokenet', async () => {
    lagreToken('utlopt')
    const lytter = vi.fn()
    const stopp = vedUtlogging(lytter)
    svar(401, { code: 'ikke_innlogget', message: 'Du må logge inn.' })

    await expect(apiKall('/api/meg')).rejects.toMatchObject({
      code: 'ikke_innlogget',
      message: 'Du er logget ut. Logg inn igjen.',
    })
    expect(hentToken()).toBeNull()
    expect(lytter).toHaveBeenCalledOnce()
    stopp()
  })

  it('feil passord logger ikke ut, men gir norsk melding', async () => {
    const lytter = vi.fn()
    const stopp = vedUtlogging(lytter)
    svar(401, { code: 'ugyldig_innlogging', message: 'Feil e-post eller passord.' })

    const feil = await loggInn('ola@example.com', 'feil').catch((e: unknown) => e)

    expect(feil).toBeInstanceOf(ApiFeil)
    expect((feil as ApiFeil).code).toBe('ugyldig_innlogging')
    expect(lytter).not.toHaveBeenCalled()
    stopp()
  })

  it('innlogging sender e-post og passord som JSON', async () => {
    svar(200, { token: 't', bruker: { id: 1, epost: 'ola@example.com', navn: 'Ola', husstander: [] } })

    const resultat = await loggInn('ola@example.com', 'hemmelig123')

    expect(resultat.token).toBe('t')
    const [url, init] = vi.mocked(fetch).mock.calls[0]
    expect(url).toBe('/api/auth/logg-inn')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(init?.body as string)).toEqual({ epost: 'ola@example.com', passord: 'hemmelig123' })
  })

  it('henter bilder som blob med token', async () => {
    lagreToken('t')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(new Uint8Array([1, 2, 3]), { status: 200 })))

    const blob = await apiBlob('/api/skanninger/1/bilde')

    expect(new Uint8Array(await blob.arrayBuffer())).toEqual(new Uint8Array([1, 2, 3]))
    expect(sendtHeader('Authorization')).toBe('Bearer t')
  })

  it('tåler 204 uten innhold', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })))

    await expect(apiKall('/api/skanninger/1/beholdning', { method: 'POST' })).resolves.toBeUndefined()
  })

  it('bruker serverens melding for valideringsfeil', async () => {
    svar(400, { code: 'ugyldig_foresporsel', message: 'Passordet må ha mellom 8 og 72 tegn.' })

    await expect(apiKall('/api/auth/registrer')).rejects.toMatchObject({
      message: 'Passordet må ha mellom 8 og 72 tegn.',
    })
  })
})

describe('token', () => {
  it('huskes i minnet når localStorage ikke virker', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => { throw new Error('blokkert') },
      setItem: () => { throw new Error('blokkert') },
      removeItem: () => { throw new Error('blokkert') },
    })

    lagreToken('i-minnet')

    expect(hentToken()).toBe('i-minnet')
  })
})
