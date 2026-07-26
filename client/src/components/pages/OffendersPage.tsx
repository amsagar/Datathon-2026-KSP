import React from 'react';
import { Navigate } from 'react-router-dom';
import { analyticsChatPath } from '@constants/routePaths';

const OffendersPage: React.FC = () => <Navigate to={analyticsChatPath('offenders')} replace />;

export default OffendersPage;
