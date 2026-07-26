import React, { useEffect, useState } from 'react';
import { Loader2 } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';

export interface ConfirmOptions {
  title: string;
  body?: React.ReactNode;
  okText?: string;
  cancelText?: string;
  danger?: boolean;
  onOk?: () => void | Promise<void>;
  onCancel?: () => void;
}

type Listener = (opts: ConfirmOptions | null) => void;

let listener: Listener | null = null;

/**
 * Imperative confirm dialog. Mount <ConfirmHost /> once at the app root, then
 * call `confirm({ title, body, danger, onOk })` from anywhere. Replaces
 * `window.confirm` so we render our own modal chrome (no native browser UI,
 * no AntD `Modal.confirm`, no AntD `Popconfirm`).
 */
export const confirm = (opts: ConfirmOptions): void => {
  if (!listener) {
    console.warn(
      '[CustomConfirm] <ConfirmHost /> is not mounted; falling back to window.confirm.'
    );
    if (window.confirm(opts.title)) {
      void opts.onOk?.();
    } else {
      opts.onCancel?.();
    }
    return;
  }
  listener(opts);
};

export const ConfirmHost: React.FC = () => {
  const [opts, setOpts] = useState<ConfirmOptions | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    listener = setOpts;
    return () => {
      listener = null;
    };
  }, []);

  const close = () => {
    if (busy) return;
    setOpts(null);
  };

  const handleOk = async () => {
    if (!opts) return;
    setBusy(true);
    try {
      await opts.onOk?.();
      setOpts(null);
    } finally {
      setBusy(false);
    }
  };

  const handleCancel = () => {
    opts?.onCancel?.();
    close();
  };

  return (
    <Dialog
      open={!!opts}
      onOpenChange={(next) => {
        if (!next) handleCancel();
      }}
    >
      <DialogContent
        className="max-w-none p-0 sm:max-w-none"
        style={{ width: '100%', maxWidth: 420 }}
        showCloseButton={!busy}
        onInteractOutside={(e) => {
          if (busy) e.preventDefault();
        }}
        onEscapeKeyDown={(e) => {
          if (busy) e.preventDefault();
        }}
      >
        <DialogHeader className="border-b border-border px-6 py-4 text-left">
          <DialogTitle className="text-base font-semibold text-foreground">
            {opts?.title}
          </DialogTitle>
        </DialogHeader>
        <div className="px-6 py-5 text-sm text-muted-foreground">
          {opts?.body}
        </div>
        <DialogFooter className="border-t border-border px-6 py-4">
          <Button variant="outline" onClick={handleCancel} disabled={busy}>
            {opts?.cancelText ?? 'Cancel'}
          </Button>
          <Button
            variant={opts?.danger ? 'destructive' : 'default'}
            onClick={handleOk}
            disabled={busy}
          >
            {busy && <Loader2 className="size-4 animate-spin" />}
            {opts?.okText ?? 'Confirm'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default ConfirmHost;
