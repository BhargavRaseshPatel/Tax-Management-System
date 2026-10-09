import { useState, type ReactNode } from 'react'
import { Alert, Box, Chip, Grid, Paper, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import AccountBalanceOutlinedIcon from '@mui/icons-material/AccountBalanceOutlined'
import HomeWorkOutlinedIcon from '@mui/icons-material/HomeWorkOutlined'
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined'
import WarningAmberIcon from '@mui/icons-material/WarningAmber'
import { date, money } from '../../components/format'
import { EmptyState, Mono, PageHeader, QueryState, SectionCard, StatCard, StatusChip } from '../../components/ui'
import { tokens } from '../../theme'
import { useResidentLedger, type ResidentProperty } from './useResidentLedger'

export function ResidentPropertiesPage() {
  const ledger = useResidentLedger()
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const rows = ledger.rows
  const selected = rows.find((r) => r.property.id === selectedId) ?? rows[0]

  const latestYear = Math.max(0, ...rows.flatMap((r) => r.bills.filter((b) => b.status !== 'CANCELLED').map((b) => b.taxYear)))
  const billedThisYear = rows.flatMap((r) => r.bills).filter((b) => b.taxYear === latestYear && b.status !== 'CANCELLED')
    .reduce((sum, b) => sum + b.amount, 0)
  const outstanding = rows.reduce((sum, r) => sum + (r.balance?.outstanding ?? 0), 0)
  const assessedTotal = rows.reduce((sum, r) => sum + (r.assessments[0]?.assessedValue ?? 0), 0)
  const nextDue = rows.flatMap((r) => r.bills).filter((b) => b.outstandingAmount > 0)
    .sort((a, b) => a.dueDate.localeCompare(b.dueDate))[0]

  return (
    <>
      <PageHeader
        eyebrow="Resident services"
        title="My property portfolio"
        subtitle={ledger.owner
          ? <>Properties registered to owner record <Mono>#{ledger.owner.id}</Mono>, with assessments, tax bills, and balances.</>
          : 'Your registered properties, assessments, tax bills, and balances.'}
      />

      {ledger.ownerResolved && !ledger.owner ? (
        <Alert severity="info">This resident login isn&apos;t linked to an owner record yet. A tax officer can link it from the Owners page.</Alert>
      ) : (
        <QueryState query={{ isPending: ledger.isPending, error: ledger.error }}>
          <Grid container spacing={2} sx={{ mb: 3 }}>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <StatCard label="Assessed value" value={money(assessedTotal)} hint="Latest assessment, all parcels" icon={<AccountBalanceOutlinedIcon fontSize="small" />} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <StatCard label="Registered parcels" value={rows.length}
                hint={`${rows.filter((r) => r.share === 100).length} sole · ${rows.filter((r) => (r.share ?? 100) < 100).length} co-owned`}
                icon={<HomeWorkOutlinedIcon fontSize="small" />} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <StatCard label={latestYear ? `${latestYear} levy billed` : 'Levy billed'} value={money(billedThisYear)} hint="Interim + final bills" icon={<ReceiptLongOutlinedIcon fontSize="small" />} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <StatCard label="Outstanding" value={money(outstanding)} tone={outstanding > 0 ? 'danger' : 'default'}
                hint={nextDue ? `Next due ${date(nextDue.dueDate)}` : 'Nothing due'} icon={<WarningAmberIcon fontSize="small" />} />
            </Grid>
          </Grid>

          {rows.length === 0 ? <EmptyState>No properties are registered to you.</EmptyState> : (
            <Grid container spacing={2.5}>
              <Grid size={{ xs: 12, md: 5 }}>
                <Typography variant="overline" sx={{ color: 'text.secondary' }}>Registered parcels ({rows.length})</Typography>
                <Stack spacing={1.5} sx={{ mt: 1 }}>
                  {rows.map((row) => (
                    <PropertyCard key={row.property.id} row={row} selected={row === selected} onSelect={() => setSelectedId(row.property.id)} />
                  ))}
                </Stack>
              </Grid>
              <Grid size={{ xs: 12, md: 7 }}>
                {selected && <PropertyDetail row={selected} />}
              </Grid>
            </Grid>
          )}
        </QueryState>
      )}
    </>
  )
}

function PropertyCard({ row, selected, onSelect }: { row: ResidentProperty; selected: boolean; onSelect: () => void }) {
  const due = row.balance?.outstanding ?? 0
  return (
    <Paper
      component="button"
      onClick={onSelect}
      sx={{
        p: 2, textAlign: 'left', width: '100%', cursor: 'pointer', font: 'inherit', bgcolor: 'background.paper',
        borderColor: selected ? tokens.navy : tokens.border, borderWidth: selected ? 2 : 1,
        '&:hover': { borderColor: tokens.navy },
      }}
    >
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
        <Mono sx={{ color: 'text.secondary' }}>ROLL {row.property.rollNumber}</Mono>
        <Chip size="small" label={row.share === 100 ? '100% sole' : `${row.share ?? '—'}% co-owned`}
          sx={{ bgcolor: row.share === 100 ? tokens.mint : tokens.panel, color: row.share === 100 ? tokens.teal : tokens.navy }} />
      </Stack>
      <Typography sx={{ fontWeight: 700, fontSize: '1.1rem', mt: 1 }}>{row.property.streetAddress}</Typography>
      <Typography variant="body2" sx={{ color: 'text.secondary' }}>{row.property.city}, {row.property.postalCode}</Typography>
      <Box sx={{ mt: 1.5, p: 1.25, borderRadius: 2, bgcolor: tokens.panel, display: 'grid', gridTemplateColumns: 'auto 1fr', gap: 0.5, fontSize: '0.8rem' }}>
        <Box sx={{ color: 'text.secondary' }}>ASSESSED</Box>
        <Mono sx={{ textAlign: 'right' }}>{money(row.assessments[0]?.assessedValue)}</Mono>
        <Box sx={{ color: due > 0 ? tokens.danger : 'text.secondary' }}>OUTSTANDING</Box>
        <Mono sx={{ textAlign: 'right', color: due > 0 ? tokens.danger : undefined }}>{money(due)}</Mono>
      </Box>
    </Paper>
  )
}

function PropertyDetail({ row }: { row: ResidentProperty }) {
  const { property, account, assessments, bills } = row
  return (
    <Stack spacing={2.5}>
      <Paper sx={{ p: 2.5 }}>
        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
          <StatusChip value={property.propertyClass} />
          {account && <StatusChip value={account.status} />}
        </Stack>
        <Typography variant="h1" sx={{ mt: 1.5, fontSize: '1.6rem' }}>{property.streetAddress}</Typography>
        <Typography sx={{ color: 'text.secondary' }}>{property.city}, {property.postalCode}</Typography>
        <Box sx={{ mt: 2, p: 2, borderRadius: 2, bgcolor: tokens.panel, display: 'grid', gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(4, 1fr)' }, gap: 2 }}>
          <Fact label="Roll number" value={<Mono>{property.rollNumber}</Mono>} />
          <Fact label="Tax account" value={<Mono>{account?.accountNumber ?? 'Not opened'}</Mono>} />
          <Fact label="Latest assessment" value={assessments[0] ? `${assessments[0].taxYear}` : '—'} />
          <Fact label="Your share" value={row.share != null ? `${row.share}%` : '—'} />
        </Box>
      </Paper>

      <SectionCard title="Assessment history" subtitle="Assessed values set by the assessor's office" noPadding>
        {assessments.length === 0 ? <EmptyState>No assessments recorded.</EmptyState> : (
          <Table size="small">
            <TableHead><TableRow><TableCell>Tax year</TableCell><TableCell>Assessment date</TableCell><TableCell align="right">Assessed value</TableCell></TableRow></TableHead>
            <TableBody>
              {assessments.map((a) => (
                <TableRow key={a.id}>
                  <TableCell><Mono>{a.taxYear}</Mono></TableCell>
                  <TableCell><Mono>{date(a.assessmentDate)}</Mono></TableCell>
                  <TableCell align="right"><Mono>{money(a.assessedValue)}</Mono></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </SectionCard>

      <SectionCard title="Tax bills" subtitle="Interim and final bills for this property" noPadding>
        {bills.length === 0 ? <EmptyState>No bills issued yet.</EmptyState> : <BillsTable bills={bills} />}
      </SectionCard>
    </Stack>
  )
}

function Fact({ label, value }: { label: string; value: ReactNode }) {
  return (
    <Box>
      <Typography variant="overline" sx={{ color: 'text.secondary', display: 'block' }}>{label}</Typography>
      <Typography sx={{ fontWeight: 600, fontSize: '0.92rem' }}>{value}</Typography>
    </Box>
  )
}

export function BillsTable({ bills }: { bills: ResidentProperty['bills'] }) {
  return (
    <Table size="small">
      <TableHead>
        <TableRow>
          <TableCell>Bill</TableCell><TableCell>Type</TableCell><TableCell>Due</TableCell>
          <TableCell align="right">Amount</TableCell><TableCell align="right">Outstanding</TableCell><TableCell>Status</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {bills.map((b) => (
          <TableRow key={b.id} sx={{ opacity: b.status === 'CANCELLED' ? 0.55 : 1 }}>
            <TableCell><Mono>{b.billNumber}</Mono></TableCell>
            <TableCell><StatusChip value={b.billType} /></TableCell>
            <TableCell><Mono>{date(b.dueDate)}</Mono></TableCell>
            <TableCell align="right"><Mono>{money(b.amount)}</Mono></TableCell>
            <TableCell align="right"><Mono sx={{ color: b.outstandingAmount > 0 ? tokens.danger : undefined }}>{money(b.outstandingAmount)}</Mono></TableCell>
            <TableCell><StatusChip value={b.status} /></TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
