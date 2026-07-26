import React, { Suspense, createElement, useEffect } from 'react';
import { Routes, Route, useLocation } from 'react-router-dom';
import { ROUTES } from '@constants/routes';
import { ROUTE_PATHS } from '@constants/routePaths';
import {
  isAuthenticated,
  redirectToSso,
  getMustChangePassword,
} from '@apiCalls/auth';
import CustomSpinner from '@atoms/CustomSpinner';
import LoginPage from '@pages/LoginPage';
import ErrorPage from '@pages/ErrorPage';
import ForcePasswordChangePage from '@pages/ForcePasswordChangePage';

/** Minimal centered fallback shown while a lazy route chunk downloads. */
const RouteFallback: React.FC = () => (
  <div
    style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '100vh',
    }}
  >
    <CustomSpinner size="large" />
  </div>
);

const RequireAuth: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  if (!isAuthenticated()) {
    redirectToSso();
    return null;
  }
  // Users issued an admin temp password must set a new one before using the app.
  if (getMustChangePassword()) {
    return <ForcePasswordChangePage />;
  }
  return <>{children}</>;
};

const CustomRoutes: React.FC = () => {
  const location = useLocation();

  useEffect(() => {
    const match = ROUTES.find((r) => r.path === location.pathname);
    document.title = match ? match.title : 'KSP Crime Intelligence';
  }, [location.pathname]);

  return (
    <Routes>
      <Route path={ROUTE_PATHS.LOGIN} element={<LoginPage />} />
      <Route path={ROUTE_PATHS.ERROR} element={<ErrorPage />} />
      {ROUTES.map((route) => (
        <Route
          key={route.key}
          path={route.path}
          element={
            <RequireAuth>
              <Suspense fallback={<RouteFallback />}>
                {createElement(route.element)}
              </Suspense>
            </RequireAuth>
          }
        />
      ))}
    </Routes>
  );
};

export default CustomRoutes;
