import { History, KeyRound, Pencil, Power, Search, ShieldCheck, UserPlus } from 'lucide-react';
import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { errorMessage, fieldErrors } from '../../api/client';
import { adminApi } from '../../api/endpoints';
import type { AdminAccount, AuditEntry, Page, RoleCode } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { Alert, EmptyState, FullPageSpinner, Spinner } from '../../components/Feedback';
import { Modal } from '../../components/Modal';
import { Pagination } from '../../components/Pagination';
import { useDepartments, useWards } from '../../hooks/useReference';
import { formatDateTime, timeAgo } from '../../lib/format';

const ROLE_LABELS: Record<RoleCode, string> = {
  CITIZEN: 'Citizen',
  WARD_HEAD: 'Ward Head',
  DEPARTMENT_HEAD: 'Department Head',
  OFFICER: 'Field Officer',
  ADMIN: 'Administrator',
};
const OFFICIAL_ROLES: RoleCode[] = ['WARD_HEAD', 'DEPARTMENT_HEAD', 'OFFICER', 'ADMIN'];

interface FormState {
  fullName: string;
  email: string;
  phone: string;
  password: string;
  role: RoleCode;
  wardId: string;
  departmentId: string;
}

const EMPTY_FORM: FormState = { fullName: '', email: '', phone: '', password: '', role: 'OFFICER', wardId: '', departmentId: '' };

