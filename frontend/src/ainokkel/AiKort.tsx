import { useEffect, useState, type FormEvent } from 'react'
import {
  fjernAiNokkel,
  formaterPris,
  hentAi,
  lagreAiNokkel,
  velgModell,
  type AiInnstillinger,
  type Leverandor,
} from '../api/ai.ts'
import { ApiFeil } from '../api/klient.ts'
import { formaterTidspunkt } from '../sider/KjoleskapSide.tsx'

const NOKKELHJELP: Record<string, { url: string; hvor: string; eksempel: string }> = {
  anthropic: {
    url: 'https://platform.claude.com/settings/keys',
    hvor: 'platform.claude.com → Settings → API Keys',
    eksempel: 'sk-ant-api03-…',
  },
  gemini: {
    url: 'https://aistudio.google.com/apikey',
    hvor: 'aistudio.google.com → Get API key',
    eksempel: 'AIza…',
  },
}

function feilmelding(err: unknown): string {
  return err instanceof ApiFeil ? err.message : 'Noe gikk galt. Prøv igjen.'
}

/**
 * Husstandens AI-innstillinger: leverandør og modell (#54) og nøkkel per
 * leverandør (#42). Nøkkelen vises aldri igjen etter lagring, bare de fire
 * siste tegnene.
 */
export function AiKort() {
  const [ai, setAi] = useState<AiInnstillinger | null>(null)
  const [lasteFeil, setLasteFeil] = useState<string | null>(null)
  // Leverandøren som vises; ikke nødvendigvis den som er valgt.
  const [visning, setVisning] = useState<string | null>(null)
  const [velger, setVelger] = useState<string | null>(null)
  const [feil, setFeil] = useState<string | null>(null)

  useEffect(() => {
    hentAi()
      .then((a) => {
        setAi(a)
        setVisning(a.valg.leverandor)
      })
      .catch((err: unknown) => setLasteFeil(feilmelding(err)))
  }, [])

  async function velg(leverandor: string, modell: string) {
    setFeil(null)
    setVelger(modell)
    try {
      setAi(await velgModell(leverandor, modell))
    } catch (err) {
      setFeil(feilmelding(err))
    } finally {
      setVelger(null)
    }
  }

  if (lasteFeil) {
    return <Kort><p role="alert" className="text-red-700 dark:text-red-300">{lasteFeil}</p></Kort>
  }
  if (!ai || !visning) {
    return <Kort><p className="text-ink-soft dark:text-slate-400">Henter …</p></Kort>
  }

  const lev = ai.leverandorer.find((l) => l.id === visning) ?? ai.leverandorer[0]
  const valgtLev = ai.leverandorer.find((l) => l.id === ai.valg.leverandor)
  const valgtModell = valgtLev?.modeller.find((m) => m.id === ai.valg.modell)
  const manglerNokkel = ai.kreves && valgtLev !== undefined && !valgtLev.nokkel && !valgtLev.reserve

  return (
    <Kort>
      <p className="mb-3 text-sm text-ink-soft dark:text-slate-400">
        Husstanden velger selv hvilken AI som ser på bildene, og betaler for kallene med sin egen nøkkel.
        Valget og nøklene deles av alle i husstanden.
      </p>
      {!ai.kreves && (
        <p className="mb-3 rounded-xl bg-frost-deep px-3 py-2 text-sm dark:bg-slate-700">
          Testmodus: appen kjører uten ekte AI, så valget og nøkkelen brukes ikke nå.
        </p>
      )}

      <p className="mb-3 text-sm">
        Bruker nå: <span className="font-medium">{valgtModell?.navn ?? ai.valg.modell}</span>
        {!ai.valg.egetValg && <span className="text-ink-soft dark:text-slate-400"> (standard)</span>}
      </p>
      {manglerNokkel && (
        <p role="alert" className="mb-3 rounded-xl bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:bg-amber-950 dark:text-amber-200">
          {valgtLev.navn} mangler nøkkel. Legg den inn under for å kunne skanne.
        </p>
      )}

      <div role="tablist" aria-label="AI-leverandør" className="mb-3 grid grid-cols-2 gap-1 rounded-xl bg-frost-deep p-1 dark:bg-slate-700">
        {ai.leverandorer.map((l) => (
          <button
            key={l.id}
            type="button"
            role="tab"
            aria-selected={l.id === lev.id}
            onClick={() => {
              setVisning(l.id)
              setFeil(null)
            }}
            className={`rounded-lg px-3 py-2 text-sm font-medium ${
              l.id === lev.id ? 'bg-white shadow-sm dark:bg-slate-900' : 'text-ink-soft dark:text-slate-300'
            }`}
          >
            {l.navn}
          </button>
        ))}
      </div>

      {feil && (
        <p role="alert" className="mb-3 rounded-xl bg-red-50 px-3 py-2 text-sm text-red-800 dark:bg-red-950 dark:text-red-200">
          {feil}
        </p>
      )}

      <fieldset className="mb-4">
        <legend className="mb-2 text-sm font-medium">Modell</legend>
        <div className="flex flex-col gap-2">
          {lev.modeller.map((m) => {
            const valgt = ai.valg.leverandor === lev.id && ai.valg.modell === m.id
            const pris = formaterPris(m.prisPerBildeUsd)
            return (
              <label
                key={m.id}
                className={`flex cursor-pointer gap-3 rounded-xl border px-3 py-2 ${
                  valgt ? 'border-accent bg-frost-deep dark:bg-slate-700' : 'border-slate-200 dark:border-slate-600'
                }`}
              >
                <input
                  type="radio"
                  name="ai-modell"
                  className="mt-1 accent-accent"
                  checked={valgt}
                  disabled={velger !== null}
                  onChange={() => velg(lev.id, m.id)}
                />
                <span className="flex flex-col">
                  <span className="font-medium">
                    {m.navn}
                    {velger === m.id && <span className="ml-2 text-sm text-ink-soft">Lagrer …</span>}
                  </span>
                  <span className="text-sm text-ink-soft dark:text-slate-400">
                    {m.beskrivelse}
                    {pris && ` · ${pris}`}
                  </span>
                </span>
              </label>
            )
          })}
        </div>
      </fieldset>

      <NokkelSkjema key={lev.id} leverandor={lev} onEndret={setAi} />
    </Kort>
  )
}

