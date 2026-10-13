export function Logo({ compact = false, inverted = false }: { compact?: boolean; inverted?: boolean }) {
  return (
    <span className="inline-flex items-center gap-2.5">
      <svg viewBox="0 0 64 64" className="h-9 w-9 shrink-0" aria-hidden="true">
        <rect width="64" height="64" rx="14" fill={inverted ? '#ffffff' : '#12603e'} />
        <path d="M10 42c8-14 36-14 44 0" stroke={inverted ? '#12603e' : '#fff'} strokeWidth="5" fill="none" strokeLinecap="round" />
        <path d="M18 42V30M32 42V24M46 42V30" stroke={inverted ? '#12603e' : '#fff'} strokeWidth="4" strokeLinecap="round" />
        <circle cx="32" cy="16" r="4" fill="#e8891c" />
      </svg>
      {!compact && (
        <span className="leading-tight">
          <span className={`block text-lg font-bold tracking-tight ${inverted ? 'text-white' : 'text-brand-800'}`}>Nagrik Setu</span>
          <span className={`block text-[11px] font-medium ${inverted ? 'text-brand-100' : 'text-ink-500'}`}>
            नागरिक सेतु · Citizen Portal
          </span>
        </span>
      )}
    </span>
  );
}
