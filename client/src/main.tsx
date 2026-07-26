import React from 'react';
import ReactDOM from 'react-dom/client';
import 'normalize.css';
import '@styles/tailwind.css';
import App from '@src/App';
import { registerGlobalApiErrorHandlers } from '@utils/apiError';

registerGlobalApiErrorHandlers();

const root = ReactDOM.createRoot(
  document.getElementById('root') as HTMLElement
);

root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);

declare const module: { hot?: { accept: (path: string, cb: () => void) => void } };
if (module.hot) {
  module.hot.accept('@src/App', () => {
    const NextApp = require('@src/App').default;
    root.render(
      <React.StrictMode>
        <NextApp />
      </React.StrictMode>
    );
  });
}
