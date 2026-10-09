import { useState } from 'react'
import {
  Box, Button, Grid, IconButton, MenuItem, Paper, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField,
  ToggleButton, ToggleButtonGroup, Tooltip, Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import { fieldError, useApi, useApiMutation } from '../../api/client'
import type { PropertyClass, TaxRate } from '../../api/types'
import { PROPERTY_CLASSES } from '../../api/types'
import { CURRENT_YEAR, enumLabel, ratePercent } from '../../components/format'
import { EmptyState, FormDialog, Mono, PageHeader, QueryState, SectionCard, StatusChip } from '../../components/ui'
import { tokens } from '../../theme'

export function TaxRatesPage() {
  const rates = useApi<TaxRate[]>('/api/tax-rates')
  const years = [...new Set((rates.data ?? []).map((r) => r.taxYear))].sort((a, b) => b - a)
  const [year, setYear] = useState<number | null>(null)
  const selectedYear = year ?? years[0] ?? null
  const ofYear = (rates.data ?? []).filter((r) => r.taxYear === selectedYear)
  const [editing, setEditing] = useState<TaxRate | 'new' | null>(null)

  return (
    <>
      <PageHeader eyebrow="Levy engine" title="Municipal tax rates"
        subtitle={<>One rate per property class per tax year. Annual tax = assessed value × rate, calculated by the server when a bill is generated. <b>Rates shown are fictional demo values.</b></>}
        actions={<Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing('new')}>Enact new rate</Button>} />

      <QueryState query={rates}>
        {years.length === 0 ? <Paper sx={{ p: 4 }}><EmptyState>No rates enacted yet.</EmptyState></Paper> : (
          <>
            <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 2, flexWrap: 'wrap', gap: 1 }}>
              <Typography variant="overline" sx={{ color: 'text.secondary' }}>Fiscal year</Typography>
              <ToggleButtonGroup size="small" exclusive value={selectedYear} onChange={(_, y: number | null) => y && setYear(y)}>
                {years.map((y) => <ToggleButton key={y} value={y} sx={{ px: 2, '&.Mui-selected': { bgcolor: tokens.navy, color: '#fff' }, '&.Mui-selected:hover': { bgcolor: tokens.navy } }}>{y}</ToggleButton>)}
              </ToggleButtonGroup>
            </Stack>

            <Grid container spacing={2} sx={{ mb: 3 }}>
              {PROPERTY_CLASSES.map((cls) => {
                const rate = ofYear.find((r) => r.propertyClass === cls)
                return (
                  <Grid key={cls} size={{ xs: 12, md: 4 }}>
                    <Paper sx={{ p: 2.5, height: '100%' }}>
                      <StatusChip value={cls} />
                      <Typography variant="overline" sx={{ display: 'block', color: 'text.secondary', mt: 1.5 }}>Rate</Typography>
                      <Typography sx={{ fontSize: '1.9rem', fontWeight: 700, fontFamily: tokens.mono, letterSpacing: '-0.02em' }}>{rate ? rate.rate.toFixed(8) : '—'}</Typography>
                      <Typography variant="body2" sx={{ color: rate ? tokens.teal : 'text.secondary' }}>
                        {rate ? <>{ratePercent(rate.rate)} · ${(rate.rate * 1000).toFixed(2)} per $1,000 assessed</> : `Not enacted for ${selectedYear}`}
                      </Typography>
                    </Paper>
                  </Grid>
                )
              })}
            </Grid>

            <SectionCard title="Rate schedule" subtitle="All enacted rates, newest year first. A rate used by a live bill is locked." noPadding>
              <Box sx={{ overflowX: 'auto' }}>
                <Table size="small">
                  <TableHead><TableRow><TableCell>Year</TableCell><TableCell>Class</TableCell><TableCell align="right">Rate</TableCell><TableCell align="right">%</TableCell><TableCell>Description</TableCell><TableCell /></TableRow></TableHead>
                  <TableBody>
                    {(rates.data ?? []).map((r) => (
                      <TableRow key={r.id} selected={r.taxYear === selectedYear}>
                        <TableCell><Mono>{r.taxYear}</Mono></TableCell>
                        <TableCell><StatusChip value={r.propertyClass} /></TableCell>
                        <TableCell align="right"><Mono>{r.rate.toFixed(8)}</Mono></TableCell>
                        <TableCell align="right"><Mono>{ratePercent(r.rate)}</Mono></TableCell>
                        <TableCell>{r.description ?? '—'}</TableCell>
                        <TableCell align="right"><Tooltip title="Revise rate"><IconButton size="small" onClick={() => setEditing(r)}><EditOutlinedIcon fontSize="small" /></IconButton></Tooltip></TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Box>
            </SectionCard>
          </>
        )}
      </QueryState>

      {editing && <RateDialog rate={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </>
  )
}

function RateDialog({ rate, onClose }: { rate: TaxRate | null; onClose: () => void }) {
  const [form, setForm] = useState({
    taxYear: String(rate?.taxYear ?? CURRENT_YEAR), propertyClass: (rate?.propertyClass ?? 'RESIDENTIAL') as PropertyClass,
    rate: rate ? String(rate.rate) : '', description: rate?.description ?? '',
  })
  const save = useApiMutation()
  const description = form.description.trim() || null
  return (
    <FormDialog open title={rate ? `Revise ${rate.taxYear} ${enumLabel(rate.propertyClass)} rate` : 'Enact new tax rate'}
      submitLabel={rate ? 'Save revision' : 'Enact rate'} pending={save.isPending} error={save.error} onClose={onClose}
      onSubmit={() => save.mutate(rate
        ? { path: `/api/tax-rates/${rate.id}`, method: 'PUT', body: { rate: form.rate, description } }
        : { path: '/api/tax-rates', body: { taxYear: Number(form.taxYear), propertyClass: form.propertyClass, rate: form.rate, description } },
      { onSuccess: onClose })}>
      {!rate && (
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
          <TextField label="Tax year" type="number" value={form.taxYear} onChange={(e) => setForm({ ...form, taxYear: e.target.value })} required fullWidth />
          <TextField select label="Property class" value={form.propertyClass} onChange={(e) => setForm({ ...form, propertyClass: e.target.value as PropertyClass })} fullWidth>
            {PROPERTY_CLASSES.map((c) => <MenuItem key={c} value={c}>{enumLabel(c)}</MenuItem>)}
          </TextField>
        </Stack>
      )}
      <TextField autoFocus label="Rate (fraction, e.g. 0.0078241 = 0.78241%)" type="number" value={form.rate} onChange={(e) => setForm({ ...form, rate: e.target.value })} required
        slotProps={{ htmlInput: { step: 0.00000001, min: 0, max: 1 } }} error={!!fieldError(save.error, 'rate')} helperText={fieldError(save.error, 'rate')} />
      <TextField label="Description" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder="e.g. Demo residential rate (fictional)" />
    </FormDialog>
  )
}
