import { useEffect, useState } from 'react';
import { errorMessage } from '../../api/client';
import { officialApi } from '../../api/endpoints';
import type { ComplaintDetail, Officer, Priority } from '../../api/types';
import { Alert, Spinner } from '../../components/Feedback';
import { Modal } from '../../components/Modal';
import { PhotoPicker } from '../../components/PhotoPicker';
import { useDepartments, useWards } from '../../hooks/useReference';

const ACTIONS: Record<string, { label: string; primary?: boolean; danger?: boolean }> = {
  VERIFY_WARD: { label: 'Confirm ward', primary: true },
  REVIEW: { label: 'Mark as verified', primary: true },
  ROUTE: { label: 'Route to department', primary: true },
  ASSIGN_OFFICER: { label: 'Assign officer', primary: true },
  START_WORK: { label: 'Start work', primary: true },
  RESOLVE: { label: 'Resolve with evidence', primary: true },
  REQUEST_INFO: { label: 'Request information' },
  POST_UPDATE: { label: 'Post update / note' },
  SET_PRIORITY: { label: 'Change priority' },
  REOPEN: { label: 'Reopen' },
  REJECT: { label: 'Reject', danger: true },
};

/** Workflow actions offered by the API for this official. The server re-checks each one. */
export function OfficialActions({ complaint, onUpdated }: { complaint: ComplaintDetail; onUpdated: (c: ComplaintDetail) => void }) {
  const [open, setOpen] = useState<string | null>(null);
  const [text, setText] = useState('');
  const [selection, setSelection] = useState('');
  const [visible, setVisible] = useState(true);
  const [photos, setPhotos] = useState<File[]>([]);
  const [progress, setProgress] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [officers, setOfficers] = useState<Officer[]>([]);
  const departments = useDepartments();
  const wards = useWards();

  useEffect(() => {
    if (open === 'ASSIGN_OFFICER') officialApi.officers().then(setOfficers).catch(() => setOfficers([]));
  }, [open]);

  const start = (action: string) => {
    setOpen(action);
    setText('');
    setSelection(action === 'SET_PRIORITY' ? complaint.priority : '');
    setVisible(true);
    setPhotos([]);
    setError('');
  };

  const run = async () => {
    setBusy(true);
    setError('');
    try {
      let updated: ComplaintDetail;
      const id = complaint.id;
      switch (open) {
        case 'VERIFY_WARD': updated = await officialApi.action(id, 'verify-ward', { wardId: Number(selection), note: text }); break;
        case 'REVIEW': updated = await officialApi.action(id, 'review', { note: text }); break;
        case 'ROUTE': updated = await officialApi.action(id, 'route', { departmentId: Number(selection), note: text }); break;
        case 'ASSIGN_OFFICER': updated = await officialApi.action(id, 'assign', { officerId: Number(selection), note: text }); break;
        case 'START_WORK': updated = await officialApi.action(id, 'start', { note: text }); break;
        case 'REJECT': updated = await officialApi.action(id, 'reject', { reason: text }); break;
        case 'REOPEN': updated = await officialApi.action(id, 'reopen', { reason: text }); break;
        case 'REQUEST_INFO': updated = await officialApi.action(id, 'request-info', { message: text }); break;
        case 'POST_UPDATE': updated = await officialApi.action(id, 'updates', { message: text, visibleToCitizen: visible }); break;
        case 'SET_PRIORITY': updated = await officialApi.setPriority(id, selection as Priority); break;
        case 'RESOLVE': {
          const form = new FormData();
          form.append('summary', text);
          photos.forEach((p) => form.append('photos', p, p.name));
          setProgress(photos.length ? 0 : null);
          updated = await officialApi.resolve(id, form, photos.length ? setProgress : undefined);
          break;
        }
        default: return;
      }
      onUpdated(updated);
      setOpen(null);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
      setProgress(null);
    }
  };

  if (complaint.allowedActions.length === 0) {
    return <p className="text-sm text-ink-500">No actions are available to you for this complaint in its current state.</p>;
  }

  const needsSelection = ['VERIFY_WARD', 'ROUTE', 'ASSIGN_OFFICER', 'SET_PRIORITY'].includes(open ?? '');
  const minText = open === 'REJECT' || open === 'REOPEN' || open === 'RESOLVE' ? 10 : open === 'REQUEST_INFO' || open === 'POST_UPDATE' ? 3 : 0;
  const canSubmit = (!needsSelection || selection) && text.trim().length >= minText;

  return (
    <>
      <div className="flex flex-wrap gap-2">
        {complaint.allowedActions.filter((a) => ACTIONS[a]).map((a) => (
          <button key={a} className={ACTIONS[a].primary ? 'btn-primary' : ACTIONS[a].danger ? 'btn-danger' : 'btn-secondary'} onClick={() => start(a)}>
            {ACTIONS[a].label}
          </button>
        ))}
      </div>
      <Modal
        open={!!open}
        title={open ? ACTIONS[open].label : ''}
        onClose={() => !busy && setOpen(null)}
        footer={
          <>
            <button className="btn-secondary" onClick={() => setOpen(null)} disabled={busy}>Cancel</button>
            <button className={open === 'REJECT' ? 'btn-danger' : 'btn-primary'} onClick={run} disabled={busy || !canSubmit}>
              {busy && <Spinner className="h-4 w-4 text-white" />} Confirm
            </button>
          </>
        }
      >
        <div className="space-y-4">
          {error && <Alert kind="error">{error}</Alert>}
          {open === 'VERIFY_WARD' && (
            <div>
              <label className="label" htmlFor="ward">Ward</label>
              <select id="ward" className="input" value={selection} onChange={(e) => setSelection(e.target.value)}>
                <option value="">Choose a ward</option>
                {wards.map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}
              </select>
            </div>
          )}
          {open === 'ROUTE' && (
            <div>
              <label className="label" htmlFor="dept">Department</label>
              <select id="dept" className="input" value={selection} onChange={(e) => setSelection(e.target.value)}>
                <option value="">Choose a department</option>
                {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
              </select>
              <p className="mt-1 text-xs text-ink-500">The department sees the same complaint, photos and history. Nothing is copied.</p>
            </div>
          )}
          {open === 'ASSIGN_OFFICER' && (
            <div>
              <label className="label" htmlFor="officer">Field officer</label>
              <select id="officer" className="input" value={selection} onChange={(e) => setSelection(e.target.value)}>
                <option value="">Choose an officer</option>
                {officers.map((o) => <option key={o.id} value={o.id}>{o.fullName}</option>)}
              </select>
              {officers.length === 0 && <p className="mt-1 text-xs text-ink-500">No active officers found in your department.</p>}
            </div>
          )}
          {open === 'SET_PRIORITY' && (
            <div>
              <label className="label" htmlFor="priority">Priority</label>
              <select id="priority" className="input" value={selection} onChange={(e) => setSelection(e.target.value)}>
                {['LOW', 'NORMAL', 'HIGH', 'URGENT'].map((p) => <option key={p} value={p}>{p.charAt(0) + p.slice(1).toLowerCase()}</option>)}
              </select>
            </div>
          )}
          {open !== 'SET_PRIORITY' && (
            <div>
              <label className="label" htmlFor="text">
                {open === 'REJECT' ? 'Official reason (shown to the citizen)' : open === 'REOPEN' ? 'Reason for reopening' : open === 'RESOLVE' ? 'Resolution summary (shown to the citizen)' : open === 'REQUEST_INFO' ? 'What information do you need?' : open === 'POST_UPDATE' ? 'Update' : 'Note (optional)'}
              </label>
              <textarea id="text" rows={4} className="input" value={text} onChange={(e) => setText(e.target.value)} maxLength={2000} />
              {minText > 0 && <p className="mt-1 text-xs text-ink-500">At least {minText} characters.</p>}
            </div>
          )}
          {open === 'POST_UPDATE' && (
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={visible} onChange={(e) => setVisible(e.target.checked)} className="h-4 w-4 rounded border-ink-300 text-brand-700" />
              Visible to the citizen (uncheck for an internal note)
            </label>
          )}
          {open === 'RESOLVE' && (
            <>
              <PhotoPicker files={photos} onChange={setPhotos} disabled={busy} label="Resolution photos (after the work)" />
              <p className="text-xs text-ink-500">Resolution photos are stored separately from the citizen&apos;s original evidence.</p>
              {progress != null && (
                <div className="h-2 overflow-hidden rounded-full bg-ink-100"><div className="h-full bg-brand-600" style={{ width: `${progress}%` }} /></div>
              )}
            </>
          )}
        </div>
      </Modal>
    </>
  );
}
