import React from 'react';
import { AlertTriangle, RotateCcw, Home } from 'lucide-react';
import { Link } from 'react-router-dom';

/**
 * ErrorBoundary: chặn lỗi render của một trang, tránh sập toàn bộ app
 * thành màn hình trắng. Hiển thị thông báo lỗi + nút thử lại.
 */
export class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { error: null, errorInfo: null };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, errorInfo) {
    this.setState({ errorInfo });
    // eslint-disable-next-line no-console
    console.error('[ErrorBoundary]', error, errorInfo);
  }

  handleRetry = () => {
    this.setState({ error: null, errorInfo: null });
    if (this.props.onRetry) this.props.onRetry();
  };

  render() {
    const { error, errorInfo } = this.state;
    if (!error) return this.props.children;

    const pageName = this.props.pageName || 'trang này';
    return (
      <div className="glass-card" style={{ maxWidth: 640, margin: '3rem auto', textAlign: 'center', padding: '2.5rem 2rem' }}>
        <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '1rem', color: '#b91c1c' }}>
          <AlertTriangle size={44} />
        </div>
        <h2 style={{ marginBottom: '0.5rem' }}>Đã xảy ra lỗi khi tải {pageName}</h2>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginBottom: '1rem' }}>
          Ứng dụng gặp sự cố ngoài ý muốn. Bạn có thể thử tải lại hoặc quay về trang chủ.
        </p>
        {(error?.message || errorInfo?.componentStack) && (
          <details style={{ textAlign: 'left', marginBottom: '1.25rem', fontSize: '0.8rem' }}>
            <summary style={{ cursor: 'pointer', color: 'var(--text-secondary)', marginBottom: '0.5rem' }}>
              Chi tiết kỹ thuật
            </summary>
            <pre style={{
              background: 'rgba(0,0,0,0.06)', padding: '0.75rem', borderRadius: '0.5rem',
              overflow: 'auto', maxHeight: 200, whiteSpace: 'pre-wrap', wordBreak: 'break-word',
            }}>
              {String(error?.message || error)}
              {errorInfo?.componentStack ? `\n${errorInfo.componentStack}` : ''}
            </pre>
          </details>
        )}
        <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'center', flexWrap: 'wrap' }}>
          <button className="btn btn-primary" onClick={this.handleRetry}>
            <RotateCcw size={16} /> Thử tải lại
          </button>
          <Link className="btn btn-secondary" to="/">
            <Home size={16} /> Về trang chủ
          </Link>
        </div>
      </div>
    );
  }
}

/** Bọc một trang bằng ErrorBoundary. */
export function withErrorBoundary(element, pageName) {
  return <ErrorBoundary pageName={pageName}>{element}</ErrorBoundary>;
}