/** Create or edit an account. Ward heads need a ward; department heads and officers need a department. */
function AccountForm({ editing, onDone, onCancel }: { editing: AdminAccount | null; onDone: (msg: string) => void; onCancel: () => void }) {
  const wards = useWards();
  const departments = useDepartments();
  const [form, setForm] = useState<FormState>(
    editing
      ? {
          fullName: editing.fullName,
          email: editing.email,
          phone: editing.phone ?? '',
          password: '',
          role: editing.role,
          wardId: editing.wardId ? String(editing.wardId) : '',
          departmentId: editing.departmentId ? String(editing.departmentId) : '',
        }
      : EMPTY_FORM,
  );
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const isCitizen = editing?.role === 'CITIZEN';
  const needsWard = form.role === 'WARD_HEAD';
  const needsDept = form.role === 'DEPARTMENT_HEAD' || form.role === 'OFFICER';

  const set = (k: keyof FormState) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    setErrors({});
    const assignment = {
      fullName: form.fullName.trim(),
      phone: form.phone.trim(),
      role: form.role,
      wardId: needsWard && form.wardId ? Number(form.wardId) : undefined,
      departmentId: needsDept && form.departmentId ? Number(form.departmentId) : undefined,
    };
    try {
      if (editing) {
        await adminApi.update(editing.id, assignment);
        onDone(`Updated ${form.fullName}`);
      } else {
        await adminApi.create({ ...assignment, email: form.email.trim(), password: form.password });
        onDone(`Created ${form.fullName}. Share the email and temporary password with them.`);
      }
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const field = (k: keyof FormState, label: string, props: React.InputHTMLAttributes<HTMLInputElement> = {}) => (
    <div>
      <label className="label" htmlFor={`acc-${k}`}>{label}</label>
      <input id={`acc-${k}`} className={`input ${errors[k] ? 'input-error' : ''}`} value={form[k]} onChange={set(k)} {...props} />
      {errors[k] && <p className="field-error">{errors[k]}</p>}
    </div>
  );

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      {error && <Alert kind="error">{error}</Alert>}
      {field('fullName', 'Full name')}
      <div className="grid gap-4 sm:grid-cols-2">
        {field('email', 'Email (sign-in ID)', { type: 'email', disabled: !!editing })}
        {field('phone', 'Mobile number', { type: 'tel', placeholder: '98765 43210' })}
      </div>
      {!editing && field('password', 'Temporary password', { type: 'text', autoComplete: 'off', placeholder: 'At least 8 characters with a number' })}
      {isCitizen ? (
        <p className="rounded-lg bg-ink-50 px-3 py-2 text-sm text-ink-600">Citizen accounts keep the Citizen role. You can still deactivate them or reset their password.</p>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label className="label" htmlFor="acc-role">Role</label>
            <select id="acc-role" className="input" value={form.role} onChange={set('role')}>
              {OFFICIAL_ROLES.map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
            </select>
          </div>
          {needsWard && (
            <div>
              <label className="label" htmlFor="acc-ward">Ward</label>
              <select id="acc-ward" className={`input ${errors.wardId ? 'input-error' : ''}`} value={form.wardId} onChange={set('wardId')}>
                <option value="">Choose a ward</option>
                {wards.map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}
              </select>
            </div>
          )}
          {needsDept && (
            <div>
              <label className="label" htmlFor="acc-dept">Department</label>
              <select id="acc-dept" className="input" value={form.departmentId} onChange={set('departmentId')}>
                <option value="">Choose a department</option>
                {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
              </select>
            </div>
          )}
        </div>
      )}
      <div className="flex justify-end gap-2 border-t border-ink-100 pt-4">
        <button type="button" className="btn-secondary" onClick={onCancel} disabled={busy}>Cancel</button>
        <button type="submit" className="btn-primary" disabled={busy}>
          {busy && <Spinner className="h-4 w-4 text-white" />} {editing ? 'Save changes' : 'Create account'}
        </button>
      </div>
    </form>
  );
}

function AccountsTab() {
  const { user } = useAuth();
  const [accounts, setAccounts] = useState<AdminAccount[] | null>(null);
  const [role, setRole] = useState<RoleCode | ''>('');
  const [q, setQ] = useState('');
  const [includeCitizens, setIncludeCitizens] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [editing, setEditing] = useState<AdminAccount | null | 'new'>(null);
  const [resetting, setResetting] = useState<AdminAccount | null>(null);
  const [newPassword, setNewPassword] = useState('');
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = useCallback(() => {
    adminApi
      .accounts({ role: role || undefined, q: q || undefined, includeCitizens })
      .then((a) => {
        setAccounts(a);
        setError('');
      })
      .catch((e) => setError(errorMessage(e)));
  }, [role, q, includeCitizens]);

  useEffect(() => {
    const t = setTimeout(load, q ? 300 : 0);
    return () => clearTimeout(t);
  }, [load, q]);

  const toggle = async (a: AdminAccount) => {
    setBusyId(a.id);
    try {
      await adminApi.setEnabled(a.id, !a.enabled);
      setNotice(`${a.fullName} ${a.enabled ? 'deactivated. They can no longer sign in.' : 'activated.'}`);
      load();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusyId(null);
    }
  };

  const reset = async (e: FormEvent) => {
    e.preventDefault();
    if (!resetting) return;
    setBusyId(resetting.id);
    try {
      await adminApi.resetPassword(resetting.id, newPassword);
      setNotice(`Password reset for ${resetting.fullName}. Share the new password with them securely.`);
      setResetting(null);
    } catch (err) {
      setError(fieldErrors(err).newPassword ?? errorMessage(err));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="space-y-4">
      <div className="card flex flex-wrap items-center gap-2 p-3">
        <div className="relative w-full sm:w-auto sm:min-w-[16rem] sm:max-w-xs sm:flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-400" />
          <input className="input pl-9" placeholder="Search name or email" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search accounts" />
        </div>
        <select className="input w-auto" value={role} onChange={(e) => setRole(e.target.value as RoleCode | '')} aria-label="Role">
          <option value="">All official roles</option>
          {[...OFFICIAL_ROLES, 'CITIZEN' as RoleCode].map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
        </select>
        <label className="flex items-center gap-2 text-sm text-ink-700">
          <input type="checkbox" className="h-4 w-4 rounded border-ink-300" checked={includeCitizens} onChange={(e) => setIncludeCitizens(e.target.checked)} />
          Include citizens
        </label>
        <button className="btn-primary ml-auto" onClick={() => setEditing('new')}>
          <UserPlus className="h-4 w-4" /> New official account
        </button>
      </div>

      {notice && <Alert kind="success" action={<button className="text-xs underline" onClick={() => setNotice('')}>Dismiss</button>}>{notice}</Alert>}
      {error && <Alert kind="error" action={<button className="text-xs underline" onClick={() => setError('')}>Dismiss</button>}>{error}</Alert>}

      {!accounts ? (
        <FullPageSpinner />
      ) : accounts.length === 0 ? (
        <EmptyState icon={ShieldCheck} title="No accounts match">Try a different search or role.</EmptyState>
      ) : (
        <div className="card overflow-x-auto">
          <table className="w-full min-w-[760px] text-sm">
            <thead className="border-b border-ink-200 bg-ink-50 text-left text-xs uppercase tracking-wide text-ink-500">
              <tr>
                <th className="px-4 py-3 font-semibold">Name</th>
                <th className="px-4 py-3 font-semibold">Role</th>
                <th className="px-4 py-3 font-semibold">Ward / Department</th>
                <th className="px-4 py-3 font-semibold">Status</th>
                <th className="px-4 py-3 font-semibold">Last sign-in</th>
                <th className="px-4 py-3 text-right font-semibold">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-ink-100">
              {accounts.map((a) => (
                <tr key={a.id} className={a.enabled ? '' : 'bg-ink-50/70 text-ink-500'}>
                  <td className="px-4 py-3">
                    <p className="font-medium text-ink-900">{a.fullName}{a.id === user?.id && <span className="ml-1 text-xs text-ink-500">(you)</span>}</p>
                    <p className="text-xs text-ink-500">{a.email}</p>
                  </td>
                  <td className="px-4 py-3">{ROLE_LABELS[a.role]}</td>
                  <td className="px-4 py-3">{a.wardName ?? a.departmentName ?? <span className="text-ink-400">—</span>}</td>
                  <td className="px-4 py-3">
                    <span className={`chip ${a.enabled ? 'bg-brand-50 text-brand-800' : 'bg-rose-50 text-rose-800'}`}>{a.enabled ? 'Active' : 'Deactivated'}</span>
                  </td>
                  <td className="px-4 py-3 text-ink-500" title={a.lastLoginAt ? formatDateTime(a.lastLoginAt) : undefined}>
                    {a.lastLoginAt ? timeAgo(a.lastLoginAt) : 'Never'}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-1">
                      <button className="btn-ghost px-2 py-1" onClick={() => setEditing(a)} title="Edit" aria-label={`Edit ${a.fullName}`}>
                        <Pencil className="h-4 w-4" />
                      </button>
                      <button
                        className="btn-ghost px-2 py-1"
                        onClick={() => { setResetting(a); setNewPassword(''); }}
                        title="Reset password"
                        aria-label={`Reset password for ${a.fullName}`}
                      >
                        <KeyRound className="h-4 w-4" />
                      </button>
                      <button
                        className={`btn-ghost px-2 py-1 ${a.enabled ? 'text-rose-700' : 'text-brand-700'}`}
                        onClick={() => toggle(a)}
                        disabled={a.id === user?.id || busyId === a.id}
                        title={a.enabled ? 'Deactivate' : 'Activate'}
                        aria-label={`${a.enabled ? 'Deactivate' : 'Activate'} ${a.fullName}`}
                      >
                        {busyId === a.id ? <Spinner className="h-4 w-4" /> : <Power className="h-4 w-4" />}
                        <span className="hidden text-xs lg:inline">{a.enabled ? 'Deactivate' : 'Activate'}</span>
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal open={editing !== null} title={editing === 'new' ? 'New official account' : 'Edit account'} onClose={() => setEditing(null)}>
        {editing !== null && (
          <AccountForm
            key={editing === 'new' ? 'new' : editing.id}
            editing={editing === 'new' ? null : editing}
            onCancel={() => setEditing(null)}
            onDone={(msg) => {
              setEditing(null);
              setNotice(msg);
              load();
            }}
          />
        )}
      </Modal>

      <Modal open={!!resetting} title={`Reset password${resetting ? `: ${resetting.fullName}` : ''}`} onClose={() => setResetting(null)}>
        <form onSubmit={reset} className="space-y-4">
          <p className="text-sm text-ink-600">Set a temporary password and share it with the person securely. They can change it later from their Profile page.</p>
          <div>
            <label className="label" htmlFor="reset-pw">New password</label>
            <input id="reset-pw" className="input" autoComplete="off" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />
            <p className="mt-1 text-xs text-ink-500">8-72 characters with at least one letter and one number.</p>
          </div>
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-secondary" onClick={() => setResetting(null)}>Cancel</button>
            <button type="submit" className="btn-primary" disabled={newPassword.length < 8 || busyId !== null}>Reset password</button>
          </div>
        </form>
      </Modal>
    </div>
  );
}

const ACTION_FILTERS = [
  'LOGIN', 'LOGIN_FAILED', 'COMPLAINT_CREATED', 'COMPLAINT_ROUTED', 'OFFICER_ASSIGNED', 'COMPLAINT_RESOLVED',
  'COMPLAINT_REJECTED', 'COMPLAINT_REOPENED', 'DUPLICATE_CONFIRMED', 'OFFICIAL_CREATED', 'ACCOUNT_DEACTIVATED',
  'PASSWORD_RESET_BY_ADMIN',
];

function AuditTab() {
  const [page, setPage] = useState(0);
  const [action, setAction] = useState('');
  const [data, setData] = useState<Page<AuditEntry> | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    adminApi.audit(page, action).then(setData).catch((e) => setError(errorMessage(e)));
  }, [page, action]);

  return (
    <div className="space-y-4">
      <div className="card flex flex-wrap items-center gap-2 p-3">
        <select className="input w-auto" value={action} onChange={(e) => { setAction(e.target.value); setPage(0); }} aria-label="Action">
          <option value="">All actions</option>
          {ACTION_FILTERS.map((a) => <option key={a} value={a}>{a.replace(/_/g, ' ').toLowerCase()}</option>)}
        </select>
        <p className="text-sm text-ink-500">Every sensitive operation is recorded with who did it and when.</p>
      </div>
      {error && <Alert kind="error">{error}</Alert>}
      {!data ? (
        <FullPageSpinner />
      ) : data.items.length === 0 ? (
        <EmptyState icon={History} title="No audit events yet" />
      ) : (
        <>
          <div className="card overflow-x-auto">
            <table className="w-full min-w-[720px] text-sm">
              <thead className="border-b border-ink-200 bg-ink-50 text-left text-xs uppercase tracking-wide text-ink-500">
                <tr>
                  <th className="px-4 py-3 font-semibold">When</th>
                  <th className="px-4 py-3 font-semibold">Action</th>
                  <th className="px-4 py-3 font-semibold">By</th>
                  <th className="px-4 py-3 font-semibold">Target</th>
                  <th className="px-4 py-3 font-semibold">Details</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-ink-100">
                {data.items.map((e) => (
                  <tr key={e.id}>
                    <td className="whitespace-nowrap px-4 py-2.5 text-ink-600">{formatDateTime(e.createdAt)}</td>
                    <td className="px-4 py-2.5"><span className="chip bg-ink-100 font-mono text-[11px] text-ink-700">{e.action}</span></td>
                    <td className="px-4 py-2.5">
                      <p className="text-ink-900">{e.actorName}</p>
                      {e.actorEmail && <p className="text-xs text-ink-500">{e.actorEmail}</p>}
                    </td>
                    <td className="px-4 py-2.5 text-ink-600">{e.entityType.toLowerCase()} #{e.entityId}</td>
                    <td className="max-w-xs truncate px-4 py-2.5 text-xs text-ink-500" title={e.details}>{e.details ?? ''}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
        </>
      )}
    </div>
  );
}

export default function AdminPage() {
  const [tab, setTab] = useState<'accounts' | 'audit'>('accounts');
  return (
    <div className="space-y-5">
      <div>
        <h1 className="page-title">Administration</h1>
        <p className="mt-1 text-ink-600">Manage official accounts and review the audit trail.</p>
      </div>
      <div className="inline-flex rounded-lg border border-ink-300 bg-white p-0.5 text-sm" role="tablist">
        {[
          { id: 'accounts' as const, label: 'Accounts', icon: ShieldCheck },
          { id: 'audit' as const, label: 'Audit log', icon: History },
        ].map((t) => (
          <button
            key={t.id}
            role="tab"
            aria-selected={tab === t.id}
            className={`inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 font-medium ${tab === t.id ? 'bg-brand-700 text-white' : 'text-ink-700'}`}
            onClick={() => setTab(t.id)}
          >
            <t.icon className="h-4 w-4" /> {t.label}
          </button>
        ))}
      </div>
      {tab === 'accounts' ? <AccountsTab /> : <AuditTab />}
    </div>
  );
}
