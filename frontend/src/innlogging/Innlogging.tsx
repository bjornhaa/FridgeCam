import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { hentMeg, loggInn as loggInnApi, registrer as registrerApi, type Bruker, type Registrering } from '../api/innlogging.ts'
import { ApiFeil, vedUtlogging } from '../api/klient.ts'
import { hentToken, lagreToken, slettToken } from '../api/token.ts'

export type Innloggingsstatus =
  | { type: 'laster' }
  | { type: 'utlogget'; avServeren: boolean }
  | { type: 'innlogget'; bruker: Bruker }
  // Har token, men fikk ikke sjekket det (typisk ikke nett). Tokenet beholdes.
  | { type: 'feil'; melding: string }

type Innlogging = {
  status: Innloggingsstatus
  loggInn: (epost: string, passord: string) => Promise<void>
  registrer: (registrering: Registrering) => Promise<void>
  loggUt: () => void
  provIgjen: () => void
}

const InnloggingKontekst = createContext<Innlogging | null>(null)

export function InnloggingProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Innloggingsstatus>(() =>
    hentToken() ? { type: 'laster' } : { type: 'utlogget', avServeren: false },
  )

  const sjekkToken = useCallback(async () => {
    if (!hentToken()) {
      setStatus({ type: 'utlogget', avServeren: false })
      return
    }
    setStatus({ type: 'laster' })
    try {
      setStatus({ type: 'innlogget', bruker: await hentMeg() })
    } catch (err) {
      // Ved 401 har API-klienten allerede slettet tokenet og varslet (under).
      if (err instanceof ApiFeil && err.code === 'ikke_innlogget') return
      setStatus({ type: 'feil', melding: err instanceof ApiFeil ? err.message : 'Noe gikk galt. Prøv igjen.' })
    }
  }, [])

  useEffect(() => {
    void sjekkToken()
  }, [sjekkToken])

  // Serveren avviste tokenet midt i bruk (utløpt): tilbake til innlogging.
  useEffect(() => vedUtlogging(() => setStatus({ type: 'utlogget', avServeren: true })), [])

  const loggInn = useCallback(async (epost: string, passord: string) => {
    const svar = await loggInnApi(epost, passord)
    lagreToken(svar.token)
    setStatus({ type: 'innlogget', bruker: svar.bruker })
  }, [])

  const registrer = useCallback(async (registrering: Registrering) => {
    const svar = await registrerApi(registrering)
    lagreToken(svar.token)
    setStatus({ type: 'innlogget', bruker: svar.bruker })
  }, [])

  const loggUt = useCallback(() => {
    slettToken()
    setStatus({ type: 'utlogget', avServeren: false })
  }, [])

  const verdi = useMemo(
    () => ({ status, loggInn, registrer, loggUt, provIgjen: () => void sjekkToken() }),
    [status, loggInn, registrer, loggUt, sjekkToken],
  )
  return <InnloggingKontekst.Provider value={verdi}>{children}</InnloggingKontekst.Provider>
}

export function useInnlogging(): Innlogging {
  const kontekst = useContext(InnloggingKontekst)
  if (!kontekst) throw new Error('useInnlogging må brukes inni <InnloggingProvider>')
  return kontekst
}

/** Den innloggede brukeren. Bare for sider bak <KreverInnlogging>. */
export function useBruker(): Bruker {
  const { status } = useInnlogging()
  if (status.type !== 'innlogget') throw new Error('useBruker krever innlogget bruker')
  return status.bruker
}
