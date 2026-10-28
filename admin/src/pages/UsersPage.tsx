import { Button, Card, Form, Input, Message, Modal, Pagination, Select, Space, Table, Tag, Typography } from '@arco-design/web-react'
import type { TableColumnProps } from '@arco-design/web-react'
import { IconLock, IconPlus, IconRefresh, IconSearch, IconUser } from '@arco-design/web-react/icon'
import { useState } from 'react'
import { api } from '../api'
import { DataCard, FilterBar, PageHead, StatePanel } from '../components/PageParts'
import { useRemote } from '../hooks/useRemote'
import { errorMessage } from '../lib/presentation'
import type { AdminUser, AdminUserCreate, AdminUserUpdate } from '../types'

/** 可分配的页面权限 key（菜单里出现的所有路径简写）。* 表示超管。 */
export const PAGE_PERMISSIONS: { key: string; label: string }[] = [
  { key: 'overview', label: '巡检概览' },
  { key: 'issues', label: '问题管理' },
  { key: 'inspection-plans', label: '巡检计划' },
  { key: 'inspection-reviews', label: '巡检复核' },
  { key: 'rooms', label: '辅房与点检项' },
  { key: 'room-check-records', label: '点检记录' },
  { key: 'dicts', label: '字典管理' },
  { key: 'categories', label: '问题分类' },
  { key: 'people', label: '区域人员' },
  { key: 'knowledge', label: '案例知识库' },
  { key: 'inventory', label: '物品台账' },
  { key: 'inventory-transactions', label: '出入库流水' },
  { key: 'inventory-alerts', label: '库存预警' },
  { key: 'settings', label: '系统设置' },
]

const initialFilters = { keyword: '', status: '' }
const emptyForm: AdminUserCreate & { editingId?: number } = { empNo: '', empName: '', pagePermissions: [], remark: '' }

