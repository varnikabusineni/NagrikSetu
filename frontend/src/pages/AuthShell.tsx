import { CheckCircle2 } from 'lucide-react';
import type { ReactNode } from 'react';
import { Logo } from '../components/Logo';

export function AuthShell({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="flex min-h-screen bg-white">
      <aside className="relative hidden w-[44%] flex-col justify-between overflow-hidden bg-brand-800 p-10 text-white lg:flex">
        <div
          className="pointer-events-none absolute inset-0 opacity-[0.07]"
          style={{ backgroundImage: 'radial-gradient(circle at 1px 1px, #fff 1px, transparent 0)', backgroundSize: '22px 22px' }}
          aria-hidden="true"
        />
        <Logo inverted />
        <div className="relative max-w-md">
          <h2 className="text-3xl font-bold leading-tight">Report it. Track it. See it fixed.</h2>
          <p className="mt-3 text-brand-100">
            Nagrik Setu connects you with your ward office and municipal departments, with every step of your complaint visible.
          </p>
          <ul className="mt-8 space-y-3 text-sm">
            {[
              'Pin the exact location and attach photos as evidence',
              'Your complaint reaches the right ward and department',
              'Follow each status change until resolution',
              'Support neighbours by upvoting issues that matter',
            ].map((t) => (
              <li key={t} className="flex gap-2.5">
                <CheckCircle2 className="h-5 w-5 shrink-0 text-saffron-500" /> {t}
              </li>
            ))}
          </ul>
        </div>
        <p className="relative text-xs text-brand-200">A civic platform for responsive local governance.</p>
      </aside>
      <main className="flex flex-1 items-center justify-center px-4 py-10 sm:px-8">
        <div className="w-full max-w-md">
          <div className="mb-8 lg:hidden">
            <Logo />
          </div>
          <h1 className="text-2xl font-bold text-ink-900">{title}</h1>
          <p className="mt-1 text-sm text-ink-500">{subtitle}</p>
          <div className="mt-6">{children}</div>
        </div>
      </main>
    </div>
  );
}
