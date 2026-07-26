import React from 'react';
import { Navigate, useSearchParams } from 'react-router-dom';
import { analyticsChatPath } from '@constants/routePaths';

const CrimeNetworkPage: React.FC = () => {
  const [params] = useSearchParams();
  const personUid = params.get('personUid');
  return (
    <Navigate
      to={analyticsChatPath('network', personUid ? { personUid } : undefined)}
      replace
    />
  );
};

export default CrimeNetworkPage;
