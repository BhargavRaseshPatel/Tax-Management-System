import { useState } from 'react'
import { Alert, Box, Button, Grid, LinearProgress, MenuItem, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Tooltip, Typography } from '@mui/material'
import RocketLaunchOutlinedIcon from '@mui/icons-material/RocketLaunchOutlined'
import { CURRENT_YEAR } from '../../components/format'
import { Mono, MockBadge, PageHeader, SectionCard, StatusChip } from '../../components/ui'
import { tokens } from '../../theme'

/** Static examples shaped like the planned DynamoDB `report_jobs` items (Stage 12). */
const MOCK_JOBS = [
  { id: 'rpt-9a1f', type: 'Tax roll summary', year: 2026, status: 'COMPLETED', progress: 100, dispatched: '2026-10-08 11:30', s3Key: 'reports/2026/tax-roll-9a1f.pdf' },
  { id: 'rpt-88b2', type: 'Outstanding balances', year: 2026, status: 'PROCESSING', progress: 68, dispatched: '2026-10-09 09:05', s3Key: null },
  { id: 'rpt-77c4', type: 'Payments reconciliation', year: 2025, status: 'COMPLETED', progress: 100, dispatched: '2026-10-07 16:15', s3Key: 'reports/2025/payments-77c4.csv' },
  { id: 'rpt-43b9', type: 'Assessment changes', year: 2026, status: 'FAILED', progress: 40, dispatched: '2026-10-07 08:05', s3Key: null, error: 'Worker timeout after 120 s (simulated)' },
] as const

export function ReportsPage() {
  const [type, setType] = useState('Tax roll summary')
  return (
    <>
      <PageHeader eyebrow="Reporting" title="Reports & audits"
        subtitle="Asynchronous report generation: request → job ID → worker builds the file → uploaded to S3 → job status updated."
        actions={<MockBadge stage="Stages 11–13 (S3, DynamoDB, Step Functions)" />} />

      <Alert severity="info" sx={{ mb: 2.5 }}>
        Report jobs are not wired yet. This page shows the planned workflow with static example jobs, so the UI is ready when the backend lands.
      </Alert>

      <Grid container spacing={2.5}>
        <Grid size={{ xs: 12, lg: 4 }}>
          <SectionCard title="Request a report" subtitle="Creates a job and returns its ID immediately">
            <Stack spacing={2}>
              <TextField select label="Report" value={type} onChange={(e) => setType(e.target.value)}>
                {['Tax roll summary', 'Outstanding balances', 'Payments reconciliation', 'Assessment changes'].map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
              </TextField>
              <TextField label="Tax year" type="number" defaultValue={CURRENT_YEAR} />
              <Tooltip title="Enabled in Stage 13">
                <span><Button fullWidth variant="contained" startIcon={<RocketLaunchOutlinedIcon />} disabled>Dispatch report job</Button></span>
              </Tooltip>
            </Stack>
          </SectionCard>
        </Grid>
        <Grid size={{ xs: 12, lg: 8 }}>
          <SectionCard title="Job tracker" subtitle="Job state will live in DynamoDB report_jobs; output files in S3" action={<MockBadge stage="Stage 12" />} noPadding>
            <Box sx={{ overflowX: 'auto' }}>
              <Table size="small">
                <TableHead><TableRow><TableCell>Job</TableCell><TableCell>Report</TableCell><TableCell>Dispatched</TableCell><TableCell>Status</TableCell><TableCell>Output</TableCell></TableRow></TableHead>
                <TableBody>
                  {MOCK_JOBS.map((j) => (
                    <TableRow key={j.id} sx={{ bgcolor: j.status === 'FAILED' ? '#fffafa' : undefined }}>
                      <TableCell><Mono sx={{ fontWeight: 600 }}>{j.id}</Mono></TableCell>
                      <TableCell>{j.type}<Typography variant="caption" sx={{ display: 'block', color: 'text.secondary' }}>Tax year {j.year}</Typography></TableCell>
                      <TableCell><Mono>{j.dispatched}</Mono></TableCell>
                      <TableCell sx={{ minWidth: 140 }}>
                        <StatusChip value={j.status} />
                        {j.status === 'PROCESSING' && <LinearProgress variant="determinate" value={j.progress} sx={{ mt: 0.75, borderRadius: 1 }} />}
                        {'error' in j && <Typography variant="caption" sx={{ display: 'block', color: tokens.danger }}>{j.error}</Typography>}
                      </TableCell>
                      <TableCell>{j.s3Key ? <Mono sx={{ fontSize: '0.72rem' }}>s3://…/{j.s3Key}</Mono> : '—'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
          </SectionCard>
        </Grid>
      </Grid>
    </>
  )
}
