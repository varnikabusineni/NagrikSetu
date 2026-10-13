import { Navigate, Outlet, useLocation } from 'react-router-dom';
import type { RoleCode } from '../api/types';
import { FullPageSpinner } from '../components/Feedback';
import { useAuth } from './AuthContext';

/** Client-side guard for navigation only; the API enforces every permission independently. */
export function ProtectedRoute({ roles }: { roles?: RoleCode[] }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <FullPageSpinner />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  if (roles && !roles.includes(user.role)) {
    return <Navigate to={user.role === 'CITIZEN' ? '/' : '/official'} replace />;
  }
  return <Outlet />;
}
