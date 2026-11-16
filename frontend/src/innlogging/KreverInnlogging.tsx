import { Navigate, Outlet, useLocation } from 'react-router'
import { useInnlogging } from './Innlogging.tsx'

/** Viser sidene under seg bare for innloggede; andre sendes til innlogging. */
export function KreverInnlogging() {
  const { status, provIgjen } = useInnlogging()
  const location = useLocation()

  switch (status.type) {
    case 'innlogget':
      return <Outlet />
    case 'utlogget':
      // Husk hvor brukeren var, så innloggingen kan sende dit igjen.
      return <Navigate to="/logg-inn" replace state={{ fra: location.pathname }} />
    case 'laster':
      return (
        <div className="flex min-h-dvh items-center justify-center">
          <div className="size-10 animate-spin rounded-full border-4 border-slate-300 border-t-accent" />
        </div>
      )
    case 'feil':
      return (
        <div className="mx-auto flex min-h-dvh max-w-lg flex-col items-center justify-center gap-4 px-4 text-center">
          <p role="alert">{status.melding}</p>
          <button
            type="button"
            onClick={provIgjen}
            className="rounded-xl bg-accent px-5 py-3 font-medium text-white active:bg-accent-hover"
          >
            Prøv igjen
          </button>
        </div>
      )
  }
}
