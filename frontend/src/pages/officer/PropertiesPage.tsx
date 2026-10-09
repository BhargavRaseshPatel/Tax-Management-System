import { useState } from 'react'
import { Link as RouterLink } from 'react-router'
import {
  Box, Button, Grid, IconButton, InputAdornment, MenuItem, Paper, Stack, Table, TableBody, TableCell, TableHead,
  TablePagination, TableRow, TextField, Tooltip, Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import SearchIcon from '@mui/icons-material/Search'
import { fieldError, useApi, useApiMutation } from '../../api/client'
import type { Assessment, Owner, Ownership, Page, Property, PropertyClass, TaxAccount } from '../../api/types'
import { PROPERTY_CLASSES } from '../../api/types'
import { CURRENT_YEAR, date, enumLabel, money, today } from '../../components/format'
import { EmptyState, ErrorAlert, FormDialog, Mono, PageHeader, QueryState, SectionCard, StatusChip } from '../../components/ui'
import { tokens } from '../../theme'

export function PropertiesPage() {
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [editing, setEditing] = useState<Property | 'new' | null>(null)

  const query = useApi<Page<Property>>(`/api/properties?page=${page}&size=10&sort=id${q ? `&q=${encodeURIComponent(q)}` : ''}`)
  const rows = query.data?.content ?? []

  return (
    <>
      <PageHeader eyebrow="Assessment roll" title="Properties"
        subtitle="Search the roll, register parcels, assign owners, record assessments, and open tax accounts."
        actions={<Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing('new')}>Register property</Button>} />

      <Grid container spacing={2.5}>
        <Grid size={{ xs: 12, lg: 7 }}>
          <Paper sx={{ overflow: 'hidden' }}>
            <Box component="form" sx={{ p: 2, borderBottom: `1px solid ${tokens.border}` }} onSubmit={(e) => { e.preventDefault(); setPage(0); setQ(search.trim()) }}>
              <TextField fullWidth size="small" placeholder="Search by roll number or street address, then press Enter" value={search}
                onChange={(e) => setSearch(e.target.value)}
                slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
            </Box>
            <QueryState query={query}>
              {rows.length === 0 ? <EmptyState>No properties match.</EmptyState> : (
                <Box sx={{ overflowX: 'auto' }}>
                  <Table size="small">
                    <TableHead><TableRow><TableCell>Roll number</TableCell><TableCell>Address</TableCell><TableCell>Class</TableCell></TableRow></TableHead>
                    <TableBody>
                      {rows.map((p) => (
                        <TableRow key={p.id} hover selected={p.id === selectedId} onClick={() => setSelectedId(p.id)}>
                          <TableCell><Mono>{p.rollNumber}</Mono></TableCell>
                          <TableCell>{p.streetAddress}<Typography variant="caption" sx={{ display: 'block', color: 'text.secondary' }}>{p.city}, {p.postalCode}</Typography></TableCell>
                          <TableCell><StatusChip value={p.propertyClass} /></TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </Box>
              )}
              <TablePagination component="div" count={query.data?.page.totalElements ?? 0} page={page} rowsPerPage={10}
                rowsPerPageOptions={[10]} onPageChange={(_, p) => setPage(p)} />
            </QueryState>
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, lg: 5 }}>
          {selectedId ? <PropertyPanel id={selectedId} onEdit={setEditing} />
            : <Paper sx={{ p: 4 }}><EmptyState>Select a property to see owners, assessments, and its tax account.</EmptyState></Paper>}
        </Grid>
      </Grid>

      {editing && <PropertyDialog property={editing === 'new' ? null : editing} onClose={() => setEditing(null)} onSaved={setSelectedId} />}
    </>
  )
}

function PropertyDialog({ property, onClose, onSaved }: { property: Property | null; onClose: () => void; onSaved: (id: number) => void }) {
  const [form, setForm] = useState({
    rollNumber: property?.rollNumber ?? '', streetAddress: property?.streetAddress ?? '', city: property?.city ?? '',
    postalCode: property?.postalCode ?? '', propertyClass: (property?.propertyClass ?? 'RESIDENTIAL') as PropertyClass,
  })
  const save = useApiMutation<Property>()
  const set = (key: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [key]: e.target.value })
  const field = (key: keyof typeof form, label: string, extra: object = {}) => (
    <TextField label={label} value={form[key]} onChange={set(key)} required error={!!fieldError(save.error, key)} helperText={fieldError(save.error, key)} {...extra} />
  )
  return (
    <FormDialog open title={property ? 'Edit property' : 'Register property'} submitLabel={property ? 'Save changes' : 'Register'}
      pending={save.isPending} error={save.error} onClose={onClose}
      onSubmit={() => save.mutate(
        { path: property ? `/api/properties/${property.id}` : '/api/properties', method: property ? 'PUT' : 'POST', body: form },
        { onSuccess: (saved) => { onSaved(saved.id); onClose() } },
      )}>
      {field('rollNumber', 'Roll number', { autoFocus: true })}
      {field('streetAddress', 'Street address')}
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        {field('city', 'City', { fullWidth: true })}
        {field('postalCode', 'Postal code', { placeholder: 'A1A 1A1', fullWidth: true })}
      </Stack>
      <TextField select label="Property class" value={form.propertyClass} onChange={set('propertyClass')}>
        {PROPERTY_CLASSES.map((c) => <MenuItem key={c} value={c}>{enumLabel(c)}</MenuItem>)}
      </TextField>
    </FormDialog>
  )
}

