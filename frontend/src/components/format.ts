const cad = new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' })
const whole = new Intl.NumberFormat('en-CA')

export const money = (value: number | null | undefined) => (value == null ? '—' : cad.format(value))
export const count = (value: number | null | undefined) => (value == null ? '—' : whole.format(value))

/** ISO date or timestamp → "2026-06-15". */
export const date = (value: string | null | undefined) => (value ? value.slice(0, 10) : '—')

/** 0.0078241 → "0.782410%". Display only; the backend owns every calculation. */
export const ratePercent = (rate: number) => `${(rate * 100).toFixed(6)}%`

export const enumLabel = (value: string) =>
  value.charAt(0) + value.slice(1).toLowerCase().replaceAll('_', ' ')

export const initials = (name: string) =>
  name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]!.toUpperCase()).join('')

/** Evaluated once at load; fine for a session-length demo. */
export const CURRENT_YEAR = new Date().getFullYear()

/** Today's date as yyyy-mm-dd in local time, for date inputs. */
export const today = () => new Date().toLocaleDateString('en-CA')
