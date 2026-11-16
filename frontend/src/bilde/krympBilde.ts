/**
 * Krymper og komprimerer et bilde i nettleseren før opplasting (#6).
 *
 * Et mobilbilde er typisk 12–48 megapiksler og 3–5 MB. AI-modellene skalerer
 * uansett ned til rundt 1500 px på lengste side, så alt over det er bortkastet
 * opplastingstid og tokens.
 */

/** Lengste side etter krymping. Nær det AI-modellene selv skalerer ned til. */
export const MAKS_SIDE = 1500

/** JPEG-kvalitet. 0,8 gir små filer uten synlige artefakter på matvarer og etiketter. */
export const JPEG_KVALITET = 0.8

export type KrympetBilde = {
  fil: File
  bredde: number
  hoyde: number
  originalStorrelse: number
}

/**
 * Regner ut ny størrelse slik at lengste side blir maks [maksSide], med samme
 * forhold mellom sidene. Bilder som allerede er små, skaleres ikke opp.
 */
export function beregnStorrelse(
  bredde: number,
  hoyde: number,
  maksSide: number = MAKS_SIDE,
): { bredde: number; hoyde: number } {
  if (bredde <= 0 || hoyde <= 0) {
    throw new Error(`Ugyldig bildestørrelse: ${bredde}×${hoyde}`)
  }
  const skala = Math.min(1, maksSide / Math.max(bredde, hoyde))
  return {
    bredde: Math.max(1, Math.round(bredde * skala)),
    hoyde: Math.max(1, Math.round(hoyde * skala)),
  }
}

/** Gir JPEG-filen et navn som passer innholdet, f.eks. «IMG_1234.HEIC» → «IMG_1234.jpg». */
export function jpegFilnavn(originalNavn: string): string {
  const uten = originalNavn.replace(/\.[^./\\]+$/, '')
  return `${uten || 'bilde'}.jpg`
}

export async function krympBilde(original: File): Promise<KrympetBilde> {
  const kilde = await lesBilde(original)
  try {
    const { bredde, hoyde } = beregnStorrelse(kilde.width, kilde.height)

    const canvas = document.createElement('canvas')
    canvas.width = bredde
    canvas.height = hoyde
    const ctx = canvas.getContext('2d')
    if (!ctx) throw new Error('Nettleseren støtter ikke canvas')

    // Hvit bakgrunn, ellers blir gjennomsiktige PNG-er svarte i JPEG.
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, bredde, hoyde)
    ctx.imageSmoothingQuality = 'high'
    ctx.drawImage(kilde, 0, 0, bredde, hoyde)

    const blob = await tilBlob(canvas, 'image/jpeg', JPEG_KVALITET)
    return {
      fil: new File([blob], jpegFilnavn(original.name), { type: 'image/jpeg' }),
      bredde,
      hoyde,
      originalStorrelse: original.size,
    }
  } finally {
    if ('close' in kilde) kilde.close()
  }
}

/**
 * Leser bildet med EXIF-orienteringen brukt, så et bilde tatt med telefonen
 * på høykant ikke havner liggende. `createImageBitmap` med
 * `imageOrientation: 'from-image'` gjør det direkte; eldre nettlesere uten
 * støtte for valget faller tilbake til `<img>`, som også følger EXIF.
 */
async function lesBilde(fil: File): Promise<ImageBitmap | HTMLImageElement> {
  if (typeof createImageBitmap === 'function') {
    try {
      return await createImageBitmap(fil, { imageOrientation: 'from-image' })
    } catch {
      // Faller tilbake til <img> under.
    }
  }
  const url = URL.createObjectURL(fil)
  try {
    const img = new Image()
    img.src = url
    await img.decode()
    return img
  } catch {
    throw new Error('Kunne ikke lese bildet. Prøv å ta det på nytt, eller velg et annet bilde.')
  } finally {
    URL.revokeObjectURL(url)
  }
}

function tilBlob(canvas: HTMLCanvasElement, type: string, kvalitet: number): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('Kunne ikke komprimere bildet'))),
      type,
      kvalitet,
    )
  })
}
