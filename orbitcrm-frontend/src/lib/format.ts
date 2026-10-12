import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import type { TenantSummary } from '../api/types'

dayjs.extend(relativeTime)

export const formatDate = (value: unknown) => (value ? dayjs(value as string).format('DD MMM YYYY') : '')
export const formatDateTime = (value: unknown) => (value ? dayjs(value as string).format('DD MMM YYYY, h:mm A') : '')
export const formatTime = (value: unknown) => (value ? dayjs(value as string).format('h:mm A') : '')
export const fromNow = (value: unknown) => (value ? dayjs(value as string).fromNow() : '')

/** Amount in the workspace currency, e.g. ₹7,50,000 or ₹7.5L when compact. */
export function formatMoney(value: unknown, tenant: TenantSummary, compact = false): string {
  return new Intl.NumberFormat(tenant.locale, {
    style: 'currency',
    currency: tenant.currency,
    notation: compact ? 'compact' : 'standard',
    maximumFractionDigits: compact ? 1 : 0,
  }).format(Number(value ?? 0))
}

export function currencySymbol(tenant: TenantSummary): string {
  return (
    new Intl.NumberFormat(tenant.locale, { style: 'currency', currency: tenant.currency })
      .formatToParts(0)
      .find((part) => part.type === 'currency')?.value ?? tenant.currency
  )
}

export function initials(name: string | undefined): string {
  return (name ?? '?')
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('')
}

/** Short time for lists: '10:42 AM' today, 'Yesterday', 'Mon' within a week, otherwise '12 Sep'. */
export function compactTime(value: string): string {
  const time = dayjs(value)
  const now = dayjs()
  if (time.isSame(now, 'day')) return time.format('h:mm A')
  if (time.isSame(now.subtract(1, 'day'), 'day')) return 'Yesterday'
  if (now.diff(time, 'day') < 7) return time.format('ddd')
  return time.format(time.isSame(now, 'year') ? 'D MMM' : 'D MMM YYYY')
}

/** Day separator text: 'Today', 'Yesterday', 'Monday, 29 September', or with the year when not this year. */
export function dayLabel(value: string): string {
  const day = dayjs(value)
  const now = dayjs()
  if (day.isSame(now, 'day')) return 'Today'
  if (day.isSame(now.subtract(1, 'day'), 'day')) return 'Yesterday'
  return day.format(day.isSame(now, 'year') ? 'dddd, D MMMM' : 'D MMMM YYYY')
}

/** '1 deal', '3 deals'. */
export const plural = (count: number, word: string) => `${count} ${word}${count === 1 ? '' : 's'}`

/** Replaces ISO timestamps inside free text (e.g. search subtitles) with readable dates. */
export const readableDates = (text: string) => text.replace(/\d{4}-\d{2}-\d{2}T[\d:.]+Z/g, (iso) => formatDateTime(iso))

export function fileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
