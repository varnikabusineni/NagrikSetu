import { AlertCircle, CheckCircle2, Info, Loader2, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';

export function Spinner({ className = 'h-5 w-5' }: { className?: string }) {
  return <Loader2 className={`animate-spin text-brand-700 ${className}`} aria-hidden="true" />;
}

export function FullPageSpinner({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="flex min-h-[50vh] flex-col items-center justify-center gap-3 text-ink-500" role="status">
      <Spinner className="h-8 w-8" />
      <span className="text-sm">{label}</span>
    </div>
  );
}

const ALERT_STYLES = {
  error: { box: 'border-rose-200 bg-rose-50 text-rose-900', icon: AlertCircle, iconClass: 'text-rose-600' },
  success: { box: 'border-brand-200 bg-brand-50 text-brand-900', icon: CheckCircle2, iconClass: 'text-brand-600' },
  info: { box: 'border-sky-200 bg-sky-50 text-sky-900', icon: Info, iconClass: 'text-sky-600' },
  warning: { box: 'border-amber-200 bg-amber-50 text-amber-900', icon: AlertCircle, iconClass: 'text-amber-600' },
} as const;

export function Alert({
  kind = 'info',
  title,
  children,
  action,
}: {
  kind?: keyof typeof ALERT_STYLES;
  title?: string;
  children?: ReactNode;
  action?: ReactNode;
}) {
  const s = ALERT_STYLES[kind];
  const Icon = s.icon;
  return (
    <div className={`flex gap-3 rounded-lg border px-4 py-3 text-sm ${s.box}`} role={kind === 'error' ? 'alert' : 'status'}>
      <Icon className={`mt-0.5 h-5 w-5 shrink-0 ${s.iconClass}`} aria-hidden="true" />
      <div className="min-w-0 flex-1">
        {title && <p className="font-semibold">{title}</p>}
        {children && <div className={title ? 'mt-0.5' : ''}>{children}</div>}
      </div>
      {action && <div className="shrink-0 self-center">{action}</div>}
    </div>
  );
}

export function EmptyState({
  icon: Icon,
  title,
  children,
  action,
}: {
  icon: LucideIcon;
  title: string;
  children?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-ink-300 bg-white px-6 py-12 text-center">
      <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-brand-50">
        <Icon className="h-6 w-6 text-brand-700" aria-hidden="true" />
      </div>
      <h3 className="text-base font-semibold text-ink-900">{title}</h3>
      {children && <p className="mt-1 max-w-sm text-sm text-ink-500">{children}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function SkeletonCard() {
  return (
    <div className="card animate-pulse overflow-hidden">
      <div className="h-36 bg-ink-100" />
      <div className="space-y-2 p-4">
        <div className="h-3 w-1/3 rounded bg-ink-100" />
        <div className="h-4 w-3/4 rounded bg-ink-100" />
        <div className="h-3 w-1/2 rounded bg-ink-100" />
      </div>
    </div>
  );
}