function PropertyPanel({ id, onEdit }: { id: number; onEdit: (p: Property) => void }) {
  const property = useApi<Property>(`/api/properties/${id}`)
  const p = property.data
  return (
    <QueryState query={property}>
      {p && (
        <Stack spacing={2}>
          <Paper sx={{ p: 2.5 }}>
            <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <Box>
                <StatusChip value={p.propertyClass} />
                <Typography variant="h1" sx={{ fontSize: '1.45rem', mt: 1 }}>{p.streetAddress}</Typography>
                <Typography sx={{ color: 'text.secondary' }}>{p.city}, {p.postalCode} · <Mono>{p.rollNumber}</Mono></Typography>
              </Box>
              <Tooltip title="Edit property"><IconButton onClick={() => onEdit(p)}><EditOutlinedIcon /></IconButton></Tooltip>
            </Stack>
          </Paper>
          <OwnersSection propertyId={id} />
          <AssessmentsSection propertyId={id} />
          <AccountSection propertyId={id} />
        </Stack>
      )}
    </QueryState>
  )
}

function OwnersSection({ propertyId }: { propertyId: number }) {
  const owners = useApi<Ownership[]>(`/api/properties/${propertyId}/owners`)
  const allOwners = useApi<Page<Owner>>('/api/owners?size=100&sort=fullName')
  const mutation = useApiMutation()
  const [ownerId, setOwnerId] = useState('')
  const [percent, setPercent] = useState('100')
  const total = (owners.data ?? []).reduce((sum, o) => sum + o.ownershipPercent, 0)

  return (
    <SectionCard title="Ownership" subtitle={`${total}% of 100% assigned`}>
      <QueryState query={owners}>
        <Stack spacing={1}>
          {(owners.data ?? []).map((o) => (
            <Stack key={o.ownerId} direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between', p: 1, borderRadius: 2, bgcolor: tokens.panel }}>
              <Typography sx={{ fontWeight: 600 }}>{o.ownerName} <Mono sx={{ color: 'text.secondary' }}>#{o.ownerId}</Mono></Typography>
              <Stack direction="row" sx={{ alignItems: 'center' }}>
                <Mono sx={{ fontWeight: 600 }}>{o.ownershipPercent}%</Mono>
                <Tooltip title="Remove owner">
                  <IconButton size="small" onClick={() => mutation.mutate({ path: `/api/properties/${propertyId}/owners/${o.ownerId}`, method: 'DELETE' })}>
                    <DeleteOutlineIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              </Stack>
            </Stack>
          ))}
          {owners.data?.length === 0 && <Typography variant="body2" sx={{ color: 'text.secondary' }}>No owners assigned.</Typography>}
        </Stack>
      </QueryState>
      {total < 100 && (
        <Stack component="form" direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mt: 2 }}
          onSubmit={(e) => {
            e.preventDefault()
            mutation.mutate({ path: `/api/properties/${propertyId}/owners`, body: { ownerId: Number(ownerId), ownershipPercent: Number(percent) } },
              { onSuccess: () => setOwnerId('') })
          }}>
          <TextField select size="small" label="Owner" value={ownerId} onChange={(e) => setOwnerId(e.target.value)} required sx={{ flexGrow: 1 }}>
            {(allOwners.data?.content ?? []).map((o) => <MenuItem key={o.id} value={o.id}>{o.fullName}</MenuItem>)}
          </TextField>
          <TextField size="small" label="Share %" type="number" value={percent} onChange={(e) => setPercent(e.target.value)} required
            slotProps={{ htmlInput: { min: 0.01, max: 100, step: 0.01 } }} sx={{ width: { sm: 110 } }} />
          <Button type="submit" variant="outlined" disabled={mutation.isPending}>Assign</Button>
        </Stack>
      )}
      <ErrorAlert error={mutation.error} />
    </SectionCard>
  )
}

