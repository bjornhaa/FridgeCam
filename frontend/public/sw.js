// Service worker for FridgeCam (#47). Gjør appen installerbar og lar appskallet
// åpnes uten nett. Bevisst enkel:
// - API og actuator går alltid rett mot nettet og caches aldri (data og token
//   skal ikke ligge i cachen; offline handleliste kommer i fase 5).
// - Sider: nettet først, cache bare hvis nettet svikter. Da tas en ny versjon i
//   bruk med en gang man er på nett, og ingen blir sittende fast i en gammel cache.
// - /assets/ har hash i filnavnet og endres aldri: cache først. Filer som ikke
//   lenger brukes av siste index.html, ryddes bort.
const CACHE = 'fridgecam-skall-v1'
const SKALL = '/'

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll([SKALL, '/favicon.svg', '/manifest.webmanifest'])))
  // Ny versjon av service workeren tas i bruk uten å vente på at alle faner lukkes.
  self.skipWaiting()
})

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((navn) => Promise.all(navn.filter((n) => n !== CACHE).map((n) => caches.delete(n))))
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (e) => {
  const req = e.request
  if (req.method !== 'GET') return
  const url = new URL(req.url)
  if (url.origin !== self.location.origin) return
  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/actuator/')) return

  if (req.mode === 'navigate') {
    e.respondWith(side(req))
  } else if (url.pathname.startsWith('/assets/')) {
    e.respondWith(cacheForst(req))
  } else {
    e.respondWith(nettForst(req, req))
  }
})

/** Alle sider er samme index.html (SPA), så den lagres under én nøkkel. */
async function side(req) {
  try {
    const svar = await fetch(req)
    if (svar.ok && (svar.headers.get('content-type') ?? '').includes('text/html')) {
      const cache = await caches.open(CACHE)
      await cache.put(SKALL, svar.clone())
      ryddAssets(cache, await svar.clone().text())
    }
    return svar
  } catch {
    return (await caches.match(SKALL)) ?? Response.error()
  }
}

async function cacheForst(req) {
  const lagret = await caches.match(req)
  if (lagret) return lagret
  const svar = await fetch(req)
  if (svar.ok) (await caches.open(CACHE)).put(req, svar.clone())
  return svar
}

async function nettForst(req, nokkel) {
  try {
    const svar = await fetch(req)
    if (svar.ok) (await caches.open(CACHE)).put(nokkel, svar.clone())
    return svar
  } catch {
    return (await caches.match(nokkel)) ?? Response.error()
  }
}

/** Sletter cachede /assets/-filer som den nye index.html ikke viser til. */
async function ryddAssets(cache, html) {
  const iBruk = new Set(html.match(/\/assets\/[^"'\s)]+/g) ?? [])
  for (const req of await cache.keys()) {
    const sti = new URL(req.url).pathname
    if (sti.startsWith('/assets/') && !iBruk.has(sti)) await cache.delete(req)
  }
}
