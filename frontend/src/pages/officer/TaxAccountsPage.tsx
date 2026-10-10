import { useState } from 'react'
import { useSearchParams } from 'react-router'
import {
  Accordion, AccordionDetails, AccordionSummary, Box, Button, Drawer, Grid, IconButton, MenuItem, Paper, Stack, Table,
  TableBody, TableCell, TableHead, TablePagination, TableRow, TextField, Tooltip, Typography,
} from '@mui/material'
import CloseIcon from '@mui/icons-material/Close'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import UndoIcon from '@mui/icons-material/Undo'
import { useApi, useApiMutation } from '../../api/client'
import type { AccountBalance, BillType, Page, Payment, PaymentMethod, Property, TaxAccount, TaxBill } from '../../api/types'
import { PAYMENT_METHODS } from '../../api/types'
import { BillPdfButton } from '../../components/BillPdfButton'
import { CURRENT_YEAR, date, enumLabel, money, today } from '../../components/format'
import { EmptyState, ErrorAlert, Mono, PageHeader, QueryState, SectionCard, StatusChip } from '../../components/ui'
import { tokens } from '../../theme'

export function TaxAccountsPage() {
  const [params, setParams] = useSearchParams()
  const selected = Number(params.get('account')) || null
  const [page, setPage] = useState(0)
  const accounts = useApi<Page<TaxAccount>>(`/api/tax-accounts?page=${page}&size=10&sort=id`)
  const rows = accounts.data?.content ?? []

  return (
    <>
      <PageHeader eyebrow="Billing ledger" title="Tax accounts & bills"
        subtitle="One account per property. Balances are derived live from bills minus recorded payments, never stored." />

      <Paper sx={{ overflow: 'hidden' }}>
        <QueryState query={accounts}>
          {rows.length === 0 ? <EmptyState>No tax accounts yet. Open one from a property.</EmptyState> : (
            <Box sx={{ overflowX: 'auto' }}>
              <Table size="small">
                <TableHead><TableRow><TableCell>Account</TableCell><TableCell>Property</TableCell><TableCell>Status</TableCell><TableCell align="right">Outstanding</TableCell></TableRow></TableHead>
                <TableBody>
                  {rows.map((a) => (
                    <TableRow key={a.id} hover selected={a.id === selected} onClick={() => setParams({ account: String(a.id) })}>
                      <TableCell><Mono sx={{ fontWeight: 600 }}>{a.accountNumber}</Mono></TableCell>
                      <TableCell><PropertyCell id={a.propertyId} /></TableCell>
                      <TableCell><StatusChip value={a.status} /></TableCell>
                      <TableCell align="right"><OutstandingCell accountId={a.id} /></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
          )}
          <TablePagination component="div" count={accounts.data?.page.totalElements ?? 0} page={page} rowsPerPage={10} rowsPerPageOptions={[10]} onPageChange={(_, p) => setPage(p)} />
        </QueryState>
      </Paper>

      <Drawer anchor="right" open={selected !== null} onClose={() => setParams({})}
        slotProps={{ paper: { sx: { width: { xs: '100%', md: 640 }, bgcolor: tokens.canvas } } }}>
        {selected !== null && <AccountDetail accountId={selected} onClose={() => setParams({})} />}
      </Drawer>
    </>
  )
}

function PropertyCell({ id }: { id: number }) {
  const property = useApi<Property>(`/api/properties/${id}`)
  if (!property.data) return <Mono>#{id}</Mono>
  return <>{property.data.streetAddress}<Typography variant="caption" sx={{ display: 'block', color: 'text.secondary' }}><Mono>{property.data.rollNumber}</Mono></Typography></>
}

function OutstandingCell({ accountId }: { accountId: number }) {
  const balance = useApi<AccountBalance>(`/api/tax-accounts/${accountId}/balance`)
  const value = balance.data?.outstanding
  return <Mono sx={{ fontWeight: 600, color: value ? tokens.danger : undefined }}>{money(value)}</Mono>
}

function AccountDetail({ accountId, onClose }: { accountId: number; onClose: () => void }) {
  const account = useApi<TaxAccount>(`/api/tax-accounts/${accountId}`)
  const balance = useApi<AccountBalance>(`/api/tax-accounts/${accountId}/balance`)
  const bills = useApi<TaxBill[]>(`/api/tax-accounts/${accountId}/bills`)

  return (
    <Box sx={{ p: { xs: 2, md: 3 } }}>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Box>
          <Typography variant="overline" sx={{ color: 'text.secondary' }}>Municipal ledger account</Typography>
          <Typography variant="h1" sx={{ fontSize: '1.4rem', fontFamily: tokens.mono }}>{account.data?.accountNumber ?? '…'}</Typography>
        </Box>
        <IconButton onClick={onClose} aria-label="Close"><CloseIcon /></IconButton>
      </Stack>

      <QueryState query={account}>
        {account.data && (
          <Stack spacing={2}>
            <Paper sx={{ p: 2 }}>
              <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                <PropertyCell id={account.data.propertyId} />
                <StatusChip value={account.data.status} />
              </Stack>
            </Paper>

            <Grid container spacing={1.5}>
              {([['Total billed', balance.data?.totalBilled], ['Recorded payments', balance.data?.totalPaid], ['Outstanding', balance.data?.outstanding]] as const).map(([label, value]) => (
                <Grid key={label} size={4}>
                  <Paper sx={{ p: 1.5 }}>
                    <Typography variant="overline" sx={{ color: 'text.secondary', fontSize: '0.62rem' }}>{label}</Typography>
                    <Typography sx={{ fontFamily: tokens.mono, fontWeight: 700, color: label === 'Outstanding' && value ? tokens.danger : undefined }}>{money(value)}</Typography>
                  </Paper>
                </Grid>
              ))}
            </Grid>

            {account.data.status === 'ACTIVE' && <GenerateBill accountId={accountId} />}

            <SectionCard title="Bills" subtitle="Expand a bill to record or reverse payments" noPadding>
              <QueryState query={bills}>
                {bills.data?.length === 0 ? <EmptyState>No bills issued yet.</EmptyState> : (
                  (bills.data ?? []).map((bill) => <BillRow key={bill.id} bill={bill} />)
                )}
              </QueryState>
            </SectionCard>
          </Stack>
        )}
      </QueryState>
    </Box>
  )
}

/** The server calculates the amount: interim = 50% of prior-year tax; final = annual − interim. */
function GenerateBill({ accountId }: { accountId: number }) {
  const year = CURRENT_YEAR
  const [form, setForm] = useState({ taxYear: String(year), billType: 'FINAL' as BillType, issueDate: today(), dueDate: `${year}-12-31` })
  const generate = useApiMutation<TaxBill>()
  return (
    <SectionCard title="Issue tax bill" subtitle="Amount is calculated by the server from the assessment and rate">
      <Box component="form" onSubmit={(e) => { e.preventDefault(); generate.mutate({ path: `/api/tax-accounts/${accountId}/bills`, body: { ...form, taxYear: Number(form.taxYear) } }) }}>
        <Grid container spacing={1.5}>
          <Grid size={{ xs: 6, sm: 3 }}><TextField size="small" fullWidth label="Tax year" type="number" value={form.taxYear} onChange={(e) => setForm({ ...form, taxYear: e.target.value })} required /></Grid>
          <Grid size={{ xs: 6, sm: 3 }}>
            <TextField size="small" fullWidth select label="Type" value={form.billType} onChange={(e) => setForm({ ...form, billType: e.target.value as BillType })}>
              <MenuItem value="INTERIM">Interim</MenuItem><MenuItem value="FINAL">Final</MenuItem>
            </TextField>
          </Grid>
          <Grid size={{ xs: 6, sm: 3 }}><TextField size="small" fullWidth label="Issue date" type="date" value={form.issueDate} onChange={(e) => setForm({ ...form, issueDate: e.target.value })} slotProps={{ inputLabel: { shrink: true } }} required /></Grid>
          <Grid size={{ xs: 6, sm: 3 }}><TextField size="small" fullWidth label="Due date" type="date" value={form.dueDate} onChange={(e) => setForm({ ...form, dueDate: e.target.value })} slotProps={{ inputLabel: { shrink: true } }} required /></Grid>
        </Grid>
        <Stack direction="row" sx={{ justifyContent: 'flex-end', mt: 1.5 }}>
          <Button type="submit" variant="contained" disabled={generate.isPending}>Generate bill</Button>
        </Stack>
        <ErrorAlert error={generate.error} />
        {generate.isSuccess && <Typography variant="body2" sx={{ color: tokens.teal, mt: 1 }}>Issued {generate.data.billNumber} for {money(generate.data.amount)}.</Typography>}
      </Box>
    </SectionCard>
  )
}

function BillRow({ bill }: { bill: TaxBill }) {
  const payments = useApi<Payment[]>(`/api/tax-bills/${bill.id}/payments`)
  const action = useApiMutation()
  const open = bill.status === 'ISSUED' || bill.status === 'PARTIALLY_PAID'

  return (
    <Accordion disableGutters elevation={0} sx={{ border: 0, borderBottom: `1px solid ${tokens.border}`, '&:before': { display: 'none' } }}>
      <AccordionSummary expandIcon={<ExpandMoreIcon />}>
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', flexWrap: 'wrap', width: '100%', pr: 1, gap: 1 }}>
          <Mono sx={{ fontWeight: 600, textDecoration: bill.status === 'CANCELLED' ? 'line-through' : undefined }}>{bill.billNumber}</Mono>
          <StatusChip value={bill.billType} />
          <Box sx={{ flexGrow: 1 }} />
          <Mono>{money(bill.amount)}</Mono>
          <StatusChip value={bill.status} />
        </Stack>
      </AccordionSummary>
      <AccordionDetails sx={{ bgcolor: '#fafbfd' }}>
        <Grid container spacing={1} sx={{ mb: 1.5, fontSize: '0.85rem' }}>
          <Grid size={{ xs: 6, sm: 3 }}><Typography variant="overline" sx={{ color: 'text.secondary', display: 'block' }}>Issued</Typography><Mono>{date(bill.issueDate)}</Mono></Grid>
          <Grid size={{ xs: 6, sm: 3 }}><Typography variant="overline" sx={{ color: 'text.secondary', display: 'block' }}>Due</Typography><Mono>{date(bill.dueDate)}</Mono></Grid>
          <Grid size={{ xs: 6, sm: 3 }}><Typography variant="overline" sx={{ color: 'text.secondary', display: 'block' }}>Paid</Typography><Mono>{money(bill.paidAmount)}</Mono></Grid>
          <Grid size={{ xs: 6, sm: 3 }}><Typography variant="overline" sx={{ color: 'text.secondary', display: 'block' }}>Outstanding</Typography><Mono sx={{ color: bill.outstandingAmount ? tokens.danger : undefined }}>{money(bill.outstandingAmount)}</Mono></Grid>
        </Grid>

        <QueryState query={payments}>
          {(payments.data ?? []).length > 0 && (
            <Table size="small" sx={{ mb: 1.5, bgcolor: '#fff' }}>
              <TableHead><TableRow><TableCell>Reference</TableCell><TableCell>Date</TableCell><TableCell>Method</TableCell><TableCell align="right">Amount</TableCell><TableCell>Status</TableCell><TableCell /></TableRow></TableHead>
              <TableBody>
                {(payments.data ?? []).map((p) => (
                  <TableRow key={p.id}>
                    <TableCell><Mono>{p.referenceNumber}</Mono></TableCell>
                    <TableCell><Mono>{date(p.paymentDate)}</Mono></TableCell>
                    <TableCell>{enumLabel(p.method)}</TableCell>
                    <TableCell align="right"><Mono>{money(p.amount)}</Mono></TableCell>
                    <TableCell><StatusChip value={p.status} /></TableCell>
                    <TableCell align="right">
                      {p.status === 'RECORDED' && (
                        <Tooltip title="Reverse payment (kept for audit)">
                          <IconButton size="small" disabled={action.isPending} onClick={() => action.mutate({ path: `/api/payments/${p.id}/reverse` })}><UndoIcon fontSize="small" /></IconButton>
                        </Tooltip>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </QueryState>

        {open && <RecordPayment bill={bill} />}
        <Stack direction="row" sx={{ justifyContent: 'flex-end', alignItems: 'center', mt: 1 }}>
          <BillPdfButton bill={bill} />
          {bill.status !== 'CANCELLED' && (
            <Button size="small" color="error" disabled={action.isPending} onClick={() => action.mutate({ path: `/api/tax-bills/${bill.id}/cancel` })}>Cancel bill</Button>
          )}
        </Stack>
        <ErrorAlert error={action.error} />
      </AccordionDetails>
    </Accordion>
  )
}

function RecordPayment({ bill }: { bill: TaxBill }) {
  const [form, setForm] = useState({ amount: String(bill.outstandingAmount), paymentDate: today(), method: 'BANK_TRANSFER' as PaymentMethod, referenceNumber: '' })
  const record = useApiMutation()
  return (
    <Box component="form" sx={{ p: 1.5, borderRadius: 2, bgcolor: tokens.panel }}
      onSubmit={(e) => {
        e.preventDefault()
        record.mutate({ path: `/api/tax-bills/${bill.id}/payments`, body: form },
          { onSuccess: () => setForm({ ...form, referenceNumber: '' }) })
      }}>
      <Typography variant="overline" sx={{ color: 'text.secondary' }}>Record simulated payment</Typography>
      <Grid container spacing={1} sx={{ mt: 0.5 }}>
        <Grid size={{ xs: 6, sm: 3 }}><TextField size="small" fullWidth label="Amount" type="number" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} slotProps={{ htmlInput: { min: 0.01, step: 0.01 } }} required sx={{ bgcolor: '#fff' }} /></Grid>
        <Grid size={{ xs: 6, sm: 3 }}><TextField size="small" fullWidth label="Date" type="date" value={form.paymentDate} onChange={(e) => setForm({ ...form, paymentDate: e.target.value })} slotProps={{ inputLabel: { shrink: true } }} required sx={{ bgcolor: '#fff' }} /></Grid>
        <Grid size={{ xs: 6, sm: 3 }}>
          <TextField size="small" fullWidth select label="Method" value={form.method} onChange={(e) => setForm({ ...form, method: e.target.value as PaymentMethod })} sx={{ bgcolor: '#fff' }}>
            {PAYMENT_METHODS.map((m) => <MenuItem key={m} value={m}>{enumLabel(m)}</MenuItem>)}
          </TextField>
        </Grid>
        <Grid size={{ xs: 6, sm: 3 }}><TextField size="small" fullWidth label="Reference" value={form.referenceNumber} onChange={(e) => setForm({ ...form, referenceNumber: e.target.value })} required sx={{ bgcolor: '#fff' }} /></Grid>
      </Grid>
      <Stack direction="row" sx={{ justifyContent: 'flex-end', mt: 1 }}>
        <Button type="submit" variant="contained" size="small" disabled={record.isPending}>Record payment</Button>
      </Stack>
      <ErrorAlert error={record.error} />
    </Box>
  )
}
