import type { ReactNode } from 'react'
import {
  Alert, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Paper, Stack,
  Typography,
} from '@mui/material'
import { ApiError } from '../api/client'
import { tokens } from '../theme'
import { enumLabel } from './format'

export function PageHeader(props: { eyebrow?: string; title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mb: 3, justifyContent: 'space-between', alignItems: { md: 'flex-end' } }}>
      <Box sx={{ maxWidth: 760 }}>
        {props.eyebrow && <Typography variant="overline" sx={{ color: tokens.teal }}>{props.eyebrow}</Typography>}
        <Typography variant="h1">{props.title}</Typography>
        {props.subtitle && <Typography sx={{ color: 'text.secondary', mt: 0.75 }}>{props.subtitle}</Typography>}
      </Box>
      {props.actions && <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>{props.actions}</Stack>}
    </Stack>
  )
}

export function StatCard(props: { label: string; value: ReactNode; hint?: ReactNode; icon?: ReactNode; tone?: 'default' | 'danger' }) {
  const danger = props.tone === 'danger'
  return (
    <Paper sx={{ p: 2.5, height: '100%', bgcolor: danger ? '#fffafa' : undefined }}>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <Typography variant="overline" sx={{ color: danger ? tokens.danger : 'text.secondary' }}>{props.label}</Typography>
        {props.icon && (
          <Box sx={{ p: 0.75, borderRadius: 2, display: 'flex', bgcolor: danger ? tokens.dangerBg : tokens.panel, color: danger ? tokens.danger : tokens.navy }}>
            {props.icon}
          </Box>
        )}
      </Stack>
      <Typography sx={{ fontSize: '1.75rem', fontWeight: 700, mt: 1, color: danger ? tokens.danger : 'text.primary', lineHeight: 1.2 }}>
        {props.value}
      </Typography>
      {props.hint && <Typography variant="body2" sx={{ color: 'text.secondary', mt: 1 }}>{props.hint}</Typography>}
    </Paper>
  )
}

const STATUS_COLORS: Record<string, [string, string]> = {
  // [text, background]
  ACTIVE: [tokens.teal, tokens.mint],
  RECORDED: [tokens.teal, tokens.mint],
  PAID: [tokens.teal, tokens.mint],
  ISSUED: ['#1d4ed8', '#e0eaff'],
  PARTIALLY_PAID: [tokens.warn, tokens.warnBg],
  CANCELLED: [tokens.muted, '#eef0f3'],
  CLOSED: [tokens.muted, '#eef0f3'],
  REVERSED: [tokens.danger, tokens.dangerBg],
  SUSPENDED: [tokens.danger, tokens.dangerBg],
  QUEUED: [tokens.warn, tokens.warnBg],
  COMPLETED: [tokens.teal, tokens.mint],
  PROCESSING: ['#1d4ed8', '#e0eaff'],
  FAILED: [tokens.danger, tokens.dangerBg],
  RESIDENT: [tokens.teal, tokens.mint],
  TAX_OFFICER: ['#1d4ed8', '#e0eaff'],
  ADMIN: [tokens.danger, tokens.dangerBg],
  INTERIM: [tokens.navy, tokens.panel],
  FINAL: ['#ffffff', tokens.navy],
  RESIDENTIAL: [tokens.teal, tokens.mint],
  COMMERCIAL: ['#1d4ed8', '#e0eaff'],
  INDUSTRIAL: [tokens.warn, tokens.warnBg],
}

/** Coloured chip for any enum value (status, role, bill type, property class). */
export function StatusChip({ value }: { value: string }) {
  const [color, bgcolor] = STATUS_COLORS[value] ?? [tokens.muted, '#eef0f3']
  return <Chip label={`● ${enumLabel(value)}`} size="small" sx={{ color, bgcolor }} />
}

export function SectionCard(props: { title: ReactNode; subtitle?: ReactNode; action?: ReactNode; children: ReactNode; noPadding?: boolean }) {
  return (
    <Paper sx={{ overflow: 'hidden' }}>
      <Stack direction="row" spacing={2} sx={{ px: 2.5, py: 2, justifyContent: 'space-between', alignItems: 'center', borderBottom: `1px solid ${tokens.border}` }}>
        <Box>
          <Typography variant="h2">{props.title}</Typography>
          {props.subtitle && <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.25 }}>{props.subtitle}</Typography>}
        </Box>
        {props.action}
      </Stack>
      <Box sx={{ p: props.noPadding ? 0 : 2.5, overflowX: 'auto' }}>{props.children}</Box>
    </Paper>
  )
}

/** Renders spinner / error / children for a TanStack query. */
export function QueryState(props: { query: { isPending: boolean; error: unknown }; children: ReactNode }) {
  if (props.query.error) return <ErrorAlert error={props.query.error} />
  if (props.query.isPending) {
    return <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}><CircularProgress size={28} /></Box>
  }
  return <>{props.children}</>
}

export function ErrorAlert({ error }: { error: unknown }) {
  if (!error) return null
  const message = error instanceof Error ? error.message : 'Something went wrong'
  const constraint = error instanceof ApiError ? error.problem.constraint : undefined
  return (
    <Alert severity="error" sx={{ my: 1 }}>
      {message}
      {constraint && <Mono sx={{ display: 'block', fontSize: '0.75rem' }}>constraint: {constraint}</Mono>}
    </Alert>
  )
}

export function Mono(props: { children: ReactNode; sx?: object }) {
  return <Box component="span" sx={{ fontFamily: tokens.mono, fontSize: '0.85em', whiteSpace: 'nowrap', ...props.sx }}>{props.children}</Box>
}

export function EmptyState({ children }: { children: ReactNode }) {
  return <Typography sx={{ color: 'text.secondary', py: 3, textAlign: 'center' }}>{children}</Typography>
}

/** Dialog with a form: submit button shows progress, API errors appear inline. */
export function FormDialog(props: {
  open: boolean
  title: string
  submitLabel: string
  pending: boolean
  error: unknown
  onClose: () => void
  onSubmit: () => void
  children: ReactNode
}) {
  return (
    <Dialog open={props.open} onClose={props.onClose} fullWidth maxWidth="sm">
      <Box component="form" onSubmit={(e) => { e.preventDefault(); props.onSubmit() }}>
        <DialogTitle>{props.title}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {props.children}
            <ErrorAlert error={props.error} />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={props.onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={props.pending}>
            {props.pending ? 'Saving…' : props.submitLabel}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  )
}
