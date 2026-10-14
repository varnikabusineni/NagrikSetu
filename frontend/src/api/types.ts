export type RoleCode = 'CITIZEN' | 'WARD_HEAD' | 'DEPARTMENT_HEAD' | 'OFFICER' | 'ADMIN';

export type ComplaintStatus =
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'RESOLVED'
  | 'REJECTED'
  | 'REOPENED';

export type Priority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
export type Access = 'OWNER' | 'OFFICIAL' | 'PUBLIC';
export type WardMatch = 'AUTO_MATCHED' | 'PENDING_VERIFICATION' | 'VERIFIED';

export interface User {
  id: number;
  fullName: string;
  email: string;
  phone?: string;
  locality?: string;
  address?: string;
  role: RoleCode;
  roleName: string;
  wardId?: number;
  wardName?: string;
  departmentId?: number;
  departmentName?: string;
  permissions: string[];
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  expiresAt: string;
  user: User;
}

export interface Category {
  id: number;
  code: string;
  name: string;
  description?: string;
  defaultDepartment?: string;
}

export interface Department {
  id: number;
  code: string;
  name: string;
  description?: string;
}

export interface Ward {
  id: number;
  code: string;
  name: string;
  city: string;
  hasBoundary: boolean;
  boundaryJson?: string;
  boundarySource?: string;
}

export interface Evidence {
  id: string;
  type: 'CITIZEN_REPORT' | 'RESOLUTION';
  url: string;
  contentType: string;
  filename?: string;
  sizeBytes: number;
  createdAt: string;
  uploadedByRole: string;
}

export interface ComplaintSummary {
  id: number;
  referenceNo: string;
  title: string;
  categoryCode: string;
  categoryName: string;
  status: ComplaintStatus;
  priority: Priority;
  locality: string;
  latitude: number;
  longitude: number;
  createdAt: string;
  updatedAt: string;
  upvoteCount: number;
  followerCount: number;
  thumbnailUrl?: string;
  photoCount: number;
  upvoted: boolean;
  followed: boolean;
  mine: boolean;
  wardName?: string;
  departmentName?: string;
  duplicateOfReference?: string;
  wardVerificationPending?: boolean;
}

export interface TimelineEntry {
  id: number;
  eventType: string;
  fromStatus?: ComplaintStatus;
  toStatus: ComplaintStatus;
  note?: string;
  actorRole: string;
  actorName?: string;
  internal: boolean;
  createdAt: string;
}

export interface ComplaintDetail {
  id: number;
  referenceNo: string;
  title: string;
  description: string;
  categoryCode: string;
  categoryName: string;
  status: ComplaintStatus;
  priority: Priority;
  locality: string;
  address: string;
  latitude: number;
  longitude: number;
  isPublic: boolean;
  createdAt: string;
  updatedAt: string;
  resolvedAt?: string;
  wardMatch?: WardMatch;
  wardName?: string;
  departmentName?: string;
  assignedOfficerName?: string;
  rejectionReason?: string;
  duplicateOfReference?: string;
  duplicateOfId?: number;
  upvoteCount: number;
  followerCount: number;
  upvoted: boolean;
  followed: boolean;
  access: Access;
  citizenEvidence: Evidence[];
  resolutionEvidence: Evidence[];
  timeline: TimelineEntry[];
  resolution?: { summary: string; resolvedAt: string; resolvedByRole: string; resolvedByName?: string };
  feedback?: { rating: number; comment?: string; createdAt: string; updatedAt: string };
  reporter?: { fullName: string; email: string; phone?: string };
  assignments?: {
    type: string;
    target: string;
    assignedByName: string;
    note?: string;
    active: boolean;
    createdAt: string;
    endedAt?: string;
  }[];
  canGiveFeedback: boolean;
  canRespond: boolean;
  canParticipate: boolean;
  allowedActions: string[];
}

export interface MapMarker {
  id: number;
  referenceNo: string;
  title: string;
  categoryCode: string;
  categoryName: string;
  status: ComplaintStatus;
  latitude: number;
  longitude: number;
  locality: string;
  createdAt: string;
  upvoteCount: number;
  mine: boolean;
}

export interface NearbyComplaint {
  id: number;
  referenceNo: string;
  title: string;
  categoryName: string;
  status: ComplaintStatus;
  locality: string;
  createdAt: string;
  upvoteCount: number;
  distanceMeters: number;
  thumbnailUrl?: string;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface AppNotification {
  id: number;
  type: string;
  title: string;
  message: string;
  complaintId?: number;
  complaintReference?: string;
  read: boolean;
  createdAt: string;
}

export interface CitizenDashboard {
  totals: { total: number; awaitingReview: number; inProgress: number; resolved: number; rejected: number };
  byStatus: Record<ComplaintStatus, number>;
  recentlyUpdated: ComplaintSummary[];
  notifications: AppNotification[];
  unreadNotifications: number;
  communityAttention: ComplaintSummary[];
}

export interface CommunityStats {
  byStatus: Partial<Record<ComplaintStatus, number>>;
  byCategory: Record<string, number>;
  resolvedLast30Days: number;
  openIssues: number;
}

export interface OfficialStats {
  byStatus: Record<ComplaintStatus, number>;
  pendingWardVerification: number;
  pendingDuplicateFlags: number;
  scopeLabel: string;
}

export interface Officer {
  id: number;
  fullName: string;
  email: string;
  departmentName: string;
}

export interface DuplicateFlag {
  id: number;
  complaintId: number;
  complaintReference: string;
  complaintTitle: string;
  duplicateOfId: number;
  duplicateOfReference: string;
  duplicateOfTitle: string;
  reason?: string;
  status: string;
  createdAt: string;
}

export interface ApiErrorBody {
  status: number;
  code: string;
  message: string;
  fieldErrors?: Record<string, string>;
}

export interface AdminAccount {
  id: number;
  fullName: string;
  email: string;
  phone?: string;
  role: RoleCode;
  roleName: string;
  wardId?: number;
  wardName?: string;
  departmentId?: number;
  departmentName?: string;
  enabled: boolean;
  createdAt: string;
  lastLoginAt?: string;
}

export interface AuditEntry {
  id: number;
  createdAt: string;
  action: string;
  entityType: string;
  entityId?: string;
  actorName: string;
  actorEmail?: string;
  details?: string;
  ipAddress?: string;
}
