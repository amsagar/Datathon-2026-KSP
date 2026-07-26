import React from 'react';
import { Link } from 'react-router-dom';
import { ROUTE_PATHS } from '@constants/routePaths';

const NotFoundPage: React.FC = () => (
  <div
    style={{
      minHeight: '100vh',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      gap: 12,
      color: '#b01722',
    }}
  >
    <h1 style={{ margin: 0, fontSize: 48 }}>404</h1>
    <p>The page you're looking for doesn't exist.</p>
    <Link to={ROUTE_PATHS.CHAT}>Back to chat</Link>
  </div>
);

export default NotFoundPage;
