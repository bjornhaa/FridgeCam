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
  visSistSkannet = false,
}: {
  liste: Kjoleskap[]
  valgt: Kjoleskap
  onVelg: (id: number) => void
  /** F.eks. etter skanning: da hører listen til kjøleskapet som ble valgt. */
  laast?: boolean
  /** Viser når hvert kjøleskap sist ble skannet, under navnet. */
  visSistSkannet?: boolean
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
            className={`flex flex-col items-center border px-4 py-1.5 text-sm font-medium disabled:opacity-40 ${
              visSistSkannet ? 'rounded-xl' : 'rounded-full'
            } ${
              erValgt
                ? 'border-accent bg-accent text-white'
                : 'border-slate-300 bg-white text-ink dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200'
            }`}
          >
            {k.navn}
            {visSistSkannet && (
              <span className={`text-xs font-normal ${erValgt ? 'text-white/85' : 'text-ink-soft dark:text-slate-400'}`}>
                {k.sistSkannet ? kortDato(k.sistSkannet) : 'ikke skannet'}
              </span>
            )}
          </button>
        )
      })}
    </div>
  )
}

/** «i dag», «i går» eller «12. nov.»; kort nok til en knapp. */
export function kortDato(iso: string, na: Date = new Date()): string {
  const tid = new Date(iso)
  const dag = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
  const dager = Math.round((dag(na) - dag(tid)) / 86_400_000)
  if (dager === 0) return 'i dag'
  if (dager === 1) return 'i går'
  return tid.toLocaleDateString('nb-NO', {
    day: 'numeric',
    month: 'short',
    ...(tid.getFullYear() === na.getFullYear() ? {} : { year: 'numeric' }),
  })
}
