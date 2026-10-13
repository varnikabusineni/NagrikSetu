import { Eye, EyeOff, LogIn } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Alert, Spinner } from '../components/Feedback';
import { AuthShell } from './AuthShell';

export function LoginPage() {
  const { login, sessionMessage, clearSessionMessage } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [show, setShow] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const user = await login(email.trim(), password);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from && from !== '/login' ? from : user.role === 'CITIZEN' ? '/' : '/official', { replace: true });
    } catch (err) {
      setError(errorMessage(err, 'Could not sign in. Please try again.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthShell title="Sign in" subtitle="Welcome back. Sign in to report and track civic issues.">
      <form onSubmit={submit} className="space-y-4" noValidate>
        {sessionMessage && (
          <Alert kind="warning" action={<button type="button" className="text-xs underline" onClick={clearSessionMessage}>Dismiss</button>}>
            {sessionMessage}
          </Alert>
        )}
        {error && <Alert kind="error">{error}</Alert>}
        <div>
          <label htmlFor="email" className="label">Email</label>
          <input id="email" type="email" autoComplete="email" required className="input" value={email} onChange={(e) => setEmail(e.target.value)} />
        </div>
        <div>
          <label htmlFor="password" className="label">Password</label>
          <div className="relative">
            <input
              id="password"
              type={show ? 'text' : 'password'}
              autoComplete="current-password"
              required
              className="input pr-10"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            <button
              type="button"
              className="absolute inset-y-0 right-0 flex items-center px-3 text-ink-500"
              onClick={() => setShow((s) => !s)}
              aria-label={show ? 'Hide password' : 'Show password'}
            >
              {show ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </div>
        </div>
        <button type="submit" className="btn-primary w-full py-2.5" disabled={busy || !email || !password}>
          {busy ? <Spinner className="h-4 w-4 text-white" /> : <LogIn className="h-4 w-4" />} Sign in
        </button>
        <p className="text-center text-sm text-ink-600">
          New to Nagrik Setu?{' '}
          <Link to="/register" className="font-semibold text-brand-700 hover:underline">
            Create a citizen account
          </Link>
        </p>
        {import.meta.env.DEV && (
          <div className="rounded-lg border border-ink-200 bg-ink-50 p-3 text-xs text-ink-600">
            <p className="font-semibold text-ink-700">Local demo accounts (development only)</p>
            <p className="mt-1">
              <code>priya.sharma@demo.nagriksetu.in</code> (citizen), <code>wardhead.mdp@demo.nagriksetu.in</code>,{' '}
              <code>head.roads@demo.nagriksetu.in</code>, <code>officer.roads@demo.nagriksetu.in</code>,{' '}
              <code>admin@demo.nagriksetu.in</code>. The password is the backend&apos;s <code>NAGRIK_DEMO_PASSWORD</code> (see README).
            </p>
          </div>
        )}
      </form>
    </AuthShell>
  );
}
