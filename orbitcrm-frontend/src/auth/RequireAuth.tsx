import { Spin } from 'antd'
import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { useAuth } from './context'

export function RequireAuth({ children }: { children: ReactNode }) {
  const { me, ready } = useAuth()
  const location = useLocation()
  if (!ready) {
    return (
      <div className="full-page-center">
        <Spin size="large" />
      </div>
    )
  }
  if (!me) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  return children
}
