import { useEffect, useState } from 'react'
import { hentKjoleskap, type Kjoleskap } from '../api/kjoleskap.ts'
import { ApiFeil } from '../api/klient.ts'
import { gjeldende, hentSistValgt, lagreSistValgt } from './valgtKjoleskap.ts'

/** Husstandens kjøleskap og det valgte (sist brukte på denne enheten). */
export function useKjoleskap() {
  const [liste, setListe] = useState<Kjoleskap[] | null>(null)
  const [valgtId, setValgtId] = useState<number | null>(hentSistValgt)
  const [feil, setFeil] = useState<string | null>(null)

  useEffect(() => {
    let avbrutt = false
    hentKjoleskap()
      .then((l) => !avbrutt && setListe(l))
      .catch((err: unknown) => !avbrutt && setFeil(err instanceof ApiFeil ? err.message : 'Noe gikk galt. Prøv igjen.'))
    return () => {
      avbrutt = true
    }
  }, [])

  function velg(id: number) {
    lagreSistValgt(id)
    setValgtId(id)
  }

  return { liste, valgt: liste ? gjeldende(liste, valgtId) : null, velg, feil }
}
