import React, { useMemo } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import CustomButton from '@atoms/CustomButton';
import { ROUTE_PATHS } from '@constants/routePaths';
import { getErrorDetails } from '@utils/apiError';
import * as styles from '@styles/errorPage.module.scss';
import KspLogo from '@atoms/KspLogo';

export interface ErrorPageProps {
  status?: number;
  message?: string | null;
  onRetry?: () => void;
}

const ErrorPage: React.FC<ErrorPageProps> = ({
  status: statusProp,
  message: messageProp,
  onRetry,
}) => {
  const [params] = useSearchParams();

  const status = useMemo(() => {
    if (statusProp !== undefined) return statusProp;
    const raw = params.get('status');
    if (!raw) return undefined;
    const parsed = Number.parseInt(raw, 10);
    return Number.isNaN(parsed) ? undefined : parsed;
  }, [statusProp, params]);

  const message = messageProp ?? params.get('message');
  const details = getErrorDetails(status, message);

  const handleRetry = () => {
    if (onRetry) {
      onRetry();
      return;
    }
    window.location.href = ROUTE_PATHS.CHAT;
  };

  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <KspLogo className={styles.brandMark} />
        {details.status && (
          <p className={styles.status}>Error {details.status}</p>
        )}
        <h1 className={styles.title}>{details.title}</h1>
        <p className={styles.message}>{details.message}</p>
        <div className={styles.actions}>
          {details.retryable && (
            <CustomButton
              variant="primary"
              className={styles.retryBtn}
              onClick={handleRetry}
            >
              Try again
            </CustomButton>
          )}
          <Link className={styles.backLink} to={ROUTE_PATHS.CHAT}>
            Back to chat
          </Link>
        </div>
      </div>
    </div>
  );
};

export default ErrorPage;
