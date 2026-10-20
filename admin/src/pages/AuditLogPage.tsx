import { Card, Input, Pagination, Select, Table, Tag, Typography } from '@arco-design/web-react'
import type { TableColumnProps } from '@arco-design/web-react'
import { IconRefresh, IconSearch } from '@arco-design/web-react/icon'
import { Button } from '@arco-design/web-react'
import { useState } from 'react'
import { api } from '../api'
import { DataCard, FilterBar, PageHead, StatePanel } from '../components/PageParts'
import { useRemote } from '../hooks/useRemote'
import type { AdminAuditLog } from '../types'

/** 审计动作的语义映射（仅展示用，真实值以后端为准） */
const ACTION_LABELS: Record<string, { label: string; color: string }> = {
  LOGIN_SUCCESS: { label: '登录成功', color: 'green' },
  LOGIN_FAIL: { label: '登录失败', color: 'red' },
  LOGOUT: { label: '退出登录', color: 'gray' },
  PASSWORD_CHANGE: { label: '修改密码', color: 'arcoblue' },
  USER_CREATE: { label: '新建账号', color: 'arcoblue' },
  USER_UPDATE: { label: '更新账号', color: 'arcoblue' },
  USER_DISABLE: { label: '停用账号', color: 'orange' },
  USER_ENABLE: { label: '启用账号', color: 'green' },
  USER_RESET_PASSWORD: { label: '重置密码', color: 'orange' },
  PERMISSION_CHANGE: { label: '权限变更', color: 'purple' },
}

const initialFilters = { keyword: '', empNo: '', action: '', status: '' }

export function AuditLogPage() {
  const [filters, setFilters] = useState(initialFilters)
  const [query, setQuery] = useState({ ...initialFilters, page: 1, pageSize: 30 })

  const remote = useRemote(() => api.auditLogPage({
    keyword: query.keyword || undefined,
    empNo: query.empNo || undefined,
    action: query.action || undefined,
    status: query.status || undefined,
    page: query.page,
    pageSize: query.pageSize,
  }), [query])

  const columns: TableColumnProps<AdminAuditLog>[] = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '操作人', dataIndex: 'empNo', width: 120, render: (v, r) => v ? <><Typography.Text>{v}</Typography.Text>{r.empName ? <Typography.Text type="secondary"> / {r.empName}</Typography.Text> : null}</> : <Tag>SYSTEM</Tag> },
    {
      title: '动作',
      dataIndex: 'action',
      width: 130,
      render: (v) => {
        const meta = ACTION_LABELS[v] || { label: v, color: 'gray' }
        return <Tag color={meta.color}>{meta.label}</Tag>
      },
    },
    { title: '对象', dataIndex: 'targetType', width: 100, render: (v) => v ? <Tag>{v}</Tag> : '-' },
    { title: '对象ID', dataIndex: 'targetId', width: 140, render: (v) => v ?? '-' },
    { title: '详情', dataIndex: 'detail', width: 280, render: (v) => v ? <Typography.Text style={{ wordBreak: 'break-all' }}>{v}</Typography.Text> : '-' },
    {
      title: '结果',
      dataIndex: 'status',
      width: 90,
      render: (v) => v === 'SUCCESS' ? <Tag color="green">成功</Tag> : <Tag color="red">失败</Tag>,
    },
    { title: '错误信息', dataIndex: 'errorMessage', width: 220, render: (v) => v ? <Typography.Text type="error">{v}</Typography.Text> : '-' },
    { title: '时间', dataIndex: 'createdAt', width: 170, render: (v) => v ? new Date(v).toLocaleString('zh-CN') : '-' },
  ]

  return (
    <div className="page-stack">
      <PageHead
        title="审计日志"
        detail="记录登录、登出、密码、账号、权限相关操作。保留全量历史（仅超管可访问）。"
      >
        <Button icon={<IconRefresh />} onClick={remote.refresh}>刷新</Button>
      </PageHead>
      <Card className="filter-card">
        <FilterBar>
        <Input
          allowClear
          prefix={<IconSearch />}
          placeholder="搜索工号/对象/详情"
          value={filters.keyword}
          onChange={(v) => setFilters((c) => ({ ...c, keyword: v }))}
          onPressEnter={() => setQuery((c) => ({ ...c, keyword: filters.keyword, page: 1 }))}
          style={{ width: 220 }}
        />
        <Input
          allowClear
          placeholder="操作人工号"
          value={filters.empNo}
          onChange={(v) => setFilters((c) => ({ ...c, empNo: v }))}
          onPressEnter={() => setQuery((c) => ({ ...c, empNo: filters.empNo, page: 1 }))}
          style={{ width: 160 }}
        />
        <Select
          placeholder="动作"
          value={filters.action}
          onChange={(v) => setFilters((c) => ({ ...c, action: v }))}
          onBlur={() => setQuery((c) => ({ ...c, action: filters.action, page: 1 }))}
          style={{ width: 160 }}
        >
          <Select.Option value="">全部动作</Select.Option>
          {Object.entries(ACTION_LABELS).map(([k, v]) => (
            <Select.Option key={k} value={k}>{v.label}</Select.Option>
          ))}
        </Select>
        <Select
          placeholder="结果"
          value={filters.status}
          onChange={(v) => setFilters((c) => ({ ...c, status: v }))}
          onBlur={() => setQuery((c) => ({ ...c, status: filters.status, page: 1 }))}
          style={{ width: 120 }}
        >
          <Select.Option value="">全部</Select.Option>
          <Select.Option value="SUCCESS">成功</Select.Option>
          <Select.Option value="FAILED">失败</Select.Option>
        </Select>
        <Button type="primary" onClick={() => setQuery({ ...filters, page: 1, pageSize: 30 })}>查询</Button>
        </FilterBar>
      </Card>
      <StatePanel loading={remote.loading} error={remote.error} onRetry={remote.refresh}>
        <DataCard>
          <Table
            rowKey="id"
            loading={remote.loading}
            data={remote.data?.records ?? []}
            columns={columns}
            pagination={false}
            size="small"
            scroll={{ x: 1500 }}
          />
          {remote.data && remote.data.total > 0 && (
            <div className="pagination-wrap">
              <Pagination
                current={query.page}
                pageSize={query.pageSize}
                total={remote.data.total}
                sizeCanChange
                sizeOptions={[20, 30, 50, 100]}
                onChange={(p, ps) => setQuery((c) => ({ ...c, page: p, pageSize: ps }))}
                showTotal
              />
            </div>
          )}
        </DataCard>
      </StatePanel>
    </div>
  )
}
