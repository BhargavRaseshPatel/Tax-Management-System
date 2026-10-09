import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router'
import type { Role } from './api/types'
import { HOME_BY_ROLE, useSession } from './auth/session'
import { AppShell } from './components/AppShell'
import { LoginPage } from './pages/LoginPage'
import { AdminOverviewPage } from './pages/admin/AdminOverviewPage'
import { ReportsPage } from './pages/admin/ReportsPage'
import { UsersPage } from './pages/admin/UsersPage'
import { OfficerOverviewPage } from './pages/officer/OfficerOverviewPage'
import { OwnersPage } from './pages/officer/OwnersPage'
import { PropertiesPage } from './pages/officer/PropertiesPage'
import { TaxAccountsPage } from './pages/officer/TaxAccountsPage'
import { TaxRatesPage } from './pages/officer/TaxRatesPage'
import { ResidentPaymentsPage } from './pages/resident/ResidentPaymentsPage'
import { ResidentPropertiesPage } from './pages/resident/ResidentPropertiesPage'

/**
 * UI-only guard for navigation. It is NOT security: the backend enforces roles and data ownership from Stage 8.
 */
function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const { user } = useSession()
  if (!user) return <Navigate to="/login" replace />
  if (user.role !== role) return <Navigate to={HOME_BY_ROLE[user.role]} replace />
  return children
}

function Home() {
  const { user } = useSession()
  return <Navigate to={user ? HOME_BY_ROLE[user.role] : '/login'} replace />
}

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<AppShell />}>
        <Route path="/resident" element={<RequireRole role="RESIDENT"><ResidentPropertiesPage /></RequireRole>} />
        <Route path="/resident/payments" element={<RequireRole role="RESIDENT"><ResidentPaymentsPage /></RequireRole>} />

        <Route path="/officer" element={<RequireRole role="TAX_OFFICER"><OfficerOverviewPage /></RequireRole>} />
        <Route path="/officer/properties" element={<RequireRole role="TAX_OFFICER"><PropertiesPage /></RequireRole>} />
        <Route path="/officer/owners" element={<RequireRole role="TAX_OFFICER"><OwnersPage /></RequireRole>} />
        <Route path="/officer/rates" element={<RequireRole role="TAX_OFFICER"><TaxRatesPage /></RequireRole>} />
        <Route path="/officer/accounts" element={<RequireRole role="TAX_OFFICER"><TaxAccountsPage /></RequireRole>} />

        <Route path="/admin" element={<RequireRole role="ADMIN"><AdminOverviewPage /></RequireRole>} />
        <Route path="/admin/users" element={<RequireRole role="ADMIN"><UsersPage /></RequireRole>} />
        <Route path="/admin/reports" element={<RequireRole role="ADMIN"><ReportsPage /></RequireRole>} />
      </Route>
      <Route path="*" element={<Home />} />
    </Routes>
  )
}
