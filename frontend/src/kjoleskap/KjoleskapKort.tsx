import { useEffect, useState, type FormEvent } from 'react'
import {
  giKjoleskapNyttNavn,
  hentKjoleskap,
  opprettKjoleskap,
  slettKjoleskap,
  type Kjoleskap,
} from '../api/kjoleskap.ts'
import { ApiFeil } from '../api/klient.ts'
import { formaterTidspunkt } from '../sider/KjoleskapSide.tsx'

const MAKS_NAVN = 40

function feilmelding(err: unknown): string {
  return err instanceof ApiFeil ? err.message : 'Noe gikk galt. Prøv igjen.'
}

const feltKlasse =
  'min-w-0 flex-1 rounded-xl border border-slate-300 bg-white px-3 py-2 dark:border-slate-600 dark:bg-slate-900'
const knappKlasse = 'rounded-xl bg-accent px-4 py-2 font-medium text-white active:bg-accent-hover disabled:opacity-50'
const sekundarKlasse = 'rounded-xl border border-accent px-4 py-2 font-medium text-accent'

/** Husstandens kjøleskap (#62): legge til, gi nytt navn og slette. */
export function KjoleskapKort() {
  const [liste, setListe] = useState<Kjoleskap[] | null>(null)
  const [feil, setFeil] = useState<string | null>(null)
  const [nytt, setNytt] = useState('')
  const [lagrer, setLagrer] = useState(false)

  useEffect(() => {
    hentKjoleskap().then(setListe).catch((err: unknown) => setFeil(feilmelding(err)))
  }, [])

  async function leggTil(e: FormEvent) {
    e.preventDefault()
    setFeil(null)
    setLagrer(true)
    try {
      setListe(await opprettKjoleskap(nytt))
      setNytt('')
    } catch (err) {
      setFeil(feilmelding(err))
    } finally {
      setLagrer(false)
    }
  }

  return (
    <section className="mt-6 rounded-2xl bg-white px-4 py-4 shadow-sm dark:bg-slate-800">
      <h2 className="mb-2 text-lg font-semibold">Kjøleskap</h2>
      <p className="mb-3 text-sm text-ink-soft dark:text-slate-400">
        Hjemme, på hytta … Hvert kjøleskap har sin egen vareliste.
      </p>
      {feil && (
        <p role="alert" className="mb-3 rounded-xl bg-red-50 px-3 py-2 text-sm text-red-800 dark:bg-red-950 dark:text-red-200">
          {feil}
        </p>
      )}
      {liste === null ? (
        !feil && <p className="text-ink-soft dark:text-slate-400">Henter …</p>
      ) : (
        <>
          <ul className="mb-4 divide-y divide-slate-200 dark:divide-slate-700">
            {liste.map((k) => (
              <Rad
                key={k.id}
                kjoleskap={k}
                kanSlettes={liste.length > 1}
                onEndret={(ny) => {
                  setFeil(null)
                  setListe(ny)
                }}
              />
            ))}
          </ul>
          <form onSubmit={leggTil} className="flex gap-2">
            <input
              value={nytt}
              onChange={(e) => setNytt(e.target.value)}
              placeholder="Nytt kjøleskap, f.eks. Hytta"
              aria-label="Navn på nytt kjøleskap"
              maxLength={MAKS_NAVN}
              className={feltKlasse}
            />
            <button type="submit" disabled={lagrer || nytt.trim() === ''} className={knappKlasse}>
              {lagrer ? 'Legger til …' : 'Legg til'}
            </button>
          </form>
        </>
      )}
    </section>
  )
}

/** Ett kjøleskap: visning, redigering av navn eller bekreftelse av sletting. */
function Rad({
  kjoleskap,
  kanSlettes,
  onEndret,
}: {
  kjoleskap: Kjoleskap
  kanSlettes: boolean
  onEndret: (liste: Kjoleskap[]) => void
}) {
  const [modus, setModus] = useState<'vis' | 'navn' | 'slett'>('vis')
  const [navn, setNavn] = useState(kjoleskap.navn)
  const [opptatt, setOpptatt] = useState(false)
  const [feil, setFeil] = useState<string | null>(null)

  async function utfor(handling: () => Promise<Kjoleskap[]>) {
    setFeil(null)
    setOpptatt(true)
    try {
      onEndret(await handling())
      setModus('vis')
    } catch (err) {
      setFeil(feilmelding(err))
    } finally {
      setOpptatt(false)
    }
  }

  function lagreNavn(e: FormEvent) {
    e.preventDefault()
    void utfor(() => giKjoleskapNyttNavn(kjoleskap.id, navn))
  }

  function slett() {
    void utfor(async () => {
      await slettKjoleskap(kjoleskap.id)
      return hentKjoleskap()
    })
  }

  function avbryt() {
    setModus('vis')
    setNavn(kjoleskap.navn)
    setFeil(null)
  }

  return (
    <li className="py-3">
      {modus === 'navn' ? (
        <form onSubmit={lagreNavn} className="flex gap-2">
          <input
            value={navn}
            onChange={(e) => setNavn(e.target.value)}
            aria-label={`Nytt navn på ${kjoleskap.navn}`}
            maxLength={MAKS_NAVN}
            autoFocus
            className={feltKlasse}
          />
          <button type="submit" disabled={opptatt || navn.trim() === ''} className={knappKlasse}>Lagre</button>
          <button type="button" onClick={avbryt} className={sekundarKlasse}>Avbryt</button>
        </form>
      ) : (
        <div className="flex items-center gap-2">
          <div className="min-w-0 flex-1">
            <p className="break-words font-medium">{kjoleskap.navn}</p>
            <p className="text-sm text-ink-soft dark:text-slate-400">
              {kjoleskap.sistSkannet ? `Sist skannet ${formaterTidspunkt(kjoleskap.sistSkannet)}` : 'Ikke skannet ennå'}
            </p>
          </div>
          {modus === 'vis' && (
            <>
              <button type="button" onClick={() => setModus('navn')} className="px-2 py-2 text-sm font-medium text-accent">
                Endre navn
              </button>
              {kanSlettes && (
                <button
                  type="button"
                  onClick={() => setModus('slett')}
                  className="px-2 py-2 text-sm font-medium text-red-700 dark:text-red-400"
                >
                  Slett
                </button>
              )}
            </>
          )}
        </div>
      )}
      {modus === 'slett' && (
        <div className="mt-2 flex flex-col gap-2">
          <p className="text-sm">Slette «{kjoleskap.navn}»? Varelisten og bildet for kjøleskapet blir borte.</p>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={slett}
              disabled={opptatt}
              className="flex-1 rounded-xl bg-red-700 px-4 py-2 font-medium text-white disabled:opacity-50"
            >
              {opptatt ? 'Sletter …' : 'Slett'}
            </button>
            <button type="button" onClick={avbryt} className={`flex-1 ${sekundarKlasse}`}>Avbryt</button>
          </div>
        </div>
      )}
      {feil && (
        <p role="alert" className="mt-2 text-sm text-red-700 dark:text-red-300">{feil}</p>
      )}
    </li>
  )
}
