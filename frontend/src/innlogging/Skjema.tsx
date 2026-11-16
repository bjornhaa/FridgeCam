import type { InputHTMLAttributes, ReactNode } from 'react'

/** Felles ramme for innlogging og registrering. */
export function InnloggingSkall({ tittel, children, bunn }: { tittel: string; children: ReactNode; bunn: ReactNode }) {
  return (
    <div className="mx-auto flex min-h-dvh max-w-sm flex-col justify-center px-4 pt-[max(1.5rem,env(safe-area-inset-top))] pb-[max(1.5rem,env(safe-area-inset-bottom))]">
      <p className="mb-1 text-center text-3xl font-semibold tracking-tight">FridgeCam</p>
      <h1 className="mb-6 text-center text-ink-soft dark:text-slate-400">{tittel}</h1>
      {children}
      <div className="mt-6 text-center">{bunn}</div>
    </div>
  )
}

export function Felt({ etikett, ...input }: { etikett: string } & InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium">{etikett}</span>
      {/* text-base (16 px) så iOS ikke zoomer inn når feltet får fokus. */}
      <input
        {...input}
        className="w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-base outline-none focus:border-accent focus:ring-2 focus:ring-accent/30 dark:border-slate-600 dark:bg-slate-800"
      />
    </label>
  )
}

export function Feilmelding({ children }: { children: ReactNode }) {
  return (
    <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-red-800 dark:bg-red-950 dark:text-red-200">
      {children}
    </p>
  )
}

export function SendKnapp({ sender, children }: { sender: boolean; children: ReactNode }) {
  return (
    <button
      type="submit"
      disabled={sender}
      className="flex w-full items-center justify-center gap-3 rounded-xl bg-accent px-5 py-4 text-lg font-medium text-white active:bg-accent-hover disabled:opacity-70"
    >
      {sender && <span className="size-5 animate-spin rounded-full border-2 border-white/40 border-t-white" />}
      {children}
    </button>
  )
}

export function feilmeldingFra(err: unknown): string {
  return err instanceof Error && 'code' in err ? err.message : 'Noe gikk galt. Prøv igjen.'
}