/** Nøkkelen for én leverandør: legge inn, bytte og fjerne. */
function NokkelSkjema({ leverandor, onEndret }: { leverandor: Leverandor; onEndret: (a: AiInnstillinger) => void }) {
  const [redigerer, setRedigerer] = useState(false)
  const [verdi, setVerdi] = useState('')
  const [lagrer, setLagrer] = useState(false)
  const [feil, setFeil] = useState<string | null>(null)
  const [bekreftFjern, setBekreftFjern] = useState(false)
  const hjelp = NOKKELHJELP[leverandor.id] ?? NOKKELHJELP.anthropic
  const nokkel = leverandor.nokkel

  async function lagre(e: FormEvent) {
    e.preventDefault()
    setFeil(null)
    setLagrer(true)
    try {
      onEndret(await lagreAiNokkel(leverandor.id, verdi))
      setRedigerer(false)
    } catch (err) {
      setFeil(feilmelding(err))
    } finally {
      // Nøkkelen skal ikke bli liggende i skjemaet, heller ikke etter en feil.
      setVerdi('')
      setLagrer(false)
    }
  }

  async function fjern() {
    setFeil(null)
    try {
      await fjernAiNokkel(leverandor.id)
      onEndret(await hentAi())
    } catch (err) {
      setFeil(feilmelding(err))
    } finally {
      setBekreftFjern(false)
    }
  }

  return (
    <div>
      <h3 className="mb-2 text-sm font-medium">Nøkkel for {leverandor.navn}</h3>
      <p className="mb-3 text-sm text-ink-soft dark:text-slate-400">
        Lagres kryptert og kan ikke vises igjen etter at den er lagret.
      </p>
      {leverandor.reserve && (
        <p className="mb-3 rounded-xl bg-frost-deep px-3 py-2 text-sm dark:bg-slate-700">
          Bare lokalt: nøkkelen fra .env brukes til du legger inn en her.
        </p>
      )}

      {nokkel && !redigerer && (
        <div className="mb-3">
          <p className="font-mono text-lg tracking-wider">••••••••{nokkel.sisteTegn}</p>
          <p className="text-sm text-ink-soft dark:text-slate-400">
            Lagt inn {formaterTidspunkt(nokkel.endret)}
            {nokkel.endretAv && ` av ${nokkel.endretAv}`}
          </p>
        </div>
      )}

      {feil && (
        <p role="alert" className="mb-3 rounded-xl bg-red-50 px-3 py-2 text-sm text-red-800 dark:bg-red-950 dark:text-red-200">
          {feil}
        </p>
      )}

      {redigerer || !nokkel ? (
        <form onSubmit={lagre} className="flex flex-col gap-3">
          <label className="flex flex-col gap-1">
            <span className="text-sm">
              API-nøkkel fra{' '}
              <a href={hjelp.url} target="_blank" rel="noopener noreferrer" className="text-accent underline underline-offset-4">
                {hjelp.hvor}
              </a>
            </span>
            <input
              type="password"
              value={verdi}
              onChange={(e) => setVerdi(e.target.value)}
              placeholder={hjelp.eksempel}
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
            {nokkel && (
              <button
                type="button"
                onClick={() => {
                  setRedigerer(false)
                  setVerdi('')
                  setFeil(null)
                }}
                className="rounded-xl border border-accent px-4 py-3 font-medium text-accent"
              >
                Avbryt
              </button>
            )}
          </div>
        </form>
      ) : bekreftFjern ? (
        <div className="flex flex-col gap-2">
          <p className="text-sm">Fjerne nøkkelen? Da kan ingen i husstanden skanne med {leverandor.navn} før en ny er lagt inn.</p>
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
    </div>
  )
}

function Kort({ children }: { children: React.ReactNode }) {
  return (
    <section className="mt-6 rounded-2xl bg-white px-4 py-4 shadow-sm dark:bg-slate-800">
      <h2 className="mb-2 text-lg font-semibold">AI</h2>
      {children}
    </section>
  )
}
