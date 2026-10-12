import { Button, Result } from 'antd'
import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <Result
      status="404"
      title="Page not found"
      extra={
        <Link to="/">
          <Button type="primary">Back to dashboard</Button>
        </Link>
      }
    />
  )
}
