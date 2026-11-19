import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import './index.css'
import App from './App.tsx'
import { Feilgrense } from './Feilgrense.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Feilgrense>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </Feilgrense>
  </StrictMode>,
)

// Service worker (#47): bare i produksjonsbygget, så utvikling med Vite ikke
// får gamle filer fra en cache.
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch((err) => console.warn('Service worker ble ikke registrert', err))
  })
}
