import { Component, type ErrorInfo, type ReactNode } from 'react';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error?: Error;
}

export class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('Uncaught error:', error, errorInfo);
  }

  public render() {
    if (this.state.hasError) {
      return this.props.fallback || (
          <div className="h-full flex flex-col items-center justify-center p-6 text-red-400 border border-red-400/20 rounded-2xl bg-red-400/5">
            <h2 className="text-xl font-bold mb-2">렌더링 중 오류가 발생했습니다.</h2>
            <p className="text-sm text-red-300">{this.state.error?.message}</p>
          </div>
      );
    }

    return this.props.children;
  }
}
