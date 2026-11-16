import { Component, type ErrorInfo, type ReactNode } from 'react'

type State = { feil: Error | null }

/**
 * Fanger feil under tegning, så en feil viser en melding med mulighet for å
 * laste på nytt i stedet for en helt hvit side. React krever en klassekomponent her.
 */
export class Feilgrense extends Component<{ children: ReactNode }, State> {
  state: State = { feil: null }

  static getDerivedStateFromError(feil: Error): State {
    return { feil }
  }

  componentDidCatch(feil: Error, info: ErrorInfo) {
    console.error('Appen krasjet', feil, info.componentStack)
  }

  render() {
    if (!this.state.feil) return this.props.children
    return (
      <div className="mx-auto flex min-h-dvh max-w-lg flex-col items-center justify-center gap-4 px-4 text-center">
        <h1 className="text-xl font-semibold">Noe gikk galt</h1>
        <p className="text-ink-soft dark:text-slate-400">Appen fikk en uventet feil. Last siden på nytt og prøv igjen.</p>
        <button
          type="button"
          onClick={() => window.location.reload()}
          className="rounded-xl bg-accent px-5 py-3 font-medium text-white active:bg-accent-hover"
        >
          Last på nytt
        </button>
      </div>
    )
  }
}
