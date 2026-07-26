import React from 'react';
import { Navigate } from 'react-router-dom';
import { analyticsChatPath } from '@constants/routePaths';

const CrimeMapPage: React.FC = () => <Navigate to={analyticsChatPath('map')} replace />;

export default CrimeMapPage;
