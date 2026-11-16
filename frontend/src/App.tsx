import { useEffect, useRef, useState, type ChangeEvent } from 'react'
import { krympBilde, type KrympetBilde } from './bilde/krympBilde.ts'

/**
 * Kameraflyten: ta bilde, krymp det (#6), se forhåndsvisning, ta nytt.
 *
 * Kameraet åpnes med en vanlig filvelger med `capture="environment"`. Det er
 * det mest robuste på tvers av iOS og Android, og gir telefonens eget
 * kamera-grensesnitt (fokus, blits, zoom) uten at vi må bygge det selv.
 *
 * Forhåndsvisningen viser det krympede bildet, altså det AI-en vil få se.
 * Da synes det med en gang hvis orienteringen har blitt feil.
 */
export default function App() {
  const [bilde, setBilde] = useState<KrympetBilde | null>(null)
  const [visningsUrl, setVisningsUrl] = useState<string | null>(null)
  const [behandler, setBehandler] = useState(false)
  const [feil, setFeil] = useState<string | null>(null)
  const kameraInput = useRef<HTMLInputElement>(null)
  const galleriInput = useRef<HTMLInputElement>(null)

  // Lag en midlertidig URL for forhåndsvisning, og frigjør den når bildet
  // byttes ut. Ellers blir hvert bilde liggende i minnet.
  useEffect(() => {
    if (!bilde) {
      setVisningsUrl(null)
      return
    }
    const url = URL.createObjectURL(bilde.fil)
    setVisningsUrl(url)
    return () => URL.revokeObjectURL(url)
  }, [bilde])

  async function velgBilde(e: ChangeEvent<HTMLInputElement>) {
    const fil = e.target.files?.[0]
    // Nullstill så samme fil kan velges på nytt.
    e.target.value = ''
    if (!fil) return

    setFeil(null)
    setBehandler(true)
    try {
      setBilde(await krympBilde(fil))
    } catch (err) {
      setFeil(err instanceof Error ? err.message : 'Noe gikk galt med bildet.')
    } finally {
      setBehandler(false)
    }
  }

  return (
    <div className="mx-auto flex min-h-dvh max-w-lg flex-col px-4 pt-[max(1.5rem,env(safe-area-inset-top))] pb-[max(1.5rem,env(safe-area-inset-bottom))]">
      <header className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight">FridgeCam</h1>
        <p className="text-ink-soft dark:text-slate-400">
          Ta bilde av kjøleskapet, så finner vi ut hva som er der.
        </p>
      </header>

      <input
        ref={kameraInput}
        type="file"
        accept="image/*"
        capture="environment"
        className="hidden"
        onChange={velgBilde}
      />
      <input
        ref={galleriInput}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={velgBilde}
      />

      <main className="flex flex-1 flex-col gap-4">
        {feil && (
          <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-red-800 dark:bg-red-950 dark:text-red-200">
            {feil}
          </p>
        )}
        {behandler ? (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 text-ink-soft dark:text-slate-400">
            <div className="size-10 animate-spin rounded-full border-4 border-slate-300 border-t-accent" />
            <p>Gjør klar bildet …</p>
          </div>
        ) : visningsUrl && bilde ? (
          <>
            <figure className="overflow-hidden rounded-2xl bg-frost-deep shadow-sm dark:bg-slate-800">
              <img
                src={visningsUrl}
                alt="Bilde av kjøleskapet"
                className="max-h-[60dvh] w-full object-contain"
              />
              <figcaption className="px-4 py-2 text-sm text-ink-soft dark:text-slate-400">
                {formaterStorrelse(bilde.originalStorrelse)} → {formaterStorrelse(bilde.fil.size)} ·{' '}
                {bilde.bredde} × {bilde.hoyde} px
              </figcaption>
            </figure>

            <button
              type="button"
              disabled
              className="rounded-xl bg-accent px-5 py-4 text-lg font-medium text-white disabled:opacity-50"
            >
              Finn varer
            </button>
            <p className="-mt-2 text-center text-sm text-ink-soft dark:text-slate-400">
              Analyse med AI kommer i neste steg.
            </p>

            <button
              type="button"
              onClick={() => kameraInput.current?.click()}
              className="rounded-xl border border-accent px-5 py-3 font-medium text-accent active:bg-frost-deep dark:active:bg-slate-800"
            >
              Ta nytt bilde
            </button>
          </>
        ) : (
          <div className="flex flex-1 flex-col items-center justify-center gap-4 rounded-2xl border-2 border-dashed border-slate-300 p-8 text-center dark:border-slate-700">
            <KameraIkon />
            <button
              type="button"
              onClick={() => kameraInput.current?.click()}
              className="w-full rounded-xl bg-accent px-5 py-4 text-lg font-medium text-white active:bg-accent-hover"
            >
              Ta bilde
            </button>
            <button
              type="button"
              onClick={() => galleriInput.current?.click()}
              className="text-accent underline underline-offset-4"
            >
              eller velg et bilde du har tatt
            </button>
          </div>
        )}
      </main>
    </div>
  )
}

function formaterStorrelse(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} kB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function KameraIkon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="size-16 text-accent"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      aria-hidden="true"
    >
      <path d="M3 8.5A2.5 2.5 0 0 1 5.5 6h1.9l1.2-1.8A1.5 1.5 0 0 1 9.85 3.5h4.3a1.5 1.5 0 0 1 1.25.7L16.6 6h1.9A2.5 2.5 0 0 1 21 8.5v9a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 17.5z" />
      <circle cx="12" cy="13" r="3.75" />
    </svg>
  )
}
