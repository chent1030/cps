import { memo } from 'react'
import { Avatar, Button, Layout, Menu, Space, Typography } from '@arco-design/web-react'
import { IconApps, IconBook, IconDashboard, IconExclamationCircle, IconRefresh, IconSettings, IconStorage, IconUnorderedList, IconUserGroup } from '@arco-design/web-react/icon'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { getAdminOperator } from '../api'

const navigation = [
  { path: '/overview', label: '巡检概览', icon: <IconDashboard /> },
  { path: '/issues', label: '问题管理', icon: <IconUnorderedList /> },
  { path: '/categories', label: '问题分类', icon: <IconApps /> },
  { path: '/people', label: '区域人员', icon: <IconUserGroup /> },
  { path: '/knowledge', label: '案例知识库', icon: <IconBook /> },
  // 库存管理分组（波次 14 E4 收尾，3 页面分离）
  { path: '/admin/inventory/items', label: '物品台账', icon: <IconStorage /> },
  { path: '/admin/inventory/transactions', label: '出入库流水', icon: <IconUnorderedList /> },
  { path: '/admin/inventory/alerts', label: '库存预警', icon: <IconExclamationCircle /> },
]
const titles = new Map(navigation.map((item) => [item.path, item.label]))

export function AppShell({ onLogout }: { onLogout: () => void }) {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const title = titles.get(pathname) ?? '巡检管理'
  const operator = getAdminOperator()
  return <Layout className="app-layout"><Layout.Sider className="app-sider" width={256} breakpoint="lg" collapsedWidth={0}>
    <Space align="center" size={12} style={{ padding: '0 8px' }}><div className="brand-mark">C</div><div><Typography.Text className="brand-name">CPS Inspect</Typography.Text><div className="sider-subtitle">巡检管理平台</div></div></Space>
    <Menu className="app-menu" theme="light" selectedKeys={[pathname]} onClickMenuItem={(key) => navigate(key)}>{navigation.map(({ path, label, icon }) => <Menu.Item key={path}>{icon}{label}</Menu.Item>)}</Menu>
    <SiderFooter onSettings={() => navigate('/settings')} onLogout={onLogout} operatorName={operator?.empName} />
  </Layout.Sider><Layout className="app-main-layout"><Layout.Header className="app-header"><div style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}><div><Typography.Text type="secondary" style={{ fontSize: 12 }}>CPS INSPECTION SYSTEM</Typography.Text><Typography.Title heading={6} style={{ margin: 0, color: '#273a31' }}>{title}</Typography.Title></div><Space size={6}><Button type="text" icon={<IconRefresh />} aria-label="刷新页面" onClick={() => window.location.reload()} /><Button type="text" aria-label="打开系统设置" onClick={() => navigate('/settings')}><Avatar size={28} className="header-avatar">管</Avatar></Button></Space></div></Layout.Header><Layout.Content className="app-content"><main><Outlet /></main></Layout.Content></Layout></Layout>
}

const SiderFooter = memo(function SiderFooter({ onSettings, onLogout, operatorName }: { onSettings: () => void; onLogout: () => void; operatorName?: string }) {
  return <div className="sider-footer"><Button className="sider-settings" type="text" icon={<IconSettings />} onClick={onSettings}>系统设置</Button><Space align="center" size={10} className="sider-account"><Avatar size={32} className="sider-avatar">{operatorName?.slice(0, 1) || '管'}</Avatar><div><div className="sider-user">{operatorName || '管理中心'}</div><div className="sider-subtitle">已登录管理会话</div></div></Space><Button type="text" size="small" onClick={onLogout} style={{ marginTop: 10, color: '#899799' }}>退出登录</Button></div>
})
