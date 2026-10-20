import { memo } from 'react'
import { Avatar, Button, Layout, Menu, Space, Tag, Typography } from '@arco-design/web-react'
import {
  IconApps, IconBook, IconCalendarClock, IconCamera, IconCheck, IconDashboard,
  IconExclamationCircle, IconHistory, IconHome, IconLanguage, IconRefresh, IconSafe, IconSettings,
  IconStorage, IconUnorderedList, IconUser, IconUserGroup,
} from '@arco-design/web-react/icon'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { getAdminOperator } from '../api'
import { canAccessPath } from '../App'

const navigation = [
  { path: '/overview', label: '巡检概览', icon: <IconDashboard /> },
  { path: '/issues', label: '问题管理', icon: <IconUnorderedList /> },
  { path: '/inspection-plans', label: '巡检计划', icon: <IconCalendarClock /> },
  { path: '/approvals', label: '审批中心', icon: <IconCheck />, superAdminOnly: true },
  { path: '/rooms', label: '巡检区域与点检项', icon: <IconHome /> },
  { path: '/room-check-records', label: '巡检记录', icon: <IconCamera /> },
  { path: '/dicts', label: '字典管理', icon: <IconLanguage /> },
  { path: '/categories', label: '问题分类', icon: <IconApps /> },
  { path: '/people', label: '区域人员', icon: <IconUserGroup /> },
  { path: '/knowledge', label: '案例知识库', icon: <IconBook /> },
  { path: '/admin/inventory/items', label: '物品台账', icon: <IconStorage /> },
  { path: '/admin/inventory/transactions', label: '出入库流水', icon: <IconUnorderedList /> },
  { path: '/admin/inventory/alerts', label: '库存预警', icon: <IconExclamationCircle /> },
  { path: '/users', label: '账号管理', icon: <IconUser />, superAdminOnly: true },
  { path: '/audit-logs', label: '审计日志', icon: <IconHistory />, superAdminOnly: true },
  { path: '/inspection-types', label: '巡检类型', icon: <IconCheck />, superAdminOnly: true },
  { path: '/settings', label: '系统设置', icon: <IconSettings /> },
]
const titles = new Map(navigation.map((item) => [item.path, item.label]))

export function AppShell({ onLogout }: { onLogout: () => void }) {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const operator = getAdminOperator()
  const visibleNav = navigation.filter((item) => {
    if (item.superAdminOnly && !operator?.superAdmin) return false
    return canAccessPath(operator, item.path)
  })
  const title = titles.get(pathname) ?? '巡检管理'
  return (
    <Layout className="app-layout">
      <Layout.Sider className="app-sider" width={256} breakpoint="lg" collapsedWidth={0}>
        <Space align="center" size={12} style={{ padding: '0 8px' }}>
          <div className="brand-mark">C</div>
          <div>
            <Typography.Text className="brand-name">CPS Inspect</Typography.Text>
            <div className="sider-subtitle">巡检管理平台</div>
          </div>
        </Space>
        <Menu className="app-menu" theme="light" selectedKeys={[pathname]} onClickMenuItem={(key) => navigate(key)}>
          {visibleNav.map(({ path, label, icon }) => <Menu.Item key={path}>{icon}{label}</Menu.Item>)}
        </Menu>
        <SiderFooter
          onSettings={() => navigate('/settings')}
          onChangePassword={() => navigate('/settings', { state: { tab: 'security' } })}
          onLogout={onLogout}
          operator={operator}
        />
      </Layout.Sider>
      <Layout className="app-main-layout">
        <Layout.Header className="app-header">
          <div style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <div><Typography.Title heading={6} style={{ margin: 0, color: '#273a31' }}>{title}</Typography.Title></div>
            <Space size={6}>
              {operator?.mustChangePassword && (
                <Tag color="orange" icon={<IconSafe />}>首次登录请到「系统设置」修改密码</Tag>
              )}
              <Button type="text" icon={<IconRefresh />} aria-label="刷新页面" onClick={() => window.location.reload()} />
              <Button type="text" aria-label="打开系统设置" onClick={() => navigate('/settings')}>
                <Avatar size={28} className="header-avatar">{operator?.empName?.slice(0, 1) || '管'}</Avatar>
              </Button>
            </Space>
          </div>
        </Layout.Header>
        <Layout.Content className="app-content"><main><Outlet /></main></Layout.Content>
      </Layout>
    </Layout>
  )
}

type Operator = ReturnType<typeof getAdminOperator>

const SiderFooter = memo(function SiderFooter({
  onSettings, onChangePassword, onLogout, operator,
}: { onSettings: () => void; onChangePassword: () => void; onLogout: () => void; operator: Operator }) {
  return (
    <div className="sider-footer">
      <Button className="sider-settings" type="text" icon={<IconSettings />} onClick={onSettings}>系统设置</Button>
      <Space align="center" size={10} className="sider-account">
        <Avatar size={32} className="sider-avatar">{operator?.empName?.slice(0, 1) || '管'}</Avatar>
        <div>
          <div className="sider-user">{operator?.empName || '管理中心'}</div>
          <div className="sider-user-meta">
            {operator?.empNo}
            {operator?.superAdmin ? <Tag color="arcoblue" size="small" style={{ marginLeft: 4 }}>超管</Tag> : null}
          </div>
        </div>
      </Space>
      <Space size={4} style={{ marginTop: 10 }}>
        <Button type="text" size="mini" icon={<IconSafe />} onClick={onChangePassword}>修改密码</Button>
        <Button type="text" size="mini" onClick={onLogout} style={{ color: '#899799' }}>退出登录</Button>
      </Space>
    </div>
  )
})
