import { useBruker, useInnlogging } from '../innlogging/Innlogging.tsx'
import { Side } from '../navigasjon/Side.tsx'
import { AiKort } from '../ainokkel/AiKort.tsx'

export function ProfilSide() {
  const bruker = useBruker()
  const { loggUt } = useInnlogging()

  return (
    <Side>
      <h1 className="mb-6 text-2xl font-semibold tracking-tight">Profil</h1>
      <dl className="divide-y divide-slate-200 rounded-2xl bg-white shadow-sm dark:divide-slate-700 dark:bg-slate-800">
        <Rad etikett="Navn">{bruker.navn}</Rad>
        <Rad etikett="E-post">{bruker.epost}</Rad>
        {bruker.husstander.map((h) => (
          <Rad key={h.id} etikett="Husstand">
            {h.navn} <span className="text-ink-soft dark:text-slate-400">({h.rolle === 'EIER' ? 'eier' : 'medlem'})</span>
          </Rad>
        ))}
      </dl>
      <AiKort />
      <button
        type="button"
        onClick={loggUt}
        className="mt-8 rounded-xl border border-accent px-5 py-3 font-medium text-accent active:bg-frost-deep dark:active:bg-slate-800"
      >
        Logg ut
      </button>
    </Side>
  )
}

function Rad({ etikett, children }: { etikett: string; children: React.ReactNode }) {
  return (
    <div className="px-4 py-3">
      <dt className="text-sm text-ink-soft dark:text-slate-400">{etikett}</dt>
      <dd className="break-words">{children}</dd>
    </div>
  )
}
