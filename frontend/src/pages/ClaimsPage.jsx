import React, { useState, useEffect, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  ShieldAlert, Plus, Search, Filter, RefreshCw, FileText,
  Clock, CheckCircle, XCircle, DollarSign, ChevronRight, Image as ImageIcon
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { claimApi } from '../services/claimApi';
import { formatMoney, formatDateTime } from '../utils/format';
import { Skeleton, EmptyState } from '../components/ui';

const STATUS_CONFIG = {
  ALL: { label: 'Tất cả', color: '#64748b', bg: '#f1f5f9' },
  SUBMITTED: { label: 'Mới gửi', color: '#d97706', bg: '#fef3c7' },
  UNDER_REVIEW: { label: 'Đang thẩm định', color: '#2563eb', bg: '#dbeafe' },
  APPROVED: { label: 'Đã duyệt', color: '#059669', bg: '#d1fae5' },
  REJECTED: { label: 'Từ chối', color: '#dc2626', bg: '#fee2e2' },
  PAID: { label: 'Đã chi trả', color: '#047857', bg: '#a7f3d0' },
};

export function ClaimsPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const { user, hasRole, hasAnyRole } = useAuth();

  const [claims, setClaims] = useState([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [policySearch, setPolicySearch] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const isCustomer = hasRole('ROLE_CUSTOMER') || hasRole('ROLE_USER');
  const isAdmin = hasRole('ROLE_ADMIN');

  const fetchClaims = useCallback(async () => {
    try {
      setLoading(true);
      const params = {
        page,
        size: 10,
        sort: 'createdAt,desc',
      };
      if (statusFilter !== 'ALL') {
        params.status = statusFilter;
      }
      if (policySearch.trim()) {
        params.policyNumber = policySearch.trim();
      }

      const res = await claimApi.getClaims(params);
      // Kiểm tra cấu trúc phân trang của backend Spring Data Page
      const content = res.content || res.data?.content || (Array.isArray(res) ? res : []);
      const totalP = res.totalPages ?? res.data?.totalPages ?? 1;
      const totalEl = res.totalElements ?? res.data?.totalElements ?? content.length;

      setClaims(content);
      setTotalPages(totalP);
      setTotalElements(totalEl);
    } catch (err) {
      console.error(err);
      toast.error(err.message || 'Không thể tải danh sách yêu cầu bồi thường.');
    } finally {
      setLoading(false);
    }
  }, [page, statusFilter, policySearch, toast]);

  useEffect(() => {
    fetchClaims();
  }, [fetchClaims]);

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    setPage(0);
    fetchClaims();
  };

  const getStatusBadge = (status) => {
    const cfg = STATUS_CONFIG[status] || { label: status, color: '#64748b', bg: '#f1f5f9' };
    return (
      <span style={{
        display: 'inline-flex',
        alignItems: 'center',
        padding: '0.2rem 0.6rem',
        borderRadius: '9999px',
        fontSize: '0.78rem',
        fontWeight: 600,
        color: cfg.color,
        backgroundColor: cfg.bg,
      }}>
        {cfg.label}
      </span>
    );
  };

  return (
    <div className="container" style={{ maxWidth: '1200px', margin: '0 auto', padding: '1.5rem 1rem' }}>
      {/* Header bar */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem', marginBottom: '1.5rem' }}>
        <div>
          <h1 style={{ fontSize: '1.6rem', fontWeight: 600, margin: 0, display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
            <ShieldAlert color="#ea580c" size={28} />
            Hồ sơ bồi thường bảo hiểm
          </h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', margin: '0.25rem 0 0' }}>
            {isAdmin ? 'Quản trị và thẩm định duyệt chi trả các yêu cầu bồi thường' : 'Theo dõi và quản lý tiến độ xử lý bồi thường hợp đồng'}
          </p>
        </div>

        <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={fetchClaims}
            disabled={loading}
            title="Làm mới"
            style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}
          >
            <RefreshCw size={15} className={loading ? 'spin' : ''} />
            Làm mới
          </button>

          {isCustomer && (
            <Link
              to="/claims/new"
              className="btn btn-primary"
              style={{
                backgroundColor: '#ea580c',
                borderColor: '#ea580c',
                color: '#fff',
                display: 'flex',
                alignItems: 'center',
                gap: '0.4rem',
              }}
            >
              <Plus size={16} /> Yêu cầu bồi thường mới
            </Link>
          )}
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="glass-card" style={{ padding: '1rem 1.25rem', marginBottom: '1.5rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
          {/* Status Tabs */}
          <div style={{ display: 'flex', gap: '0.4rem', flexWrap: 'wrap' }}>
            {Object.keys(STATUS_CONFIG).map((st) => (
              <button
                key={st}
                type="button"
                onClick={() => {
                  setStatusFilter(st);
                  setPage(0);
                }}
                className={`tab ${statusFilter === st ? 'active' : ''}`}
                style={{
                  padding: '0.35rem 0.85rem',
                  fontSize: '0.85rem',
                  borderRadius: '6px',
                  fontWeight: statusFilter === st ? 600 : 500,
                  border: 'none',
                  cursor: 'pointer',
                  backgroundColor: statusFilter === st ? 'var(--primary-color, #1663d9)' : 'transparent',
                  color: statusFilter === st ? '#fff' : 'inherit',
                }}
              >
                {STATUS_CONFIG[st].label}
              </button>
            ))}
          </div>

          {/* Search by Policy Number */}
          <form onSubmit={handleSearchSubmit} style={{ display: 'flex', gap: '0.5rem', minWidth: '260px' }}>
            <input
              type="text"
              className="form-control"
              placeholder="Tìm theo số hợp đồng..."
              value={policySearch}
              onChange={(e) => setPolicySearch(e.target.value)}
              style={{ padding: '0.4rem 0.75rem', fontSize: '0.85rem' }}
            />
            <button type="submit" className="btn btn-secondary" style={{ padding: '0.4rem 0.75rem' }}>
              <Search size={15} />
            </button>
          </form>
        </div>
      </div>

      {/* Claims List Table */}
      {loading ? (
        <div className="glass-card" style={{ padding: '1.5rem' }}>
          <Skeleton height={40} style={{ marginBottom: '1rem' }} />
          <Skeleton height={50} style={{ marginBottom: '0.5rem' }} />
          <Skeleton height={50} style={{ marginBottom: '0.5rem' }} />
          <Skeleton height={50} />
        </div>
      ) : claims.length === 0 ? (
        <div className="glass-card">
          <EmptyState
            icon={<ShieldAlert size={44} color="#94a3b8" />}
            title="Không có hồ sơ bồi thường nào"
            desc={statusFilter !== 'ALL' || policySearch ? 'Không tìm thấy hồ sơ phù hợp với bộ lọc hiện tại.' : 'Chưa có yêu cầu bồi thường nào được ghi nhận.'}
            action={isCustomer && (
              <Link to="/claims/new" className="btn btn-primary" style={{ backgroundColor: '#ea580c', borderColor: '#ea580c' }}>
                <Plus size={16} /> Tạo yêu cầu bồi thường
              </Link>
            )}
          />
        </div>
      ) : (
        <div className="glass-card" style={{ overflowX: 'auto', padding: 0 }}>
          <table className="table" style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9rem' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--border-color, #e2e8f0)', backgroundColor: 'var(--bg-secondary, #f8fafc)', textAlign: 'left' }}>
                <th style={{ padding: '0.75rem 1rem' }}>Mã hồ sơ</th>
                <th style={{ padding: '0.75rem 1rem' }}>Số hợp đồng</th>
                <th style={{ padding: '0.75rem 1rem' }}>Người yêu cầu</th>
                <th style={{ padding: '0.75rem 1rem' }}>Số tiền yêu cầu</th>
                <th style={{ padding: '0.75rem 1rem' }}>Số tiền duyệt</th>
                <th style={{ padding: '0.75rem 1rem' }}>Ảnh</th>
                <th style={{ padding: '0.75rem 1rem' }}>Trạng thái</th>
                <th style={{ padding: '0.75rem 1rem' }}>Ngày gửi</th>
                <th style={{ padding: '0.75rem 1rem', textAlign: 'right' }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {claims.map((claim) => (
                <tr
                  key={claim.id}
                  style={{
                    borderBottom: '1px solid var(--border-color, #f1f5f9)',
                    transition: 'background-color 0.15s ease',
                  }}
                  className="table-row-hover"
                >
                  <td style={{ padding: '0.85rem 1rem', fontWeight: 600, fontFamily: 'monospace' }}>
                    {claim.id.substring(0, 8)}...
                  </td>
                  <td style={{ padding: '0.85rem 1rem' }}>
                    <Link
                      to={`/policies/${claim.policyNumber}`}
                      style={{ color: '#1663d9', textDecoration: 'none', fontWeight: 500 }}
                    >
                      {claim.policyNumber}
                    </Link>
                  </td>
                  <td style={{ padding: '0.85rem 1rem', color: 'var(--text-secondary)' }}>
                    {claim.claimantEmail}
                  </td>
                  <td style={{ padding: '0.85rem 1rem', fontWeight: 600 }}>
                    {formatMoney(claim.claimedAmount)}
                  </td>
                  <td style={{ padding: '0.85rem 1rem', fontWeight: 600, color: claim.approvedAmount ? '#059669' : 'var(--text-secondary)' }}>
                    {claim.approvedAmount ? formatMoney(claim.approvedAmount) : '—'}
                  </td>
                  <td style={{ padding: '0.85rem 1rem' }}>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.25rem', color: 'var(--text-secondary)', fontSize: '0.82rem' }}>
                      <ImageIcon size={14} />
                      {claim.photos ? claim.photos.length : 0}
                    </span>
                  </td>
                  <td style={{ padding: '0.85rem 1rem' }}>
                    {getStatusBadge(claim.status)}
                  </td>
                  <td style={{ padding: '0.85rem 1rem', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>
                    {claim.createdAt ? formatDateTime(claim.createdAt) : '—'}
                  </td>
                  <td style={{ padding: '0.85rem 1rem', textAlign: 'right' }}>
                    <Link
                      to={`/claims/${claim.id}`}
                      className="btn btn-secondary"
                      style={{ padding: '0.3rem 0.65rem', fontSize: '0.8rem', display: 'inline-flex', alignItems: 'center', gap: '0.25rem' }}
                    >
                      Chi tiết <ChevronRight size={14} />
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {/* Pagination */}
          {totalPages > 1 && (
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.75rem 1rem', borderTop: '1px solid var(--border-color, #e2e8f0)' }}>
              <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
                Hiển thị trang {page + 1} / {totalPages} (tổng cộng {totalElements} hồ sơ)
              </div>
              <div style={{ display: 'flex', gap: '0.4rem' }}>
                <button
                  type="button"
                  className="btn btn-secondary"
                  disabled={page === 0}
                  onClick={() => setPage(p => Math.max(0, p - 1))}
                  style={{ padding: '0.3rem 0.75rem', fontSize: '0.85rem' }}
                >
                  Trước
                </button>
                <button
                  type="button"
                  className="btn btn-secondary"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage(p => p + 1)}
                  style={{ padding: '0.3rem 0.75rem', fontSize: '0.85rem' }}
                >
                  Sau
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default ClaimsPage;

