import { useState } from 'react'
import { CircularProgress, IconButton, Tooltip } from '@mui/material'
import PictureAsPdfOutlinedIcon from '@mui/icons-material/PictureAsPdfOutlined'
import { downloadFile } from '../api/client'
import type { TaxBill } from '../api/types'

/** Downloads the bill's PDF through the API (the server checks access; the browser never sees a storage URL). */
export function BillPdfButton({ bill }: { bill: TaxBill }) {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const download = () => {
    setPending(true)
    setError(null)
    downloadFile(`/api/tax-bills/${bill.id}/document`, `${bill.billNumber}.pdf`)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Download failed'))
      .finally(() => setPending(false))
  }

  return (
    <Tooltip title={error ?? 'Download bill (PDF)'}>
      <span>
        <IconButton size="small" onClick={download} disabled={pending} color={error ? 'error' : 'default'}
          aria-label={`Download ${bill.billNumber} as PDF`}>
          {pending ? <CircularProgress size={18} /> : <PictureAsPdfOutlinedIcon fontSize="small" />}
        </IconButton>
      </span>
    </Tooltip>
  )
}
