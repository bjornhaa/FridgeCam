import type { Kjoleskap } from '../api/kjoleskap.ts'

/**
 * Knapper for å velge kjøleskap. Vises bare når husstanden har flere enn ett,
 * så appen ser ut som før for den som bare har ett.
 */
export function KjoleskapVelger({
  liste,
  valgt,
  onVelg,
  laast = false,
}: {
  liste: Kjoleskap[]
  valgt: Kjoleskap
  onVelg: (id: number) => void
  /** F.eks. etter skanning: da hører listen til kjøleskapet som ble valgt. */
  laast?: boolean
}) {
  if (liste.length < 2) return null
  return (
    <div role="radiogroup" aria-label="Kjøleskap" className="flex flex-wrap gap-2">
      {liste.map((k) => {
        const erValgt = k.id === valgt.id
        return (
          <button
            key={k.id}
            type="button"
            role="radio"
            aria-checked={erValgt}
            disabled={laast && !erValgt}
            onClick={() => onVelg(k.id)}
            className={`rounded-full border px-4 py-1.5 text-sm font-medium disabled:opacity-40 ${
              erValgt
                ? 'border-accent bg-accent text-white'
                : 'border-slate-300 bg-white text-ink dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200'
            }`}
          >
            {k.navn}
          </button>
        )
      })}
    </div>
  )
}
