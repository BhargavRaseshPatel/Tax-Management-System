import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Box, Button, CircularProgress, Grid, IconButton, LinearProgress, MenuItem, Stack, Table, TableBody, TableCell,
  TableHead, TableRow, TextField, Tooltip, Typography,
} from '@mui/material'
import DownloadOutlinedIcon from '@mui/icons-material/DownloadOutlined'
import RocketLaunchOutlinedIcon from '@mui/icons-material/RocketLaunchOutlined'
import { apiQuery, downloadFile, useApiMutation } from '../../api/client'
import type { ReportJob, ReportType } from '../../api/types'
import { REPORT_TYPES } from '../../api/types'
import { CURRENT_YEAR, enumLabel } from '../../components/format'
import { EmptyState, ErrorAlert, Mono, PageHeader, QueryState, SectionCard, StatusChip } from '../../components/ui'
import { tokens } from '../../theme'

const isActive = (job: ReportJob) => job.status === 'QUEUED' || job.status === 'PROCESSING'

/**
 * Asynchronous reports: the request returns a job at once; a background worker builds the CSV, stores it in S3
 * and updates the job in DynamoDB. The list refreshes every 2 s while any job is still running.
 */
export function ReportsPage() {
  const [type, setType] = useState<ReportType>('TAX_ROLL_SUMMARY')
  const [taxYear, setTaxYear] = useState(String(CURRENT_YEAR))
  const request = useApiMutation<ReportJob>()
  const jobs = useQuery({
    ...apiQuery<ReportJob[]>('/api/reports'),
    refetchInterval: (query) => (query.state.data?.some(isActive) ? 2000 : false),
  })

  return (
    <>
      <PageHeader eyebrow="Reporting" title="Reports & audits"
        subtitle="Reports are generated in the background: request → job ID → worker builds the file → stored in S3 → job status updated in DynamoDB. Step Functions retries a failed attempt twice before marking the job failed." />

      <Grid container spacing={2.5}>
        <Grid size={{ xs: 12, lg: 4 }}>
          <SectionCard title="Request a report" subtitle="Returns immediately with a job ID">
            <Stack component="form" spacing={2}
              onSubmit={(e) => { e.preventDefault(); request.mutate({ path: '/api/reports', body: { type, taxYear: Number(taxYear) } }) }}>
              <TextField select label="Report" value={type} onChange={(e) => setType(e.target.value as ReportType)}>
                {REPORT_TYPES.map((t) => <MenuItem key={t} value={t}>{enumLabel(t)}</MenuItem>)}
              </TextField>
              <TextField label="Tax year" type="number" value={taxYear} onChange={(e) => setTaxYear(e.target.value)} required
                slotProps={{ htmlInput: { min: 2000, max: 2100 } }} />
              <Button type="submit" variant="contained" startIcon={<RocketLaunchOutlinedIcon />} disabled={request.isPending}>
                {request.isPending ? 'Requesting…' : 'Request report'}
              </Button>
              <ErrorAlert error={request.error} />
              {request.isSuccess && (
                <Typography variant="body2" sx={{ color: tokens.teal }}>Job <Mono>{request.data.jobId.slice(0, 8)}</Mono> queued.</Typography>
              )}
            </Stack>
          </SectionCard>
        </Grid>

        <Grid size={{ xs: 12, lg: 8 }}>
          <SectionCard title="My report jobs" subtitle="Newest first. Job state lives in DynamoDB; finished files in S3" noPadding>
            <QueryState query={jobs}>
              {jobs.data?.length === 0 ? <EmptyState>No reports requested yet.</EmptyState> : (
                <Box sx={{ overflowX: 'auto' }}>
                  <Table size="small">
                    <TableHead><TableRow><TableCell>Job</TableCell><TableCell>Report</TableCell><TableCell>Requested</TableCell><TableCell>Status</TableCell><TableCell>File</TableCell></TableRow></TableHead>
                    <TableBody>
                      {(jobs.data ?? []).map((job) => <JobRow key={job.jobId} job={job} />)}
                    </TableBody>
                  </Table>
                </Box>
              )}
            </QueryState>
          </SectionCard>
        </Grid>
      </Grid>
    </>
  )
}

function JobRow({ job }: { job: ReportJob }) {
  const [downloading, setDownloading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const download = () => {
    setDownloading(true)
    setError(null)
    downloadFile(`/api/reports/${job.jobId}/download`, `report-${job.jobId.slice(0, 8)}.csv`)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Download failed'))
      .finally(() => setDownloading(false))
  }

  return (
    <TableRow sx={{ bgcolor: job.status === 'FAILED' ? '#fffafa' : undefined }}>
      <TableCell><Tooltip title={job.jobId}><span><Mono sx={{ fontWeight: 600 }}>{job.jobId.slice(0, 8)}</Mono></span></Tooltip></TableCell>
      <TableCell>{enumLabel(job.type)}<Typography variant="caption" sx={{ display: 'block', color: 'text.secondary' }}>Tax year {job.taxYear}</Typography></TableCell>
      <TableCell><Mono>{job.requestedAt.slice(0, 19).replace('T', ' ')}</Mono></TableCell>
      <TableCell sx={{ minWidth: 150 }}>
        <StatusChip value={job.status} />
        {isActive(job) && <LinearProgress sx={{ mt: 0.75, borderRadius: 1 }} />}
        {job.errorMessage && <Typography variant="caption" sx={{ display: 'block', color: tokens.danger }}>{job.errorMessage}</Typography>}
        {error && <Typography variant="caption" sx={{ display: 'block', color: tokens.danger }}>{error}</Typography>}
      </TableCell>
      <TableCell>
        {job.downloadable ? (
          <Tooltip title="Download CSV">
            <span>
              <IconButton size="small" onClick={download} disabled={downloading} aria-label={`Download report ${job.jobId.slice(0, 8)}`}>
                {downloading ? <CircularProgress size={18} /> : <DownloadOutlinedIcon fontSize="small" />}
              </IconButton>
            </span>
          </Tooltip>
        ) : '—'}
      </TableCell>
    </TableRow>
  )
}
