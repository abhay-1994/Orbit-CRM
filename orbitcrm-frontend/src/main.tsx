import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp, ConfigProvider } from 'antd'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router'
import { ApiError } from './api/client'
import { AuthProvider } from './auth/AuthProvider'
import { FeedbackBridge } from './components/FeedbackBridge'
import { showError } from './lib/feedback'
import { router } from './router'
import { theme } from './theme'
import './styles.css'

declare module '@tanstack/react-query' {
  interface Register {
    /** `silent: true` means the caller handles the error itself (e.g. shows it on a form). */
    mutationMeta: { silent?: boolean }
  }
}

const queryClient = new QueryClient({
  queryCache: new QueryCache({ onError: showError }),
  mutationCache: new MutationCache({
    onError: (error, _variables, _context, mutation) => {
      if (!mutation.meta?.silent) showError(error)
    },
  }),
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      // API errors (4xx/5xx with a message) are final; only network failures are retried.
      retry: (failures, error) => !(error instanceof ApiError) && failures < 2,
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ConfigProvider theme={theme}>
      <AntApp>
        <FeedbackBridge />
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <RouterProvider router={router} />
          </AuthProvider>
        </QueryClientProvider>
      </AntApp>
    </ConfigProvider>
  </StrictMode>,
)
