import React from 'react';
import { Navigate } from 'react-router-dom';
import { settingsPath } from '@constants/routePaths';

/** Appearance is only available from the account menu modal. */
const AppearancePage: React.FC = () => (
  <Navigate to={settingsPath('assistants')} replace />
);

export default AppearancePage;
