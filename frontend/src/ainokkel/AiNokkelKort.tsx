import { useEffect, useState, type FormEvent } from 'react'
import { fjernAiNokkel, hentAiNokkel, lagreAiNokkel, type AiNokkelStatus } from '../api/aiNokkel.ts'
import { ApiFeil } from '../api/klient.ts'
import { formaterTidspunkt } from '../sider/KjoleskapSide.tsx'

const LEVERANDORER: Record<string, { navn: string; url: string; hvor: string; eksempel: string }> = {
  anthropic: {
    navn: 'Anthropic (Claude)',
    url: 'https://platform.claude.com/settings/keys',
    hvor: 'platform.claude.com → Settings → API Keys',
    eksempel: 'sk-ant-api03-…',
  },
  gemini: {
    navn: 'Google Gemini',
    url: 'https://aistudio.google.com/apikey',
    hvor: 'aistudio.google.com → Get API key',
    eksempel: 'AIza…',
  },
}

/**
 * Husstandens AI-nøkkel (#42): legge inn, bytte og fjerne. Nøkkelen vises aldri
 * igjen etter lagring, bare de fire siste tegnene, og feltet tømmes med en gang.
 */
export function AiNokkelKort() {
  const [status, setStatus] = useState<AiNokkelStatus | null>(null)
  const [lasteFeil, setLasteFeil] = useState<string | null>(null)
  const [redigerer, setRedigerer] = useState(false)
  const [verdi, setVerdi] = useState('')
  const [lagrer, setLagrer] = useState(false)
  const [feil, setFeil] = useState<string | null>(null)
  const [bekreftFjern, setBekreftFjern] = useState(false)

  useEffect(() => {
    hentAiNokkel()
      .then(setStatus)
      .catch((err: unknown) => setLasteFeil(err instanceof ApiFeil ? err.message : 'Kunne ikke hente AI-nøkkelen.'))
  }, [])

  async function lagre(e: FormEvent) {
    e.preventDefault()
    setFeil(null)
    setLagrer(true)
    try {
      setStatus(await lagreAiNokkel(verdi))
      setRedigerer(false)
    } catch (err) {
      setFeil(err instanceof ApiFeil ? err.message : 'Noe gikk galt. Prøv igjen.')
    } finally {
      // Nøkkelen skal ikke bli liggende i skjemaet, heller ikke etter en feil.
      setVerdi('')
      setLagrer(false)
    }
  }

  async function fjern() {
    setFeil(null)
    try {
      await fjernAiNokkel()
      setStatus((s) => (s ? { ...s, nokkel: null } : s))
    } catch (err) {
      setFeil(err instanceof ApiFeil ? err.message : 'Noe gikk galt. Prøv igjen.')
    } finally {
      setBekreftFjern(false)
    }
  }

  function avbryt() {
    setRedigerer(false)
    setVerdi('')
    setFeil(null)
  }

  if (lasteFeil) {
    return <Kort><p role="alert" className="text-red-700 dark:text-red-300">{lasteFeil}</p></Kort>
  }
  if (!status) {
    return <Kort><p className="text-ink-soft dark:text-slate-400">Henter …</p></Kort>
  }

  const lev = LEVERANDORER[status.leverandor] ?? LEVERANDORER.anthropic
  const visSkjema = redigerer || !status.nokkel

  return (
    <Kort>
      <p className="mb-3 text-sm text-ink-soft dark:text-slate-400">
        Husstanden bruker sin egen nøkkel hos {lev.navn} og betaler selv for AI-kallene
        (noen få øre per bilde). Nøkkelen lagres kryptert, deles av alle i husstanden og
        kan ikke vises igjen etter at den er lagret.
      </p>
      {!status.kreves && (
        <p className="mb-3 rounded-xl bg-frost-deep px-3 py-2 text-sm dark:bg-slate-700">
          Testmodus: appen kjører uten ekte AI, så nøkkelen brukes ikke nå.
        </p>
      )}
      {status.reserve && (
        <p className="mb-3 rounded-xl bg-frost-deep px-3 py-2 text-sm dark:bg-slate-700">
          Bare lokalt: nøkkelen fra .env brukes til du legger inn en her.
        </p>
      )}

      {status.nokkel && !redigerer && (
        <div className="mb-3">
          <p className="font-mono text-lg tracking-wider">••••••••{status.nokkel.sisteTegn}</p>
          <p className="text-sm text-ink-soft dark:text-slate-400">
            Lagt inn {formaterTidspunkt(status.nokkel.endret)}
            {status.nokkel.endretAv && ` av ${status.nokkel.endretAv}`}
          </p>
        </div>
      )}

      {feil && (
        <p role="alert" className="mb-3 rounded-xl bg-red-50 px-3 py-2 text-sm text-red-800 dark:bg-red-950 dark:text-red-200">
          {feil}
        </p>
      )}

      {visSkjema ? (
        <form onSubmit={lagre} className="flex flex-col gap-3">
          <label className="flex flex-col gap-1">
            <span className="text-sm">
              API-nøkkel fra{' '}
              <a href={lev.url} target="_blank" rel="noopener noreferrer" className="text-accent underline underline-offset-4">
                {lev.hvor}
              </a>
            </span>
            <input
              type="password"
              value={verdi}
              onChange={(e) => setVerdi(e.target.value)}
              placeholder={lev.eksempel}
              autoComplete="off"
              autoCorrect="off"
              autoCapitalize="none"
              spellCheck={false}
              className="rounded-xl border border-slate-300 bg-white px-3 py-3 font-mono dark:border-slate-600 dark:bg-slate-900"
            />
          </label>
          <div className="flex gap-3">
            <button
              type="submit"
              disabled={lagrer || verdi.trim() === ''}
              className="flex-1 rounded-xl bg-accent px-4 py-3 font-medium text-white active:bg-accent-hover disabled:opacity-50"
            >
              {lagrer ? 'Lagrer …' : 'Lagre nøkkel'}
            </button>
            {status.nokkel && (
              <button
                type="button"
                onClick={avbryt}
                className="rounded-xl border border-accent px-4 py-3 font-medium text-accent"
              >
                Avbryt
              </button>
            )}
          </div>
        </form>
      ) : bekreftFjern ? (
        <div className="flex flex-col gap-2">
          <p className="text-sm">Fjerne nøkkelen? Da kan ingen i husstanden skanne før en ny er lagt inn.</p>
          <div className="flex gap-3">
            <button type="button" onClick={fjern} className="flex-1 rounded-xl bg-red-700 px-4 py-3 font-medium text-white">
              Fjern
            </button>
            <button
              type="button"
              onClick={() => setBekreftFjern(false)}
              className="flex-1 rounded-xl border border-accent px-4 py-3 font-medium text-accent"
            >
              Avbryt
            </button>
          </div>
        </div>
      ) : (
        <div className="flex gap-3">
          <button
            type="button"
            onClick={() => setRedigerer(true)}
            className="flex-1 rounded-xl border border-accent px-4 py-3 font-medium text-accent"
          >
            Bytt nøkkel
          </button>
          <button
            type="button"
            onClick={() => setBekreftFjern(true)}
            className="flex-1 rounded-xl border border-red-700 px-4 py-3 font-medium text-red-700 dark:border-red-400 dark:text-red-400"
          >
            Fjern
          </button>
        </div>
      )}
    </Kort>
  )
}

function Kort({ children }: { children: React.ReactNode }) {
  return (
    <section className="mt-6 rounded-2xl bg-white px-4 py-4 shadow-sm dark:bg-slate-800">
      <h2 className="mb-2 text-lg font-semibold">AI-nøkkel</h2>
      {children}
    </section>
  )
}
