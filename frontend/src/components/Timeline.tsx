import { Lock } from 'lucide-react';
import type { TimelineEntry } from '../api/types';
import { formatDateTime } from '../lib/format';
import { EVENT_LABELS, STATUS_META } from '../lib/status';
import { StatusBadge } from './StatusBadge';

export function Timeline({ entries }: { entries: TimelineEntry[] }) {
  if (entries.length === 0) return <p className="text-sm text-ink-500">No updates yet.</p>;
  return (
    <ol className="relative space-y-5 border-l-2 border-ink-200 pl-6">
      {entries.map((e) => {
        const isStatus = e.eventType === 'STATUS_CHANGE' || e.eventType === 'CREATED';
        return (
          <li key={e.id} className="relative">
            <span
              className={`absolute -left-[31px] top-1 h-4 w-4 rounded-full border-2 border-white ${
                isStatus ? STATUS_META[e.toStatus].dot : e.internal ? 'bg-ink-400' : 'bg-brand-300'
              }`}
              aria-hidden="true"
            />
            <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
              <span className="text-sm font-semibold text-ink-900">
                {e.eventType === 'STATUS_CHANGE' ? `Moved to ${STATUS_META[e.toStatus].label}` : EVENT_LABELS[e.eventType] ?? e.eventType}
              </span>
              {e.eventType === 'STATUS_CHANGE' && <StatusBadge status={e.toStatus} />}
              {e.internal && (
                <span className="chip bg-ink-100 text-ink-600">
                  <Lock className="h-3 w-3" /> Internal
                </span>
              )}
            </div>
            <p className="mt-0.5 text-xs text-ink-500">
              {formatDateTime(e.createdAt)} · {e.actorName ? `${e.actorName} (${e.actorRole})` : e.actorRole}
            </p>
            {e.note && <p className="mt-1.5 whitespace-pre-line rounded-lg bg-ink-50 px-3 py-2 text-sm text-ink-700">{e.note}</p>}
          </li>
        );
      })}
    </ol>
  );
}
