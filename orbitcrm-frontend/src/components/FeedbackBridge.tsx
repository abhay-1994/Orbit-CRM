import { App } from 'antd'
import { useEffect } from 'react'
import { setToaster } from '../lib/feedback'

/** Hands the themed message API to code outside components (see lib/feedback). */
export function FeedbackBridge() {
  const { message } = App.useApp()
  useEffect(() => setToaster(message), [message])
  return null
}
