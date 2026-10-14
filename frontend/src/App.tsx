import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { FullPageSpinner } from './components/Feedback';
import { Layout } from './components/Layout';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';

const DashboardPage = lazy(() => import('./pages/DashboardPage'));
const ReportIssuePage = lazy(() => import('./pages/ReportIssuePage'));
const MyComplaintsPage = lazy(() => import('./pages/MyComplaintsPage'));
const ComplaintDetailPage = lazy(() => import('./pages/ComplaintDetailPage'));
const CommunityPage = lazy(() => import('./pages/CommunityPage'));
const MapPage = lazy(() => import('./pages/MapPage'));
const NotificationsPage = lazy(() => import('./pages/NotificationsPage'));
const ProfilePage = lazy(() => import('./pages/ProfilePage'));
const OfficialQueuePage = lazy(() => import('./pages/official/OfficialQueuePage'));
const AdminPage = lazy(() => import('./pages/admin/AdminPage'));
const NotFoundPage = lazy(() => import('./pages/NotFoundPage'));

function PublicOnly({ children }: { children: JSX.Element }) {
  const { user, loading } = useAuth();
  if (loading) return <FullPageSpinner />;
  if (user) return <Navigate to={user.role === 'CITIZEN' ? '/' : '/official'} replace />;
  return children;
}

export function App() {
  return (
    <Suspense fallback={<FullPageSpinner />}>
      <Routes>
        <Route path="/login" element={<PublicOnly><LoginPage /></PublicOnly>} />
        <Route path="/register" element={<PublicOnly><RegisterPage /></PublicOnly>} />
        <Route element={<ProtectedRoute />}>
          <Route element={<Layout />}>
            <Route element={<ProtectedRoute roles={['CITIZEN']} />}>
              <Route index element={<DashboardPage />} />
              <Route path="/report" element={<ReportIssuePage />} />
              <Route path="/complaints" element={<MyComplaintsPage />} />
            </Route>
            <Route path="/complaints/:id" element={<ComplaintDetailPage />} />
            <Route path="/community" element={<CommunityPage />} />
            <Route path="/map" element={<MapPage />} />
            <Route path="/notifications" element={<NotificationsPage />} />
            <Route path="/profile" element={<ProfilePage />} />
            <Route element={<ProtectedRoute roles={['WARD_HEAD', 'DEPARTMENT_HEAD', 'OFFICER', 'ADMIN']} />}>
              <Route path="/official" element={<OfficialQueuePage />} />
            </Route>
            <Route element={<ProtectedRoute roles={['ADMIN']} />}>
              <Route path="/admin" element={<AdminPage />} />
            </Route>
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Route>
      </Routes>
    </Suspense>
  );
}
