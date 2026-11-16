import { describe, expect, it } from 'vitest'
import { boksStil } from './boks.ts'

describe('boksStil', () => {
  it('gjør koordinater om til prosent av bildet', () => {
    expect(boksStil({ x1: 100, y1: 250, x2: 400, y2: 750 })).toEqual({
      left: '10%',
      top: '25%',
      width: '30%',
      height: '50%',
    })
  })

  it('hele bildet blir 0–100 %', () => {
    expect(boksStil({ x1: 0, y1: 0, x2: 1000, y2: 1000 })).toEqual({
      left: '0%',
      top: '0%',
      width: '100%',
      height: '100%',
    })
  })

  it('holder boksen innenfor bildet', () => {
    const stil = boksStil({ x1: -100, y1: 0, x2: 1200, y2: 100 })
    expect(stil.left).toBe('0%')
    expect(stil.width).toBe('100%')
  })
})
