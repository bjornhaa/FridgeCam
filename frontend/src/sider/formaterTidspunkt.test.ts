import { describe, expect, it } from 'vitest'
import { formaterTidspunkt } from './KjoleskapSide.tsx'

describe('formaterTidspunkt', () => {
  const na = new Date(2026, 10, 17, 20, 0)

  it('sier i dag og i går', () => {
    expect(formaterTidspunkt(new Date(2026, 10, 17, 18, 42).toISOString(), na)).toBe('i dag kl. 18:42')
    expect(formaterTidspunkt(new Date(2026, 10, 16, 9, 5).toISOString(), na)).toBe('i går kl. 09:05')
  })

  it('viser dato for eldre, med år bare når det er et annet år', () => {
    expect(formaterTidspunkt(new Date(2026, 10, 12, 18, 42).toISOString(), na)).toBe('12. november kl. 18:42')
    expect(formaterTidspunkt(new Date(2025, 0, 3, 7, 0).toISOString(), na)).toBe('3. januar 2025 kl. 07:00')
  })
})
