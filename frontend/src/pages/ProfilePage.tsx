import { KeyRound, Save } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { errorMessage, fieldErrors } from '../api/client';
import { authApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { Alert, Spinner } from '../components/Feedback';
import { formatDate } from '../lib/format';

export default function ProfilePage() {
  const { user, setUser } = useAuth();
  const [form, setForm] = useState({ fullName: user!.fullName, phone: user!.phone ?? '', locality: user!.locality ?? '', address: user!.address ?? '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [status, setStatus] = useState<{ kind: 'success' | 'error'; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [pw, setPw] = useState({ current: '', next: '' });
  const [pwStatus, setPwStatus] = useState<{ kind: 'success' | 'error'; text: string } | null>(null);
  const [pwBusy, setPwBusy] = useState(false);

  const save = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setStatus(null);
    setErrors({});
    try {
      setUser(await authApi.updateProfile({ ...form, address: form.address || undefined }));
      setStatus({ kind: 'success', text: 'Profile updated.' });
    } catch (err) {
      setErrors(fieldErrors(err));
      setStatus({ kind: 'error', text: errorMessage(err) });
    } finally {
      setBusy(false);
    }
  };

  const changePassword = async (e: FormEvent) => {
    e.preventDefault();
    setPwBusy(true);
    setPwStatus(null);
    try {
      await authApi.changePassword(pw.current, pw.next);
      setPw({ current: '', next: '' });
      setPwStatus({ kind: 'success', text: 'Password changed.' });
    } catch (err) {
      setPwStatus({ kind: 'error', text: fieldErrors(err).newPassword ?? errorMessage(err) });
    } finally {
      setPwBusy(false);
    }
  };

  const input = (k: keyof typeof form, label: string, type = 'text') => (
    <div>
      <label className="label" htmlFor={k}>{label}</label>
      <input id={k} type={type} className={`input ${errors[k] ? 'input-error' : ''}`} value={form[k]} onChange={(e) => setForm({ ...form, [k]: e.target.value })} />
      {errors[k] && <p className="field-error">{errors[k]}</p>}
    </div>
  );

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="page-title">Profile</h1>
        <p className="mt-1 text-sm text-ink-600">{user!.roleName}{user!.wardName ? ` · ${user!.wardName}` : ''}{user!.departmentName ? ` · ${user!.departmentName}` : ''} · member since {formatDate(user!.createdAt)}</p>
      </div>
      <form onSubmit={save} className="card space-y-4 p-5 sm:p-6" noValidate>
        <h2 className="font-semibold text-ink-900">Personal details</h2>
        {status && <Alert kind={status.kind}>{status.text}</Alert>}
        <div>
          <label className="label" htmlFor="email">Email</label>
          <input id="email" className="input" value={user!.email} disabled />
          <p className="mt-1 text-xs text-ink-500">Your email is your sign-in ID and cannot be changed here.</p>
        </div>
        {input('fullName', 'Full name')}
        {input('phone', 'Mobile number', 'tel')}
        <div className="grid gap-4 sm:grid-cols-2">
          {input('locality', 'Locality')}
          {input('address', 'Address (optional)')}
        </div>
        <button className="btn-primary" disabled={busy}>{busy ? <Spinner className="h-4 w-4 text-white" /> : <Save className="h-4 w-4" />} Save changes</button>
      </form>
      <form onSubmit={changePassword} className="card space-y-4 p-5 sm:p-6">
        <h2 className="font-semibold text-ink-900">Change password</h2>
        {pwStatus && <Alert kind={pwStatus.kind}>{pwStatus.text}</Alert>}
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label className="label" htmlFor="current">Current password</label>
            <input id="current" type="password" autoComplete="current-password" className="input" value={pw.current} onChange={(e) => setPw({ ...pw, current: e.target.value })} />
          </div>
          <div>
            <label className="label" htmlFor="next">New password</label>
            <input id="next" type="password" autoComplete="new-password" className="input" value={pw.next} onChange={(e) => setPw({ ...pw, next: e.target.value })} />
          </div>
        </div>
        <button className="btn-secondary" disabled={pwBusy || !pw.current || pw.next.length < 8}>
          {pwBusy ? <Spinner className="h-4 w-4" /> : <KeyRound className="h-4 w-4" />} Update password
        </button>
      </form>
    </div>
  );
}
