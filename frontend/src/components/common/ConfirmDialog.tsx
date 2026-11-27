import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle } from '@mui/material'
import { useState, type ReactNode } from 'react'
import { errorMessage } from '../../hooks/useApiData'

interface ConfirmDialogProps {
  open: boolean
  title: string
  children?: ReactNode
  confirmLabel: string
  danger?: boolean
  disabled?: boolean
  onClose: () => void
  /** Throwing shows the error inside the dialog; resolving closes it. */
  onConfirm: () => Promise<void>
}

export function ConfirmDialog({ open, title, children, confirmLabel, danger, disabled, onClose, onConfirm }: ConfirmDialogProps) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function confirm() {
    setBusy(true)
    setError(null)
    try {
      await onConfirm()
      onClose()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onClose={busy ? undefined : onClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ fontWeight: 700 }}>{title}</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {children}
        {error && <Alert severity="error">{error}</Alert>}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button onClick={onClose} disabled={busy} color="inherit" variant="outlined">Cancel</Button>
        <Button onClick={confirm} disabled={busy || disabled} variant="contained" color={danger ? 'error' : 'primary'}>
          {busy ? 'Working…' : confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
