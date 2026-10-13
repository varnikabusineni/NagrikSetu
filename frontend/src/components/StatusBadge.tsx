import type { ComplaintStatus, Priority } from '../api/types';
import { PRIORITY_META, STATUS_META } from '../lib/status';

export function StatusBadge({ status, size = 'sm' }: { status: ComplaintStatus; size?: 'sm' | 'md' }) {
  const meta = STATUS_META[status];
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full font-semibold ring-1 ring-inset ${meta.badge} ${
        size === 'md' ? 'px-3 py-1 text-sm' : 'px-2.5 py-0.5 text-xs'
      }`}
      title={meta.description}
    >
      <span className={`h-1.5 w-1.5 rounded-full ${meta.dot}`} aria-hidden="true" />
      {meta.label}
    </span>
  );
}

export function PriorityBadge({ priority }: { priority: Priority }) {
  if (priority === 'NORMAL') return null;
  return <span className={`chip ${PRIORITY_META[priority]}`}>{priority.charAt(0) + priority.slice(1).toLowerCase()} priority</span>;
}
