import type { FormInstance } from 'antd'
import { ApiError } from '../api/client'

interface Toaster {
  success(content: string): unknown
  error(content: string): unknown
}

// Set by <FeedbackBridge/> so code outside components (query cache, helpers) can show messages
// with the app's theme.
let toaster: Toaster | null = null

export function setToaster(next: Toaster): void {
  toaster = next
}

export const toast = {
  success: (text: string) => toaster?.success(text),
  error: (text: string) => toaster?.error(text),
}

export function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Something went wrong. Please try again.'
}

/** Default handling for failed requests. A 401 ends the session and redirects to sign-in instead. */
export function showError(error: unknown): void {
  if (error instanceof ApiError && error.status === 401) return
  toast.error(errorText(error))
}

/** Shows server validation errors next to their form fields; anything else becomes a message. */
export function handleFormError(form: FormInstance, error: unknown): void {
  if (!(error instanceof ApiError) || error.details.length === 0) {
    showError(error)
    return
  }
  const registered = form.getFieldsValue()
  const onForm = error.details.filter((d) => d.field !== null && d.field in registered)
  form.setFields(onForm.map((d) => ({ name: d.field as string, errors: [d.message] })))
  const elsewhere = error.details.filter((d) => !onForm.includes(d))
  if (elsewhere.length) toast.error(elsewhere.map((d) => d.message).join(' '))
}
