import type { LucideIcon } from 'lucide-react';

export function StatCard({ label, value, icon: Icon, hint, tone = 'brand' }: {
  label: string;
  value: number | string;
  icon: LucideIcon;
  hint?: string;
  tone?: 'brand' | 'sky' | 'amber' | 'violet';
}) {
  const tones = {
    brand: 'bg-brand-50 text-brand-700',
    sky: 'bg-sky-50 text-sky-700',
    amber: 'bg-amber-50 text-amber-700',
    violet: 'bg-violet-50 text-violet-700',
  };
  return (
    <div className="card flex items-start gap-3 p-4" title={hint}>
      <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg ${tones[tone]}`}>
        <Icon className="h-5 w-5" aria-hidden="true" />
      </span>
      <div className="min-w-0">
        <p className="text-sm text-ink-500">{label}</p>
        <p className="text-2xl font-bold tabular-nums text-ink-900">{value}</p>
        {hint && <p className="mt-0.5 hidden text-xs text-ink-400 sm:block">{hint}</p>}
      </div>
    </div>
  );
}
