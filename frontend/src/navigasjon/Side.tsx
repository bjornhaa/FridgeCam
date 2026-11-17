import type { ReactNode } from 'react'

/**
 * Innholdet på en fane. Fyller høyden over fanelinjen, med luft til
 * statuslinjen/hakket øverst på iPhone.
 */
export function Side({ children }: { children: ReactNode }) {
  return (
    <div className="mx-auto flex w-full max-w-lg flex-1 flex-col px-4 pt-[max(1.5rem,env(safe-area-inset-top))] pb-6">
      {children}
    </div>
  )
}
