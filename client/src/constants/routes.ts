import React from 'react';
import { ROUTE_PATHS } from '@constants/routePaths';

// ChatWorkspace is the landing page — keep it eager so first paint doesn't
// wait on a second chunk. Everything else is route-split via React.lazy
// (a lazy component is still a component, so RouteDefinition is unchanged).
import ChatWorkspace from '@pages/ChatWorkspace';
import NotFoundPage from '@pages/NotFoundPage';

const ShareChatPage = React.lazy(() => import('@pages/ShareChatPage'));
const SettingsPage = React.lazy(() => import('@pages/SettingsPage'));
const CrimeDashboardPage = React.lazy(
  () => import('@pages/CrimeDashboardPage')
);
const CrimeMapPage = React.lazy(() => import('@pages/CrimeMapPage'));
const CrimeNetworkPage = React.lazy(() => import('@pages/CrimeNetworkPage'));
const OffendersPage = React.lazy(() => import('@pages/OffendersPage'));

export interface RouteDefinition {
  key: string;
  path: string;
  element: React.ComponentType;
  title: string;
}

export const ROUTES: RouteDefinition[] = [
  {
    key: 'chat',
    path: ROUTE_PATHS.CHAT,
    element: ChatWorkspace,
    title: 'KSP Crime Intelligence',
  },
  {
    key: 'share-chat',
    path: ROUTE_PATHS.SHARE_CHAT,
    element: ShareChatPage,
    title: 'Shared chat · KSP Crime Intelligence',
  },
  {
    key: 'settings-root',
    path: ROUTE_PATHS.SETTINGS,
    element: SettingsPage,
    title: 'Settings · KSP Crime Intelligence',
  },
  {
    key: 'settings-section',
    path: ROUTE_PATHS.SETTINGS_SECTION,
    element: SettingsPage,
    title: 'Settings · KSP Crime Intelligence',
  },
  {
    key: 'crime-dashboard',
    path: ROUTE_PATHS.DASHBOARD,
    element: CrimeDashboardPage,
    title: 'Crime Dashboard',
  },
  {
    key: 'crime-map',
    path: ROUTE_PATHS.CRIME_MAP,
    element: CrimeMapPage,
    title: 'Crime Hotspot Map',
  },
  {
    key: 'crime-network',
    path: ROUTE_PATHS.CRIME_NETWORK,
    element: CrimeNetworkPage,
    title: 'Criminal Network',
  },
  {
    key: 'offenders',
    path: ROUTE_PATHS.OFFENDERS,
    element: OffendersPage,
    title: 'Offender Risk',
  },
  {
    key: 'not-found',
    path: ROUTE_PATHS.NOT_FOUND,
    element: NotFoundPage,
    title: 'Not Found',
  },
];
