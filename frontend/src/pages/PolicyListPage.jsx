import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { useToast } from '../context/ToastContext';
import { policyService } from '../services/api';
import {
  Search,
  Filter,
  Layers,
  ArrowUpDown,
  ChevronLeft,
  ChevronRight,
  Plus,
  Calendar,
  Building,
  DollarSign,
  MapPin,
  RefreshCw,
  Eye,
  CheckCircle2,
  Clock,
  ShieldAlert,
  X,
  FileText,
} from 'lucide-react';

export const PolicyListPage = () => {
  const { showToast } = useToast();

  // Filters State
  const [filters, setFilters] = useState({
    status: '',
    insuredName: '',
    location: '',
    policyNumber: '',
    effectiveDateFrom: '',
    effectiveDateTo: '',
    minPremium: '',
    maxPremium: '',
  });

  // Paging & Sorting State
  const [pagination, setPagination] = useState({
    page: 0,
    size: 5,
    sortBy: 'createdAt',
    sortDirection: 'DESC',
  });

  // Data State
  const [policies, setPolicies] = useState([]);
  const [pageInfo, setPageInfo] = useState({
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
  });
  const [loading, setLoading] = useState(false);

  // Modal State for New Policy
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [newPolicyForm, setNewPolicyForm] = useState({
    policyNumber: '',
    insuredName: '',
    insuredEmail: '',
    insuredPhone: '',
    address: '',
    status: 'DRAFT',
  });

  const fetchPolicies = useCallback(async () => {
    try {
      setLoading(true);
      const params = {
        ...filters,
        page: pagination.page,
        size: pagination.size,
        sortBy: pagination.sortBy,
        sortDirection: pagination.sortDirection,
      };

      // Clean empty params
      Object.keys(params).forEach((key) => {
        if (params[key] === '' || params[key] === null || params[key] === undefined) {
          delete params[key];
        }
      });

      const res = await policyService.searchPolicies(params);
      if (res && res.data) {
        setPolicies(res.data.content || []);
        setPageInfo({
          totalElements: res.data.totalElements || 0,
          totalPages: res.data.totalPages || 0,
          first: res.data.first,
          last: res.data.last,
        });
      }
    } catch (err) {
      showToast(err.message || 'Không thể tải danh sách hợp đồng', 'error');
    } finally {
      setLoading(false);
    }
  }, [filters, pagination, showToast]);

  useEffect(() => {
    fetchPolicies();
  }, [fetchPolicies]);

  const handleFilterChange = (e) => {
    const { name, value } = e.target;
    setFilters((prev) => ({ ...prev, [name]: value }));
    setPagination((prev) => ({ ...prev, page: 0 })); // Reset to first page
  };

  const handleStatusFilter = (statusValue) => {
    setFilters((prev) => ({ ...prev, status: statusValue }));
    setPagination((prev) => ({ ...prev, page: 0 }));
  };

  const handleSortChange = (e) => {
    setPagination((prev) => ({ ...prev, sortBy: e.target.value, page: 0 }));
  };

  const toggleSortDirection = () => {
    setPagination((prev) => ({
      ...prev,
      sortDirection: prev.sortDirection === 'ASC' ? 'DESC' : 'ASC',
      page: 0,
    }));
  };

  const handlePageChange = (newPage) => {
    if (newPage >= 0 && newPage < pageInfo.totalPages) {
      setPagination((prev) => ({ ...prev, page: newPage }));
    }
  };

  const handleResetFilters = () => {
    setFilters({
      status: '',
      insuredName: '',
      location: '',
      policyNumber: '',
      effectiveDateFrom: '',
      effectiveDateTo: '',
      minPremium: '',
      maxPremium: '',
    });
    setPagination((prev) => ({ ...prev, page: 0 }));
  };

  const handleCreatePolicy = async (e) => {
    e.preventDefault();
    if (!newPolicyForm.policyNumber.trim()) {
      showToast('Vui lòng nhập số hợp đồng', 'error');
      return;
    }

    try {
      setCreating(true);
      const payload = {
        policyNumber: newPolicyForm.policyNumber.trim(),
        status: newPolicyForm.status || 'DRAFT',
        insured: {
          name: newPolicyForm.insuredName,
          email: newPolicyForm.insuredEmail,
          phone: newPolicyForm.insuredPhone,
          address: newPolicyForm.address,
        },
        locations: [
          {
            locationId: 1,
            address: newPolicyForm.address || 'Địa điểm chính',
            coverages: [],
          },
        ],
      };

      await policyService.createPolicy(payload);
      showToast('Tạo mới hợp đồng bảo hiểm thành công!', 'success');
      setIsCreateModalOpen(false);
      setNewPolicyForm({
        policyNumber: '',
        insuredName: '',
        insuredEmail: '',
        insuredPhone: '',
        address: '',
        status: 'DRAFT',
      });
      fetchPolicies();
    } catch (err) {
      showToast(err.message || 'Lỗi khi tạo hợp đồng', 'error');
    } finally {
      setCreating(false);
    }
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case 'DRAFT':
        return <span className="badge badge-warning">DRAFT</span>;
      case 'QUOTED':
        return <span className="badge badge-primary">QUOTED</span>;
      case 'BOUND':
        return <span className="badge badge-primary" style={{ background: 'rgba(6, 182, 212, 0.15)', color: '#67e8f9' }}>BOUND</span>;
      case 'ACTIVE':
        return <span className="badge badge-success">ACTIVE</span>;
      case 'CANCELLED':
        return <span className="badge badge-danger">CANCELLED</span>;
      case 'EXPIRED':
        return <span className="badge" style={{ background: 'rgba(100, 116, 139, 0.2)', color: '#94a3b8' }}>EXPIRED</span>;
      default:
        return <span className="badge badge-primary">{status}</span>;
    }
  };

  const statusOptions = ['ALL', 'DRAFT', 'QUOTED', 'BOUND', 'ACTIVE', 'CANCELLED', 'EXPIRED'];

  return (
    <div className="page-wrapper">
      {/* Header Banner */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          flexWrap: 'wrap',
          gap: '1rem',
          marginBottom: '2rem',
        }}
      >
        <div>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
            <span className="badge badge-primary">
              <Layers size={12} /> P02 & P03: Search & Paging
            </span>
          </div>
          <h1 style={{ fontSize: '1.85rem' }}>Danh sách Hợp đồng & Tìm kiếm Nâng cao</h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.95rem' }}>
            Dynamic Search kết hợp lọc nhiều tiêu chí (Status, Insured, Location, Date), Phân trang và Sắp xếp động.
          </p>
        </div>

        <button
          onClick={() => {
            setNewPolicyForm((p) => ({ ...p, policyNumber: 'POL-' + Date.now().toString().slice(-6) }));
            setIsCreateModalOpen(true);
          }}
          className="btn btn-primary"
        >
          <Plus size={18} />
          <span>Tạo Hợp đồng mới</span>
        </button>
      </div>

      {/* Filter Control Box */}
      <div
        className="glass-card"
        style={{
          marginBottom: '1.5rem',
          display: 'flex',
          flexDirection: 'column',
          gap: '1.25rem',
        }}
      >
        {/* Status Pill Filters */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
          <span style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-muted)', marginRight: '0.5rem' }}>
            TRẠNG THÁI:
          </span>
          {statusOptions.map((st) => {
            const isSelected = (st === 'ALL' && !filters.status) || filters.status === st;
            return (
              <button
                key={st}
                onClick={() => handleStatusFilter(st === 'ALL' ? '' : st)}
                className="btn"
                style={{
                  padding: '0.35rem 0.75rem',
                  fontSize: '0.775rem',
                  borderRadius: 'var(--radius-full)',
                  background: isSelected ? 'var(--primary-gradient)' : 'var(--bg-input)',
                  color: isSelected ? '#ffffff' : 'var(--text-secondary)',
                  border: isSelected ? '1px solid var(--primary)' : '1px solid var(--border-subtle)',
                  boxShadow: isSelected ? 'var(--shadow-glow)' : 'none',
                }}
              >
                {st}
              </button>
            );
          })}
        </div>

        {/* Inputs Grid */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: '1rem',
          }}
        >
          {/* Policy Number */}
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label" style={{ fontSize: '0.775rem' }}>Mã hợp đồng (Policy #)</label>
            <input
              type="text"
              name="policyNumber"
              value={filters.policyNumber}
              onChange={handleFilterChange}
              placeholder="VD: POL-2026..."
              className="form-control"
              style={{ fontSize: '0.85rem', padding: '0.5rem 0.75rem' }}
            />
          </div>

          {/* Insured Name */}
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label" style={{ fontSize: '0.775rem' }}>Bên mua bảo hiểm (Insured Name)</label>
            <input
              type="text"
              name="insuredName"
              value={filters.insuredName}
              onChange={handleFilterChange}
              placeholder="VD: Công ty TNHH..."
              className="form-control"
              style={{ fontSize: '0.85rem', padding: '0.5rem 0.75rem' }}
            />
          </div>

          {/* Location Address */}
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label" style={{ fontSize: '0.775rem' }}>Địa chỉ địa điểm (Location)</label>
            <input
              type="text"
              name="location"
              value={filters.location}
              onChange={handleFilterChange}
              placeholder="VD: Nguyễn Huệ, Q1..."
              className="form-control"
              style={{ fontSize: '0.85rem', padding: '0.5rem 0.75rem' }}
            />
          </div>

          {/* Min Premium */}
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label" style={{ fontSize: '0.775rem' }}>Phí tối thiểu (VND)</label>
            <input
              type="number"
              name="minPremium"
              value={filters.minPremium}
              onChange={handleFilterChange}
              placeholder="0"
              className="form-control"
              style={{ fontSize: '0.85rem', padding: '0.5rem 0.75rem' }}
            />
          </div>
        </div>

        {/* Action Controls Bar */}
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            borderTop: '1px solid var(--border-subtle)',
            paddingTop: '0.75rem',
            flexWrap: 'wrap',
            gap: '0.75rem',
          }}
        >
          {/* Sort Controls */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Sắp xếp theo:</span>
            <select
              value={pagination.sortBy}
              onChange={handleSortChange}
              className="form-control"
              style={{ width: '160px', padding: '0.4rem 0.6rem', fontSize: '0.8rem' }}
            >
              <option value="createdAt">Ngày tạo</option>
              <option value="effectiveDate">Ngày hiệu lực</option>
              <option value="totalPremium">Tổng phí bảo hiểm</option>
              <option value="policyNumber">Số hợp đồng</option>
              <option value="status">Trạng thái</option>
            </select>

            <button
              onClick={toggleSortDirection}
              className="btn btn-secondary"
              style={{ padding: '0.4rem 0.75rem', fontSize: '0.8rem' }}
              title={`Sắp xếp ${pagination.sortDirection === 'ASC' ? 'Tăng dần' : 'Giảm dần'}`}
            >
              <ArrowUpDown size={14} />
              <span>{pagination.sortDirection}</span>
            </button>
          </div>

          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <button
              onClick={handleResetFilters}
              className="btn btn-secondary"
              style={{ padding: '0.4rem 0.85rem', fontSize: '0.8rem' }}
            >
              Xóa bộ lọc
            </button>
            <button
              onClick={fetchPolicies}
              disabled={loading}
              className="btn btn-primary"
              style={{ padding: '0.4rem 0.85rem', fontSize: '0.8rem' }}
            >
              <RefreshCw size={14} className={loading ? 'spinner' : ''} />
              <span>Tìm kiếm</span>
            </button>
          </div>
        </div>
      </div>

      {/* Results Header & Stats */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
          Tìm thấy <b style={{ color: 'var(--text-primary)' }}>{pageInfo.totalElements}</b> kết quả (Trang {pagination.page + 1}/{Math.max(pageInfo.totalPages, 1)})
        </span>

        {/* Page size dropdown */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.8rem' }}>
          <span style={{ color: 'var(--text-muted)' }}>Hiển thị:</span>
          <select
            value={pagination.size}
            onChange={(e) => setPagination((p) => ({ ...p, size: parseInt(e.target.value), page: 0 }))}
            className="form-control"
            style={{ width: '70px', padding: '0.25rem 0.5rem', fontSize: '0.8rem' }}
          >
            <option value="5">5</option>
            <option value="10">10</option>
            <option value="25">25</option>
          </select>
          <span style={{ color: 'var(--text-muted)' }}>hợp đồng/trang</span>
        </div>
      </div>

      {/* Results Table / Cards */}
      {policies.length === 0 ? (
        <div
          className="glass-card"
          style={{
            textAlign: 'center',
            padding: '3rem 2rem',
            borderStyle: 'dashed',
          }}
        >
          <FileText size={40} color="var(--text-muted)" style={{ margin: '0 auto 1rem' }} />
          <h3 style={{ fontSize: '1.15rem', marginBottom: '0.5rem' }}>Không tìm thấy hợp đồng nào phù hợp</h3>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', marginBottom: '1rem' }}>
            Vui lòng thử điều chỉnh hoặc xóa bớt tiêu chí lọc tìm kiếm.
          </p>
          <button onClick={handleResetFilters} className="btn btn-secondary" style={{ fontSize: '0.85rem' }}>
            Đặt lại bộ lọc
          </button>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {policies.map((policy) => (
            <div
              key={policy.id || policy.policyNumber}
              className="glass-card"
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: '1.25rem',
                transition: 'all var(--transition-fast)',
              }}
            >
              {/* Left Column: Number, Insured, Status */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem', flex: 1, minWidth: '250px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                  <span
                    style={{
                      fontFamily: 'var(--font-mono)',
                      fontWeight: 700,
                      fontSize: '1.05rem',
                      color: 'var(--primary)',
                    }}
                  >
                    {policy.policyNumber}
                  </span>
                  {getStatusBadge(policy.status)}
                  <span style={{ fontSize: '0.725rem', color: 'var(--text-muted)' }}>
                    v{policy.version || 1}
                  </span>
                </div>

                <div style={{ fontSize: '0.9rem', fontWeight: 600, color: 'var(--text-primary)' }}>
                  {policy.insured?.name || 'Chưa cập nhật người thụ hưởng'}
                </div>

                <div style={{ display: 'flex', gap: '1rem', fontSize: '0.775rem', color: 'var(--text-muted)' }}>
                  <span>Email: {policy.insured?.email || '—'}</span>
                  <span>SĐT: {policy.insured?.phone || '—'}</span>
                </div>
              </div>

              {/* Middle Column: Locations & Dates */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem', minWidth: '220px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.825rem', color: 'var(--text-secondary)' }}>
                  <MapPin size={15} color="var(--primary)" />
                  <span>
                    <b>{policy.locations?.length || 0}</b> địa điểm bảo hiểm
                  </span>
                </div>

                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                  Hiệu lực: {policy.effectiveDate ? new Date(policy.effectiveDate).toLocaleDateString() : '—'}
                </div>
              </div>

              {/* Right Column: Total Premium & Action */}
              <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem' }}>
                <div style={{ textAlign: 'right' }}>
                  <span style={{ fontSize: '0.725rem', color: 'var(--text-muted)', display: 'block' }}>TỔNG PHÍ BẢO HIỂM</span>
                  <span style={{ fontSize: '1.2rem', fontWeight: 700, color: '#10b981' }}>
                    {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(policy.totalPremium || 0)}
                  </span>
                </div>

                <Link
                  to={`/policies/locations`}
                  className="btn btn-secondary"
                  style={{ padding: '0.5rem 0.9rem', fontSize: '0.825rem' }}
                >
                  <Eye size={15} />
                  <span>Xem & Sửa Địa điểm</span>
                </Link>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Pagination Controls */}
      {pageInfo.totalPages > 1 && (
        <div
          style={{
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            gap: '0.5rem',
            marginTop: '2rem',
          }}
        >
          <button
            onClick={() => handlePageChange(pagination.page - 1)}
            disabled={pageInfo.first}
            className="btn btn-secondary"
            style={{ padding: '0.4rem 0.75rem', fontSize: '0.85rem' }}
          >
            <ChevronLeft size={16} />
            <span>Trước</span>
          </button>

          {Array.from({ length: pageInfo.totalPages }, (_, i) => (
            <button
              key={i}
              onClick={() => handlePageChange(i)}
              className="btn"
              style={{
                width: '36px',
                height: '36px',
                padding: 0,
                fontSize: '0.85rem',
                fontWeight: 600,
                borderRadius: 'var(--radius-md)',
                background: pagination.page === i ? 'var(--primary-gradient)' : 'var(--bg-card)',
                color: pagination.page === i ? '#ffffff' : 'var(--text-secondary)',
                border: '1px solid var(--border-subtle)',
                boxShadow: pagination.page === i ? 'var(--shadow-glow)' : 'none',
              }}
            >
              {i + 1}
            </button>
          ))}

          <button
            onClick={() => handlePageChange(pagination.page + 1)}
            disabled={pageInfo.last}
            className="btn btn-secondary"
            style={{ padding: '0.4rem 0.75rem', fontSize: '0.85rem' }}
          >
            <span>Sau</span>
            <ChevronRight size={16} />
          </button>
        </div>
      )}

      {/* Create Modal */}
      {isCreateModalOpen && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: 'rgba(0,0,0,0.75)',
            backdropFilter: 'blur(8px)',
            zIndex: 1000,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '1rem',
          }}
        >
          <div
            className="glass-card"
            style={{
              width: '100%',
              maxWidth: '560px',
              background: '#0f172a',
              border: '1px solid rgba(99, 102, 241, 0.3)',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
              <h2 style={{ fontSize: '1.25rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <Plus size={20} color="var(--primary)" />
                Tạo mới Hợp đồng Bảo hiểm
              </h2>
              <button
                onClick={() => setIsCreateModalOpen(false)}
                style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}
              >
                <X size={20} />
              </button>
            </div>

            <form onSubmit={handleCreatePolicy}>
              <div className="form-group">
                <label className="form-label">Mã số hợp đồng (Policy Number) *</label>
                <input
                  type="text"
                  value={newPolicyForm.policyNumber}
                  onChange={(e) => setNewPolicyForm((p) => ({ ...p, policyNumber: e.target.value }))}
                  required
                  className="form-control"
                  style={{ fontFamily: 'var(--font-mono)' }}
                />
              </div>

              <div className="form-group">
                <label className="form-label">Tên bên mua bảo hiểm (Insured Name) *</label>
                <input
                  type="text"
                  value={newPolicyForm.insuredName}
                  onChange={(e) => setNewPolicyForm((p) => ({ ...p, insuredName: e.target.value }))}
                  placeholder="Ví dụ: Công ty Cổ Phần ABC"
                  required
                  className="form-control"
                />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                <div className="form-group">
                  <label className="form-label">Email liên hệ</label>
                  <input
                    type="email"
                    value={newPolicyForm.insuredEmail}
                    onChange={(e) => setNewPolicyForm((p) => ({ ...p, insuredEmail: e.target.value }))}
                    placeholder="contact@abc.vn"
                    className="form-control"
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Số điện thoại</label>
                  <input
                    type="text"
                    value={newPolicyForm.insuredPhone}
                    onChange={(e) => setNewPolicyForm((p) => ({ ...p, insuredPhone: e.target.value }))}
                    placeholder="0912345678"
                    className="form-control"
                  />
                </div>
              </div>

              <div className="form-group">
                <label className="form-label">Địa chỉ trụ sở / Địa điểm ban đầu</label>
                <input
                  type="text"
                  value={newPolicyForm.address}
                  onChange={(e) => setNewPolicyForm((p) => ({ ...p, address: e.target.value }))}
                  placeholder="Ví dụ: 123 Đường Nam Kỳ Khởi Nghĩa, Q3, TP.HCM"
                  className="form-control"
                />
              </div>

              <div
                style={{
                  display: 'flex',
                  justifyContent: 'flex-end',
                  gap: '0.75rem',
                  marginTop: '1.5rem',
                  paddingTop: '1rem',
                  borderTop: '1px solid var(--border-subtle)',
                }}
              >
                <button
                  type="button"
                  onClick={() => setIsCreateModalOpen(false)}
                  className="btn btn-secondary"
                  disabled={creating}
                >
                  Hủy
                </button>
                <button type="submit" className="btn btn-primary" disabled={creating}>
                  {creating ? (
                    <>
                      <div className="spinner" />
                      <span>Đang tạo...</span>
                    </>
                  ) : (
                    <>
                      <Plus size={16} />
                      <span>Tạo hợp đồng</span>
                    </>
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
