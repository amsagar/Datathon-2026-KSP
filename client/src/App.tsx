import React from 'react';
import { BrowserRouter } from 'react-router-dom';
import CustomRoutes from '@routes/Routes';
import AppErrorBoundary from '@organisms/AppErrorBoundary';
import { NotificationProvider } from '@providers/NotificationProviders';
import ThemeInitializer from '@atoms/ThemeInitializer';
import { ConfirmHost } from '@atoms/CustomConfirm';
import { Toaster } from '@/components/ui/sonner';
import * as styles from '@styles/app.module.scss';
import '@styles/global.scss';

const App: React.FC = () => {
  return (
    <>
      <ThemeInitializer />
      <NotificationProvider>
        <section className={styles.body}>
          <ConfirmHost />
          <Toaster position="top-center" richColors />
          <BrowserRouter>
            <AppErrorBoundary>
              <CustomRoutes />
            </AppErrorBoundary>
          </BrowserRouter>
        </section>
      </NotificationProvider>
    </>
  );
};

export default App;
