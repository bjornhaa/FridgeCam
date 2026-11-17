import { Side } from '../navigasjon/Side.tsx'

/** Beholdningen med siste bilde og bokser kommer i #33. */
export function KjoleskapSide() {
  return (
    <Side>
      <h1 className="mb-6 text-2xl font-semibold tracking-tight">Kjøleskapet</h1>
      <div className="flex flex-1 flex-col items-center justify-center rounded-2xl border-2 border-dashed border-slate-300 p-8 text-center text-ink-soft dark:border-slate-700 dark:text-slate-400">
        <p>Her kommer det som er i kjøleskapet ditt, fra siste skanning.</p>
      </div>
    </Side>
  )
}
