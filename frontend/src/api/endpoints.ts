import { api } from './client';
import type {
  AppNotification,
  AuthResponse,
  Category,
  CitizenDashboard,
  CommunityStats,
  ComplaintDetail,
  ComplaintStatus,
  ComplaintSummary,
  Department,
  DuplicateFlag,
  MapMarker,
  NearbyComplaint,
  Officer,
  OfficialStats,
  Page,
  Priority,
  User,
  Ward,
} from './types';

export interface RegisterInput {
  fullName: string;
  email: string;
  phone: string;
  password: string;
  locality: string;
  address?: string;
}

export interface FeedParams {
  q?: string;
  category?: string;
  status?: ComplaintStatus[];
  locality?: string;
  from?: string;
  to?: string;
  sort?: 'newest' | 'activity' | 'updated' | 'oldest';
  mine?: boolean;
  page?: number;
  size?: number;
}

const paramsSerializer = { indexes: null } as const;

export const authApi = {
  login: (email: string, password: string) =>
    api.post<AuthResponse>('/api/auth/login', { email, password }).then((r) => r.data),
  register: (input: RegisterInput) => api.post<AuthResponse>('/api/auth/register', input).then((r) => r.data),
  me: () => api.get<User>('/api/auth/me').then((r) => r.data),
  updateProfile: (input: { fullName: string; phone: string; locality: string; address?: string }) =>
    api.put<User>('/api/auth/me', input).then((r) => r.data),
  changePassword: (currentPassword: string, newPassword: string) =>
    api.post('/api/auth/me/password', { currentPassword, newPassword }),
  logout: () => api.post('/api/auth/logout'),
};

export const referenceApi = {
  categories: () => api.get<Category[]>('/api/reference/categories').then((r) => r.data),
  departments: () => api.get<Department[]>('/api/reference/departments').then((r) => r.data),
  wards: () => api.get<Ward[]>('/api/reference/wards').then((r) => r.data),
};

export const complaintApi = {
  create: (form: FormData, onProgress?: (percent: number) => void) =>
    api
      .post<ComplaintDetail>('/api/complaints', form, {
        timeout: 120_000,
        onUploadProgress: (e) => {
          if (onProgress && e.total) onProgress(Math.round((e.loaded / e.total) * 100));
        },
      })
      .then((r) => r.data),
  feed: (params: FeedParams) =>
    api.get<Page<ComplaintSummary>>('/api/complaints', { params, paramsSerializer }).then((r) => r.data),
  get: (id: number | string) => api.get<ComplaintDetail>(`/api/complaints/${id}`).then((r) => r.data),
  map: (params: { category?: string; status?: ComplaintStatus[] }) =>
    api.get<MapMarker[]>('/api/complaints/map', { params, paramsSerializer }).then((r) => r.data),
  nearby: (lat: number, lng: number, category?: string) =>
    api.get<NearbyComplaint[]>('/api/complaints/nearby', { params: { lat, lng, category } }).then((r) => r.data),
  respond: (id: number, form: FormData, onProgress?: (percent: number) => void) =>
    api
      .post<ComplaintDetail>(`/api/complaints/${id}/responses`, form, {
        timeout: 120_000,
        onUploadProgress: (e) => {
          if (onProgress && e.total) onProgress(Math.round((e.loaded / e.total) * 100));
        },
      })
      .then((r) => r.data),
  upvote: (id: number) => api.post<{ upvoted: boolean; upvoteCount: number }>(`/api/complaints/${id}/upvote`).then((r) => r.data),
  removeUpvote: (id: number) =>
    api.delete<{ upvoted: boolean; upvoteCount: number }>(`/api/complaints/${id}/upvote`).then((r) => r.data),
  follow: (id: number) => api.post<{ followed: boolean; followerCount: number }>(`/api/complaints/${id}/follow`).then((r) => r.data),
  unfollow: (id: number) =>
    api.delete<{ followed: boolean; followerCount: number }>(`/api/complaints/${id}/follow`).then((r) => r.data),
  flagDuplicate: (id: number, duplicateOfReference: string, reason?: string) =>
    api
      .post<{ id: number; status: string; message: string }>(`/api/complaints/${id}/duplicate-flags`, {
        duplicateOfReference,
        reason,
      })
      .then((r) => r.data),
  giveFeedback: (id: number, rating: number, comment?: string) =>
    api.post(`/api/complaints/${id}/feedback`, { rating, comment }).then((r) => r.data),
  editFeedback: (id: number, rating: number, comment?: string) =>
    api.put(`/api/complaints/${id}/feedback`, { rating, comment }).then((r) => r.data),
};

export const dashboardApi = {
  citizen: () => api.get<CitizenDashboard>('/api/dashboard/citizen').then((r) => r.data),
  community: () => api.get<CommunityStats>('/api/dashboard/community').then((r) => r.data),
};

export const notificationApi = {
  list: (page = 0, unreadOnly = false) =>
    api.get<Page<AppNotification>>('/api/notifications', { params: { page, unreadOnly, size: 20 } }).then((r) => r.data),
  unreadCount: () => api.get<{ unread: number }>('/api/notifications/unread-count').then((r) => r.data.unread),
  markRead: (id: number) => api.post(`/api/notifications/${id}/read`),
  markAllRead: () => api.post('/api/notifications/read-all'),
};

export const officialApi = {
  queue: (params: { status?: ComplaintStatus[]; q?: string; category?: string; view?: string; page?: number }) =>
    api.get<Page<ComplaintSummary>>('/api/official/queue', { params, paramsSerializer }).then((r) => r.data),
  stats: () => api.get<OfficialStats>('/api/official/stats').then((r) => r.data),
  officers: (departmentId?: number) =>
    api.get<Officer[]>('/api/official/officers', { params: { departmentId } }).then((r) => r.data),
  action: (id: number, action: string, body: object = {}) =>
    api.post<ComplaintDetail>(`/api/official/complaints/${id}/${action}`, body).then((r) => r.data),
  resolve: (id: number, form: FormData, onProgress?: (percent: number) => void) =>
    api
      .post<ComplaintDetail>(`/api/official/complaints/${id}/resolve`, form, {
        timeout: 120_000,
        onUploadProgress: (e) => {
          if (onProgress && e.total) onProgress(Math.round((e.loaded / e.total) * 100));
        },
      })
      .then((r) => r.data),
  setPriority: (id: number, priority: Priority) =>
    api.post<ComplaintDetail>(`/api/official/complaints/${id}/priority`, { priority }).then((r) => r.data),
  duplicateFlags: () => api.get<DuplicateFlag[]>('/api/official/duplicate-flags').then((r) => r.data),
  reviewDuplicate: (flagId: number, confirm: boolean) =>
    api.post(`/api/official/duplicate-flags/${flagId}/${confirm ? 'confirm' : 'dismiss'}`),
};
