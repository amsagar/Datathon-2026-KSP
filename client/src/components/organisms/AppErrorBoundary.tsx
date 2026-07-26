import React from 'react';
import ErrorPage from '@pages/ErrorPage';

interface AppErrorBoundaryState {
  hasError: boolean;
  message?: string;
}

class AppErrorBoundary extends React.Component<
  { children: React.ReactNode },
  AppErrorBoundaryState
> {
  state: AppErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(error: Error): AppErrorBoundaryState {
    return { hasError: true, message: error.message };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error('Unhandled application error', error, info);
  }

  render() {
    if (this.state.hasError) {
      return (
        <ErrorPage
          message={this.state.message}
          onRetry={() => window.location.reload()}
        />
      );
    }
    return this.props.children;
  }
}

export default AppErrorBoundary;
