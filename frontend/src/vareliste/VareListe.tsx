import { bekreft, endre, erUsikker, fjern, leggTil, MAKS_ANTALL, type RedigerbarVare } from './redigering.ts'

type Props = {
  varer: RedigerbarVare[]
  /** Id for varen som er åpen (vises med boks i bildet), eller null. */
  valgt: string | null
  onVelg: (id: string | null) => void
  onEndre: (varer: RedigerbarVare[]) => void
  /** Ruller bildet i syne, så boksen for den valgte varen synes. */
  onVisIBildet: () => void
}

/**
 * Varene AI-en fant, som brukeren retter før de lagres (#31). Trykk på en vare
 * for å se den i bildet (#20) og rette navn og antall, eller fjerne den.
 * Usikre varer ligger øverst og er merket til brukeren har sjekket dem.
 */
export function VareListe({ varer, valgt, onVelg, onEndre, onVisIBildet }: Props) {
  const usikre = varer.filter(erUsikker).length

  function nyVare() {
    const { varer: nye, id } = leggTil(varer)
    onEndre(nye)
    onVelg(id)
  }

  return (
    <section aria-labelledby="varer-tittel" className="rounded-2xl bg-white shadow-sm dark:bg-slate-800">
      <h2 id="varer-tittel" className="px-4 pt-4 text-lg font-semibold">
        {varer.length === 0 ? 'Ingen varer' : `${varer.length} ${varer.length === 1 ? 'vare' : 'varer'}`}
      </h2>
      <p className="px-4 text-sm text-ink-soft dark:text-slate-400">
        {varer.length === 0
          ? 'AI-en fant ingen varer. Legg dem til selv, eller prøv et bilde med mer lys.'
          : 'Trykk på en vare for å rette den.'}
        {usikre > 0 && ` ${usikre === 1 ? 'Én vare' : `${usikre} varer`} er usikre; sjekk dem først.`}
      </p>
      <ul className="mt-2 divide-y divide-slate-100 dark:divide-slate-700">
        {varer.map((vare) => (
          <Rad
            key={vare.id}
            vare={vare}
            apen={valgt === vare.id}
            onVelg={() => onVelg(valgt === vare.id ? null : vare.id)}
            onEndre={(endring) => onEndre(endre(varer, vare.id, endring))}
            onBekreft={() => onEndre(bekreft(varer, vare.id))}
            onFjern={() => {
              onEndre(fjern(varer, vare.id))
              onVelg(null)
            }}
            onVisIBildet={onVisIBildet}
          />
        ))}
      </ul>
      <div className="border-t border-slate-100 p-2 dark:border-slate-700">
        <button
          type="button"
          onClick={nyVare}
          className="w-full rounded-xl px-4 py-3 text-left font-medium text-accent active:bg-frost-deep dark:active:bg-slate-700"
        >
          + Legg til vare
        </button>
      </div>
    </section>
  )
}

type RadProps = {
  vare: RedigerbarVare
  apen: boolean
  onVelg: () => void
  onEndre: (endring: { navn?: string; antall?: number }) => void
  onBekreft: () => void
  onFjern: () => void
  onVisIBildet: () => void
}

function Rad({ vare, apen, onVelg, onEndre, onBekreft, onFjern, onVisIBildet }: RadProps) {
  const usikker = erUsikker(vare)
  const tomtNavn = vare.navn.trim() === ''

  return (
    <li className={apen ? 'bg-amber-50 dark:bg-amber-950' : undefined}>
      <button
        type="button"
        aria-expanded={apen}
        onClick={onVelg}
        className={`flex w-full items-center justify-between gap-3 px-4 py-3 text-left ${
          apen ? '' : 'active:bg-frost-deep dark:active:bg-slate-700'
        }`}
      >
        <span className="flex min-w-0 items-center gap-2">
          <span
            aria-hidden="true"
            className={`size-2.5 shrink-0 rounded-sm border-2 ${
              apen ? 'border-amber-500 bg-amber-400' : 'border-slate-300 dark:border-slate-600'
            }`}
          />
          <span className={`truncate first-letter:uppercase ${tomtNavn ? 'italic text-ink-soft dark:text-slate-400' : ''}`}>
            {tomtNavn ? 'Ny vare' : vare.navn}
          </span>
          {usikker && (
            <span className="shrink-0 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-900 dark:bg-amber-900 dark:text-amber-100">
              usikker
            </span>
          )}
        </span>
        <span className="shrink-0 tabular-nums text-ink-soft dark:text-slate-400">{vare.antall} stk</span>
      </button>

      {apen && (
        <div className="flex flex-col gap-3 px-4 pb-4">
          <label className="block">
            <span className="sr-only">Navn</span>
            <input
              value={vare.navn}
              onChange={(e) => onEndre({ navn: e.target.value })}
              placeholder="Hva er det?"
              // Ny, tom vare: rett inn i skrivingen.
              autoFocus={tomtNavn}
              enterKeyHint="done"
              onKeyDown={(e) => e.key === 'Enter' && e.currentTarget.blur()}
              className="w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-base outline-none focus:border-accent focus:ring-2 focus:ring-accent/30 dark:border-slate-600 dark:bg-slate-800"
            />
          </label>

          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-1" role="group" aria-label="Antall">
              <Stegknapp etikett="Én færre" disabled={vare.antall <= 1} onClick={() => onEndre({ antall: vare.antall - 1 })}>
                −
              </Stegknapp>
              <span className="w-12 text-center text-lg tabular-nums" aria-live="polite">
                {vare.antall}
              </span>
              <Stegknapp etikett="Én til" disabled={vare.antall >= MAKS_ANTALL} onClick={() => onEndre({ antall: vare.antall + 1 })}>
                +
              </Stegknapp>
            </div>
            <button
              type="button"
              onClick={onFjern}
              className="rounded-xl px-4 py-3 font-medium text-red-700 active:bg-red-50 dark:text-red-300 dark:active:bg-red-950"
            >
              Fjern
            </button>
          </div>

          {(usikker || vare.bokser.length > 0) && (
            <div className="flex gap-2">
              {usikker && (
                <button
                  type="button"
                  onClick={onBekreft}
                  className="flex-1 rounded-xl bg-accent px-4 py-3 font-medium text-white active:bg-accent-hover"
                >
                  Stemmer
                </button>
              )}
              {vare.bokser.length > 0 && (
                <button
                  type="button"
                  onClick={onVisIBildet}
                  className="flex-1 rounded-xl border border-accent px-4 py-3 font-medium text-accent active:bg-frost-deep dark:active:bg-slate-700"
                >
                  Vis i bildet
                </button>
              )}
            </div>
          )}
        </div>
      )}
    </li>
  )
}

function Stegknapp({ etikett, disabled, onClick, children }: { etikett: string; disabled: boolean; onClick: () => void; children: string }) {
  return (
    <button
      type="button"
      aria-label={etikett}
      disabled={disabled}
      onClick={onClick}
      className="flex size-11 items-center justify-center rounded-full border border-slate-300 text-xl font-medium active:bg-frost-deep disabled:opacity-40 dark:border-slate-600 dark:active:bg-slate-700"
    >
      {children}
    </button>
  )
}
