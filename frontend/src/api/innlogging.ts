/** Klient for innlogging og registrering (#26). */
import { apiKall, json } from './klient.ts'

export type Husstand = {
  id: number
  navn: string
  rolle: 'EIER' | 'MEDLEM'
}

export type Bruker = {
  id: number
  epost: string
  navn: string
  husstander: Husstand[]
}

export type InnloggingSvar = {
  token: string
  bruker: Bruker
}

export type Registrering = {
  navn: string
  epost: string
  passord: string
}

const MELDINGER: Record<string, string> = {
  ugyldig_innlogging: 'Feil e-post eller passord.',
  epost_i_bruk: 'Det finnes allerede en konto med denne e-postadressen. Prøv å logge inn i stedet.',
}

export function loggInn(epost: string, passord: string): Promise<InnloggingSvar> {
  return apiKall('/api/auth/logg-inn', json({ epost, passord }), MELDINGER)
}

export function registrer(registrering: Registrering): Promise<InnloggingSvar> {
  return apiKall('/api/auth/registrer', json(registrering), MELDINGER)
}

export function hentMeg(): Promise<Bruker> {
  return apiKall('/api/meg')
}