function AssessmentsSection({ propertyId }: { propertyId: number }) {
  const assessments = useApi<Assessment[]>(`/api/properties/${propertyId}/assessments`)
  const [open, setOpen] = useState(false)
  return (
    <SectionCard title="Assessments" subtitle="One assessed value per tax year"
      action={<Button size="small" startIcon={<AddIcon />} onClick={() => setOpen(true)}>Record</Button>} noPadding>
      <QueryState query={assessments}>
        {assessments.data?.length === 0 ? <EmptyState>No assessments yet.</EmptyState> : (
          <Table size="small">
            <TableHead><TableRow><TableCell>Year</TableCell><TableCell>Date</TableCell><TableCell align="right">Assessed value</TableCell></TableRow></TableHead>
            <TableBody>
              {(assessments.data ?? []).map((a) => (
                <TableRow key={a.id}>
                  <TableCell><Mono>{a.taxYear}</Mono></TableCell>
                  <TableCell><Mono>{date(a.assessmentDate)}</Mono></TableCell>
                  <TableCell align="right"><Mono>{money(a.assessedValue)}</Mono></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </QueryState>
      {open && <AssessmentDialog propertyId={propertyId} onClose={() => setOpen(false)} />}
    </SectionCard>
  )
}

function AssessmentDialog({ propertyId, onClose }: { propertyId: number; onClose: () => void }) {
  const [form, setForm] = useState({ taxYear: String(CURRENT_YEAR), assessedValue: '', assessmentDate: today() })
  const save = useApiMutation()
  return (
    <FormDialog open title="Record assessment" submitLabel="Record" pending={save.isPending} error={save.error} onClose={onClose}
      onSubmit={() => save.mutate({
        path: `/api/properties/${propertyId}/assessments`,
        body: { taxYear: Number(form.taxYear), assessedValue: form.assessedValue, assessmentDate: form.assessmentDate },
      }, { onSuccess: onClose })}>
      <TextField autoFocus label="Tax year" type="number" value={form.taxYear} onChange={(e) => setForm({ ...form, taxYear: e.target.value })} required
        error={!!fieldError(save.error, 'taxYear')} helperText={fieldError(save.error, 'taxYear')} />
      <TextField label="Assessed value (CAD)" type="number" value={form.assessedValue} onChange={(e) => setForm({ ...form, assessedValue: e.target.value })} required
        slotProps={{ htmlInput: { min: 0, step: 0.01 } }} error={!!fieldError(save.error, 'assessedValue')} helperText={fieldError(save.error, 'assessedValue')} />
      <TextField label="Assessment date" type="date" value={form.assessmentDate} onChange={(e) => setForm({ ...form, assessmentDate: e.target.value })} required
        slotProps={{ inputLabel: { shrink: true } }} />
    </FormDialog>
  )
}

function AccountSection({ propertyId }: { propertyId: number }) {
  const account = useApi<TaxAccount | null>(`/api/properties/${propertyId}/tax-account`, { allow404: true })
  const open = useApiMutation()
  return (
    <SectionCard title="Tax account">
      <QueryState query={account}>
        {account.data ? (
          <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
            <Box>
              <Mono sx={{ fontWeight: 600 }}>{account.data.accountNumber}</Mono>
              <Box sx={{ mt: 0.5 }}><StatusChip value={account.data.status} /></Box>
            </Box>
            <Button component={RouterLink} to={`/officer/accounts?account=${account.data.id}`} variant="outlined">Bills & payments</Button>
          </Stack>
        ) : (
          <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>No tax account yet.</Typography>
            <Button variant="contained" disabled={open.isPending}
              onClick={() => open.mutate({ path: '/api/tax-accounts', body: { propertyId } })}>Open tax account</Button>
          </Stack>
        )}
      </QueryState>
      <ErrorAlert error={open.error} />
    </SectionCard>
  )
}
