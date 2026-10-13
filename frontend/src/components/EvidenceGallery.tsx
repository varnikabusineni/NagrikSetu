import { ChevronLeft, ChevronRight, ImageOff, X } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { mediaUrl } from '../api/client';
import type { Evidence } from '../api/types';
import { formatBytes, formatDateTime } from '../lib/format';

/** Thumbnails with a keyboard-accessible lightbox. Images load through short-lived signed URLs. */
export function EvidenceGallery({ items, emptyText }: { items: Evidence[]; emptyText: string }) {
  const [open, setOpen] = useState<number | null>(null);
  const [failed, setFailed] = useState<Record<string, boolean>>({});

  const close = useCallback(() => setOpen(null), []);
  const step = useCallback((d: number) => setOpen((i) => (i == null ? i : (i + d + items.length) % items.length)), [items.length]);

  useEffect(() => {
    if (open == null) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') close();
      if (e.key === 'ArrowRight') step(1);
      if (e.key === 'ArrowLeft') step(-1);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, close, step]);

  if (items.length === 0) {
    return (
      <div className="flex items-center gap-2 rounded-lg border border-dashed border-ink-300 px-4 py-6 text-sm text-ink-500">
        <ImageOff className="h-4 w-4" /> {emptyText}
      </div>
    );
  }

  const current = open != null ? items[open] : null;
  return (
    <>
      <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        {items.map((e, i) => (
          <li key={e.id}>
            <button
              type="button"
              onClick={() => setOpen(i)}
              className="group relative block aspect-[4/3] w-full overflow-hidden rounded-lg bg-ink-100 ring-1 ring-ink-200"
              aria-label={`Open photo ${i + 1} of ${items.length}`}
            >
              {failed[e.id] ? (
                <span className="flex h-full flex-col items-center justify-center gap-1 text-xs text-ink-500">
                  <ImageOff className="h-5 w-5" /> Could not load. Reload the page.
                </span>
              ) : (
                <img
                  src={mediaUrl(e.url)}
                  alt={`Evidence photo ${i + 1}`}
                  loading="lazy"
                  onError={() => setFailed((f) => ({ ...f, [e.id]: true }))}
                  className="h-full w-full object-cover transition-transform group-hover:scale-[1.02]"
                />
              )}
            </button>
          </li>
        ))}
      </ul>
      {current && (
        <div className="fixed inset-0 z-50 flex flex-col bg-black/90" role="dialog" aria-modal="true" aria-label="Photo viewer" onClick={close}>
          <div className="flex items-center justify-between px-4 py-3 text-sm text-white/80">
            <span>
              Photo {open! + 1} of {items.length} · {current.uploadedByRole} · {formatDateTime(current.createdAt)}
              {current.filename && ` · ${current.filename}`} · {formatBytes(current.sizeBytes)}
            </span>
            <button className="rounded p-2 hover:bg-white/10" onClick={close} aria-label="Close">
              <X className="h-6 w-6 text-white" />
            </button>
          </div>
          <div className="relative flex flex-1 items-center justify-center p-4" onClick={(e) => e.stopPropagation()}>
            {items.length > 1 && (
              <button className="absolute left-2 rounded-full bg-white/10 p-2 hover:bg-white/20" onClick={() => step(-1)} aria-label="Previous photo">
                <ChevronLeft className="h-7 w-7 text-white" />
              </button>
            )}
            <img src={mediaUrl(current.url)} alt={`Evidence photo ${open! + 1}`} className="max-h-[80vh] max-w-full rounded object-contain" />
            {items.length > 1 && (
              <button className="absolute right-2 rounded-full bg-white/10 p-2 hover:bg-white/20" onClick={() => step(1)} aria-label="Next photo">
                <ChevronRight className="h-7 w-7 text-white" />
              </button>
            )}
          </div>
        </div>
      )}
    </>
  );
}
