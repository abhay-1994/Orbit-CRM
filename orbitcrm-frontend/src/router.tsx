import { Spin } from 'antd'
import { Suspense, lazy, type ComponentType } from 'react'
import { Navigate, Outlet, createBrowserRouter } from 'react-router'
import { RequireAuth } from './auth/RequireAuth'
import { MODULES } from './lib/modules'

/** Loads a page on first visit, so each screen downloads only the code it needs. */
function page<M extends Record<K, ComponentType<any>>, K extends keyof M & string>(load: () => Promise<M>, name: K) {
  return lazy(() => load().then((module) => ({ default: module[name] })))
}

const AppLayout = page(() => import('./layout/AppLayout'), 'AppLayout')
const LoginPage = page(() => import('./pages/auth/LoginPage'), 'LoginPage')
const RegisterPage = page(() => import('./pages/auth/RegisterPage'), 'RegisterPage')
const ForgotPasswordPage = page(() => import('./pages/auth/ForgotPasswordPage'), 'ForgotPasswordPage')
const ResetPasswordPage = page(() => import('./pages/auth/ResetPasswordPage'), 'ResetPasswordPage')
const AcceptInvitePage = page(() => import('./pages/auth/AcceptInvitePage'), 'AcceptInvitePage')
const DashboardPage = page(() => import('./pages/DashboardPage'), 'DashboardPage')
const RecordListPage = page(() => import('./pages/records/RecordListPage'), 'RecordListPage')
const RecordDetailPage = page(() => import('./pages/records/RecordDetailPage'), 'RecordDetailPage')
const DealBoardPage = page(() => import('./pages/DealBoardPage'), 'DealBoardPage')
const CalendarPage = page(() => import('./pages/CalendarPage'), 'CalendarPage')
const SearchPage = page(() => import('./pages/SearchPage'), 'SearchPage')
const ImportPage = page(() => import('./pages/ImportPage'), 'ImportPage')
const ProfilePage = page(() => import('./pages/ProfilePage'), 'ProfilePage')
const ChatPage = page(() => import('./pages/chat/ChatPage'), 'ChatPage')
const NotFoundPage = page(() => import('./pages/NotFoundPage'), 'NotFoundPage')
const SettingsLayout = page(() => import('./pages/settings/SettingsLayout'), 'SettingsLayout')
const WorkspaceSettings = page(() => import('./pages/settings/WorkspaceSettings'), 'WorkspaceSettings')
const UsersSettings = page(() => import('./pages/settings/UsersSettings'), 'UsersSettings')
const TeamsSettings = page(() => import('./pages/settings/TeamsSettings'), 'TeamsSettings')
const RolesSettings = page(() => import('./pages/settings/RolesSettings'), 'RolesSettings')
const PicklistsSettings = page(() => import('./pages/settings/PicklistsSettings'), 'PicklistsSettings')
const PipelinesSettings = page(() => import('./pages/settings/PipelinesSettings'), 'PipelinesSettings')
const AuditLogPage = page(() => import('./pages/settings/AuditLogPage'), 'AuditLogPage')

export const router = createBrowserRouter([
  {
    element: (
      <Suspense
        fallback={
          <div className="full-page-center">
            <Spin size="large" />
          </div>
        }
      >
        <Outlet />
      </Suspense>
    ),
    children: [
      // Public pages. The invite and reset paths match the links in the backend's emails.
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
      { path: '/forgot-password', element: <ForgotPasswordPage /> },
      { path: '/reset-password', element: <ResetPasswordPage /> },
      { path: '/accept-invite', element: <AcceptInvitePage /> },
      {
        element: (
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        ),
        children: [
          { index: true, element: <DashboardPage /> },
          ...MODULES.flatMap((m) => [
            { path: m.key, element: <RecordListPage key={m.key} module={m.key} /> },
            { path: `${m.key}/:id`, element: <RecordDetailPage key={m.key} module={m.key} /> },
          ]),
          { path: 'deals/board', element: <DealBoardPage /> },
          { path: 'calendar', element: <CalendarPage /> },
          { path: 'search', element: <SearchPage /> },
          { path: 'import', element: <ImportPage /> },
          { path: 'profile', element: <ProfilePage /> },
          { path: 'chat/:conversationId?', element: <ChatPage /> },
          {
            path: 'settings',
            element: <SettingsLayout />,
            children: [
              { index: true, element: <Navigate to="workspace" replace /> },
              { path: 'workspace', element: <WorkspaceSettings /> },
              { path: 'users', element: <UsersSettings /> },
              { path: 'teams', element: <TeamsSettings /> },
              { path: 'roles', element: <RolesSettings /> },
              { path: 'picklists', element: <PicklistsSettings /> },
              { path: 'pipelines', element: <PipelinesSettings /> },
              { path: 'audit', element: <AuditLogPage /> },
            ],
          },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
])
