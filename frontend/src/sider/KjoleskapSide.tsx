import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { hentBeholdning, hentSkanningsbilde, type Beholdning } from '../api/kjoleskap.ts'
import { BildeMedBokser } from '../bilde/BildeMedBokser.tsx'
import { ApiFeil } from '../api/klient.ts'
import { Side } from '../navigasjon/Side.tsx'

/**
 * Det som er i kjøleskapet nå (#32, #33): bildet fra siste lagrede skanning og
 * listen. Trykk på varer for å markere dem i bildet; de blir liggende til man
 * trykker på dem igjen, så flere kan vises samtidig.
 */
export function KjoleskapSide() {
  const [beholdning, setBeholdning] = useState<Beholdning | null>(null)
  const [feil, setFeil] = useState<string | null>(null)
  const [bildeUrl, setBildeUrl] = useState<string | null>(null)
  const [valgte, setValgte] = useState<ReadonlySet<number>>(new Set())
  const skanningId = beholdning?.skanning?.id

  useEffect(() => {
    let avbrutt = false
    hentBeholdning()
      .then((b) => !avbrutt && setBeholdning(b))
      .catch((err) => !avbrutt && setFeil(err instanceof ApiFeil ? err.message : 'Noe gikk galt. Prøv igjen.'))
    return () => {
      avbrutt = true
    }
  }, [])

  // Bildet hentes med token (et vanlig <img src> kan ikke sende det) og vises
  // fra en midlertidig URL som frigjøres igjen. Uten bilde vises bare listen.
  useEffect(() => {
    if (skanningId === undefined) return
    let url: string | null = null
    let avbrutt = false
    hentSkanningsbilde(skanningId)
      .then((blob) => {
        if (avbrutt) return
        url = URL.createObjectURL(blob)
        setBildeUrl(url)
      })
      .catch(() => {})
    return () => {
      avbrutt = true
      if (url) URL.revokeObjectURL(url)
    }
  }, [skanningId])

  const valgteVarer = beholdning?.varer.filter((_, i) => valgte.has(i)) ?? []
  const umerkede = valgteVarer.filter((v) => v.bokser.length === 0)

  function veksle(indeks: number) {
    setValgte((forrige) => {
      const neste = new Set(forrige)
      if (!neste.delete(indeks)) neste.add(indeks)
      return neste
    })
  }

  return (
    <Side>
      <h1 className="text-2xl font-semibold tracking-tight">Kjøleskapet</h1>
      {beholdning?.skanning && (
        <p className="text-ink-soft dark:text-slate-400">Skannet {formaterTidspunkt(beholdning.skanning.tidspunkt)}</p>
      )}

      <div className="mt-6 flex flex-1 flex-col">
        {feil ? (
          <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-red-800 dark:bg-red-950 dark:text-red-200">
            {feil}
          </p>
        ) : !beholdning ? (
          <div className="flex flex-1 items-center justify-center">
            <div className="size-10 animate-spin rounded-full border-4 border-slate-300 border-t-accent" />
          </div>
        ) : beholdning.varer.length === 0 ? (
          <div className="flex flex-1 flex-col items-center justify-center gap-4 rounded-2xl border-2 border-dashed border-slate-300 p-8 text-center text-ink-soft dark:border-slate-700 dark:text-slate-400">
            <p>{beholdning.skanning ? 'Kjøleskapet er tomt.' : 'Du har ikke skannet kjøleskapet ennå.'}</p>
            <Link to="/" className="rounded-xl bg-accent px-5 py-3 font-medium text-white active:bg-accent-hover">
              Skann kjøleskapet
            </Link>
          </div>
        ) : (
          <div className="flex flex-col gap-4">
            {bildeUrl && (
              <figure className="overflow-hidden rounded-2xl bg-frost-deep shadow-sm dark:bg-slate-800">
                <BildeMedBokser
                  src={bildeUrl}
                  alt="Siste bilde av kjøleskapet"
                  valgte={valgteVarer.map((v) => ({ bokser: v.bokser, etikett: v.navn }))}
                />
                <figcaption className="px-4 py-2 text-sm text-ink-soft dark:text-slate-400">
                  {umerkede.length > 0
                    ? `Ikke merket i bildet: ${umerkede.map((v) => v.navn).join(', ')}.`
                    : 'Trykk på varer i listen for å se dem i bildet.'}
                </figcaption>
              </figure>
            )}
            <ul className="divide-y divide-slate-100 rounded-2xl bg-white shadow-sm dark:divide-slate-700 dark:bg-slate-800">
              {beholdning.varer.map((vare, i) => {
                const erValgt = valgte.has(i)
                return (
                  <li key={i}>
                    <button
                      type="button"
                      aria-pressed={erValgt}
                      onClick={() => veksle(i)}
                      className={`flex w-full items-center justify-between gap-3 px-4 py-3 text-left ${
                        erValgt ? 'bg-amber-50 dark:bg-amber-950' : 'active:bg-frost-deep dark:active:bg-slate-700'
                      }`}
                    >
                      <span className="flex min-w-0 items-center gap-2">
                        <span
                          aria-hidden="true"
                          className={`size-2.5 shrink-0 rounded-sm border-2 ${
                            erValgt ? 'border-amber-500 bg-amber-400' : 'border-slate-300 dark:border-slate-600'
                          }`}
                        />
                        <span className="truncate first-letter:uppercase">{vare.navn}</span>
                      </span>
                      <span className="shrink-0 tabular-nums text-ink-soft dark:text-slate-400">{vare.antall} stk</span>
                    </button>
                  </li>
                )
              })}
            </ul>
          </div>
        )}
      </div>
    </Side>
  )
}

/** «i dag kl. 18:42», «i går kl. 09:10» eller «12. november kl. 18:42». */
export function formaterTidspunkt(iso: string, na: Date = new Date()): string {
  const tid = new Date(iso)
  const klokke = tid.toLocaleTimeString('nb-NO', { hour: '2-digit', minute: '2-digit' })
  const dager = Math.round((startAvDag(na) - startAvDag(tid)) / 86_400_000)
  if (dager === 0) return `i dag kl. ${klokke}`
  if (dager === 1) return `i går kl. ${klokke}`
  const dato = tid.toLocaleDateString('nb-NO', {
    day: 'numeric',
    month: 'long',
    ...(tid.getFullYear() === na.getFullYear() ? {} : { year: 'numeric' }),
  })
  return `${dato} kl. ${klokke}`
}

function startAvDag(d: Date): number {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
}
