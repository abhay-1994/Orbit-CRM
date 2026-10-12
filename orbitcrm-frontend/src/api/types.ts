// Shapes of the OrbitCRM REST API (mirrors the backend DTOs).

export type Action = 'CREATE' | 'READ' | 'UPDATE' | 'DELETE' | 'EXPORT' | 'IMPORT'
export type AccessScope = 'NONE' | 'OWN' | 'TEAM' | 'ALL'
export type ActionScopes = Partial<Record<Action, AccessScope>>
export type Permissions = Record<string, ActionScopes>

export interface FieldErrorDetail {
  field: string | null
  code: string
  message: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Ref {
  id: string
  name: string
}

// ---------------------------------------------------------------- identity

export interface TenantSummary {
  id: string
  slug: string
  name: string
  timezone: string
  currency: string
  locale: string
  fiscalYearStartMonth: number
}

export interface Me {
  id: string
  email: string
  firstName: string
  lastName: string
  fullName: string
  jobTitle?: string
  phone?: string
  timezone: string
  locale: string
  mfaEnabled: boolean
  administrator: boolean
  tenant: TenantSummary
  role: Ref
  team?: Ref
  permissions: Permissions
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: Me
}

export type LoginResponse =
  | ({ mfaRequired: false } & AuthResponse)
  | { mfaRequired: true; mfaToken: string }

export interface UserLookup {
  id: string
  fullName: string
  email: string
}

export type UserStatus = 'INVITED' | 'ACTIVE' | 'DISABLED'

export interface UserResponse {
  id: string
  email: string
  firstName: string
  lastName: string
  fullName: string
  status: UserStatus
  roleId: string
  roleName: string
  teamId?: string
  teamName?: string
  jobTitle?: string
  phone?: string
  mfaEnabled: boolean
  lastLoginAt?: string
  createdAt: string
}

export interface RoleResponse {
  id: string
  name: string
  description?: string
  administrator: boolean
  system: boolean
  userCount: number
  permissions: Permissions
}

export interface CatalogResource {
  key: string
  label: string
  category: 'crm' | 'system'
  actions: Action[]
  scoped: boolean
}

export interface PermissionCatalog {
  resources: CatalogResource[]
  scopes: AccessScope[]
}

export interface TeamResponse {
  id: string
  name: string
  description?: string
  parentId?: string
  memberCount: number
}

export interface SessionResponse {
  id: string
  createdAt: string
  lastUsedAt: string
  expiresAt: string
  userAgent?: string
  ipAddress?: string
  current: boolean
}

export interface TenantInfo {
  id: string
  slug: string
  name: string
  timezone: string
  currency: string
  locale: string
  fiscalYearStartMonth: number
}

// ---------------------------------------------------------------- metadata and records

export type FieldType =
  | 'TEXT' | 'TEXTAREA' | 'EMAIL' | 'PHONE' | 'URL'
  | 'INTEGER' | 'DECIMAL' | 'CURRENCY' | 'PERCENT'
  | 'DATE' | 'DATETIME' | 'BOOLEAN'
  | 'PICKLIST' | 'MULTI_PICKLIST' | 'TAGS'
  | 'USER' | 'MULTI_USER' | 'LOOKUP' | 'REFERENCE'

export interface FieldOption {
  key: string
  label: string
  color?: string
  active: boolean
  isDefault: boolean
  group?: string
}

export interface FieldMeta {
  key: string
  label: string
  type: FieldType
  required: boolean
  readOnly: boolean
  maxLength?: number
  min?: number
  max?: number
  group: string
  helpText?: string
  filterable: boolean
  sortable: boolean
  listDefault: boolean
  lookupModule?: string
  lookupModuleField?: string
  referenceType?: string
  picklist?: string
  options?: FieldOption[]
  operators: string[]
}

export interface ModuleDetail {
  key: string
  singularLabel: string
  pluralLabel: string
  recordPrefix: string
  titleFields: string[]
  defaultSort: string
  permissions: ActionScopes
  fields: FieldMeta[]
}

export type RecordValues = Record<string, unknown>

export interface RecordView {
  id: string
  module: string
  recordNumber: string
  version: number
  title: string
  values: RecordValues
  labels: Record<string, string>
}

export interface Condition {
  field: string
  op: string
  value?: unknown
}

export interface SortSpec {
  field: string
  direction: 'asc' | 'desc'
}

export interface BulkResult {
  requested: number
  succeeded: number
  failures: { id: string; code: string; message: string }[]
}

export interface ViewResponse {
  id: string
  module: string
  name: string
  ownerUserId?: string
  visibility: 'PRIVATE' | 'SHARED'
  system: boolean
  filter?: unknown
  sort?: SortSpec[]
}

export interface PicklistValueView {
  id: string
  key: string
  label: string
  color?: string
  position: number
  active: boolean
  isDefault: boolean
  system: boolean
}

export interface PicklistResponse {
  key: string
  label: string
  values: PicklistValueView[]
}

// ---------------------------------------------------------------- deals

export type StageType = 'OPEN' | 'WON' | 'LOST'

export interface StageView {
  id: string
  pipelineId: string
  name: string
  position: number
  probability: number
  type: StageType
  active: boolean
}

export interface PipelineView {
  id: string
  name: string
  isDefault: boolean
  active: boolean
  position: number
  stages: StageView[]
}

export interface BoardColumn {
  stage: StageView
  count: number
  totalAmount: number
  weightedAmount: number
  deals: RecordView[]
}

export interface PipelineBoard {
  pipelineId: string
  pipelineName: string
  columns: BoardColumn[]
}

export interface StageMove {
  fromStage?: string
  toStage: string
  amount?: number
  changedBy: string
  changedAt: string
  secondsInPriorStage?: number
}

export interface ConversionSuggestion {
  id: string
  title: string
  recordNumber: string
}

export interface ConversionPreview {
  company: RecordValues
  contact: RecordValues
  deal: RecordValues
  matchingCompanies: ConversionSuggestion[]
  matchingContacts: ConversionSuggestion[]
}

export interface ConversionResult {
  company?: RecordView
  contact?: RecordView
  deal?: RecordView
}

// ---------------------------------------------------------------- collaboration

export interface NoteView {
  id: string
  body: string
  pinned: boolean
  createdBy: string
  createdByName?: string
  createdAt: string
  updatedAt: string
}

export interface AttachmentView {
  id: string
  fileName: string
  contentType: string
  sizeBytes: number
  uploadedByName?: string
  createdAt: string
}

export interface AuditChange {
  field: string
  label: string
  oldValue?: unknown
  newValue?: unknown
}

export interface TimelineItem {
  kind: 'audit' | 'note' | 'attachment' | 'activity'
  action: string
  at: string
  actorName?: string
  summary?: string
  refId?: string
  detail?: { changes?: AuditChange[]; [key: string]: unknown }
}

export interface Timeline {
  items: TimelineItem[]
  nextBefore?: string
}

export interface AuditLogView {
  id: string
  occurredAt: string
  actorName?: string
  action: string
  moduleKey?: string
  recordId?: string
  recordLabel?: string
  changes?: AuditChange[]
  metadata?: Record<string, unknown>
  ipAddress?: string
}

export interface NotificationView {
  id: string
  type: string
  title: string
  body?: string
  moduleKey?: string
  recordId?: string
  readAt?: string
  createdAt: string
}

// ---------------------------------------------------------------- search, dashboard, import

export interface SearchGroup {
  module: string
  label: string
  count: number
  results: { id: string; recordNumber: string; title: string; subtitle?: string }[]
}

export interface SearchResult {
  query: string
  total: number
  groups: SearchGroup[]
}

export interface Dashboard {
  range: string
  from: string
  to: string
  leads?: { open: number; created: number; converted: number; conversionRatePercent: number }
  deals?: {
    openCount: number
    pipelineValue: number
    weightedPipeline: number
    wonCount: number
    wonRevenue: number
    lostCount: number
    winRatePercent: number
  }
  funnel?: { stageId: string; stage: string; type: StageType; count: number; amount: number }[]
  revenueByMonth?: { month: string; wonCount: number; revenue: number }[]
  leadSources?: { key?: string; label: string; count: number }[]
  activities?: {
    dueToday: number
    overdue: number
    upcomingMeetings: { id: string; subject: string; startAt: string; relatedTo?: string }[]
  }
}

export type ImportStatus = 'UPLOADED' | 'MAPPED' | 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'

export interface ImportJob {
  id: string
  module: string
  fileName: string
  status: ImportStatus
  columns: string[]
  totalRows: number
  processedRows: number
  created: number
  updated: number
  skipped: number
  failed: number
  mapping: Record<string, string | null>
  errors: { row: number; messages: string[] }[]
}

export interface ImportPreview {
  job: ImportJob
  rows: { row: number; values: RecordValues; errors: string[] }[]
}

// ---------------------------------------------------------------- chat

export type PresenceStatus =
  | 'AVAILABLE'
  | 'BUSY'
  | 'IN_A_MEETING'
  | 'DO_NOT_DISTURB'
  | 'BE_RIGHT_BACK'
  | 'AWAY'
  | 'OFFLINE'

export interface PresenceView {
  userId: string
  status: PresenceStatus
}

export interface ChatPerson {
  id: string
  name: string
  email?: string
  active: boolean
  presence: PresenceStatus
}

export interface ChatMessage {
  id: string
  conversationId: string
  senderId: string
  body: string | null
  clientId: string
  createdAt: string
  editedAt?: string
  deleted: boolean
}

export interface Conversation {
  id: string
  with: ChatPerson
  lastMessage?: ChatMessage
  unreadCount: number
  lastReadAt?: string
  theirLastReadAt?: string
  createdAt: string
}

export interface MessagePage {
  messages: ChatMessage[]
  hasMore: boolean
}
