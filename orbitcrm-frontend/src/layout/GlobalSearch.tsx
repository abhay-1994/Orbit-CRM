import { SearchOutlined } from '@ant-design/icons'
import { Button, Input, Select, Space } from 'antd'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { toast } from '../lib/feedback'
import type { ModuleInfo } from '../lib/modules'

/** Header search box; opens the results page for all modules or one module. */
export function GlobalSearch({ modules }: { modules: ModuleInfo[] }) {
  const navigate = useNavigate()
  const [text, setText] = useState('')
  const [module, setModule] = useState('all')

  const submit = () => {
    const q = text.trim()
    if (q.length < 2) {
      toast.error('Type at least 2 characters to search')
      return
    }
    const params = new URLSearchParams({ q })
    if (module !== 'all') params.set('module', module)
    navigate(`/search?${params}`)
  }

  return (
    <Space.Compact className="global-search">
      <Input
        value={text}
        onChange={(e) => setText(e.target.value)}
        onPressEnter={submit}
        placeholder="Search leads, contacts, companies, deals…"
        allowClear
      />
      <Select
        value={module}
        onChange={setModule}
        style={{ width: 150 }}
        popupMatchSelectWidth={false}
        options={[{ value: 'all', label: 'All records' }, ...modules.map((m) => ({ value: m.key, label: m.label }))]}
      />
      <Button type="primary" icon={<SearchOutlined />} onClick={submit} aria-label="Search" />
    </Space.Compact>
  )
}
