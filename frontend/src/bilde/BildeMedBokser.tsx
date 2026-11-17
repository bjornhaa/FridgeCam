import type { Boks } from '../api/analyse.ts'
import { boksStil } from './boks.ts'

type Props = {
  src: string
  alt: string
  /** De valgte varene: boksene markeres, med navnelapp på den første boksen til hver vare. */
  valgte: { bokser: Boks[]; etikett: string }[]
}

/**
 * Bildet med bokser for varer AI-en fant. Beholderen er nøyaktig like stor som
 * bildet, så boksene (i prosent av bildet) treffer uansett visningsstørrelse.
 */
export function BildeMedBokser({ src, alt, valgte }: Props) {
  return (
    <div className="relative mx-auto w-fit">
      <img src={src} alt={alt} className="block max-h-[60dvh] max-w-full" />
      {valgte.flatMap((vare, v) => vare.bokser.map((boks, i) => (
        <div
          key={`${v}-${i}`}
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
              {vare.etikett}
            </span>
          )}
        </div>
      )))}
    </div>
  )
}
