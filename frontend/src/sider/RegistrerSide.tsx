import { useState, type FormEvent } from 'react'
import { Link, Navigate } from 'react-router'
import { useInnlogging } from '../innlogging/Innlogging.tsx'
import { Feilmelding, feilmeldingFra, Felt, InnloggingSkall, PassordFelt, SendKnapp } from '../innlogging/Skjema.tsx'

export function RegistrerSide() {
  const { status, registrer } = useInnlogging()
  const [navn, setNavn] = useState('')
  const [epost, setEpost] = useState('')
  const [passord, setPassord] = useState('')
  const [sender, setSender] = useState(false)
  const [feil, setFeil] = useState<string | null>(null)

  if (status.type === 'innlogget') return <Navigate to="/" replace />

  async function send(e: FormEvent) {
    e.preventDefault()
    setFeil(null)
    setSender(true)
    try {
      await registrer({ navn: navn.trim(), epost: epost.trim(), passord })
    } catch (err) {
      setFeil(feilmeldingFra(err))
      setSender(false)
    }
  }

  return (
    <InnloggingSkall
      tittel="Registrer deg"
      bunn={
        <Link to="/logg-inn" className="text-accent underline underline-offset-4">
          Har du allerede konto? Logg inn
        </Link>
      }
    >
      <form onSubmit={send} className="flex flex-col gap-4">
        <Felt
          etikett="Navn"
          autoComplete="name"
          autoCapitalize="words"
          required
          maxLength={100}
          value={navn}
          onChange={(e) => setNavn(e.target.value)}
        />
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
          etikett="Passord (minst 8 tegn)"
          autoComplete="new-password"
          required
          minLength={8}
          maxLength={72}
          value={passord}
          onChange={(e) => setPassord(e.target.value)}
        />
        {feil && <Feilmelding>{feil}</Feilmelding>}
        <SendKnapp sender={sender}>{sender ? 'Registrerer …' : 'Registrer deg'}</SendKnapp>
      </form>
    </InnloggingSkall>
  )
}
