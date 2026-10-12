import { SearchOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Card, Col, Empty, Row, Skeleton, Tag } from 'antd'
import { Link, useSearchParams } from 'react-router'
import { http, qs } from '../api/client'
import type { SearchResult } from '../api/types'
import { readableDates } from '../lib/format'
import { MODULE_BY_KEY } from '../lib/modules'

/** Global search results, grouped by module. Only records the user may see are returned. */
export function SearchPage() {
  const [params] = useSearchParams()
  const q = params.get('q') ?? ''
  const module = params.get('module')
  const search = useQuery({
    queryKey: ['search', q, module],
    queryFn: () => http.get<SearchResult>(`/search${qs({ q, modules: module, limit: 20 })}`),
    enabled: q.length >= 2,
  })

  return (
    <div>
      <div className="page-head">
        <div className="page-icon">
          <SearchOutlined />
        </div>
        <div>
          <h1>Search results for “{q}”</h1>
          <p>{search.data ? `${search.data.total} matching records` : 'Searching…'}</p>
        </div>
      </div>
      {search.isLoading ? (
        <Skeleton active />
      ) : !search.data?.groups.length ? (
        <Card>
          <Empty description="Nothing found. Try a name, email, phone number or record number like LEAD-12." />
        </Card>
      ) : (
        <Row gutter={[16, 16]}>
          {search.data.groups.map((group) => (
            <Col key={group.module} xs={24} lg={12}>
              <Card
                title={
                  <span>
                    {MODULE_BY_KEY[group.module]?.icon} {group.label} <Tag>{group.count}</Tag>
                  </span>
                }
              >
                {group.results.map((hit) => (
                  <div key={hit.id} style={{ padding: '8px 0', borderBottom: '1px solid #f0f1f6' }}>
                    <Link to={`/${group.module}/${hit.id}`}>
                      <strong>{hit.title}</strong>
                    </Link>{' '}
                    <span className="muted" style={{ fontSize: 12 }}>
                      {hit.recordNumber}
                    </span>
                    {hit.subtitle && <div className="muted">{readableDates(hit.subtitle)}</div>}
                  </div>
                ))}
                {group.count > group.results.length && (
                  <div className="muted" style={{ marginTop: 8 }}>
                    Showing {group.results.length} of {group.count}. Refine your search to narrow it down.
                  </div>
                )}
              </Card>
            </Col>
          ))}
        </Row>
      )}
    </div>
  )
}
