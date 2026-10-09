import { Link as RouterLink } from 'react-router'
import { Box, Button, Grid, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import ArrowForwardIcon from '@mui/icons-material/ArrowForward'
import HomeWorkOutlinedIcon from '@mui/icons-material/HomeWorkOutlined'
import PeopleOutlineIcon from '@mui/icons-material/PeopleOutlined'
import PercentIcon from '@mui/icons-material/Percent'
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined'
import { useApi } from '../../api/client'
import type { Owner, Page, Property, TaxAccount, TaxRate } from '../../api/types'
import { count, ratePercent } from '../../components/format'
import { EmptyState, Mono, PageHeader, QueryState, SectionCard, StatCard, StatusChip } from '../../components/ui'
import { tokens } from '../../theme'

const WORKFLOW = [
  { step: 'Register owner', detail: 'Optionally link a resident login', to: '/officer/owners' },
  { step: 'Register property', detail: 'Roll number, address, class', to: '/officer/properties' },
  { step: 'Assign ownership', detail: 'Shares may not exceed 100%', to: '/officer/properties' },
  { step: 'Record assessment', detail: 'One assessed value per tax year', to: '/officer/properties' },
  { step: 'Enact tax rate', detail: 'One rate per year and class', to: '/officer/rates' },
  { step: 'Open account & bill', detail: 'Interim, then final; amounts calculated by the server', to: '/officer/accounts' },
  { step: 'Record payment', detail: 'Simulated; overpayments rejected', to: '/officer/accounts' },
]

export function OfficerOverviewPage() {
  const properties = useApi<Page<Property>>('/api/properties?size=1')
  const owners = useApi<Page<Owner>>('/api/owners?size=1')
  const accounts = useApi<Page<TaxAccount>>('/api/tax-accounts?size=1')
  const rates = useApi<TaxRate[]>('/api/tax-rates')
  const latestYear = Math.max(0, ...(rates.data ?? []).map((r) => r.taxYear))
  const currentRates = (rates.data ?? []).filter((r) => r.taxYear === latestYear)

  return (
    <>
      <PageHeader eyebrow="Officer administration" title="Assessment & tax roll overview"
        subtitle="Manage the municipal tax roll from property registration to payment. All figures come live from the API." />

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}><StatCard label="Registered properties" value={count(properties.data?.page.totalElements)} icon={<HomeWorkOutlinedIcon fontSize="small" />} hint="Assessment roll parcels" /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}><StatCard label="Taxpayer owners" value={count(owners.data?.page.totalElements)} icon={<PeopleOutlineIcon fontSize="small" />} hint="Individuals and corporations" /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}><StatCard label="Tax accounts" value={count(accounts.data?.page.totalElements)} icon={<ReceiptLongOutlinedIcon fontSize="small" />} hint="One per property" /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}><StatCard label="Rate schedule" value={latestYear || '—'} icon={<PercentIcon fontSize="small" />} hint={`${currentRates.length} class rate(s) enacted`} /></Grid>
      </Grid>

      <Grid container spacing={2.5}>
        <Grid size={{ xs: 12, lg: 7 }}>
          <SectionCard title="Billing workflow" subtitle="Each step maps to a backend rule; the server rejects anything out of order">
            <Stack spacing={1}>
              {WORKFLOW.map((w, i) => (
                <Stack key={w.step} direction="row" spacing={2} sx={{ alignItems: 'center', p: 1.25, borderRadius: 2, '&:hover': { bgcolor: tokens.panel } }}>
                  <Box sx={{ width: 28, height: 28, borderRadius: '50%', bgcolor: tokens.navy, color: '#fff', display: 'grid', placeItems: 'center', fontSize: '0.8rem', fontWeight: 700, flexShrink: 0 }}>{i + 1}</Box>
                  <Box sx={{ flexGrow: 1 }}>
                    <Typography sx={{ fontWeight: 600 }}>{w.step}</Typography>
                    <Typography variant="body2" sx={{ color: 'text.secondary' }}>{w.detail}</Typography>
                  </Box>
                  <Button component={RouterLink} to={w.to} size="small" endIcon={<ArrowForwardIcon />}>Open</Button>
                </Stack>
              ))}
            </Stack>
          </SectionCard>
        </Grid>
        <Grid size={{ xs: 12, lg: 5 }}>
          <SectionCard title={`${latestYear || ''} tax rates`} subtitle="Fictional demo rates, not official municipal rates" noPadding>
            <QueryState query={rates}>
              {currentRates.length === 0 ? <EmptyState>No rates enacted yet.</EmptyState> : (
                <Table size="small">
                  <TableHead><TableRow><TableCell>Class</TableCell><TableCell align="right">Rate</TableCell><TableCell align="right">Per $1,000</TableCell></TableRow></TableHead>
                  <TableBody>
                    {currentRates.map((r) => (
                      <TableRow key={r.id}>
                        <TableCell><StatusChip value={r.propertyClass} /></TableCell>
                        <TableCell align="right"><Mono>{ratePercent(r.rate)}</Mono></TableCell>
                        <TableCell align="right"><Mono>${(r.rate * 1000).toFixed(2)}</Mono></TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </QueryState>
          </SectionCard>
        </Grid>
      </Grid>
    </>
  )
}
