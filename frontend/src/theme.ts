import { createTheme } from '@mui/material/styles'

/** Colours taken from the Stitch design: navy primary, teal accents, cool grey canvas. */
export const tokens = {
  navy: '#0f1b2d',
  teal: '#0f766e',
  mint: '#ccfbf1',
  canvas: '#f4f6fa',
  border: '#e3e8ef',
  panel: '#eef3fb',
  muted: '#5b6b7f',
  danger: '#b42318',
  dangerBg: '#fdecea',
  warn: '#a15c07',
  warnBg: '#fff4e0',
  mono: '"JetBrains Mono", ui-monospace, SFMono-Regular, Menlo, monospace',
}

export const theme = createTheme({
  palette: {
    primary: { main: tokens.navy },
    secondary: { main: tokens.teal },
    error: { main: tokens.danger },
    background: { default: tokens.canvas, paper: '#ffffff' },
    text: { primary: '#101828', secondary: tokens.muted },
    divider: tokens.border,
  },
  shape: { borderRadius: 10 },
  typography: {
    fontFamily: '"Inter", system-ui, -apple-system, "Segoe UI", sans-serif',
    h1: { fontSize: '1.9rem', fontWeight: 800, letterSpacing: '-0.02em' },
    h2: { fontSize: '1.15rem', fontWeight: 700 },
    h3: { fontSize: '1rem', fontWeight: 700 },
    overline: { fontSize: '0.68rem', fontWeight: 600, letterSpacing: '0.08em', lineHeight: 1.6 },
    button: { textTransform: 'none', fontWeight: 600 },
  },
  components: {
    MuiPaper: {
      defaultProps: { elevation: 0 },
      styleOverrides: { root: { border: `1px solid ${tokens.border}` } },
    },
    MuiAppBar: { styleOverrides: { root: { border: 0 } } },
    MuiDrawer: { styleOverrides: { paper: { border: 0, borderRight: `1px solid ${tokens.border}` } } },
    MuiButton: { defaultProps: { disableElevation: true } },
    MuiChip: { styleOverrides: { root: { fontWeight: 600, fontSize: '0.7rem', height: 22, borderRadius: 6 } } },
    MuiTableCell: {
      styleOverrides: {
        head: {
          backgroundColor: tokens.panel,
          color: tokens.muted,
          fontSize: '0.7rem',
          fontWeight: 600,
          textTransform: 'uppercase',
          letterSpacing: '0.05em',
          whiteSpace: 'nowrap',
        },
        root: { borderColor: tokens.border },
      },
    },
    MuiTableRow: { styleOverrides: { hover: { cursor: 'pointer' } } },
    MuiDialogTitle: { styleOverrides: { root: { fontWeight: 700 } } },
  },
})
