import { useState } from 'react'
import {
  Box, Button, Chip, IconButton, InputAdornment, MenuItem, Paper, Table, TableBody, TableCell, TableHead,
  TablePagination, TableRow, TextField, Tooltip, Typography,
} from '@mui/material'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import LinkIcon from '@mui/icons-material/Link'
import PersonAddAlt1OutlinedIcon from '@mui/icons-material/PersonAddAlt1Outlined'
import SearchIcon from '@mui/icons-material/Search'
import { fieldError, useApi, useApiMutation } from '../../api/client'
import type { Owner, Page, User } from '../../api/types'
import { date } from '../../components/format'
import { EmptyState, FormDialog, Mono, PageHeader, QueryState } from '../../components/ui'
import { tokens } from '../../theme'

export function OwnersPage() {
  const [search, setSearch] = useState('')
  const [name, setName] = useState('')
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<Owner | 'new' | null>(null)
  const owners = useApi<Page<Owner>>(`/api/owners?page=${page}&size=10&sort=id${name ? `&name=${encodeURIComponent(name)}` : ''}`)
  const rows = owners.data?.content ?? []

  return (
    <>
      <PageHeader eyebrow="Taxpayer registry" title="Property owners"
        subtitle="Legal titleholders who receive tax bills. Linking an owner to a resident login lets that resident see their properties."
        actions={<Button variant="contained" startIcon={<PersonAddAlt1OutlinedIcon />} onClick={() => setEditing('new')}>Register owner</Button>} />

      <Paper sx={{ overflow: 'hidden' }}>
        <Box component="form" sx={{ p: 2, borderBottom: `1px solid ${tokens.border}` }} onSubmit={(e) => { e.preventDefault(); setPage(0); setName(search.trim()) }}>
          <TextField fullWidth size="small" placeholder="Search by owner name, then press Enter" value={search} onChange={(e) => setSearch(e.target.value)}
            slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
        </Box>
        <QueryState query={owners}>
          {rows.length === 0 ? <EmptyState>No owners match.</EmptyState> : (
            <Box sx={{ overflowX: 'auto' }}>
              <Table size="small">
                <TableHead>
                  <TableRow><TableCell>Owner</TableCell><TableCell>Portal link</TableCell><TableCell>Contact</TableCell><TableCell>Mailing address</TableCell><TableCell>Registered</TableCell><TableCell /></TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((o) => (
                    <TableRow key={o.id}>
                      <TableCell><Typography sx={{ fontWeight: 600 }}>{o.fullName}</Typography><Mono sx={{ color: 'text.secondary' }}>#{o.id}</Mono></TableCell>
                      <TableCell>
                        {o.userId
                          ? <Chip icon={<LinkIcon />} label={`user #${o.userId}`} size="small" sx={{ bgcolor: tokens.mint, color: tokens.teal }} />
                          : <Chip label="Unlinked" size="small" variant="outlined" />}
                      </TableCell>
                      <TableCell><Mono>{o.email ?? '—'}</Mono><Typography variant="caption" sx={{ display: 'block', color: 'text.secondary' }}>{o.phone ?? ''}</Typography></TableCell>
                      <TableCell>{o.mailingAddress ?? '—'}</TableCell>
                      <TableCell><Mono>{date(o.createdAt)}</Mono></TableCell>
                      <TableCell align="right"><Tooltip title="Edit owner"><IconButton size="small" onClick={() => setEditing(o)}><EditOutlinedIcon fontSize="small" /></IconButton></Tooltip></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
          )}
          <TablePagination component="div" count={owners.data?.page.totalElements ?? 0} page={page} rowsPerPage={10} rowsPerPageOptions={[10]} onPageChange={(_, p) => setPage(p)} />
        </QueryState>
      </Paper>

      {editing && <OwnerDialog owner={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </>
  )
}

function OwnerDialog({ owner, onClose }: { owner: Owner | null; onClose: () => void }) {
  const residents = useApi<Page<User>>('/api/users?size=100&sort=fullName')
  const [form, setForm] = useState({
    fullName: owner?.fullName ?? '', email: owner?.email ?? '', phone: owner?.phone ?? '',
    mailingAddress: owner?.mailingAddress ?? '', userId: owner?.userId ? String(owner.userId) : '',
  })
  const save = useApiMutation()
  const set = (key: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [key]: e.target.value })
  const blankToNull = (v: string) => (v.trim() === '' ? null : v.trim())

  return (
    <FormDialog open title={owner ? 'Edit owner' : 'Register owner'} submitLabel={owner ? 'Save changes' : 'Register'} pending={save.isPending} error={save.error} onClose={onClose}
      onSubmit={() => save.mutate({
        path: owner ? `/api/owners/${owner.id}` : '/api/owners', method: owner ? 'PUT' : 'POST',
        body: { fullName: form.fullName, email: blankToNull(form.email), phone: blankToNull(form.phone), mailingAddress: blankToNull(form.mailingAddress), userId: form.userId ? Number(form.userId) : null },
      }, { onSuccess: onClose })}>
      <TextField autoFocus label="Legal name" value={form.fullName} onChange={set('fullName')} required error={!!fieldError(save.error, 'fullName')} helperText={fieldError(save.error, 'fullName')} />
      <TextField label="Email" type="email" value={form.email} onChange={set('email')} error={!!fieldError(save.error, 'email')} helperText={fieldError(save.error, 'email')} />
      <TextField label="Phone" value={form.phone} onChange={set('phone')} />
      <TextField label="Mailing address" value={form.mailingAddress} onChange={set('mailingAddress')} />
      <TextField select label="Resident portal login (optional)" value={form.userId} onChange={set('userId')} helperText="Only RESIDENT users can be linked; each login links to one owner.">
        <MenuItem value="">Not linked</MenuItem>
        {(residents.data?.content ?? []).filter((u) => u.role === 'RESIDENT').map((u) => <MenuItem key={u.id} value={u.id}>{u.fullName} · {u.email}</MenuItem>)}
      </TextField>
    </FormDialog>
  )
}
