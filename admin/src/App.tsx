import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useState } from 'react'
import { api, getAdminOperator, setAdminOperator } from './api'
import { AppShell } from './components/AppShell'
import { ApprovalCenterPage } from './pages/ApprovalCenterPage'
import { AuditLogPage } from './pages/AuditLogPage'
import { InspectionTypesPage } from './pages/InspectionTypesPage'
import { CategoriesPage } from './pages/CategoriesPage'
import { IssuesPage } from './pages/IssuesPage'
import { InspectionPlansPage } from './pages/InspectionPlansPage'
import { InspectionReviewsPage } from './pages/InspectionReviewsPage'
import { InventoryItemsPage } from './pages/inventory/Items'
import { InventoryTransactionsPage } from './pages/inventory/Transactions'
import { InventoryAlertsPage } from './pages/inventory/Alerts'
import { DictsPage } from './pages/DictsPage'
import { KnowledgePage } from './pages/KnowledgePage'
import { LoginPage } from './pages/LoginPage'
import { OverviewPage } from './pages/OverviewPage'
import { PeoplePage } from './pages/PeoplePage'
import { RoomsPage } from './pages/RoomsPage'
import { RoomCheckRecordsPage } from './pages/RoomCheckRecordsPage'
import { SettingsPage } from './pages/SettingsPage'
import { UsersPage } from './pages/UsersPage'

/** 路由 → 页面权限 key 的映射（用于 Route guard 与 AppShell 菜单过滤） */
export const ROUTE_PERMISSIONS: Record<string, string> = {
  '/overview': 'overview',
  '/issues': 'issues',
  '/inspection-plans': 'inspection-plans',
  '/inspection-reviews': 'inspection-reviews',
  '/approvals': 'approvals',
  '/rooms': 'rooms',
  '/room-check-records': 'room-check-records',
  '/dicts': 'dicts',
  '/categories': 'categories',
  '/people': 'people',
  '/knowledge': 'knowledge',
  '/admin/inventory/items': 'inventory',
  '/admin/inventory/transactions': 'inventory-transactions',
  '/admin/inventory/alerts': 'inventory-alerts',
  '/settings': 'settings',
  '/users': 'users',
  '/audit-logs': 'audit-logs',
  '/inspection-types': 'inspection-types',
}

/** 是否允许访问某路径。超管（* 或 pagePermissions 包含 path key）放行；其它一律拒绝。 */
export function canAccessPath(operator: ReturnType<typeof getAdminOperator>, path: string): boolean {
  if (!operator) return false
  if (operator.superAdmin) return true
  const perms = operator.pagePermissions ?? []
  if (perms.includes('*')) return true
  // 兼容：'inventory' 顶层同时包含 items/transactions/alerts
  if (perms.includes('inventory') && (path.startsWith('/admin/inventory') || path === '/inventory')) return true
  const key = ROUTE_PERMISSIONS[path]
  if (!key) return true // 未在映射表里的路径（/、登录页）默认放行
  return perms.includes(key)
}

function ProtectedRoute({ path, children }: { path: string; children: React.ReactNode }) {
  const operator = getAdminOperator()
  const location = useLocation()
  if (!canAccessPath(operator, path)) {
    return <Navigate to="/overview" replace state={{ denied: true, from: location.pathname }} />
  }
  return <>{children}</>
}

export default function App() {
  const [operator, setOperator] = useState(getAdminOperator)
  return (
    <BrowserRouter>
      {!operator
        ? <LoginPage onLoggedIn={() => setOperator(getAdminOperator())} />
        : <Routes>
          <Route element={<AppShell onLogout={() => { void api.adminLogout({ empNo: operator.empNo, empName: operator.empName }); setAdminOperator(null); setOperator(null) }} />}>
            <Route index element={<Navigate to="/overview" replace />} />
            <Route path="overview" element={<OverviewPage />} />
            <Route path="issues" element={<ProtectedRoute path="/issues"><IssuesPage /></ProtectedRoute>} />
            <Route path="inspection-plans" element={<ProtectedRoute path="/inspection-plans"><InspectionPlansPage /></ProtectedRoute>} />
            <Route path="inspection-reviews" element={<ProtectedRoute path="/inspection-reviews"><InspectionReviewsPage /></ProtectedRoute>} />
            <Route path="approvals" element={<ProtectedRoute path="/approvals"><ApprovalCenterPage /></ProtectedRoute>} />
            <Route path="rooms" element={<ProtectedRoute path="/rooms"><RoomsPage /></ProtectedRoute>} />
            <Route path="room-check-records" element={<ProtectedRoute path="/room-check-records"><RoomCheckRecordsPage /></ProtectedRoute>} />
            <Route path="dicts" element={<ProtectedRoute path="/dicts"><DictsPage /></ProtectedRoute>} />
            <Route path="categories" element={<ProtectedRoute path="/categories"><CategoriesPage /></ProtectedRoute>} />
            <Route path="people" element={<ProtectedRoute path="/people"><PeoplePage /></ProtectedRoute>} />
            <Route path="knowledge" element={<ProtectedRoute path="/knowledge"><KnowledgePage /></ProtectedRoute>} />
            <Route path="inventory" element={<Navigate to="/admin/inventory/items" replace />} />
            <Route path="admin/inventory/items" element={<ProtectedRoute path="/admin/inventory/items"><InventoryItemsPage /></ProtectedRoute>} />
            <Route path="admin/inventory/transactions" element={<ProtectedRoute path="/admin/inventory/transactions"><InventoryTransactionsPage /></ProtectedRoute>} />
            <Route path="admin/inventory/alerts" element={<ProtectedRoute path="/admin/inventory/alerts"><InventoryAlertsPage /></ProtectedRoute>} />
            <Route path="settings" element={<ProtectedRoute path="/settings"><SettingsPage /></ProtectedRoute>} />
            <Route path="users" element={<ProtectedRoute path="/users"><UsersPage /></ProtectedRoute>} />
            <Route path="audit-logs" element={<ProtectedRoute path="/audit-logs"><AuditLogPage /></ProtectedRoute>} />
            <Route path="inspection-types" element={<ProtectedRoute path="/inspection-types"><InspectionTypesPage /></ProtectedRoute>} />
          </Route>
        </Routes>
      }
    </BrowserRouter>
  )
}
