import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation } from 'react-router'
import { useInnlogging } from '../innlogging/Innlogging.tsx'
import { Feilmelding, feilmeldingFra, Felt, InnloggingSkall, PassordFelt, SendKnapp } from '../innlogging/Skjema.tsx'

export function LoggInnSide() {
  const { status, loggInn } = useInnlogging()
  const location = useLocation()
  const [epost, setEpost] = useState('')
  const [passord, setPassord] = useState('')
  const [sender, setSender] = useState(false)
  const [feil, setFeil] = useState<string | null>(null)

  if (status.type === 'innlogget') {
    const fra = (location.state as { fra?: string } | null)?.fra
    return <Navigate to={fra ?? '/'} replace />
  }

  async function send(e: FormEvent) {
    e.preventDefault()
    setFeil(null)
    setSender(true)
    try {
      await loggInn(epost.trim(), passord)
    } catch (err) {
      setFeil(feilmeldingFra(err))
      setSender(false)
    }
  }

  return (
    <InnloggingSkall
      tittel="Logg inn"
      bunn={
        <Link to="/registrer" className="text-accent underline underline-offset-4">
          Ny bruker? Registrer deg
        </Link>
      }
    >
      <form onSubmit={send} className="flex flex-col gap-4">
        {status.type === 'utlogget' && status.avServeren && !feil && (
          <p className="rounded-xl bg-amber-50 px-4 py-3 text-amber-900 dark:bg-amber-950 dark:text-amber-200">
            Du er logget ut. Logg inn igjen.
          </p>
        )}
        <Felt
          etikett="E-post"
          type="email"
          autoComplete="email"
          inputMode="email"
          autoCapitalize="none"
          required
          value={epost}
          onChange={(e) => setEpost(e.target.value)}
        />
        <PassordFelt
          etikett="Passord"
          autoComplete="current-password"
          required
          value={passord}
          onChange={(e) => setPassord(e.target.value)}
        />
        {feil && <Feilmelding>{feil}</Feilmelding>}
        <SendKnapp sender={sender}>{sender ? 'Logger inn …' : 'Logg inn'}</SendKnapp>
      </form>
    </InnloggingSkall>
  )
}
