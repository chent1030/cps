import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { useState } from 'react'
import { getAdminOperator, setAdminOperator } from './api'
import { AppShell } from './components/AppShell'
import { CategoriesPage } from './pages/CategoriesPage'
import { IssuesPage } from './pages/IssuesPage'
import { InventoryItemsPage } from './pages/inventory/Items'
import { InventoryTransactionsPage } from './pages/inventory/Transactions'
import { InventoryAlertsPage } from './pages/inventory/Alerts'
import { KnowledgePage } from './pages/KnowledgePage'
import { OverviewPage } from './pages/OverviewPage'
import { PeoplePage } from './pages/PeoplePage'
import { SettingsPage } from './pages/SettingsPage'
import { LoginPage } from './pages/LoginPage'

export default function App() {
  const [operator, setOperator] = useState(getAdminOperator)
  return (
    <BrowserRouter>
      {!operator ? <LoginPage onLoggedIn={() => setOperator(getAdminOperator())} /> :
      <Routes>
          <Route element={<AppShell onLogout={() => { setAdminOperator(null); setOperator(null) }} />}>
          <Route index element={<Navigate to="/overview" replace />} />
          <Route path="overview" element={<OverviewPage />} />
          <Route path="issues" element={<IssuesPage />} />
          <Route path="categories" element={<CategoriesPage />} />
          <Route path="people" element={<PeoplePage />} />
          <Route path="knowledge" element={<KnowledgePage />} />
          <Route path="inventory" element={<Navigate to="/admin/inventory/items" replace />} />
          <Route path="admin/inventory/items" element={<InventoryItemsPage />} />
          <Route path="admin/inventory/transactions" element={<InventoryTransactionsPage />} />
          <Route path="admin/inventory/alerts" element={<InventoryAlertsPage />} />
          <Route path="settings" element={<SettingsPage />} />
        </Route>
      </Routes>}
    </BrowserRouter>
  )
}
