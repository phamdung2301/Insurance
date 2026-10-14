import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useToast } from '../context/ToastContext';
import { policyService, documentService, excelService } from '../services/api';
import { PolicyHistoryAndEndorsementModal } from '../components/PolicyHistoryAndEndorsementModal';
import { PaymentModal } from '../components/PaymentModal';
import {
  Search,
  ArrowUpDown,
  ArrowUp,
  ArrowDown,
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  Plus,
  Calendar,
  DollarSign,
  MapPin,
  RefreshCw,
  Eye,
  CheckCircle2,
  Clock,
  X,
  FileText,
  History,
  Sparkles,
  Edit3,
  Download,
  Table as TableIcon,
  LayoutGrid,
  FileSpreadsheet,
  SlidersHorizontal,
  Shield,
  QrCode,
} from 'lucide-react';

export const PolicyListPage = () => {
  const { showToast } = useToast();

  // Search & Filter State
  const [quickSearch, setQuickSearch] = useState('');
  const [filters, setFilters] = useState({
    status: '',
    keyword: '',
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
    size: 10,
    sortBy: 'createdAt',
    sortDirection: 'DESC',
  });

  // Jump to page input
  const [jumpPage, setJumpPage] = useState('');

  // UI View Mode: 'table' (default) or 'cards'
  const [viewMode, setViewMode] = useState('table');
  const [showAdvancedFilters, setShowAdvancedFilters] = useState(false);

  // Data State
  const [policies, setPolicies] = useState([]);
  const [pageInfo, setPageInfo] = useState({
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
  });
  const [loading, setLoading] = useState(false);

  // Modals State
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [historyModalPolicy, setHistoryModalPolicy] = useState(null);
  const [paymentModalPolicy, setPaymentModalPolicy] = useState(null);
  const [newPolicyForm, setNewPolicyForm] = useState({
    policyNumber: '',
    insuredName: '',
    insuredEmail: '',
    insuredPhone: '',
    address: '',
    status: 'DRAFT',
  });

  // Fetch Policies from Backend
  const fetchPolicies = useCallback(async () => {
    try {
      setLoading(true);
      const params = {
        page: pagination.page,
        size: pagination.size,
        sortBy: pagination.sortBy,
        sortDirection: pagination.sortDirection,
      };

      if (filters.status) params.status = filters.status;
      if (filters.keyword) params.keyword = filters.keyword;
      if (filters.policyNumber) params.policyNumber = filters.policyNumber;
      if (filters.insuredName) params.insuredName = filters.insuredName;
      if (filters.location) params.location = filters.location;
      if (filters.minPremium) params.minPremium = filters.minPremium;
      if (filters.maxPremium) params.maxPremium = filters.maxPremium;

      // Format ISO Date for backend DateTimeFormat
      if (filters.effectiveDateFrom) {
        params.effectiveDateFrom = `${filters.effectiveDateFrom}T00:00:00Z`;
      }
      if (filters.effectiveDateTo) {
        params.effectiveDateTo = `${filters.effectiveDateTo}T23:59:59Z`;
      }

      const res = await policyService.searchPolicies(params);
      if (res && res.data) {
        setPolicies(res.data.content || []);
        setPageInfo({
          totalElements: res.data.totalElements || 0,
          totalPages: res.data.totalPages || 0,
          first: res.data.first ?? pagination.page === 0,
          last: res.data.last ?? (pagination.page >= (res.data.totalPages || 1) - 1),
        });
      }
    } catch (err) {
      showToast(err.message || 'Không thể tải danh sách hợp đồng bảo hiểm', 'error');
    } finally {
      setLoading(false);
    }
  }, [filters, pagination, showToast]);

  useEffect(() => {
    fetchPolicies();
  }, [fetchPolicies]);

  // Quick Search Handlers
  const handleQuickSearchSubmit = (e) => {
    if (e) e.preventDefault();
    setFilters((prev) => ({
      ...prev,
      keyword: quickSearch.trim(),
    }));
    setPagination((prev) => ({ ...prev, page: 0 }));
  };

  const handleClearQuickSearch = () => {
    setQuickSearch('');
    setFilters((prev) => ({
      ...prev,
      keyword: '',
    }));
    setPagination((prev) => ({ ...prev, page: 0 }));
  };

  // Filter Handlers
  const handleFilterChange = (e) => {
    const { name, value } = e.target;
    setFilters((prev) => ({ ...prev, [name]: value }));
    setPagination((prev) => ({ ...prev, page: 0 }));
  };

  const handleStatusFilter = (statusValue) => {
    setFilters((prev) => ({
      ...prev,
      status: statusValue === 'ALL' ? '' : statusValue,
    }));
    setPagination((prev) => ({ ...prev, page: 0 }));
  };

  const handleDatePreset = (preset) => {
    const today = new Date();
    const formatDate = (d) => d.toISOString().split('T')[0];

    if (preset === 'today') {
      const dateStr = formatDate(today);
      setFilters((prev) => ({ ...prev, effectiveDateFrom: dateStr, effectiveDateTo: dateStr }));
    } else if (preset === '7days') {
      const past = new Date();
      past.setDate(today.getDate() - 7);
      setFilters((prev) => ({ ...prev, effectiveDateFrom: formatDate(past), effectiveDateTo: formatDate(today) }));
    } else if (preset === '30days') {
      const past = new Date();
      past.setDate(today.getDate() - 30);
      setFilters((prev) => ({ ...prev, effectiveDateFrom: formatDate(past), effectiveDateTo: formatDate(today) }));
    } else if (preset === 'year') {
      const currentYear = today.getFullYear();
      setFilters((prev) => ({
        ...prev,
        effectiveDateFrom: `${currentYear}-01-01`,
        effectiveDateTo: `${currentYear}-12-31`,
      }));
    } else if (preset === 'clear') {
      setFilters((prev) => ({ ...prev, effectiveDateFrom: '', effectiveDateTo: '' }));
    }
    setPagination((prev) => ({ ...prev, page: 0 }));
  };

  const handleResetAllFilters = () => {
    setQuickSearch('');
    setFilters({
      status: '',
      keyword: '',
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

  // Sorting Handlers
  const handleSortColumn = (column) => {
    setPagination((prev) => {
      if (prev.sortBy === column) {
        return {
          ...prev,
          sortDirection: prev.sortDirection === 'ASC' ? 'DESC' : 'ASC',
          page: 0,
        };
      }
      return {
        ...prev,
        sortBy: column,
        sortDirection: 'DESC',
        page: 0,
      };
    });
  };

  const renderSortIndicator = (column) => {
    if (pagination.sortBy !== column) {
      return <ArrowUpDown size={13} style={{ opacity: 0.35, marginLeft: '4px' }} />;
    }
    return pagination.sortDirection === 'ASC' ? (
      <ArrowUp size={13} style={{ color: 'var(--primary)', marginLeft: '4px' }} />
    ) : (
      <ArrowDown size={13} style={{ color: 'var(--primary)', marginLeft: '4px' }} />
    );
  };

  // Pagination Handlers
  const handlePageChange = (newPage) => {
    if (newPage >= 0 && newPage < pageInfo.totalPages) {
      setPagination((prev) => ({ ...prev, page: newPage }));
      window.scrollTo({ top: 300, behavior: 'smooth' });
    }
  };

  const handleJumpToPage = (e) => {
    e.preventDefault();
    const pageNum = parseInt(jumpPage, 10);
    if (!isNaN(pageNum) && pageNum >= 1 && pageNum <= pageInfo.totalPages) {
      handlePageChange(pageNum - 1);
      setJumpPage('');
    } else {
      showToast(`Vui lòng nhập trang từ 1 đến ${pageInfo.totalPages}`, 'warning');
    }
  };

  // Dynamic Numeric Pagination Range
  const paginationRange = useMemo(() => {
    const total = pageInfo.totalPages;
    const current = pagination.page + 1;
    if (total <= 7) {
      return Array.from({ length: total }, (_, i) => i + 1);
    }
    if (current <= 4) {
      return [1, 2, 3, 4, 5, '...', total];
    }
    if (current >= total - 3) {
      return [1, '...', total - 4, total - 3, total - 2, total - 1, total];
    }
    return [1, '...', current - 1, current, current + 1, '...', total];
  }, [pageInfo.totalPages, pagination.page]);

  // Export CSV
  const handleExportCsv = () => {
    if (!policies || policies.length === 0) {
      showToast('Không có dữ liệu hợp đồng để xuất CSV', 'warning');
      return;
    }
    const headers = [
      'Mã hợp đồng',
      'Bên mua bảo hiểm',
      'Email',
      'SĐT',
      'Trạng thái',
      'Phiên bản',
      'Ngày hiệu lực',
      'Ngày hết hạn',
      'Số địa điểm',
      'Tổng phí (VND)',
    ];
    const rows = policies.map((p) => [
      `"${p.policyNumber || ''}"`,
      `"${p.insured?.name || ''}"`,
      `"${p.insured?.email || ''}"`,
      `"${p.insured?.phone || ''}"`,
      `"${p.status || ''}"`,
      p.version || 1,
      `"${p.effectiveDate ? new Date(p.effectiveDate).toLocaleDateString('vi-VN') : ''}"`,
      `"${p.expirationDate ? new Date(p.expirationDate).toLocaleDateString('vi-VN') : ''}"`,
      p.locations?.length || 0,
      p.totalPremium || 0,
    ]);
    const csvContent = '\uFEFF' + [headers.join(','), ...rows.map((e) => e.join(','))].join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', `danh_sach_hop_dong_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast('Đã xuất file CSV thành công!', 'success');
  };

  // Create Policy Handler (Quick Create)
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
            address: newPolicyForm.address || 'Địa điểm kinh doanh chính',
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

  // Status Badge Component
  const getStatusBadge = (status) => {
    switch (status) {
      case 'DRAFT':
        return (
          <span className="badge badge-warning" style={{ gap: '0.4rem' }}>
            <span className="status-dot status-dot-draft" />
            DRAFT
          </span>
        );
      case 'QUOTED':
        return (
          <span className="badge badge-primary" style={{ gap: '0.4rem' }}>
            <span className="status-dot status-dot-quoted" />
            QUOTED
          </span>
        );
      case 'BOUND':
        return (
          <span
            className="badge badge-primary"
            style={{ background: 'rgba(6, 182, 212, 0.15)', color: '#67e8f9', gap: '0.4rem' }}
          >
            <span className="status-dot status-dot-bound" />
            BOUND
          </span>
        );
      case 'ACTIVE':
        return (
          <span className="badge badge-success" style={{ gap: '0.4rem' }}>
            <span className="status-dot status-dot-active" />
            ACTIVE
          </span>
        );
      case 'CANCELLED':
        return (
          <span
            className="badge"
            style={{ background: 'rgba(244, 63, 94, 0.15)', color: '#fda4af', border: '1px solid rgba(244, 63, 94, 0.3)', gap: '0.4rem' }}
          >
            <span className="status-dot status-dot-cancelled" />
            CANCELLED
          </span>
        );
      case 'EXPIRED':
        return (
          <span
            className="badge"
            style={{ background: 'rgba(100, 116, 139, 0.2)', color: '#94a3b8', gap: '0.4rem' }}
          >
            <span className="status-dot status-dot-expired" />
            EXPIRED
          </span>
        );
      default:
        return <span className="badge badge-primary">{status}</span>;
    }
  };

  const statusOptions = ['ALL', 'DRAFT', 'QUOTED', 'BOUND', 'ACTIVE', 'CANCELLED', 'EXPIRED'];

  // Calculate quick metrics for KPI cards
  const metrics = useMemo(() => {
    const total = pageInfo.totalElements;
    const active = policies.filter((p) => p.status === 'ACTIVE').length;
    const pending = policies.filter((p) => p.status === 'DRAFT' || p.status === 'QUOTED').length;
    const totalPremiumSum = policies.reduce((sum, p) => sum + (p.totalPremium || 0), 0);

    return { total, active, pending, totalPremiumSum };
  }, [pageInfo.totalElements, policies]);

  // Current page records range
  const startRecord = pageInfo.totalElements === 0 ? 0 : pagination.page * pagination.size + 1;
  const endRecord = Math.min((pagination.page + 1) * pagination.size, pageInfo.totalElements);

  return (
    <div className="page-wrapper">
      {/* Dashboard Header Banner */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          flexWrap: 'wrap',
          gap: '1.25rem',
          marginBottom: '1.75rem',
        }}
      >
        <div>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
            <span className="badge badge-primary">
              <Shield size={12} /> Policy Management Dashboard View
            </span>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              Phân hệ P02, P03 & P08
            </span>
          </div>
          <h1 style={{ fontSize: '1.9rem', marginBottom: '0.4rem' }}>
            Bảng Điều phối & Quản lý Hợp đồng Bảo hiểm
          </h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.95rem' }}>
            Tra cứu nhanh đa tiêu chí, lọc trạng thái & khoảng ngày hiệu lực, bảng dữ liệu tương tác và phân trang động.
          </p>
        </div>

        {/* Action Header Buttons */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
          <button
            onClick={fetchPolicies}
            disabled={loading}
            className="btn btn-secondary"
            title="Làm mới dữ liệu từ server"
          >
            <RefreshCw size={16} className={loading ? 'spinner' : ''} />
            <span>Làm mới</span>
          </button>

          <button
            onClick={handleExportCsv}
            className="btn btn-secondary"
            title="Xuất danh sách đang xem ra file CSV"
          >
            <FileSpreadsheet size={16} color="var(--accent-emerald)" />
            <span>Xuất CSV</span>
          </button>

          <Link
            to="/policies/create"
            className="btn btn-primary"
            style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', boxShadow: 'var(--shadow-glow)' }}
          >
            <Sparkles size={16} />
            <span>Tạo Hợp đồng (Form Động UI03)</span>
          </Link>

          <button
            onClick={() => {
              setNewPolicyForm((p) => ({
                ...p,
                policyNumber: 'POL-2026-' + Date.now().toString().slice(-4),
              }));
              setIsCreateModalOpen(true);
            }}
            className="btn btn-secondary"
            title="Tạo nhanh hợp đồng dạng modal"
          >
            <Plus size={16} />
            <span>Tạo nhanh</span>
          </button>
        </div>
      </div>

      {/* KPI Summary Metric Cards */}
      <div className="metrics-grid">
        <div
          className={`metric-card ${filters.status === '' ? 'active' : ''}`}
          onClick={() => handleStatusFilter('ALL')}
          title="Bấm để xem tất cả hợp đồng"
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.75rem' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontWeight: 600 }}>TỔNG HỢP ĐỒNG</span>
            <div className="metric-icon-wrap" style={{ background: 'rgba(99, 102, 241, 0.15)', color: '#818cf8' }}>
              <FileText size={20} />
            </div>
          </div>
          <div style={{ fontSize: '1.75rem', fontWeight: 800, marginBottom: '0.2rem', color: 'var(--text-primary)' }}>
            {pageInfo.totalElements}
          </div>
          <div style={{ fontSize: '0.775rem', color: 'var(--text-secondary)' }}>
            Toàn bộ hồ sơ trên hệ thống
          </div>
        </div>

        <div
          className={`metric-card ${filters.status === 'ACTIVE' ? 'active' : ''}`}
          onClick={() => handleStatusFilter('ACTIVE')}
          title="Bấm để lọc hợp đồng đang ACTIVE"
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.75rem' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontWeight: 600 }}>ĐANG HIỆU LỰC</span>
            <div className="metric-icon-wrap" style={{ background: 'rgba(16, 185, 129, 0.15)', color: '#10b981' }}>
              <CheckCircle2 size={20} />
            </div>
          </div>
          <div style={{ fontSize: '1.75rem', fontWeight: 800, marginBottom: '0.2rem', color: '#10b981' }}>
            {metrics.active}
          </div>
          <div style={{ fontSize: '0.775rem', color: 'var(--text-secondary)' }}>
            Hợp đồng trạng thái ACTIVE
          </div>
        </div>

        <div
          className={`metric-card ${filters.status === 'DRAFT' || filters.status === 'QUOTED' ? 'active' : ''}`}
          onClick={() => handleStatusFilter('DRAFT')}
          title="Bấm để xem bản thảo DRAFT"
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.75rem' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontWeight: 600 }}>BẢN THẢO / BÁO GIÁ</span>
            <div className="metric-icon-wrap" style={{ background: 'rgba(245, 158, 11, 0.15)', color: '#f59e0b' }}>
              <Clock size={20} />
            </div>
          </div>
          <div style={{ fontSize: '1.75rem', fontWeight: 800, marginBottom: '0.2rem', color: '#f59e0b' }}>
            {metrics.pending}
          </div>
          <div style={{ fontSize: '0.775rem', color: 'var(--text-secondary)' }}>
            Chờ thẩm định hoặc phát hành
          </div>
        </div>

        <div className="metric-card" style={{ cursor: 'default' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.75rem' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontWeight: 600 }}>TỔNG PHÍ ĐANG XEM</span>
            <div className="metric-icon-wrap" style={{ background: 'rgba(6, 182, 212, 0.15)', color: '#06b6d4' }}>
              <DollarSign size={20} />
            </div>
          </div>
          <div style={{ fontSize: '1.4rem', fontWeight: 800, marginBottom: '0.2rem', color: '#38bdf8' }}>
            {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(metrics.totalPremiumSum)}
          </div>
          <div style={{ fontSize: '0.775rem', color: 'var(--text-secondary)' }}>
            Doanh thu phí trang hiện tại
          </div>
        </div>
      </div>

      {/* Quick Search & Filter Dashboard Box */}
      <div className="filter-dashboard-card">
        {/* Row 1: Quick Search Bar + Search Trigger */}
        <form onSubmit={handleQuickSearchSubmit} style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
          <div className="quick-search-wrapper">
            <Search
              size={18}
              color="var(--text-muted)"
              style={{ position: 'absolute', left: '1rem', pointerEvents: 'none' }}
            />
            <input
              type="text"
              value={quickSearch}
              onChange={(e) => setQuickSearch(e.target.value)}
              placeholder="🔍 Quick Search: Tìm kiếm nhanh theo mã hợp đồng, tên khách hàng, email, điện thoại, địa chỉ..."
              className="quick-search-input"
            />
            {quickSearch && (
              <button
                type="button"
                onClick={handleClearQuickSearch}
                style={{
                  position: 'absolute',
                  right: '0.85rem',
                  background: 'transparent',
                  border: 'none',
                  color: 'var(--text-muted)',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                }}
                title="Xóa tìm kiếm nhanh"
              >
                <X size={16} />
              </button>
            )}
          </div>

          <button type="submit" className="btn btn-primary" style={{ padding: '0.75rem 1.5rem' }}>
            <Search size={16} />
            <span>Tìm kiếm</span>
          </button>

          <button
            type="button"
            onClick={() => setShowAdvancedFilters((prev) => !prev)}
            className={`btn ${showAdvancedFilters ? 'btn-primary' : 'btn-secondary'}`}
            style={{ padding: '0.75rem 1.25rem' }}
            title="Mở rộng bộ lọc chi tiết"
          >
            <SlidersHorizontal size={16} />
            <span>Bộ lọc nâng cao</span>
          </button>
        </form>

        {/* Row 2: Status Filter Tabs / Pills */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
          <span style={{ fontSize: '0.8rem', fontWeight: 700, color: 'var(--text-muted)', marginRight: '0.5rem' }}>
            TRẠNG THÁI:
          </span>
          {statusOptions.map((st) => {
            const isSelected = (st === 'ALL' && !filters.status) || filters.status === st;
            return (
              <button
                key={st}
                type="button"
                onClick={() => handleStatusFilter(st)}
                className="btn"
                style={{
                  padding: '0.35rem 0.85rem',
                  fontSize: '0.775rem',
                  fontWeight: 600,
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

        {/* Row 3: Date Filters (Filter Status / Date) */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: '1rem',
            borderTop: '1px solid var(--border-subtle)',
            paddingTop: '1rem',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', flexWrap: 'wrap' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Calendar size={16} color="var(--primary)" />
              <span style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-secondary)' }}>
                Hiệu lực từ ngày:
              </span>
              <input
                type="date"
                name="effectiveDateFrom"
                value={filters.effectiveDateFrom}
                onChange={handleFilterChange}
                className="form-control"
                style={{ width: '150px', padding: '0.4rem 0.6rem', fontSize: '0.8rem' }}
              />
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <span style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-secondary)' }}>
                Đến ngày:
              </span>
              <input
                type="date"
                name="effectiveDateTo"
                value={filters.effectiveDateTo}
                onChange={handleFilterChange}
                className="form-control"
                style={{ width: '150px', padding: '0.4rem 0.6rem', fontSize: '0.8rem' }}
              />
            </div>

            {/* Quick Date Presets */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', flexWrap: 'wrap' }}>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Nhanh:</span>
              <button
                type="button"
                onClick={() => handleDatePreset('today')}
                className="btn btn-secondary"
                style={{ padding: '0.25rem 0.5rem', fontSize: '0.725rem' }}
              >
                Hôm nay
              </button>
              <button
                type="button"
                onClick={() => handleDatePreset('7days')}
                className="btn btn-secondary"
                style={{ padding: '0.25rem 0.5rem', fontSize: '0.725rem' }}
              >
                7 ngày
              </button>
              <button
                type="button"
                onClick={() => handleDatePreset('30days')}
                className="btn btn-secondary"
                style={{ padding: '0.25rem 0.5rem', fontSize: '0.725rem' }}
              >
                30 ngày
              </button>
              <button
                type="button"
                onClick={() => handleDatePreset('year')}
                className="btn btn-secondary"
                style={{ padding: '0.25rem 0.5rem', fontSize: '0.725rem' }}
              >
                Năm nay
              </button>
              {(filters.effectiveDateFrom || filters.effectiveDateTo) && (
                <button
                  type="button"
                  onClick={() => handleDatePreset('clear')}
                  className="btn"
                  style={{
                    padding: '0.25rem 0.5rem',
                    fontSize: '0.725rem',
                    color: '#fda4af',
                    background: 'transparent',
                    border: '1px dashed rgba(244, 63, 94, 0.4)',
                  }}
                >
                  Xóa ngày
                </button>
              )}
            </div>
          </div>

          {/* Reset Filters button */}
          <button
            type="button"
            onClick={handleResetAllFilters}
            className="btn btn-secondary"
            style={{ padding: '0.4rem 0.85rem', fontSize: '0.8rem' }}
            title="Khôi phục trạng thái tìm kiếm ban đầu"
          >
            <RefreshCw size={14} />
            <span>Đặt lại bộ lọc</span>
          </button>
        </div>

        {/* Collapsible Advanced Filters Section */}
        {showAdvancedFilters && (
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
              gap: '1rem',
              borderTop: '1px dashed var(--border-subtle)',
              paddingTop: '1.25rem',
              animation: 'fadeIn 0.25s ease-out',
            }}
          >
            <div className="form-group" style={{ marginBottom: 0 }}>
              <label className="form-label" style={{ fontSize: '0.775rem' }}>
                Mã số hợp đồng chính xác
              </label>
              <input
                type="text"
                name="policyNumber"
                value={filters.policyNumber}
                onChange={handleFilterChange}
                placeholder="VD: POL-2026-001"
                className="form-control"
                style={{ fontSize: '0.85rem', padding: '0.5rem 0.75rem', fontFamily: 'var(--font-mono)' }}
              />
            </div>

            <div className="form-group" style={{ marginBottom: 0 }}>
              <label className="form-label" style={{ fontSize: '0.775rem' }}>
                Bên mua bảo hiểm (Insured Name)
              </label>
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

            <div className="form-group" style={{ marginBottom: 0 }}>
              <label className="form-label" style={{ fontSize: '0.775rem' }}>
                Địa chỉ địa điểm bảo hiểm (Location)
              </label>
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

            <div className="form-group" style={{ marginBottom: 0 }}>
              <label className="form-label" style={{ fontSize: '0.775rem' }}>
                Phí tối thiểu (VND)
              </label>
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

            <div className="form-group" style={{ marginBottom: 0 }}>
              <label className="form-label" style={{ fontSize: '0.775rem' }}>
                Phí tối đa (VND)
              </label>
              <input
                type="number"
                name="maxPremium"
                value={filters.maxPremium}
                onChange={handleFilterChange}
                placeholder="100,000,000"
                className="form-control"
                style={{ fontSize: '0.85rem', padding: '0.5rem 0.75rem' }}
              />
            </div>
          </div>
        )}
      </div>

      {/* Toolbar: Results Count, Sort Controls, View Mode Switcher */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '1rem',
          marginBottom: '1rem',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <span style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
            Hiển thị kết quả: <b style={{ color: 'var(--text-primary)' }}>{startRecord} - {endRecord}</b>{' '}
            trên tổng số <b style={{ color: 'var(--text-primary)' }}>{pageInfo.totalElements}</b> hợp đồng
          </span>
          {loading && <div className="spinner" style={{ width: '1rem', height: '1rem' }} />}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', flexWrap: 'wrap' }}>
          {/* Sort By Dropdown */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.8rem' }}>
            <span style={{ color: 'var(--text-muted)' }}>Sắp xếp:</span>
            <select
              value={pagination.sortBy}
              onChange={(e) => setPagination((p) => ({ ...p, sortBy: e.target.value, page: 0 }))}
              className="form-control"
              style={{ width: '160px', padding: '0.35rem 0.6rem', fontSize: '0.8rem' }}
            >
              <option value="createdAt">Ngày tạo</option>
              <option value="effectiveDate">Ngày hiệu lực</option>
              <option value="totalPremium">Tổng phí bảo hiểm</option>
              <option value="policyNumber">Số hợp đồng</option>
              <option value="status">Trạng thái</option>
            </select>

            <button
              type="button"
              onClick={() =>
                setPagination((p) => ({
                  ...p,
                  sortDirection: p.sortDirection === 'ASC' ? 'DESC' : 'ASC',
                  page: 0,
                }))
              }
              className="btn btn-secondary"
              style={{ padding: '0.35rem 0.65rem', fontSize: '0.8rem' }}
              title={`Chuyển sang ${pagination.sortDirection === 'ASC' ? 'Giảm dần (DESC)' : 'Tăng dần (ASC)'}`}
            >
              <ArrowUpDown size={14} />
              <span>{pagination.sortDirection}</span>
            </button>
          </div>

          {/* View Mode Toggle */}
          <div
            style={{
              display: 'flex',
              background: 'var(--bg-input)',
              borderRadius: 'var(--radius-md)',
              border: '1px solid var(--border-subtle)',
              padding: '2px',
            }}
          >
            <button
              type="button"
              onClick={() => setViewMode('table')}
              className="btn"
              style={{
                padding: '0.35rem 0.65rem',
                fontSize: '0.775rem',
                borderRadius: 'var(--radius-sm)',
                background: viewMode === 'table' ? 'var(--primary-gradient)' : 'transparent',
                color: viewMode === 'table' ? '#ffffff' : 'var(--text-muted)',
                boxShadow: viewMode === 'table' ? 'var(--shadow-glow)' : 'none',
              }}
              title="Chế độ Bảng danh sách (Table View)"
            >
              <TableIcon size={15} />
              <span>Bảng</span>
            </button>

            <button
              type="button"
              onClick={() => setViewMode('cards')}
              className="btn"
              style={{
                padding: '0.35rem 0.65rem',
                fontSize: '0.775rem',
                borderRadius: 'var(--radius-sm)',
                background: viewMode === 'cards' ? 'var(--primary-gradient)' : 'transparent',
                color: viewMode === 'cards' ? '#ffffff' : 'var(--text-muted)',
                boxShadow: viewMode === 'cards' ? 'var(--shadow-glow)' : 'none',
              }}
              title="Chế độ Thẻ (Grid Cards View)"
            >
              <LayoutGrid size={15} />
              <span>Thẻ</span>
            </button>
          </div>
        </div>
      </div>

      {/* Main Content: Bảng hiển thị danh sách (Table View) or Card View */}
      {policies.length === 0 ? (
        <div
          className="glass-card"
          style={{
            textAlign: 'center',
            padding: '3.5rem 2rem',
            borderStyle: 'dashed',
            marginBottom: '1.5rem',
          }}
        >
          <FileText size={48} color="var(--text-muted)" style={{ margin: '0 auto 1.25rem' }} />
          <h3 style={{ fontSize: '1.2rem', marginBottom: '0.5rem' }}>
            Không tìm thấy hợp đồng nào phù hợp
          </h3>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', marginBottom: '1.25rem', maxWidth: '480px', margin: '0 auto 1.25rem' }}>
            Không có kết quả nào khớp với tiêu chí tìm kiếm hoặc bộ lọc hiện tại. Bạn có thể xóa tiêu chí lọc để xem toàn bộ danh sách.
          </p>
          <button onClick={handleResetAllFilters} className="btn btn-primary" style={{ fontSize: '0.85rem' }}>
            <RefreshCw size={14} />
            <span>Đặt lại bộ lọc tìm kiếm</span>
          </button>
        </div>
      ) : viewMode === 'table' ? (
        /* ===================================================
           BẢNG HIỂN THỊ DANH SÁCH (DATA TABLE VIEW)
           =================================================== */
        <div className="data-table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th
                  className="sortable"
                  onClick={() => handleSortColumn('policyNumber')}
                  title="Nhấp để sắp xếp theo Mã HĐ"
                >
                  <span style={{ display: 'inline-flex', alignItems: 'center' }}>
                    Số hợp đồng {renderSortIndicator('policyNumber')}
                  </span>
                </th>
                <th>Bên mua bảo hiểm</th>
                <th
                  className="sortable"
                  onClick={() => handleSortColumn('status')}
                  title="Nhấp để sắp xếp theo Trạng thái"
                >
                  <span style={{ display: 'inline-flex', alignItems: 'center' }}>
                    Trạng thái {renderSortIndicator('status')}
                  </span>
                </th>
                <th
                  className="sortable"
                  onClick={() => handleSortColumn('effectiveDate')}
                  title="Nhấp để sắp xếp theo Ngày hiệu lực"
                >
                  <span style={{ display: 'inline-flex', alignItems: 'center' }}>
                    Hiệu lực {renderSortIndicator('effectiveDate')}
                  </span>
                </th>
                <th>Địa điểm</th>
                <th
                  className="sortable"
                  style={{ textAlign: 'right' }}
                  onClick={() => handleSortColumn('totalPremium')}
                  title="Nhấp để sắp xếp theo Tổng phí"
                >
                  <span style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'flex-end', width: '100%' }}>
                    Tổng phí (VND) {renderSortIndicator('totalPremium')}
                  </span>
                </th>
                <th style={{ textAlign: 'center' }}>Thao tác nghiệp vụ</th>
              </tr>
            </thead>
            <tbody>
              {policies.map((policy) => {
                const effDateStr = policy.effectiveDate
                  ? new Date(policy.effectiveDate).toLocaleDateString('vi-VN')
                  : '—';
                const expDateStr = policy.expirationDate
                  ? new Date(policy.expirationDate).toLocaleDateString('vi-VN')
                  : '—';

                return (
                  <tr key={policy.id || policy.policyNumber}>
                    {/* Policy Number & Version */}
                    <td>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.2rem' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                          <Link
                            to={`/policies/${policy.policyNumber}`}
                            style={{
                              fontFamily: 'var(--font-mono)',
                              fontWeight: 700,
                              color: 'var(--primary)',
                              fontSize: '0.95rem',
                              textDecoration: 'none',
                            }}
                            title="Xem chi tiết hợp đồng, chuyển trạng thái & audit log"
                          >
                            {policy.policyNumber}
                          </Link>
                          <span
                            style={{
                              fontSize: '0.7rem',
                              fontWeight: 600,
                              padding: '0.15rem 0.45rem',
                              borderRadius: 'var(--radius-sm)',
                              background: 'rgba(99, 102, 241, 0.15)',
                              color: '#a5b4fc',
                            }}
                          >
                            v{policy.version || 1}
                          </span>
                        </div>
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                          Tạo ngày: {policy.createdAt ? new Date(policy.createdAt).toLocaleDateString('vi-VN') : '—'}
                        </span>
                      </div>
                    </td>

                    {/* Insured Person / Business */}
                    <td>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.2rem' }}>
                        <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>
                          {policy.insured?.name || 'Chưa cập nhật người thụ hưởng'}
                        </div>
                        <div style={{ fontSize: '0.775rem', color: 'var(--text-secondary)', display: 'flex', gap: '0.75rem' }}>
                          <span>{policy.insured?.phone || '—'}</span>
                          <span>{policy.insured?.email || '—'}</span>
                        </div>
                      </div>
                    </td>

                    {/* Status Badge */}
                    <td>{getStatusBadge(policy.status)}</td>

                    {/* Effective & Expiration Dates */}
                    <td>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.2rem', fontSize: '0.8rem' }}>
                        <div style={{ color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                          <span style={{ color: 'var(--text-muted)' }}>Từ:</span> {effDateStr}
                        </div>
                        <div style={{ color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                          <span style={{ color: 'var(--text-muted)' }}>Đến:</span> {expDateStr}
                        </div>
                      </div>
                    </td>

                    {/* Locations Count & Details */}
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                        <MapPin size={14} color="var(--primary)" />
                        <span style={{ fontWeight: 600 }}>{policy.locations?.length || 0}</span>
                        <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>địa điểm</span>
                      </div>
                    </td>

                    {/* Total Premium */}
                    <td style={{ textAlign: 'right' }}>
                      <span
                        style={{
                          fontWeight: 700,
                          fontSize: '0.975rem',
                          color: '#10b981',
                          fontFamily: 'var(--font-mono)',
                        }}
                      >
                        {new Intl.NumberFormat('vi-VN', {
                          style: 'currency',
                          currency: 'VND',
                        }).format(policy.totalPremium || 0)}
                      </span>
                    </td>

                    {/* Actions Menu */}
                    <td>
                      <div
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          gap: '0.4rem',
                          flexWrap: 'nowrap',
                        }}
                      >
                        <Link
                          to={`/policies/${policy.policyNumber}`}
                          className="btn btn-secondary"
                          style={{ padding: '0.4rem 0.6rem', fontSize: '0.775rem', color: '#38bdf8', borderColor: 'rgba(56, 189, 248, 0.3)' }}
                          title="Xem chi tiết hợp đồng, chuyển trạng thái & audit log (P06-P09)"
                        >
                          <Eye size={13} />
                          <span>Chi tiết</span>
                        </Link>

                        <Link
                          to={`/policies/edit/${policy.policyNumber}`}
                          className="btn btn-secondary"
                          style={{ padding: '0.4rem 0.6rem', fontSize: '0.775rem', color: '#818cf8', borderColor: 'rgba(99, 102, 241, 0.3)' }}
                          title="Chỉnh sửa toàn diện (Dynamic Form UI03)"
                        >
                          <Edit3 size={13} />
                          <span>Sửa</span>
                        </Link>

                        <button
                          onClick={() => {
                            showToast(`Đang tạo và tải Giấy chứng nhận PDF cho ${policy.policyNumber}...`, 'info');
                            documentService
                              .downloadCertificate(policy.policyNumber, policy.version)
                              .catch((e) => showToast(e.message || 'Lỗi tải PDF', 'error'));
                          }}
                          className="btn btn-secondary"
                          style={{ padding: '0.4rem 0.6rem', fontSize: '0.775rem' }}
                          title="Tải Giấy chứng nhận bảo hiểm (Certificate PDF)"
                        >
                          <Download size={13} color="var(--primary)" />
                          <span>PDF</span>
                        </button>

                        {policy.status === 'BOUND' && (
                          <button
                            onClick={() => setPaymentModalPolicy(policy)}
                            className="btn btn-primary"
                            style={{
                              padding: '0.4rem 0.65rem',
                              fontSize: '0.775rem',
                              background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
                              borderColor: '#10b981',
                            }}
                            title="Thanh toán QR Code & Kích hoạt hợp đồng (FR-PAY-01/02/03)"
                          >
                            <QrCode size={13} />
                            <span>QR Pay</span>
                          </button>
                        )}

                        <button
                          onClick={async () => {
                            try {
                              showToast(`Đang xuất bảng tính phí Excel cho ${policy.policyNumber}...`, 'info');
                              await excelService.downloadPolicyBreakdown(policy.policyNumber);
                              showToast('Đã tải xuống bảng tính phí Excel thành công!', 'success');
                            } catch (err) {
                              showToast(err.message || 'Lỗi xuất Excel', 'error');
                            }
                          }}
                          className="btn btn-secondary"
                          style={{ padding: '0.4rem 0.6rem', fontSize: '0.775rem', color: '#10b981', borderColor: 'rgba(16, 185, 129, 0.3)' }}
                          title="Xuất bảng tính phí bảo hiểm ra Excel (FR-CALC-04)"
                        >
                          <FileSpreadsheet size={13} />
                          <span>Excel</span>
                        </button>

                        <button
                          onClick={() => setHistoryModalPolicy(policy)}
                          className="btn btn-secondary"
                          style={{ padding: '0.4rem 0.6rem', fontSize: '0.775rem' }}
                          title="Xem sổ cái giao dịch & quản lý phụ lục phiên bản (P08/P09)"
                        >
                          <History size={13} color="var(--primary)" />
                          <span>Lịch sử & Phụ lục</span>
                        </button>

                        <Link
                          to="/policies/locations"
                          className="btn btn-secondary"
                          style={{ padding: '0.4rem 0.6rem', fontSize: '0.775rem' }}
                          title="Quản lý địa điểm bảo hiểm (P04)"
                        >
                          <Eye size={13} />
                          <span>Địa điểm</span>
                        </Link>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      ) : (
        /* ===================================================
           LƯỚI THẺ (GRID CARDS VIEW)
           =================================================== */
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginBottom: '1.5rem' }}>
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
                  <Link
                    to={`/policies/${policy.policyNumber}`}
                    style={{
                      fontFamily: 'var(--font-mono)',
                      fontWeight: 700,
                      fontSize: '1.05rem',
                      color: 'var(--primary)',
                      textDecoration: 'none',
                    }}
                    title="Xem chi tiết hợp đồng, chuyển trạng thái & audit log"
                  >
                    {policy.policyNumber}
                  </Link>
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
                  Hiệu lực:{' '}
                  {policy.effectiveDate ? new Date(policy.effectiveDate).toLocaleDateString('vi-VN') : '—'}
                </div>
              </div>

              {/* Right Column: Total Premium & Action */}
              <div style={{ display: 'flex', alignItems: 'center', gap: '1.25rem', flexWrap: 'wrap' }}>
                <div style={{ textAlign: 'right', minWidth: '130px' }}>
                  <span style={{ fontSize: '0.725rem', color: 'var(--text-muted)', display: 'block' }}>
                    TỔNG PHÍ BẢO HIỂM
                  </span>
                  <span style={{ fontSize: '1.15rem', fontWeight: 700, color: '#10b981' }}>
                    {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(
                      policy.totalPremium || 0
                    )}
                  </span>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', flexWrap: 'wrap' }}>
                  <Link
                    to={`/policies/${policy.policyNumber}`}
                    className="btn btn-secondary"
                    style={{ padding: '0.45rem 0.75rem', fontSize: '0.8rem', color: '#38bdf8', borderColor: 'rgba(56, 189, 248, 0.3)' }}
                    title="Xem chi tiết hợp đồng, chuyển trạng thái & audit log (P06-P09)"
                  >
                    <Eye size={14} />
                    <span>Chi tiết</span>
                  </Link>

                  <Link
                    to={`/policies/edit/${policy.policyNumber}`}
                    className="btn btn-secondary"
                    style={{ padding: '0.45rem 0.75rem', fontSize: '0.8rem', color: '#818cf8', borderColor: 'rgba(99, 102, 241, 0.3)' }}
                    title="Chỉnh sửa toàn diện (Dynamic Form UI03)"
                  >
                    <Edit3 size={14} />
                    <span>Sửa (UI03)</span>
                  </Link>

                  <button
                    onClick={() => {
                      showToast(`Đang tạo và tải Giấy chứng nhận PDF cho ${policy.policyNumber}...`, 'info');
                      documentService
                        .downloadCertificate(policy.policyNumber, policy.version)
                        .catch((e) => showToast(e.message || 'Lỗi tải PDF', 'error'));
                    }}
                    className="btn btn-secondary"
                    style={{ padding: '0.45rem 0.75rem', fontSize: '0.8rem' }}
                    title="Tải Giấy chứng nhận bảo hiểm (Certificate PDF)"
                  >
                    <Download size={14} color="var(--primary)" />
                    <span>Tải PDF</span>
                  </button>

                  {policy.status === 'BOUND' && (
                    <button
                      onClick={() => setPaymentModalPolicy(policy)}
                      className="btn btn-primary"
                      style={{
                        padding: '0.45rem 0.75rem',
                        fontSize: '0.8rem',
                        background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
                        borderColor: '#10b981',
                      }}
                      title="Thanh toán QR Code & Kích hoạt hợp đồng (FR-PAY-01/02/03)"
                    >
                      <QrCode size={14} />
                      <span>Thanh toán QR</span>
                    </button>
                  )}

                  <button
                    onClick={async () => {
                      try {
                        showToast(`Đang xuất bảng tính phí Excel cho ${policy.policyNumber}...`, 'info');
                        await excelService.downloadPolicyBreakdown(policy.policyNumber);
                        showToast('Đã tải xuống bảng tính phí Excel thành công!', 'success');
                      } catch (err) {
                        showToast(err.message || 'Lỗi xuất Excel', 'error');
                      }
                    }}
                    className="btn btn-secondary"
                    style={{ padding: '0.45rem 0.75rem', fontSize: '0.8rem', color: '#10b981', borderColor: 'rgba(16, 185, 129, 0.3)' }}
                    title="Xuất bảng tính phí bảo hiểm ra Excel (FR-CALC-04)"
                  >
                    <FileSpreadsheet size={14} />
                    <span>Excel</span>
                  </button>

                  <button
                    onClick={() => setHistoryModalPolicy(policy)}
                    className="btn btn-secondary"
                    style={{ padding: '0.45rem 0.75rem', fontSize: '0.8rem' }}
                    title="Xem sổ cái giao dịch & quản lý phụ lục phiên bản (P08/P09)"
                  >
                    <History size={14} color="var(--primary)" />
                    <span>Lịch sử & Phụ lục</span>
                  </button>

                  <Link
                    to="/policies/locations"
                    className="btn btn-secondary"
                    style={{ padding: '0.45rem 0.75rem', fontSize: '0.8rem' }}
                  >
                    <Eye size={14} />
                    <span>Địa điểm</span>
                  </Link>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* ===================================================
          PAGING CONTROLS (PHÂN TRANG HOÀN CHỈNH)
          =================================================== */
      }
      <div className="pagination-wrapper">
        {/* Left: Page Size Selector & Range */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.85rem' }}>
            <span style={{ color: 'var(--text-muted)' }}>Hiển thị:</span>
            <select
              value={pagination.size}
              onChange={(e) => setPagination((p) => ({ ...p, size: parseInt(e.target.value, 10), page: 0 }))}
              className="form-control"
              style={{ width: '75px', padding: '0.35rem 0.5rem', fontSize: '0.85rem' }}
            >
              <option value="5">5</option>
              <option value="10">10</option>
              <option value="25">25</option>
              <option value="50">50</option>
            </select>
            <span style={{ color: 'var(--text-muted)' }}>hợp đồng / trang</span>
          </div>

          <span style={{ fontSize: '0.825rem', color: 'var(--text-secondary)' }}>
            Trang <b style={{ color: 'var(--text-primary)' }}>{pagination.page + 1}</b> /{' '}
            <b style={{ color: 'var(--text-primary)' }}>{Math.max(pageInfo.totalPages, 1)}</b>
          </span>
        </div>

        {/* Center: Numeric Page Navigation Controls */}
        <div className="pagination-controls">
          {/* First Page Button */}
          <button
            onClick={() => handlePageChange(0)}
            disabled={pageInfo.first || pagination.page === 0}
            className="page-num-btn"
            title="Trang đầu tiên"
          >
            <ChevronsLeft size={16} />
          </button>

          {/* Previous Page Button */}
          <button
            onClick={() => handlePageChange(pagination.page - 1)}
            disabled={pageInfo.first || pagination.page === 0}
            className="page-num-btn"
            title="Trang trước"
          >
            <ChevronLeft size={16} />
          </button>

          {/* Numeric Page Buttons */}
          {paginationRange.map((item, idx) => {
            if (item === '...') {
              return (
                <span
                  key={`ellipsis-${idx}`}
                  style={{ padding: '0 0.35rem', color: 'var(--text-muted)', fontSize: '0.85rem' }}
                >
                  …
                </span>
              );
            }
            const pageIndex = item - 1;
            const isActive = pagination.page === pageIndex;
            return (
              <button
                key={item}
                onClick={() => handlePageChange(pageIndex)}
                className={`page-num-btn ${isActive ? 'active' : ''}`}
              >
                {item}
              </button>
            );
          })}

          {/* Next Page Button */}
          <button
            onClick={() => handlePageChange(pagination.page + 1)}
            disabled={pageInfo.last || pagination.page >= pageInfo.totalPages - 1}
            className="page-num-btn"
            title="Trang tiếp theo"
          >
            <ChevronRight size={16} />
          </button>

          {/* Last Page Button */}
          <button
            onClick={() => handlePageChange(pageInfo.totalPages - 1)}
            disabled={pageInfo.last || pagination.page >= pageInfo.totalPages - 1}
            className="page-num-btn"
            title="Trang cuối cùng"
          >
            <ChevronsRight size={16} />
          </button>
        </div>

        {/* Right: Direct Jump to Page */}
        {pageInfo.totalPages > 1 && (
          <form onSubmit={handleJumpToPage} style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Đến:</span>
            <input
              type="number"
              min="1"
              max={pageInfo.totalPages}
              value={jumpPage}
              onChange={(e) => setJumpPage(e.target.value)}
              placeholder="#"
              className="form-control"
              style={{ width: '56px', padding: '0.35rem 0.4rem', fontSize: '0.8rem', textAlign: 'center' }}
            />
            <button
              type="submit"
              className="btn btn-secondary"
              style={{ padding: '0.35rem 0.65rem', fontSize: '0.8rem' }}
            >
              Đi
            </button>
          </form>
        )}
      </div>

      {/* ===================================================
          MODAL: TẠO HỢP ĐỒNG BẢO HIỂM MỚI (TẠO NHANH)
          =================================================== */}
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
                Tạo nhanh Hợp đồng Bảo hiểm
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

      {/* History and Endorsement Modal (P08, P09) */}
      <PolicyHistoryAndEndorsementModal
        policy={historyModalPolicy}
        isOpen={Boolean(historyModalPolicy)}
        onClose={() => setHistoryModalPolicy(null)}
        onPolicyUpdated={(updated) => {
          fetchPolicies();
          if (historyModalPolicy) {
            setHistoryModalPolicy(updated);
          }
        }}
      />

      {/* Payment Modal (FR-PAY-01/02/03) */}
      <PaymentModal
        policy={paymentModalPolicy}
        isOpen={Boolean(paymentModalPolicy)}
        onClose={() => setPaymentModalPolicy(null)}
        onPaymentSuccess={() => {
          setPaymentModalPolicy(null);
          fetchPolicies();
        }}
      />
    </div>
  );
};
