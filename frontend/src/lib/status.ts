import type { ComplaintStatus } from '../api/types';

export const STATUS_META: Record<ComplaintStatus, { label: string; badge: string; dot: string; marker: string; description: string }> = {
  SUBMITTED: {
    label: 'Submitted',
    badge: 'bg-sky-50 text-sky-800 ring-sky-200',
    dot: 'bg-sky-500',
    marker: '#0284c7',
    description: 'Received and waiting for the ward office to review',
  },
  UNDER_REVIEW: {
    label: 'Under Review',
    badge: 'bg-amber-50 text-amber-800 ring-amber-200',
    dot: 'bg-amber-500',
    marker: '#d97706',
    description: 'Verified by the ward office and being routed to a department',
  },
  ASSIGNED: {
    label: 'Assigned',
    badge: 'bg-violet-50 text-violet-800 ring-violet-200',
    dot: 'bg-violet-500',
    marker: '#7c3aed',
    description: 'A field officer has been assigned',
  },
  IN_PROGRESS: {
    label: 'In Progress',
    badge: 'bg-orange-50 text-orange-800 ring-orange-200',
    dot: 'bg-orange-500',
    marker: '#ea580c',
    description: 'Work is under way',
  },
  RESOLVED: {
    label: 'Resolved',
    badge: 'bg-brand-50 text-brand-800 ring-brand-200',
    dot: 'bg-brand-500',
    marker: '#15784b',
    description: 'The department reports the issue as fixed',
  },
  REJECTED: {
    label: 'Rejected',
    badge: 'bg-rose-50 text-rose-800 ring-rose-200',
    dot: 'bg-rose-500',
    marker: '#e11d48',
    description: 'Closed by an official with a stated reason',
  },
  REOPENED: {
    label: 'Reopened',
    badge: 'bg-fuchsia-50 text-fuchsia-800 ring-fuchsia-200',
    dot: 'bg-fuchsia-500',
    marker: '#c026d3',
    description: 'An official determined more work is needed',
  },
};

export const ALL_STATUSES = Object.keys(STATUS_META) as ComplaintStatus[];

/** The main lifecycle, used for the progress tracker on complaint pages. */
export const LIFECYCLE: ComplaintStatus[] = ['SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED'];

export const PRIORITY_META: Record<string, string> = {
  LOW: 'bg-ink-100 text-ink-700',
  NORMAL: 'bg-ink-100 text-ink-700',
  HIGH: 'bg-orange-100 text-orange-800',
  URGENT: 'bg-rose-100 text-rose-800',
};

export const EVENT_LABELS: Record<string, string> = {
  CREATED: 'Complaint submitted',
  STATUS_CHANGE: 'Status changed',
  WARD_VERIFIED: 'Ward confirmed',
  ROUTED: 'Routed',
  ASSIGNED: 'Officer assigned',
  REASSIGNED: 'Officer reassigned',
  INFO_REQUESTED: 'More information requested',
  CITIZEN_RESPONSE: 'Citizen added information',
  PUBLIC_UPDATE: 'Official update',
  INTERNAL_NOTE: 'Internal note',
  DUPLICATE_CONFIRMED: 'Linked as duplicate',
  PRIORITY_CHANGED: 'Priority changed',
};
