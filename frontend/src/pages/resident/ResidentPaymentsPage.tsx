import { useState } from 'react'
import { Alert, Box, Grid, MenuItem, Paper, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from '@mui/material'
import EventOutlinedIcon from '@mui/icons-material/EventOutlined'
import PaymentsOutlinedIcon from '@mui/icons-material/PaymentsOutlined'
import TaskAltIcon from '@mui/icons-material/TaskAlt'
import UndoIcon from '@mui/icons-material/Undo'
import { date, enumLabel, money } from '../../components/format'
import { EmptyState, Mono, PageHeader, QueryState, SectionCard, StatCard, StatusChip } from '../../components/ui'
import { tokens } from '../../theme'
import { useResidentLedger } from './useResidentLedger'

export function ResidentPaymentsPage() {
  const ledger = useResidentLedger({ withPayments: true })
  const [status, setStatus] = useState<'ALL' | 'RECORDED' | 'REVERSED'>('ALL')

  const payments = [...ledger.payments].sort((a, b) => b.payment.paymentDate.localeCompare(a.payment.paymentDate))
  const recorded = payments.filter((p) => p.payment.status === 'RECORDED')
  const shown = status === 'ALL' ? payments : payments.filter((p) => p.payment.status === status)
  const totalPaid = recorded.reduce((sum, p) => sum + p.payment.amount, 0)
  const outstanding = ledger.rows.reduce((sum, r) => sum + (r.balance?.outstanding ?? 0), 0)
  const dueBills = ledger.rows
    .flatMap((r) => r.bills.filter((b) => b.outstandingAmount > 0 && b.status !== 'CANCELLED').map((bill) => ({ bill, property: r.property })))
    .sort((a, b) => a.bill.dueDate.localeCompare(b.bill.dueDate))

  return (
    <>
      <PageHeader
        eyebrow="Municipal treasury"
        title="Payment history"
        subtitle="Payments recorded against your tax bills. Records are simulated for this demo: no bank accounts or payment gateways are involved."
      />
      {ledger.ownerResolved && !ledger.ownerId ? (
        <Alert severity="info">This resident login isn&apos;t linked to an owner record yet.</Alert>
      ) : (
        <QueryState query={{ isPending: ledger.isPending, error: ledger.error }}>
          <Grid container spacing={2} sx={{ mb: 3 }}>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}><StatCard label="Total paid" value={money(totalPaid)} hint={`${recorded.length} recorded payments`} icon={<TaskAltIcon fontSize="small" />} /></Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}><StatCard label="Outstanding" value={money(outstanding)} tone={outstanding > 0 ? 'danger' : 'default'} hint={`${dueBills.length} bill(s) with a balance`} icon={<EventOutlinedIcon fontSize="small" />} /></Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}><StatCard label="Next due date" value={dueBills[0] ? date(dueBills[0].bill.dueDate) : '—'} hint={dueBills[0]?.property.streetAddress ?? 'Nothing due'} icon={<PaymentsOutlinedIcon fontSize="small" />} /></Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}><StatCard label="Reversed" value={payments.length - recorded.length} hint="Corrections by a tax officer" icon={<UndoIcon fontSize="small" />} /></Grid>
          </Grid>

          {dueBills.length > 0 && (
            <Box sx={{ mb: 3 }}>
              <Typography variant="overline" sx={{ color: 'text.secondary' }}>Upcoming balances</Typography>
              <Grid container spacing={2} sx={{ mt: 0.5 }}>
                {dueBills.map(({ bill, property }) => (
                  <Grid key={bill.id} size={{ xs: 12, sm: 6, lg: 4 }}>
                    <Paper sx={{ p: 2, bgcolor: tokens.panel }}>
                      <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
                        <Mono sx={{ color: 'text.secondary' }}>{bill.billNumber}</Mono>
                        <StatusChip value={bill.billType} />
                      </Stack>
                      <Typography sx={{ fontWeight: 700, mt: 1 }}>{property.streetAddress}</Typography>
                      <Stack direction="row" sx={{ justifyContent: 'space-between', mt: 1 }}>
                        <Mono>Due {date(bill.dueDate)}</Mono>
                        <Mono sx={{ fontWeight: 700, color: tokens.danger }}>{money(bill.outstandingAmount)}</Mono>
                      </Stack>
                    </Paper>
                  </Grid>
                ))}
              </Grid>
            </Box>
          )}

          <SectionCard
            title="Recorded payments"
            subtitle="Immutable audit trail: corrections appear as REVERSED, never deleted"
            action={
              <TextField select size="small" label="Status" value={status} onChange={(e) => setStatus(e.target.value as typeof status)} sx={{ minWidth: 150 }}>
                <MenuItem value="ALL">All</MenuItem><MenuItem value="RECORDED">Recorded</MenuItem><MenuItem value="REVERSED">Reversed</MenuItem>
              </TextField>
            }
            noPadding
          >
            {shown.length === 0 ? <EmptyState>No payments to show.</EmptyState> : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Reference</TableCell><TableCell>Tax bill</TableCell><TableCell>Property</TableCell>
                    <TableCell>Date</TableCell><TableCell>Method</TableCell><TableCell align="right">Amount</TableCell><TableCell>Status</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {shown.map(({ payment, bill, property }) => (
                    <TableRow key={payment.id} sx={{ opacity: payment.status === 'REVERSED' ? 0.6 : 1 }}>
                      <TableCell><Mono>{payment.referenceNumber}</Mono></TableCell>
                      <TableCell><Mono>{bill.billNumber}</Mono><Typography variant="caption" sx={{ display: 'block', color: 'text.secondary' }}>{enumLabel(bill.billType)} {bill.taxYear}</Typography></TableCell>
                      <TableCell>{property.streetAddress}<Typography variant="caption" sx={{ display: 'block', color: 'text.secondary' }}><Mono>{property.rollNumber}</Mono></Typography></TableCell>
                      <TableCell><Mono>{date(payment.paymentDate)}</Mono></TableCell>
                      <TableCell><Mono sx={{ fontSize: '0.75rem' }}>{payment.method}</Mono></TableCell>
                      <TableCell align="right"><Mono sx={{ fontWeight: 600 }}>{money(payment.amount)}</Mono></TableCell>
                      <TableCell><StatusChip value={payment.status} /></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </SectionCard>
        </QueryState>
      )}
    </>
  )
}
