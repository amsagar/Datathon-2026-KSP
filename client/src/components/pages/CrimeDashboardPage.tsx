import React from 'react';
import { Navigate } from 'react-router-dom';
import { analyticsChatPath } from '@constants/routePaths';

/** Legacy URL → analytics inside chat. */
const CrimeDashboardPage: React.FC = () => (
  <Navigate to={analyticsChatPath('dashboard')} replace />
);

export default CrimeDashboardPage;
