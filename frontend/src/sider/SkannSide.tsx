import { useEffect, useRef, useState, type ChangeEvent } from 'react'
import { krympBilde, type KrympetBilde } from '../bilde/krympBilde.ts'
import { analyserBilde, type AnalyseSvar } from '../api/analyse.ts'
import { ApiFeil } from '../api/klient.ts'
import { VareListe } from '../VareListe.tsx'
import { boksStil } from '../bilde/boks.ts'
import { Side } from '../navigasjon/Side.tsx'

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
export function SkannSide() {
  const [bilde, setBilde] = useState<KrympetBilde | null>(null)
  const [visningsUrl, setVisningsUrl] = useState<string | null>(null)
  const [behandler, setBehandler] = useState(false)
  const [feil, setFeil] = useState<string | null>(null)
  const [analyserer, setAnalyserer] = useState(false)
  const [analyse, setAnalyse] = useState<AnalyseSvar | null>(null)
  const [valgtIndeks, setValgtIndeks] = useState<number | null>(null)
  const figur = useRef<HTMLElement>(null)
  const valgtVare = valgtIndeks !== null ? analyse?.varer[valgtIndeks] ?? null : null
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
    setAnalyse(null)
    setValgtIndeks(null)
    setBehandler(true)
    try {
      setBilde(await krympBilde(fil))
    } catch (err) {
      setFeil(err instanceof Error ? err.message : 'Noe gikk galt med bildet.')
    } finally {
      setBehandler(false)
    }
  }

  function velgVare(indeks: number | null) {
    setValgtIndeks(indeks)
    // Bildet ligger over listen; scroll det i syne så boksen synes.
    if (indeks !== null) figur.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  async function finnVarer() {
    if (!bilde) return
    setFeil(null)
    setAnalyserer(true)
    try {
      setAnalyse(await analyserBilde(bilde.fil))
    } catch (err) {
      setFeil(err instanceof ApiFeil ? err.message : 'Noe gikk galt. Prøv igjen.')
    } finally {
      setAnalyserer(false)
    }
  }

  return (
    <Side>
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
            <figure
              ref={figur}
              className="scroll-mt-4 overflow-hidden rounded-2xl bg-frost-deep shadow-sm dark:bg-slate-800"
            >
              {/* Beholderen er nøyaktig like stor som bildet, så boksene (i prosent) treffer. */}
              <div className="relative mx-auto w-fit">
                <img
                  src={visningsUrl}
                  alt="Bilde av kjøleskapet"
                  className="block max-h-[60dvh] max-w-full"
                />
                {valgtVare?.bokser.map((boks, i) => (
                  <div
                    key={i}
                    aria-hidden="true"
                    className="pointer-events-none absolute rounded-md border-[3px] border-amber-400 shadow-[0_0_0_2px_rgba(0,0,0,0.55)]"
                    style={boksStil(boks)}
                  >
                    {i === 0 && (
                      // Ligger boksen helt øverst, legges lappen inni boksen så den ikke kuttes.
                      <span
                        className={`absolute left-0 rounded bg-amber-400 px-1.5 py-0.5 text-xs font-semibold whitespace-nowrap text-black first-letter:uppercase ${
                          boks.y1 < 60 ? 'top-0' : '-top-0.5 -translate-y-full'
                        }`}
                      >
                        {valgtVare.navn}
                      </span>
                    )}
                  </div>
                ))}
              </div>
              <figcaption className="px-4 py-2 text-sm text-ink-soft dark:text-slate-400">
                {valgtVare && valgtVare.bokser.length === 0
                  ? `AI-en oppga ikke hvor «${valgtVare.navn}» er i bildet.`
                  : `${formaterStorrelse(bilde.originalStorrelse)} → ${formaterStorrelse(bilde.fil.size)} · ${bilde.bredde} × ${bilde.hoyde} px`}
              </figcaption>
            </figure>

            {analyse ? (
              <VareListe varer={analyse.varer} valgt={valgtIndeks} onVelg={velgVare} />
            ) : (
              <button
                type="button"
                onClick={finnVarer}
                disabled={analyserer}
                className="flex items-center justify-center gap-3 rounded-xl bg-accent px-5 py-4 text-lg font-medium text-white active:bg-accent-hover disabled:opacity-70"
              >
                {analyserer && (
                  <span className="size-5 animate-spin rounded-full border-2 border-white/40 border-t-white" />
                )}
                {analyserer ? 'Ser etter varer …' : 'Finn varer'}
              </button>
            )}

            <button
              type="button"
              onClick={() => kameraInput.current?.click()}
              disabled={analyserer}
              className="rounded-xl border border-accent disabled:opacity-50 px-5 py-3 font-medium text-accent active:bg-frost-deep dark:active:bg-slate-800"
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
    </Side>
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
