import { Box, Chip, Grid, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import CloudOutlinedIcon from '@mui/icons-material/CloudOutlined'
import DnsOutlinedIcon from '@mui/icons-material/DnsOutlined'
import HomeWorkOutlinedIcon from '@mui/icons-material/HomeWorkOutlined'
import ManageAccountsOutlinedIcon from '@mui/icons-material/ManageAccountsOutlined'
import { useApi } from '../../api/client'
import type { Owner, Page, Property, TaxAccount, User } from '../../api/types'
import { count } from '../../components/format'
import { Mono, PageHeader, SectionCard, StatCard } from '../../components/ui'
import { tokens } from '../../theme'

/** The nine tables and their real constraints (from V1__initial_schema.sql). */
const SCHEMA = [
  { table: 'users', rule: 'UNIQUE lowercase email · role CHECK (RESIDENT, TAX_OFFICER, ADMIN)', countKey: 'users' },
  { table: 'owners', rule: 'Optional 1:1 → users.id (UNIQUE, nullable)', countKey: 'owners' },
  { table: 'properties', rule: 'UNIQUE roll_number · postal code format · class CHECK', countKey: 'properties' },
  { table: 'property_owners', rule: 'UNIQUE (property_id, owner_id) · share 0–100; total ≤ 100% (service)' },
  { table: 'assessments', rule: 'UNIQUE (property_id, tax_year) · value ≥ 0' },
  { table: 'tax_rates', rule: 'UNIQUE (tax_year, property_class) · 0 < rate < 1' },
  { table: 'tax_accounts', rule: '1:1 UNIQUE property_id · status CHECK', countKey: 'accounts' },
  { table: 'tax_bills', rule: 'Partial UNIQUE (account, year, type) WHERE status ≠ CANCELLED' },
  { table: 'payments', rule: 'UNIQUE reference_number · amount > 0 · REVERSED, never deleted' },
] as const

const INTEGRATIONS = [
  { name: 'Spring Boot REST API', detail: 'Validation, ProblemDetail errors, OpenAPI, JWT security', stage: 'Running', live: true },
  { name: 'PostgreSQL 17 + Flyway', detail: 'Nine-table relational schema', stage: 'Running', live: true },
  { name: 'Amazon S3', detail: 'Tax-bill PDFs and report files', stage: 'Running (local emulator)', live: true },
  { name: 'DynamoDB report_jobs', detail: 'Asynchronous report job state', stage: 'Running (local emulator)', live: true },
  { name: 'Step Functions + report worker', detail: 'Orchestration, retries, failure handling', stage: 'Running (local emulator)', live: true },
  { name: 'Amazon Cognito', detail: 'Hosted sign-in; a local token issuer stands in for it', stage: 'Config switch' },
  { name: 'Aurora PostgreSQL, ECS Fargate, ALB, CloudFront', detail: 'AWS deployment', stage: 'Templates / later stages' },
]

export function AdminOverviewPage() {
  const health = useApi<{ status: string }>('/actuator/health')
  const counts = {
    users: useApi<Page<User>>('/api/users?size=1').data?.page.totalElements,
    owners: useApi<Page<Owner>>('/api/owners?size=1').data?.page.totalElements,
    properties: useApi<Page<Property>>('/api/properties?size=1').data?.page.totalElements,
    accounts: useApi<Page<TaxAccount>>('/api/tax-accounts?size=1').data?.page.totalElements,
  }
  const up = health.data?.status === 'UP'

  return (
    <>
      <PageHeader eyebrow="System administration" title="System health & overview"
        subtitle="Live backend status, the relational schema with its enforced constraints, and the cloud integration roadmap." />

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}><StatCard label="API status" value={health.isPending ? '…' : up ? 'UP' : 'DOWN'} tone={up || health.isPending ? 'default' : 'danger'} hint="Spring Boot Actuator /health" icon={<DnsOutlinedIcon fontSize="small" />} /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}><StatCard label="User accounts" value={count(counts.users)} hint="Residents, officers, admins" icon={<ManageAccountsOutlinedIcon fontSize="small" />} /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}><StatCard label="Properties on roll" value={count(counts.properties)} hint={`${count(counts.accounts)} tax accounts`} icon={<HomeWorkOutlinedIcon fontSize="small" />} /></Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}><StatCard label="Cloud services" value="5 / 7" hint="Running locally; AWS deployment pending" icon={<CloudOutlinedIcon fontSize="small" />} /></Grid>
      </Grid>

      <Stack spacing={2.5}>
        <SectionCard title="Relational schema & integrity rules" subtitle="PostgreSQL 17 · exactly nine tables · constraints enforced by the database" noPadding>
          <Box sx={{ overflowX: 'auto' }}>
            <Table size="small">
              <TableHead><TableRow><TableCell>Table</TableCell><TableCell>Key constraints & invariants</TableCell><TableCell align="right">Rows</TableCell></TableRow></TableHead>
              <TableBody>
                {SCHEMA.map((s) => (
                  <TableRow key={s.table}>
                    <TableCell><Mono sx={{ fontWeight: 600 }}>● {s.table}</Mono></TableCell>
                    <TableCell sx={{ color: 'text.secondary' }}>{s.rule}</TableCell>
                    <TableCell align="right"><Mono>{'countKey' in s ? count(counts[s.countKey]) : '—'}</Mono></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Box>
          <Typography variant="caption" sx={{ display: 'block', px: 2.5, py: 1.5, color: 'text.secondary' }}>
            Row counts appear where the API exposes a total. No <Mono>tax_adjustments</Mono> table by design.
          </Typography>
        </SectionCard>

        <SectionCard title="Integration roadmap" subtitle="What is running today versus scheduled for later stages">
          <Grid container spacing={1.5}>
            {INTEGRATIONS.map((i) => (
              <Grid key={i.name} size={{ xs: 12, md: 6 }}>
                <Stack direction="row" spacing={1.5} sx={{ p: 1.5, borderRadius: 2, bgcolor: tokens.panel, alignItems: 'center' }}>
                  <Box sx={{ flexGrow: 1 }}>
                    <Typography sx={{ fontWeight: 600 }}>{i.name}</Typography>
                    <Typography variant="body2" sx={{ color: 'text.secondary' }}>{i.detail}</Typography>
                  </Box>
                  <Chip size="small" label={i.stage} sx={i.live ? { bgcolor: tokens.mint, color: tokens.teal } : { bgcolor: '#fff', color: tokens.muted, border: `1px solid ${tokens.border}` }} />
                </Stack>
              </Grid>
            ))}
          </Grid>
        </SectionCard>
      </Stack>
    </>
  )
}