export function UsersPage() {
  const [filters, setFilters] = useState(initialFilters)
  const [query, setQuery] = useState({ ...initialFilters, page: 1, pageSize: 20 })
  const [visible, setVisible] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form, setForm] = useState<AdminUserCreate & { editingId?: number }>(emptyForm)
  const [editingUser, setEditingUser] = useState<AdminUser | null>(null)
  const [resetPwdVisible, setResetPwdVisible] = useState(false)
  const [resetPwdResult, setResetPwdResult] = useState<{ empNo: string; newPassword: string } | null>(null)

  const remote = useRemote(() => api.usersPage({ ...query, status: query.status || undefined, keyword: query.keyword || undefined }), [query])

  const closeModal = () => { setVisible(false); setEditingUser(null); setForm(emptyForm) }
  const openCreate = () => { setEditingUser(null); setForm(emptyForm); setVisible(true) }
  const openEdit = (user: AdminUser) => {
    setEditingUser(user)
    setForm({
      empNo: user.empNo,
      empName: user.empName,
      pagePermissions: user.pagePermissions?.filter((p) => p !== '*') ?? [],
      remark: user.remark ?? '',
      editingId: user.id,
    })
    setVisible(true)
  }

  const submit = async () => {
    if (!form.empNo.trim() || !form.empName.trim()) return Message.warning('请输入工号和姓名')
    if (form.pagePermissions.length === 0) return Message.warning('请至少选择一项页面权限')
    setSaving(true)
    try {
      if (editingUser) {
        const updateBody: AdminUserUpdate = {
          empName: form.empName.trim(),
          pagePermissions: form.pagePermissions,
          remark: form.remark,
        }
        await api.updateUser(editingUser.id, updateBody)
        Message.success('账号已更新')
      } else {
        const result = await api.createUser({
          empNo: form.empNo.trim(),
          empName: form.empName.trim(),
          pagePermissions: form.pagePermissions,
          remark: form.remark,
        })
        setResetPwdResult({ empNo: result.user.empNo, newPassword: result.user.remark?.match(/初始密码: (\S+)/)?.[1] || '' })
        // 优先提示初始密码：把 message 一并展示
        Message.success(result.message)
      }
      closeModal()
      remote.refresh()
    } catch (error) { Message.error(errorMessage(error)) }
    finally { setSaving(false) }
  }

  const toggleStatus = async (user: AdminUser) => {
    try {
      await api.setUserStatus(user.id, user.status !== 'ACTIVE')
      Message.success(user.status === 'ACTIVE' ? '已停用' : '已启用')
      remote.refresh()
    } catch (error) { Message.error(errorMessage(error)) }
  }

  const resetPassword = async (user: AdminUser) => {
    try {
      const result = await api.resetUserPassword(user.id)
      setResetPwdResult({ empNo: user.empNo, newPassword: result.newPassword })
      setResetPwdVisible(true)
      remote.refresh()
    } catch (error) { Message.error(errorMessage(error)) }
  }

  const columns: TableColumnProps<AdminUser>[] = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '工号', dataIndex: 'empNo', width: 120 },
    { title: '姓名', dataIndex: 'empName', width: 120 },
    {
      title: '角色',
      dataIndex: 'pagePermissions',
      width: 240,
      render: (_, record) => record.superAdmin
        ? <Tag color="arcoblue">超级管理员</Tag>
        : (
          <Space size={4} wrap>
            {(record.pagePermissions ?? []).slice(0, 3).map((p) => (
              <Tag key={p} color="gray">{PAGE_PERMISSIONS.find((x) => x.key === p)?.label ?? p}</Tag>
            ))}
            {(record.pagePermissions?.length ?? 0) > 3 && <Tag color="gray">+{record.pagePermissions!.length - 3}</Tag>}
          </Space>
        ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v) => v === 'ACTIVE' ? <Tag color="green">启用</Tag> : <Tag color="red">停用</Tag>,
    },
    {
      title: '密码',
      dataIndex: 'mustChangePassword',
      width: 90,
      render: (v) => v ? <Tag color="orange">待修改</Tag> : <Typography.Text type="secondary">正常</Typography.Text>,
    },
    {
      title: '最近登录',
      dataIndex: 'lastLoginAt',
      width: 170,
      render: (v) => v ? new Date(v).toLocaleString('zh-CN') : <Typography.Text type="secondary">从未</Typography.Text>,
    },
    {
      title: '操作',
      dataIndex: 'op',
      width: 240,
      render: (_, record) => (
        <Space>
          <Button type="text" size="small" onClick={() => openEdit(record)}>编辑</Button>
          <Button type="text" size="small" icon={<IconLock />} onClick={() => void resetPassword(record)}>重置密码</Button>
          <Button type="text" size="small" status={record.status === 'ACTIVE' ? 'warning' : 'success'} onClick={() => void toggleStatus(record)}>
            {record.status === 'ACTIVE' ? '停用' : '启用'}
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <div className="page-stack">
      <PageHead
        title="账号管理"
        detail="管理登录账号、页面权限和初始/重置密码。仅超级管理员可访问。"
      >
        <Space>
          <Button icon={<IconRefresh />} onClick={remote.refresh}>刷新</Button>
          <Button type="primary" icon={<IconPlus />} onClick={openCreate}>新建账号</Button>
        </Space>
      </PageHead>
      <Card className="filter-card">
        <FilterBar>
        <Input
          allowClear
          prefix={<IconSearch />}
          placeholder="搜索工号 / 姓名"
          value={filters.keyword}
          onChange={(v) => setFilters((c) => ({ ...c, keyword: v }))}
          onPressEnter={() => setQuery((c) => ({ ...c, keyword: filters.keyword, page: 1 }))}
          style={{ width: 220 }}
        />
        <Select
          placeholder="状态"
          value={filters.status}
          onChange={(v) => setFilters((c) => ({ ...c, status: v }))}
          onBlur={() => setQuery((c) => ({ ...c, status: filters.status, page: 1 }))}
          style={{ width: 140 }}
        >
          <Select.Option value="">全部</Select.Option>
          <Select.Option value="ACTIVE">启用</Select.Option>
          <Select.Option value="LOCKED">停用</Select.Option>
        </Select>
        <Button type="primary" onClick={() => setQuery({ ...filters, page: 1, pageSize: 20 })}>查询</Button>
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
          />
          {remote.data && remote.data.total > 0 && (
            <div className="pagination-wrap">
              <Pagination
                current={query.page}
                pageSize={query.pageSize}
                total={remote.data.total}
                onChange={(p, ps) => setQuery((c) => ({ ...c, page: p, pageSize: ps }))}
                showTotal
              />
            </div>
          )}
        </DataCard>
      </StatePanel>

      <Modal
        title={editingUser ? `编辑账号 — ${editingUser.empNo}` : '新建账号'}
        visible={visible}
        onCancel={closeModal}
        onOk={() => void submit()}
        okText={editingUser ? '保存' : '创建'}
        confirmLoading={saving}
        style={{ width: 520 }}
      >
        <Form layout="vertical">
          <Form.Item label="员工工号" required>
            <Input
              prefix={<IconUser />}
              value={form.empNo}
              disabled={!!editingUser}
              placeholder="如 E200001"
              onChange={(v) => setForm((c) => ({ ...c, empNo: v }))}
            />
          </Form.Item>
          <Form.Item label="员工姓名" required>
            <Input
              value={form.empName}
              placeholder="如 张三"
              onChange={(v) => setForm((c) => ({ ...c, empName: v }))}
            />
          </Form.Item>
          <Form.Item label="页面权限" required extra="至少选择一项；不勾选 = 任何菜单都不可见">
            <Select
              mode="multiple"
              value={form.pagePermissions}
              onChange={(v) => setForm((c) => ({ ...c, pagePermissions: v }))}
              placeholder="选择允许访问的页面"
            >
              {PAGE_PERMISSIONS.map((p) => (
                <Select.Option key={p.key} value={p.key}>{p.label}</Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item label="备注">
            <Input.TextArea
              value={form.remark}
              placeholder="选填，例：现场运营 / 测试账号"
              onChange={(v) => setForm((c) => ({ ...c, remark: v }))}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="重置密码"
        visible={resetPwdVisible}
        onCancel={() => setResetPwdVisible(false)}
        onOk={() => setResetPwdVisible(false)}
        okText="我已记录"
        cancelText="关闭"
      >
        {resetPwdResult && (
          <div>
            <p>账号 <Typography.Text code>{resetPwdResult.empNo}</Typography.Text> 的密码已重置，请将以下新密码复制给该用户：</p>
            <Card style={{ background: '#f7f8fa', marginTop: 12 }}>
              <Typography.Title heading={5} copyable={{ text: resetPwdResult.newPassword }} style={{ margin: 0 }}>
                {resetPwdResult.newPassword}
              </Typography.Title>
            </Card>
            <p style={{ marginTop: 12, color: '#86909c' }}>首次登录后系统会强制要求修改密码。</p>
          </div>
        )}
      </Modal>
    </div>
  )
}
