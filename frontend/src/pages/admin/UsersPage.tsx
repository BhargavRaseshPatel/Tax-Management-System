import { useState } from 'react'
import {
  Avatar, Box, Button, FormControlLabel, Grid, IconButton, InputAdornment, MenuItem, Paper, Stack, Switch, Table,
  TableBody, TableCell, TableHead, TableRow, TextField, Tooltip, Typography,
} from '@mui/material'
import AdminPanelSettingsOutlinedIcon from '@mui/icons-material/AdminPanelSettingsOutlined'
import BadgeOutlinedIcon from '@mui/icons-material/BadgeOutlined'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import HomeOutlinedIcon from '@mui/icons-material/HomeOutlined'
import PeopleOutlineIcon from '@mui/icons-material/PeopleOutlined'
import PersonAddAlt1OutlinedIcon from '@mui/icons-material/PersonAddAlt1Outlined'
import SearchIcon from '@mui/icons-material/Search'
import { fieldError, useApi, useApiMutation } from '../../api/client'
import type { Page, Role, User } from '../../api/types'
import { ROLES } from '../../api/types'
import { ROLE_LABEL, useSession } from '../../auth/session'
import { date, initials } from '../../components/format'
import { EmptyState, FormDialog, Mono, PageHeader, QueryState, StatCard, StatusChip } from '../../components/ui'
import { tokens } from '../../theme'

export function UsersPage() {
  const { user: me } = useSession()
  // ponytail: loads up to 100 users and filters in the browser; add server-side search when the user table grows
  const users = useApi<Page<User>>('/api/users?size=100&sort=id')
  const [search, setSearch] = useState('')
  const [role, setRole] = useState<Role | 'ALL'>('ALL')
  const [editing, setEditing] = useState<User | 'new' | null>(null)

  const all = users.data?.content ?? []
  const term = search.trim().toLowerCase()
  const shown = all.filter((u) => (role === 'ALL' || u.role === role)
    && (!term || u.fullName.toLowerCase().includes(term) || u.email.includes(term) || String(u.id) === term))
  const ofRole = (r: Role) => all.filter((u) => u.role === r).length

  return (
    <>
      <PageHeader eyebrow="Identity governance" title="User management"
        subtitle="Create accounts and assign roles. Users are deactivated, never deleted, because payments record who entered them."
        actions={<Button variant="contained" startIcon={<PersonAddAlt1OutlinedIcon />} onClick={() => setEditing('new')}>Add user</Button>} />

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}><StatCard label="Total accounts" value={users.data?.page.totalElements ?? '—'} hint={`${all.filter((u) => !u.active).length} deactivated`} icon={<PeopleOutlineIcon fontSize="small" />} /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}><StatCard label="Residents" value={ofRole('RESIDENT')} hint="Portal access to own records" icon={<HomeOutlinedIcon fontSize="small" />} /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}><StatCard label="Tax officers" value={ofRole('TAX_OFFICER')} hint="Assessments, billing, payments" icon={<BadgeOutlinedIcon fontSize="small" />} /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}><StatCard label="Administrators" value={ofRole('ADMIN')} hint="Users and configuration" icon={<AdminPanelSettingsOutlinedIcon fontSize="small" />} /></Grid>
      </Grid>

      <Paper sx={{ overflow: 'hidden' }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ p: 2, borderBottom: `1px solid ${tokens.border}` }}>
          <TextField size="small" fullWidth placeholder="Search by name, email, or user ID" value={search} onChange={(e) => setSearch(e.target.value)}
            slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
          <TextField select size="small" label="Role" value={role} onChange={(e) => setRole(e.target.value as Role | 'ALL')} sx={{ minWidth: 180 }}>
            <MenuItem value="ALL">All roles</MenuItem>
            {ROLES.map((r) => <MenuItem key={r} value={r}>{ROLE_LABEL[r]}</MenuItem>)}
          </TextField>
        </Stack>
        <QueryState query={users}>
          {shown.length === 0 ? <EmptyState>No users match.</EmptyState> : (
            <Box sx={{ overflowX: 'auto' }}>
              <Table size="small">
                <TableHead><TableRow><TableCell>ID</TableCell><TableCell>Name & email</TableCell><TableCell>Role</TableCell><TableCell>Status</TableCell><TableCell>Created</TableCell><TableCell /></TableRow></TableHead>
                <TableBody>
                  {shown.map((u) => (
                    <TableRow key={u.id} sx={{ opacity: u.active ? 1 : 0.6 }}>
                      <TableCell><Mono sx={{ fontWeight: 600 }}>#{u.id}</Mono></TableCell>
                      <TableCell>
                        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                          <Avatar sx={{ width: 32, height: 32, fontSize: '0.75rem', bgcolor: tokens.panel, color: tokens.navy }}>{initials(u.fullName)}</Avatar>
                          <Box>
                            <Typography sx={{ fontWeight: 600, fontSize: '0.9rem' }}>{u.fullName} {u.id === me?.id && <Mono sx={{ bgcolor: tokens.navy, color: '#fff', px: 0.5, borderRadius: 1, fontSize: '0.65rem' }}>YOU</Mono>}</Typography>
                            <Mono sx={{ color: 'text.secondary' }}>{u.email}</Mono>
                          </Box>
                        </Stack>
                      </TableCell>
                      <TableCell><StatusChip value={u.role} /></TableCell>
                      <TableCell><StatusChip value={u.active ? 'ACTIVE' : 'SUSPENDED'} /></TableCell>
                      <TableCell><Mono>{date(u.createdAt)}</Mono></TableCell>
                      <TableCell align="right"><Tooltip title="Edit user"><IconButton size="small" onClick={() => setEditing(u)}><EditOutlinedIcon fontSize="small" /></IconButton></Tooltip></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
          )}
        </QueryState>
      </Paper>

      {editing && <UserDialog user={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </>
  )
}

function UserDialog({ user, onClose }: { user: User | null; onClose: () => void }) {
  const [form, setForm] = useState({ email: user?.email ?? '', fullName: user?.fullName ?? '', role: (user?.role ?? 'RESIDENT') as Role, active: user?.active ?? true })
  const save = useApiMutation()
  return (
    <FormDialog open title={user ? `Edit user #${user.id}` : 'Add user'} submitLabel={user ? 'Save changes' : 'Create user'} pending={save.isPending} error={save.error} onClose={onClose}
      onSubmit={() => save.mutate(user
        ? { path: `/api/users/${user.id}`, method: 'PUT', body: { fullName: form.fullName, role: form.role, active: form.active } }
        : { path: '/api/users', body: { email: form.email, fullName: form.fullName, role: form.role } },
      { onSuccess: onClose })}>
      <TextField label="Email" type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required disabled={!!user}
        helperText={user ? 'Email is the sign-in identity and cannot change' : fieldError(save.error, 'email')} error={!!fieldError(save.error, 'email')} />
      <TextField autoFocus label="Full name" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} required
        error={!!fieldError(save.error, 'fullName')} helperText={fieldError(save.error, 'fullName')} />
      <TextField select label="Role" value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as Role })}>
        {ROLES.map((r) => <MenuItem key={r} value={r}>{ROLE_LABEL[r]}</MenuItem>)}
      </TextField>
      {user && <FormControlLabel control={<Switch checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />} label="Active (can sign in)" />}
    </FormDialog>
  )
}
