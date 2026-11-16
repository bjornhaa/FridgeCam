import { describe, expect, it } from 'vitest'
import { beregnStorrelse, jpegFilnavn, MAKS_SIDE } from './krympBilde.ts'

describe('beregnStorrelse', () => {
  it('krymper liggende mobilbilde til maks lengste side', () => {
    expect(beregnStorrelse(4032, 3024)).toEqual({ bredde: MAKS_SIDE, hoyde: 1125 })
  })

  it('krymper stående mobilbilde til maks lengste side', () => {
    expect(beregnStorrelse(3024, 4032)).toEqual({ bredde: 1125, hoyde: MAKS_SIDE })
  })

  it('beholder forholdet mellom sidene', () => {
    const { bredde, hoyde } = beregnStorrelse(8064, 6048)
    expect(bredde / hoyde).toBeCloseTo(8064 / 6048, 2)
  })

  it('skalerer ikke opp små bilder', () => {
    expect(beregnStorrelse(800, 600)).toEqual({ bredde: 800, hoyde: 600 })
  })

  it('lar bilde på nøyaktig maks stå urørt', () => {
    expect(beregnStorrelse(MAKS_SIDE, 1000)).toEqual({ bredde: MAKS_SIDE, hoyde: 1000 })
  })

  it('gir aldri en side på 0 piksler for ekstreme formater', () => {
    expect(beregnStorrelse(30000, 10).hoyde).toBe(1)
  })

  it('avviser ugyldig størrelse', () => {
    expect(() => beregnStorrelse(0, 100)).toThrow()
  })
})

describe('jpegFilnavn', () => {
  it('bytter filendelse til .jpg', () => {
    expect(jpegFilnavn('IMG_1234.HEIC')).toBe('IMG_1234.jpg')
  })

  it('legger til .jpg når filen mangler endelse', () => {
    expect(jpegFilnavn('image')).toBe('image.jpg')
  })

  it('gir et navn når originalen ikke har noe', () => {
    expect(jpegFilnavn('')).toBe('bilde.jpg')
  })
})
