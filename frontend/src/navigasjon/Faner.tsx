import { NavLink, Outlet } from 'react-router'

/**
 * Appens ramme for innloggede: siden øverst og faner nederst, der tommelen når.
 * Fanelinjen ligger fast i bunnen og får ekstra luft over hjem-indikatoren på
 * iPhone (safe-area). Handleliste kommer som egen fane i fase 3.
 */
export function Faner() {
  return (
    <div className="flex min-h-dvh flex-col">
      {/* Plass til fanelinjen, så innholdet nederst ikke havner bak den. */}
      <div className="flex flex-1 flex-col pb-[calc(4rem+env(safe-area-inset-bottom))]">
        <Outlet />
      </div>
      <nav
        aria-label="Hovedmeny"
        className="fixed inset-x-0 bottom-0 z-10 border-t border-slate-200 bg-white/95 pb-[env(safe-area-inset-bottom)] backdrop-blur dark:border-slate-700 dark:bg-slate-900/95"
      >
        <ul className="mx-auto flex max-w-lg">
          <Fane til="/" ikon={<KameraIkon />}>Skann</Fane>
          <Fane til="/kjoleskap" ikon={<KjoleskapIkon />}>Kjøleskapet</Fane>
          <Fane til="/profil" ikon={<ProfilIkon />}>Profil</Fane>
        </ul>
      </nav>
    </div>
  )
}

function Fane({ til, ikon, children }: { til: string; ikon: React.ReactNode; children: string }) {
  return (
    <li className="flex-1">
      <NavLink
        to={til}
        end
        className={({ isActive }) =>
          `flex h-16 flex-col items-center justify-center gap-0.5 text-xs font-medium ${
            isActive ? 'text-accent' : 'text-ink-soft dark:text-slate-400'
          }`
        }
      >
        {ikon}
        {children}
      </NavLink>
    </li>
  )
}

const ikonProps = {
  viewBox: '0 0 24 24',
  className: 'size-6',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.75,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  'aria-hidden': true,
}

function KameraIkon() {
  return (
    <svg {...ikonProps}>
      <path d="M3 8.5A2.5 2.5 0 0 1 5.5 6h1.9l1.2-1.8A1.5 1.5 0 0 1 9.85 3.5h4.3a1.5 1.5 0 0 1 1.25.7L16.6 6h1.9A2.5 2.5 0 0 1 21 8.5v9a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 17.5z" />
      <circle cx="12" cy="13" r="3.75" />
    </svg>
  )
}

function KjoleskapIkon() {
  return (
    <svg {...ikonProps}>
      <rect x="5" y="2.5" width="14" height="19" rx="2" />
      <path d="M5 9.5h14M8.5 5.5v2M8.5 12.5v3" />
    </svg>
  )
}

function ProfilIkon() {
  return (
    <svg {...ikonProps}>
      <circle cx="12" cy="8" r="3.75" />
      <path d="M4.5 20.5a7.5 7.5 0 0 1 15 0" />
    </svg>
  )
}
