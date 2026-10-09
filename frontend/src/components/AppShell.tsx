import { useState, type ReactNode } from 'react'
import { Navigate, NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import {
  AppBar, Avatar, Box, Chip, Drawer, IconButton, List, ListItemButton, ListItemIcon, ListItemText, Stack,
  ToggleButton, ToggleButtonGroup, Toolbar, Tooltip, Typography, useMediaQuery,
} from '@mui/material'
import AccountBalanceIcon from '@mui/icons-material/AccountBalance'
import AssessmentOutlinedIcon from '@mui/icons-material/AssessmentOutlined'
import DashboardOutlinedIcon from '@mui/icons-material/DashboardOutlined'
import HomeWorkOutlinedIcon from '@mui/icons-material/HomeWorkOutlined'
import LogoutIcon from '@mui/icons-material/Logout'
import ManageAccountsOutlinedIcon from '@mui/icons-material/ManageAccountsOutlined'
import MenuIcon from '@mui/icons-material/Menu'
import PaymentsOutlinedIcon from '@mui/icons-material/PaymentsOutlined'
import PercentIcon from '@mui/icons-material/Percent'
import PeopleOutlineIcon from '@mui/icons-material/PeopleOutlined'
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined'
import { useApi } from '../api/client'
import type { Role, SessionUser } from '../api/types'
import { ROLES } from '../api/types'
import { HOME_BY_ROLE, ROLE_LABEL, useSession } from '../auth/session'
import { tokens } from '../theme'
import { initials } from './format'

const DRAWER_WIDTH = 248

interface NavItem { label: string; to: string; icon: ReactNode }

const NAV: Record<Role, { title: string; items: NavItem[] }> = {
  RESIDENT: {
    title: 'Resident services',
    items: [
      { label: 'My Properties', to: '/resident', icon: <HomeWorkOutlinedIcon /> },
      { label: 'Payments', to: '/resident/payments', icon: <PaymentsOutlinedIcon /> },
    ],
  },
  TAX_OFFICER: {
    title: 'Officer administration',
    items: [
      { label: 'Overview', to: '/officer', icon: <DashboardOutlinedIcon /> },
      { label: 'Properties', to: '/officer/properties', icon: <HomeWorkOutlinedIcon /> },
      { label: 'Owners', to: '/officer/owners', icon: <PeopleOutlineIcon /> },
      { label: 'Tax Rates', to: '/officer/rates', icon: <PercentIcon /> },
      { label: 'Tax Accounts & Bills', to: '/officer/accounts', icon: <ReceiptLongOutlinedIcon /> },
    ],
  },
  ADMIN: {
    title: 'Administrative navigation',
    items: [
      { label: 'System Overview', to: '/admin', icon: <DashboardOutlinedIcon /> },
      { label: 'User Management', to: '/admin/users', icon: <ManageAccountsOutlinedIcon /> },
      { label: 'Reports & Audits', to: '/admin/reports', icon: <AssessmentOutlinedIcon /> },
    ],
  },
}

export function Logo({ role }: { role?: Role }) {
  return (
    <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center' }}>
      <Box sx={{ width: 36, height: 36, borderRadius: 2, bgcolor: tokens.navy, color: '#5eead4', display: 'grid', placeItems: 'center' }}>
        <AccountBalanceIcon fontSize="small" />
      </Box>
      <Box>
        <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
          <Typography sx={{ fontWeight: 800, fontSize: '1.1rem', lineHeight: 1 }}>CivicTax</Typography>
          {role && <Chip label={role === 'RESIDENT' ? 'PORTAL' : role === 'ADMIN' ? 'ADMIN' : 'OFFICER'} size="small" sx={{ bgcolor: tokens.navy, color: '#fff', height: 18, fontSize: '0.6rem' }} />}
        </Stack>
        <Typography variant="caption" sx={{ color: 'text.secondary' }}>Municipal Property Tax Portal</Typography>
      </Box>
    </Stack>
  )
}

function Sidebar({ role, onNavigate }: { role: Role; onNavigate: () => void }) {
  const nav = NAV[role]
  return (
    <Stack sx={{ height: '100%' }}>
      <Box sx={{ px: 2.5, py: 2.25, borderBottom: `1px solid ${tokens.border}` }}><Logo role={role} /></Box>
      <Typography variant="overline" sx={{ px: 2.5, pt: 2.5, pb: 1, color: 'text.secondary' }}>{nav.title}</Typography>
      <List sx={{ px: 1.5 }}>
        {nav.items.map((item) => (
          <ListItemButton
            key={item.to}
            component={NavLink}
            to={item.to}
            end
            onClick={onNavigate}
            sx={{
              borderRadius: 2, mb: 0.5, color: 'text.primary',
              '&.active': { bgcolor: tokens.navy, color: '#fff', '& .MuiListItemIcon-root': { color: '#fff' } },
              '&.active:hover': { bgcolor: tokens.navy },
            }}
          >
            <ListItemIcon sx={{ minWidth: 36 }}>{item.icon}</ListItemIcon>
            <ListItemText primary={item.label} slotProps={{ primary: { sx: { fontWeight: 500, fontSize: '0.92rem' } } }} />
          </ListItemButton>
        ))}
      </List>
      <Box sx={{ flexGrow: 1 }} />
      <ApiStatus />
    </Stack>
  )
}

/** Live backend health from Spring Boot Actuator. */
function ApiStatus() {
  const health = useApi<{ status: string }>('/actuator/health')
  const up = health.data?.status === 'UP'
  return (
    <Box sx={{ m: 1.5, p: 1.5, borderRadius: 2, bgcolor: tokens.panel }}>
      <Typography sx={{ fontFamily: tokens.mono, fontSize: '0.72rem', color: up ? tokens.teal : tokens.danger }}>
        ● {health.isPending ? 'Checking API…' : up ? 'Spring Boot API: UP' : 'Spring Boot API: DOWN'}
      </Typography>
      <Typography sx={{ fontFamily: tokens.mono, fontSize: '0.68rem', color: 'text.secondary' }}>PostgreSQL 17 · Flyway v1</Typography>
    </Box>
  )
}

/**
 * LOCAL DEV ONLY: role preview signs in (gets a real token) as the first active user of the chosen role.
 * Uses the dev sign-in endpoints, so it disappears automatically when the backend runs with Cognito.
 */
function RolePreview({ user }: { user: SessionUser }) {
  const { signIn } = useSession()
  const navigate = useNavigate()
  const users = useApi<SessionUser[]>('/api/dev/users')
  const firstOf = (role: Role) => users.data?.find((u) => u.role === role)
  if (users.isError) return null

  return (
    <ToggleButtonGroup
      size="small"
      exclusive
      value={user.role}
      onChange={(_, role: Role | null) => {
        const next = role && firstOf(role)
        if (next) signIn(next.email).then((u) => navigate(HOME_BY_ROLE[u.role]))
      }}
      sx={{ bgcolor: tokens.panel, '& .MuiToggleButton-root': { px: 1.25, py: 0.4, fontSize: '0.72rem', fontWeight: 600, border: 0 } }}
    >
      {ROLES.map((role) => (
        <ToggleButton key={role} value={role} disabled={!firstOf(role)}
          sx={{ '&.Mui-selected, &.Mui-selected:hover': { bgcolor: tokens.navy, color: '#fff' } }}>
          {ROLE_LABEL[role]}
        </ToggleButton>
      ))}
    </ToggleButtonGroup>
  )
}

export function AppShell() {
  const { user, signOut } = useSession()
  const desktop = useMediaQuery('(min-width:900px)')
  const [mobileOpen, setMobileOpen] = useState(false)
  const location = useLocation()
  if (!user) return <Navigate to="/login" replace /> // signed out, or the token expired

  const current = NAV[user.role].items.find((i) => i.to === location.pathname)?.label ?? ''
  const sidebar = <Sidebar role={user.role} onNavigate={() => setMobileOpen(false)} />

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <Drawer
        variant={desktop ? 'permanent' : 'temporary'}
        open={desktop || mobileOpen}
        onClose={() => setMobileOpen(false)}
        sx={{ width: desktop ? DRAWER_WIDTH : 0, flexShrink: 0, '& .MuiDrawer-paper': { width: DRAWER_WIDTH } }}
      >
        {sidebar}
      </Drawer>

      <Box sx={{ flexGrow: 1, minWidth: 0 }}>
        <AppBar position="sticky" color="inherit" sx={{ bgcolor: 'rgba(255,255,255,0.92)', backdropFilter: 'blur(8px)', borderBottom: `1px solid ${tokens.border}` }}>
          <Toolbar sx={{ gap: 1.5, flexWrap: 'wrap', py: 1 }}>
            {!desktop && (
              <IconButton edge="start" onClick={() => setMobileOpen(true)} aria-label="Open navigation"><MenuIcon /></IconButton>
            )}
            <Typography sx={{ fontWeight: 600, color: 'text.secondary', fontSize: '0.9rem' }}>
              {ROLE_LABEL[user.role]} portal <Box component="span" sx={{ color: 'text.primary' }}>/ {current}</Box>
            </Typography>
            <Chip label="● Demo environment · fictional data" size="small" sx={{ bgcolor: tokens.mint, color: tokens.teal, display: { xs: 'none', lg: 'flex' } }} />
            <Box sx={{ flexGrow: 1 }} />
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <Typography variant="overline" sx={{ color: 'text.secondary', display: { xs: 'none', md: 'block' } }}>Role preview</Typography>
              <RolePreview user={user} />
            </Stack>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <Box sx={{ textAlign: 'right', display: { xs: 'none', sm: 'block' } }}>
                <Typography sx={{ fontWeight: 600, fontSize: '0.85rem', lineHeight: 1.2 }}>{user.fullName}</Typography>
                <Typography sx={{ fontFamily: tokens.mono, fontSize: '0.72rem', color: 'text.secondary' }}>{user.email}</Typography>
              </Box>
              <Avatar sx={{ bgcolor: tokens.navy, width: 34, height: 34, fontSize: '0.8rem' }}>{initials(user.fullName)}</Avatar>
              <Tooltip title="Sign out">
                <IconButton onClick={signOut} aria-label="Sign out"><LogoutIcon fontSize="small" /></IconButton>
              </Tooltip>
            </Stack>
          </Toolbar>
        </AppBar>
        <Box component="main" sx={{ p: { xs: 2, md: 4 }, maxWidth: 1400 }}>
          <Outlet />
        </Box>
      </Box>
    </Box>
  )
}
