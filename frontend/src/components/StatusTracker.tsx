import { Check } from 'lucide-react';
import type { ComplaintStatus } from '../api/types';
import { LIFECYCLE, STATUS_META } from '../lib/status';

/** Progress through Submitted → Under Review → Assigned → In Progress → Resolved. */
export function StatusTracker({ status }: { status: ComplaintStatus }) {
  if (status === 'REJECTED' || status === 'REOPENED') {
    return (
      <div className={`rounded-lg px-4 py-3 text-sm ring-1 ring-inset ${STATUS_META[status].badge}`}>
        <span className="font-semibold">{STATUS_META[status].label}:</span> {STATUS_META[status].description}
      </div>
    );
  }
  const current = LIFECYCLE.indexOf(status);
  return (
    <ol className="flex items-start" aria-label="Complaint progress">
      {LIFECYCLE.map((s, i) => {
        const done = i < current || status === 'RESOLVED';
        const active = i === current && status !== 'RESOLVED';
        return (
          <li key={s} className="relative flex flex-1 flex-col items-center text-center" aria-current={active ? 'step' : undefined}>
            {i > 0 && (
              <span className={`absolute right-1/2 top-3.5 h-0.5 w-full -translate-y-1/2 ${i <= current ? 'bg-brand-600' : 'bg-ink-200'}`} aria-hidden="true" />
            )}
            <span
              className={`relative z-10 flex h-7 w-7 items-center justify-center rounded-full border-2 text-xs font-bold ${
                done ? 'border-brand-600 bg-brand-600 text-white' : active ? 'border-brand-600 bg-white text-brand-700' : 'border-ink-300 bg-white text-ink-400'
              }`}
            >
              {done ? <Check className="h-4 w-4" /> : i + 1}
            </span>
            <span className={`mt-1.5 text-[11px] font-medium leading-tight sm:text-xs ${done || active ? 'text-ink-900' : 'text-ink-400'}`}>
              {STATUS_META[s].label}
            </span>
          </li>
        );
      })}
    </ol>
  );
}
