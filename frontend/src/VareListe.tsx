import type { FunnetVare } from './api/analyse.ts'

/**
 * Varene AI-en fant (#12). Varer den er usikker på, merkes så brukeren vet
 * hva som bør sjekkes. Redigering kommer i fase 2.
 */
export function VareListe({ varer }: { varer: FunnetVare[] }) {
  if (varer.length === 0) {
    return (
      <p className="rounded-xl bg-frost-deep px-4 py-3 text-ink-soft dark:bg-slate-800 dark:text-slate-400">
        Fant ingen varer. Prøv et bilde med mer lys, eller ta det litt nærmere.
      </p>
    )
  }

  const usikre = varer.filter((v) => v.sikkerhet === 'lav').length

  return (
    <section aria-labelledby="varer-tittel" className="rounded-2xl bg-white shadow-sm dark:bg-slate-800">
      <h2 id="varer-tittel" className="px-4 pt-4 text-lg font-semibold">
        Fant {varer.length} {varer.length === 1 ? 'vare' : 'varer'}
      </h2>
      {usikre > 0 && (
        <p className="px-4 text-sm text-ink-soft dark:text-slate-400">
          {usikre === 1 ? 'Én vare' : `${usikre} varer`} er merket som usikker.
        </p>
      )}
      <ul className="mt-2 divide-y divide-slate-100 dark:divide-slate-700">
        {varer.map((vare, i) => (
          <li key={`${vare.navn}-${i}`} className="flex items-center justify-between gap-3 px-4 py-3">
            <span className="flex items-center gap-2">
              <span className="first-letter:uppercase">{vare.navn}</span>
              {vare.sikkerhet === 'lav' && (
                <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-900 dark:bg-amber-900 dark:text-amber-100">
                  usikker
                </span>
              )}
            </span>
            <span className="tabular-nums text-ink-soft dark:text-slate-400">{vare.antall} stk</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
