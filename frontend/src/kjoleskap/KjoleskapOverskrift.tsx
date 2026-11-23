import type { Kjoleskap } from '../api/kjoleskap.ts'

const overskrift = 'text-2xl font-semibold tracking-tight'

/**
 * Overskriften i Kjøleskapet-fanen: navnet på valgt kjøleskap. Med flere enn ett
 * er den en nedtrekksliste (vanlig `<select>`, så mobilen viser sin egen velger).
 */
export function KjoleskapOverskrift({
  liste,
  valgt,
  onVelg,
}: {
  liste: Kjoleskap[] | null
  valgt: Kjoleskap | null
  onVelg: (id: number) => void
}) {
  if (!liste || !valgt || liste.length < 2) {
    return <h1 className={overskrift}>{valgt?.navn ?? 'Kjøleskapet'}</h1>
  }
  return (
    <h1 className={overskrift}>
      <label className="relative inline-flex max-w-full items-center">
        <span className="sr-only">Kjøleskap</span>
        <select
          value={valgt.id}
          onChange={(e) => onVelg(Number(e.target.value))}
          className="max-w-full appearance-none truncate bg-transparent py-0.5 pr-8 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent rounded-md"
        >
          {liste.map((k) => (
            <option key={k.id} value={k.id}>
              {k.navn}
            </option>
          ))}
        </select>
        <svg
          aria-hidden
          viewBox="0 0 20 20"
          fill="currentColor"
          className="pointer-events-none absolute right-1 size-6 text-ink-soft dark:text-slate-400"
        >
          <path
            fillRule="evenodd"
            d="M5.23 7.21a.75.75 0 0 1 1.06.02L10 11.17l3.71-3.94a.75.75 0 1 1 1.08 1.04l-4.25 4.5a.75.75 0 0 1-1.08 0l-4.25-4.5a.75.75 0 0 1 .02-1.06Z"
            clipRule="evenodd"
          />
        </svg>
      </label>
    </h1>
  )
}
