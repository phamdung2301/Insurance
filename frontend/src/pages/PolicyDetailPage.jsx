import React, { useState, useEffect, useCallback } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import {
  Shield,
  Clock,
  User,
  MapPin,
  DollarSign,
  FileText,
  History,
  GitBranch,
  ArrowRight,
  Download,
  Edit3,
  RefreshCw,
  CheckCircle2,
  AlertTriangle,
  Sparkles,
  Layers,
  ArrowRightLeft,
  ChevronRight,
  XCircle,
  Building,
  Calendar,
  AlertCircle,
  ExternalLink,
  GitCommit,
  Check,
} from 'lucide-react';
import { policyService, endorsementService, documentService } from '../services/api';
import { useToast } from '../context/ToastContext';
import { EndorsementModal } from '../components/EndorsementModal';
import { StatusTransitionModal } from '../components/StatusTransitionModal';

export const PolicyDetailPage = () => {
  const { policyNumber } = useParams();
  const navigate = useNavigate();
  const { showToast } = useToast();

  // Core Data States
  const [policy, setPolicy] = useState(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('overview'); // 'overview' | 'locations' | 'timeline' | 'versions'

  // History & Ledger States (P09)
  const [transactions, setTransactions] = useState([]);
  const [loadingHistory, setLoadingHistory] = useState(false);

  // Versions & Diff States (P08)
  const [versions, setVersions] = useState([]);
  const [loadingVersions, setLoadingVersions] = useState(false);
  const [diffV1, setDiffV1] = useState(1);
  const [diffV2, setDiffV2] = useState(1);
  const [diffResult, setDiffResult] = useState(null);
  const [comparing, setComparing] = useState(false);

  // Modals
  const [showEndorsementModal, setShowEndorsementModal] = useState(false);
  const [showTransitionModal, setShowTransitionModal] = useState(false);
  const [transitionTarget, setTransitionTarget] = useState(null);

  // Fetch Policy Details
  const fetchPolicy = useCallback(async () => {
    if (!policyNumber) return;
    try {
      setLoading(true);
      const res = await policyService.getPolicyById(policyNumber);
      const data = res.data;
      setPolicy(data);
    } catch (err) {
      showToast(err.message || 'Không thể tải thông tin hợp đồng', 'error');
    } finally {
      setLoading(false);
    }
  }, [policyNumber, showToast]);

  // Fetch Audit Ledger History (P09)
  const fetchHistory = useCallback(async () => {
    if (!policyNumber) return;
    try {
      setLoadingHistory(true);
      const res = await policyService.getHistory(policyNumber);
      setTransactions(res.data || []);
    } catch (err) {
      console.error('Error fetching transactions:', err);
    } finally {
      setLoadingHistory(false);
    }
  }, [policyNumber]);

  // Fetch Version Snapshots (P08)
  const fetchVersions = useCallback(async () => {
    if (!policyNumber) return;
    try {
      setLoadingVersions(true);
      const res = await policyService.getVersions(policyNumber);
      const vList = res.data || [];
      setVersions(vList);
      if (vList.length >= 2) {
        setDiffV1(vList[vList.length - 1].version);
        setDiffV2(vList[0].version);
      } else if (vList.length === 1) {
        setDiffV1(vList[0].version);
        setDiffV2(vList[0].version);
      }
    } catch (err) {
      console.error('Error fetching versions:', err);
    } finally {
      setLoadingVersions(false);
    }
  }, [policyNumber]);

  // Load all data on mount
  useEffect(() => {
    fetchPolicy();
    fetchHistory();
    fetchVersions();
  }, [fetchPolicy, fetchHistory, fetchVersions]);

  // Handle Version Diff Comparison
  const handleCompareVersions = async () => {
    if (!diffV1 || !diffV2) return;
    try {
      setComparing(true);
      const res = await policyService.compareVersions(policyNumber, diffV1, diffV2);
      setDiffResult(res.data);
    } catch (err) {
      showToast(err.message || 'Không thể so sánh hai phiên bản', 'error');
    } finally {
      setComparing(false);
    }
  };

  // Open Transition Modal helper
  const handleOpenTransition = (targetStatus) => {
    setTransitionTarget(targetStatus);
    setShowTransitionModal(true);
  };

  // After Status Transition or Endorsement update
  const handlePolicyUpdated = (updated) => {
    if (updated) {
      setPolicy(updated);
    } else {
      fetchPolicy();
    }
    fetchHistory();
    fetchVersions();
  };

  // PDF Downloads
  const handleDownloadCOI = async (version) => {
    try {
      showToast(`Đang tải Giấy chứng nhận bảo hiểm v${version || policy.version || 1}...`, 'info');
      await documentService.downloadCertificate(policy.policyNumber, version || policy.version);
    } catch (err) {
      showToast(err.message || 'Lỗi khi tải COI PDF', 'error');
    }
  };

  const handleDownloadSummary = async (version) => {
    try {
      showToast(`Đang tải Bảng tóm tắt hợp đồng v${version || policy.version || 1}...`, 'info');
      await documentService.downloadSummary(policy.policyNumber, version || policy.version);
    } catch (err) {
      showToast(err.message || 'Lỗi khi tải Summary PDF', 'error');
    }
  };

  if (loading) {
    return (
      <div className="page-wrapper" style={{ textAlign: 'center', padding: '6rem 2rem' }}>
        <div className="spinner" style={{ width: '40px', height: '40px', margin: '0 auto 1.5rem' }} />
        <h3 style={{ fontSize: '1.25rem', color: 'var(--text-primary)' }}>Đang tải chi tiết hợp đồng...</h3>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Vui lòng đợi trong giây lát</p>
      </div>
    );
  }

  if (!policy) {
    return (
      <div className="page-wrapper" style={{ textAlign: 'center', padding: '6rem 2rem' }}>
        <AlertTriangle size={48} color="#ef4444" style={{ margin: '0 auto 1.5rem' }} />
        <h3 style={{ fontSize: '1.35rem', color: 'var(--text-primary)' }}>Không tìm thấy hợp đồng</h3>
        <p style={{ color: 'var(--text-muted)', marginBottom: '1.5rem' }}>
          Mã số hợp đồng <b>{policyNumber}</b> không tồn tại trong hệ thống.
        </p>
        <Link to="/policies" className="btn btn-primary">
          Quay lại Danh sách hợp đồng
        </Link>
      </div>
    );
  }

  // Formatters
  const formatVND = (amount) =>
    new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount || 0);

  const formatDate = (isoStr) =>
    isoStr ? new Date(isoStr).toLocaleDateString('vi-VN') : '—';

  const formatDateTime = (isoStr) =>
    isoStr ? new Date(isoStr).toLocaleString('vi-VN') : '—';

  // Lifecycle steps calculation
  const statusSteps = ['DRAFT', 'QUOTED', 'BOUND', 'ACTIVE'];
  const currentStepIdx = statusSteps.indexOf(policy.status);
  const isTerminated = policy.status === 'CANCELLED' || policy.status === 'EXPIRED';

  // Status Badge Class
  const getStatusBadge = (status) => {
    switch (status) {
      case 'ACTIVE':
        return <span className="badge badge-success"><span className="status-dot status-dot-active" /> ACTIVE (Hiệu lực)</span>;
      case 'QUOTED':
        return <span className="badge badge-info"><span className="status-dot status-dot-quoted" /> QUOTED (Báo giá)</span>;
      case 'BOUND':
        return <span className="badge badge-primary"><span className="status-dot status-dot-bound" /> BOUND (Ràng buộc)</span>;
      case 'DRAFT':
        return <span className="badge badge-warning"><span className="status-dot status-dot-draft" /> DRAFT (Bản nháp)</span>;
      case 'CANCELLED':
        return <span className="badge badge-danger"><span className="status-dot status-dot-cancelled" /> CANCELLED (Đã hủy)</span>;
      case 'EXPIRED':
      default:
        return <span className="badge badge-secondary"><span className="status-dot status-dot-expired" /> EXPIRED (Hết hạn)</span>;
    }
  };

  // Transaction icon mapper
  const getTransactionIcon = (type) => {
    switch (type) {
      case 'CREATE_POLICY':
        return <Sparkles size={16} color="#10b981" />;
      case 'STATUS_TRANSITION':
        return <ArrowRightLeft size={16} color="#6366f1" />;
      case 'ENDORSEMENT':
        return <Layers size={16} color="#06b6d4" />;
      case 'UPDATE_POLICY':
        return <Edit3 size={16} color="#f59e0b" />;
      case 'CANCEL_POLICY':
        return <XCircle size={16} color="#ef4444" />;
      default:
        return <GitCommit size={16} color="#94a3b8" />;
    }
  };

  return (
    <div className="page-wrapper">
      {/* =========================================================
          BREADCRUMBS & TOP NAVIGATION
          ========================================================= */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1.25rem', fontSize: '0.825rem', color: 'var(--text-muted)' }}>
        <Link to="/" style={{ color: 'var(--text-muted)' }}>Bảng điều khiển</Link>
        <ChevronRight size={14} />
        <Link to="/policies" style={{ color: 'var(--text-muted)' }}>Hợp đồng bảo hiểm</Link>
        <ChevronRight size={14} />
        <span style={{ color: 'var(--text-primary)', fontWeight: 600, fontFamily: 'var(--font-mono)' }}>{policy.policyNumber}</span>
      </div>

      {/* =========================================================
          POLICY DETAIL HEADER & ACTION LIST TRANSITION BAR
          ========================================================= */}
      <div
        className="glass-card"
        style={{
          padding: '1.75rem',
          marginBottom: '1.75rem',
          border: '1px solid rgba(99, 102, 241, 0.25)',
          background: 'linear-gradient(180deg, rgba(17, 24, 39, 0.85) 0%, rgba(15, 23, 42, 0.7) 100%)',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1.25rem', marginBottom: '1.5rem' }}>
          {/* Policy Title & Status Meta */}
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.85rem', flexWrap: 'wrap', marginBottom: '0.5rem' }}>
              <div
                style={{
                  width: '42px',
                  height: '42px',
                  borderRadius: 'var(--radius-md)',
                  background: 'rgba(99, 102, 241, 0.15)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  border: '1px solid rgba(99, 102, 241, 0.3)',
                }}
              >
                <Shield size={22} color="var(--primary)" />
              </div>
              <h1 style={{ fontSize: '1.75rem', margin: 0, fontFamily: 'var(--font-mono)', fontWeight: 800 }}>
                {policy.policyNumber}
              </h1>
              {getStatusBadge(policy.status)}
              <span
                style={{
                  fontSize: '0.75rem',
                  fontWeight: 700,
                  padding: '0.2rem 0.6rem',
                  borderRadius: 'var(--radius-full)',
                  background: 'rgba(99, 102, 241, 0.2)',
                  color: '#a5b4fc',
                  border: '1px solid rgba(99, 102, 241, 0.35)',
                }}
              >
                Phiên bản v{policy.version || 1}
              </span>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '1.25rem', fontSize: '0.825rem', color: 'var(--text-secondary)', flexWrap: 'wrap' }}>
              <span>Bên mua: <b style={{ color: 'var(--text-primary)' }}>{policy.insured?.name || 'Chưa cập nhật'}</b></span>
              <span>•</span>
              <span>Khởi tạo: <b>{formatDate(policy.createdAt)}</b></span>
              <span>•</span>
              <span>Tổng phí: <b style={{ color: '#10b981', fontSize: '0.95rem' }}>{formatVND(policy.totalPremium)}</b></span>
            </div>
          </div>

          {/* Core Actions (PDF, Refresh, Locations, Edit) */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', flexWrap: 'wrap' }}>
            <button
              onClick={() => {
                fetchPolicy();
                fetchHistory();
                fetchVersions();
                showToast('Đã làm mới dữ liệu hợp đồng!', 'info');
              }}
              className="btn btn-secondary"
              title="Làm mới dữ liệu từ server"
              style={{ padding: '0.5rem 0.75rem' }}
            >
              <RefreshCw size={15} />
            </button>

            <button
              onClick={() => handleDownloadCOI()}
              className="btn btn-secondary"
              title="Tải Giấy chứng nhận bảo hiểm (Certificate PDF)"
              style={{ padding: '0.5rem 0.85rem' }}
            >
              <Download size={15} color="var(--primary)" />
              <span>COI PDF</span>
            </button>

            <button
              onClick={() => handleDownloadSummary()}
              className="btn btn-secondary"
              title="Tải Bảng tóm tắt hợp đồng (Policy Summary PDF)"
              style={{ padding: '0.5rem 0.85rem' }}
            >
              <FileText size={15} color="#10b981" />
              <span>Summary PDF</span>
            </button>

            <Link
              to="/policies/locations"
              className="btn btn-secondary"
              title="Quản lý địa điểm bảo hiểm (P04)"
              style={{ padding: '0.5rem 0.85rem' }}
            >
              <MapPin size={15} />
              <span>Địa điểm</span>
            </Link>

            {policy.status !== 'ACTIVE' && !isTerminated && (
              <Link
                to={`/policies/edit/${policy.policyNumber}`}
                className="btn btn-secondary"
                title="Chỉnh sửa toàn diện thông tin (Dynamic Form UI03)"
                style={{ padding: '0.5rem 0.85rem', color: '#818cf8', borderColor: 'rgba(99, 102, 241, 0.3)' }}
              >
                <Edit3 size={15} />
                <span>Chỉnh sửa Form</span>
              </Link>
            )}
          </div>
        </div>

        {/* =========================================================
            STATE TRANSITION ACTION BAR
            ========================================================= */}
        <div
          style={{
            background: 'rgba(15, 23, 42, 0.75)',
            border: '1px solid var(--border-subtle)',
            borderRadius: 'var(--radius-lg)',
            padding: '1rem 1.25rem',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: '1rem',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
            <Sparkles size={18} color="var(--primary)" />
            <span style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-primary)' }}>
              Thao tác chuyển trạng thái (State-Machine Actions):
            </span>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', flexWrap: 'wrap' }}>
            {/* If DRAFT -> can transition to QUOTED */}
            {policy.status === 'DRAFT' && (
              <button
                onClick={() => handleOpenTransition('QUOTED')}
                className="btn btn-primary"
                style={{ fontSize: '0.825rem', padding: '0.45rem 1rem' }}
              >
                <ArrowRight size={15} />
                <span>Phát hành Báo giá (QUOTED)</span>
              </button>
            )}

            {/* If QUOTED -> can transition to BOUND or back to DRAFT */}
            {policy.status === 'QUOTED' && (
              <>
                <button
                  onClick={() => handleOpenTransition('DRAFT')}
                  className="btn btn-secondary"
                  style={{ fontSize: '0.825rem', padding: '0.45rem 0.85rem' }}
                >
                  Quay về DRAFT
                </button>
                <button
                  onClick={() => handleOpenTransition('BOUND')}
                  className="btn btn-primary"
                  style={{ fontSize: '0.825rem', padding: '0.45rem 1rem' }}
                >
                  <ArrowRight size={15} />
                  <span>Ràng buộc Hợp đồng (BOUND)</span>
                </button>
              </>
            )}

            {/* If BOUND -> can transition to ACTIVE, CANCELLED, or DRAFT */}
            {policy.status === 'BOUND' && (
              <>
                <button
                  onClick={() => handleOpenTransition('DRAFT')}
                  className="btn btn-secondary"
                  style={{ fontSize: '0.825rem', padding: '0.45rem 0.85rem' }}
                >
                  Quay về DRAFT
                </button>
                <button
                  onClick={() => handleOpenTransition('CANCELLED')}
                  className="btn btn-danger"
                  style={{ fontSize: '0.825rem', padding: '0.45rem 0.85rem' }}
                >
                  Hủy bỏ (CANCELLED)
                </button>
                <button
                  onClick={() => handleOpenTransition('ACTIVE')}
                  className="btn btn-primary"
                  style={{
                    fontSize: '0.825rem',
                    padding: '0.45rem 1rem',
                    background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
                    borderColor: '#10b981',
                  }}
                >
                  <CheckCircle2 size={15} />
                  <span>Kích hoạt Hiệu lực (ACTIVE)</span>
                </button>
              </>
            )}

            {/* If ACTIVE -> can Endorse (P08), or CANCELLED, or EXPIRED */}
            {policy.status === 'ACTIVE' && (
              <>
                <button
                  onClick={() => setShowEndorsementModal(true)}
                  className="btn btn-primary"
                  style={{
                    fontSize: '0.825rem',
                    padding: '0.45rem 1rem',
                    background: 'var(--primary-gradient)',
                  }}
                >
                  <Layers size={15} />
                  <span>Tạo Endorsement / Phụ lục (P08)</span>
                </button>

                <button
                  onClick={() => handleOpenTransition('CANCELLED')}
                  className="btn btn-danger"
                  style={{ fontSize: '0.825rem', padding: '0.45rem 0.85rem' }}
                >
                  Hủy hợp đồng (CANCELLED)
                </button>

                <button
                  onClick={() => handleOpenTransition('EXPIRED')}
                  className="btn btn-secondary"
                  style={{ fontSize: '0.825rem', padding: '0.45rem 0.85rem' }}
                >
                  Hết hiệu lực (EXPIRED)
                </button>
              </>
            )}

            {/* If CANCELLED or EXPIRED */}
            {isTerminated && (
              <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                Hợp đồng đã kết thúc vòng đời. Không có bước chuyển tiếp khả dụng.
              </span>
            )}
          </div>
        </div>

        {/* =========================================================
            LIFECYCLE STEPPER PROGRESSION
            ========================================================= */}
        <div style={{ marginTop: '1.75rem', paddingTop: '1.25rem', borderTop: '1px dashed var(--border-subtle)' }}>
          <div className="stepper-container">
            <div className="stepper-line">
              <div
                className="stepper-line-progress"
                style={{
                  width:
                    currentStepIdx >= 0
                      ? `${(currentStepIdx / (statusSteps.length - 1)) * 100}%`
                      : '0%',
                }}
              />
            </div>

            {/* Step 1: DRAFT */}
            <div className={`stepper-item ${currentStepIdx > 0 ? 'completed' : currentStepIdx === 0 ? 'current' : ''}`}>
              <div className="stepper-circle">
                {currentStepIdx > 0 ? <Check size={18} /> : 1}
              </div>
              <div className="stepper-label">DRAFT</div>
              <div className="stepper-sub">{formatDate(policy.createdAt)}</div>
            </div>

            {/* Step 2: QUOTED */}
            <div className={`stepper-item ${currentStepIdx > 1 ? 'completed' : currentStepIdx === 1 ? 'current' : ''}`}>
              <div className="stepper-circle">
                {currentStepIdx > 1 ? <Check size={18} /> : 2}
              </div>
              <div className="stepper-label">QUOTED</div>
              <div className="stepper-sub">Đã báo giá</div>
            </div>

            {/* Step 3: BOUND */}
            <div className={`stepper-item ${currentStepIdx > 2 ? 'completed' : currentStepIdx === 2 ? 'current' : ''}`}>
              <div className="stepper-circle">
                {currentStepIdx > 2 ? <Check size={18} /> : 3}
              </div>
              <div className="stepper-label">BOUND</div>
              <div className="stepper-sub">
                {policy.boundDate ? formatDate(policy.boundDate) : 'Ràng buộc'}
              </div>
            </div>

            {/* Step 4: ACTIVE */}
            <div className={`stepper-item ${currentStepIdx >= 3 ? 'completed' : ''}`}>
              <div className="stepper-circle">
                {currentStepIdx >= 3 ? <Check size={18} /> : 4}
              </div>
              <div className="stepper-label">ACTIVE</div>
              <div className="stepper-sub">
                {policy.effectiveDate ? formatDate(policy.effectiveDate) : 'Hiệu lực'}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* =========================================================
          KEY METRIC SUMMARY CARDS
          ========================================================= */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
          gap: '1rem',
          marginBottom: '1.75rem',
        }}
      >
        {/* Card 1: Total Premium */}
        <div className="glass-card" style={{ padding: '1.25rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontWeight: 600 }}>TỔNG PHÍ BẢO HIỂM</span>
            <DollarSign size={18} color="#10b981" />
          </div>
          <div style={{ fontSize: '1.45rem', fontWeight: 800, color: '#10b981', fontFamily: 'var(--font-mono)' }}>
            {formatVND(policy.totalPremium)}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', marginTop: '0.25rem' }}>
            Phiên bản hiện tại: v{policy.version || 1}
          </div>
        </div>

        {/* Card 2: Period & Dates */}
        <div className="glass-card" style={{ padding: '1.25rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontWeight: 600 }}>THỜI HẠN BẢO HIỂM</span>
            <Calendar size={18} color="var(--primary)" />
          </div>
          <div style={{ fontSize: '0.95rem', fontWeight: 700, color: 'var(--text-primary)' }}>
            {policy.effectiveDate ? formatDate(policy.effectiveDate) : 'Chưa kích hoạt'} →{' '}
            {policy.expirationDate ? formatDate(policy.expirationDate) : '—'}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
            {policy.paymentDueDate ? `Hạn nộp phí: ${formatDate(policy.paymentDueDate)}` : 'Chưa có hạn nộp phí'}
          </div>
        </div>

        {/* Card 3: Insured Party */}
        <div className="glass-card" style={{ padding: '1.25rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontWeight: 600 }}>BÊN MUA BẢO HIỂM</span>
            <User size={18} color="#06b6d4" />
          </div>
          <div style={{ fontSize: '1.05rem', fontWeight: 700, color: 'var(--text-primary)' }}>
            {policy.insured?.name || 'Chưa cập nhật'}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', marginTop: '0.25rem' }}>
            {policy.insured?.phone || policy.insured?.email || 'N/A'}
          </div>
        </div>

        {/* Card 4: Locations & Coverages Scope */}
        <div className="glass-card" style={{ padding: '1.25rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontWeight: 600 }}>QUY MÔ BẢO HIỂM</span>
            <Building size={18} color="#f59e0b" />
          </div>
          <div style={{ fontSize: '1.45rem', fontWeight: 800, color: 'var(--text-primary)', fontFamily: 'var(--font-mono)' }}>
            {policy.locations?.length || 0}{' '}
            <span style={{ fontSize: '0.9rem', fontWeight: 500, color: 'var(--text-secondary)' }}>địa điểm</span>
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
            Tổng cộng{' '}
            {policy.locations?.reduce((acc, loc) => acc + (loc.coverages?.length || 0), 0) || 0} gói bảo hiểm
          </div>
        </div>
      </div>

      {/* =========================================================
          TAB SELECTION BAR
          ========================================================= */}
      <div
        style={{
          display: 'flex',
          borderBottom: '1px solid var(--border-subtle)',
          marginBottom: '1.75rem',
          gap: '0.5rem',
        }}
      >
        <button
          onClick={() => setActiveTab('overview')}
          className="btn"
          style={{
            padding: '0.75rem 1.25rem',
            borderRadius: 'var(--radius-md) var(--radius-md) 0 0',
            background: activeTab === 'overview' ? 'var(--bg-card)' : 'transparent',
            borderBottom: activeTab === 'overview' ? '2px solid var(--primary)' : '2px solid transparent',
            color: activeTab === 'overview' ? '#ffffff' : 'var(--text-secondary)',
            fontWeight: 600,
            fontSize: '0.875rem',
          }}
        >
          <FileText size={16} />
          <span>Tổng quan & Bên mua</span>
        </button>

        <button
          onClick={() => setActiveTab('locations')}
          className="btn"
          style={{
            padding: '0.75rem 1.25rem',
            borderRadius: 'var(--radius-md) var(--radius-md) 0 0',
            background: activeTab === 'locations' ? 'var(--bg-card)' : 'transparent',
            borderBottom: activeTab === 'locations' ? '2px solid var(--primary)' : '2px solid transparent',
            color: activeTab === 'locations' ? '#ffffff' : 'var(--text-secondary)',
            fontWeight: 600,
            fontSize: '0.875rem',
          }}
        >
          <MapPin size={16} />
          <span>Địa điểm & Phạm vi ({policy.locations?.length || 0})</span>
        </button>

        <button
          onClick={() => setActiveTab('timeline')}
          className="btn"
          style={{
            padding: '0.75rem 1.25rem',
            borderRadius: 'var(--radius-md) var(--radius-md) 0 0',
            background: activeTab === 'timeline' ? 'var(--bg-card)' : 'transparent',
            borderBottom: activeTab === 'timeline' ? '2px solid var(--primary)' : '2px solid transparent',
            color: activeTab === 'timeline' ? '#ffffff' : 'var(--text-secondary)',
            fontWeight: 600,
            fontSize: '0.875rem',
          }}
        >
          <History size={16} />
          <span>Lịch sử Audit Log ({transactions.length})</span>
        </button>

        <button
          onClick={() => setActiveTab('versions')}
          className="btn"
          style={{
            padding: '0.75rem 1.25rem',
            borderRadius: 'var(--radius-md) var(--radius-md) 0 0',
            background: activeTab === 'versions' ? 'var(--bg-card)' : 'transparent',
            borderBottom: activeTab === 'versions' ? '2px solid var(--primary)' : '2px solid transparent',
            color: activeTab === 'versions' ? '#ffffff' : 'var(--text-secondary)',
            fontWeight: 600,
            fontSize: '0.875rem',
          }}
        >
          <GitBranch size={16} />
          <span>Phiên bản & So sánh Diff ({versions.length})</span>
        </button>
      </div>

      {/* =========================================================
          TAB 1: TỔNG QUAN & BÊN MUA BẢO HIỂM (OVERVIEW)
          ========================================================= */}
      {activeTab === 'overview' && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(350px, 1fr))', gap: '1.5rem' }}>
          {/* Insured Party Card */}
          <div className="glass-card" style={{ padding: '1.5rem' }}>
            <h3 style={{ fontSize: '1.1rem', fontWeight: 700, marginBottom: '1.25rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <User size={18} color="var(--primary)" />
              <span>Thông tin Bên mua bảo hiểm (Insured)</span>
            </h3>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', fontSize: '0.875rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', paddingBottom: '0.6rem', borderBottom: '1px solid var(--border-subtle)' }}>
                <span style={{ color: 'var(--text-muted)' }}>Tên cá nhân / Doanh nghiệp:</span>
                <span style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{policy.insured?.name || '—'}</span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', paddingBottom: '0.6rem', borderBottom: '1px solid var(--border-subtle)' }}>
                <span style={{ color: 'var(--text-muted)' }}>Loại hình đối tượng:</span>
                <span className="badge badge-primary">{policy.insured?.type || 'BUSINESS'}</span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', paddingBottom: '0.6rem', borderBottom: '1px solid var(--border-subtle)' }}>
                <span style={{ color: 'var(--text-muted)' }}>Mã định danh bên mua:</span>
                <span style={{ fontFamily: 'var(--font-mono)' }}>{policy.insured?.insuredId || '—'}</span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', paddingBottom: '0.6rem', borderBottom: '1px solid var(--border-subtle)' }}>
                <span style={{ color: 'var(--text-muted)' }}>Số điện thoại:</span>
                <span style={{ fontWeight: 500 }}>{policy.insured?.phone || '—'}</span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', paddingBottom: '0.6rem', borderBottom: '1px solid var(--border-subtle)' }}>
                <span style={{ color: 'var(--text-muted)' }}>Email liên hệ:</span>
                <span style={{ color: 'var(--primary)' }}>{policy.insured?.email || '—'}</span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--text-muted)', minWidth: '120px' }}>Địa chỉ trụ sở / cư trú:</span>
                <span style={{ textAlign: 'right', fontWeight: 500 }}>{policy.insured?.address || '—'}</span>
              </div>
            </div>
          </div>

          {/* Legal Dates & Terms Card */}
          <div className="glass-card" style={{ padding: '1.5rem' }}>
            <h3 style={{ fontSize: '1.1rem', fontWeight: 700, marginBottom: '1.25rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Calendar size={18} color="var(--primary)" />
              <span>Thời hạn Hợp đồng & Pháp lý</span>
            </h3>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', fontSize: '0.875rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', paddingBottom: '0.6rem', borderBottom: '1px solid var(--border-subtle)' }}>
                <span style={{ color: 'var(--text-muted)' }}>Mã số hợp đồng:</span>
                <span style={{ fontWeight: 700, fontFamily: 'var(--font-mono)', color: 'var(--primary)' }}>{policy.policyNumber}</span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', paddingBottom: '0.6rem', borderBottom: '1px solid var(--border-subtle)' }}>
                <span style={{ color: 'var(--text-muted)' }}>Trạng thái vòng đời:</span>
                <span>{getStatusBadge(policy.status)}</span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', paddingBottom: '0.6rem', borderBottom: '1px solid var(--border-subtle)' }}>
                <span style={{ color: 'var(--text-muted)' }}>Ngày tạo hợp đồng:</span>
                <span>{formatDateTime(policy.createdAt)}</span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', paddingBottom: '0.6rem', borderBottom: '1px solid var(--border-subtle)' }}>
                <span style={{ color: 'var(--text-muted)' }}>Ngày ràng buộc (Bound Date):</span>
                <span>{formatDateTime(policy.boundDate)}</span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', paddingBottom: '0.6rem', borderBottom: '1px solid var(--border-subtle)' }}>
                <span style={{ color: 'var(--text-muted)' }}>Hạn thanh toán phí (Due Date):</span>
                <span style={{ color: policy.paymentDueDate ? '#f59e0b' : 'inherit', fontWeight: 600 }}>
                  {formatDate(policy.paymentDueDate)}
                </span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', paddingBottom: '0.6rem', borderBottom: '1px solid var(--border-subtle)' }}>
                <span style={{ color: 'var(--text-muted)' }}>Ngày bắt đầu hiệu lực (Effective):</span>
                <span style={{ color: '#10b981', fontWeight: 600 }}>{formatDate(policy.effectiveDate)}</span>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--text-muted)' }}>Ngày kết thúc hiệu lực (Expiration):</span>
                <span style={{ fontWeight: 600 }}>{formatDate(policy.expirationDate)}</span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* =========================================================
          TAB 2: ĐỊA ĐIỂM & PHẠM VI BẢO HIỂM (LOCATIONS & COVERAGES)
          ========================================================= */}
      {activeTab === 'locations' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
              Danh sách chi tiết các địa điểm rủi ro và phạm vi bảo hiểm thành phần:
            </span>
            <Link to="/policies/locations" className="btn btn-secondary" style={{ fontSize: '0.8rem', padding: '0.4rem 0.8rem' }}>
              <ExternalLink size={14} />
              <span>Mở trang Quản lý Địa điểm P04</span>
            </Link>
          </div>

          {(!policy.locations || policy.locations.length === 0) ? (
            <div className="glass-card" style={{ textAlign: 'center', padding: '3rem 1.5rem' }}>
              <Building size={40} color="var(--text-muted)" style={{ margin: '0 auto 1rem' }} />
              <h4 style={{ fontSize: '1.1rem', marginBottom: '0.5rem' }}>Chưa có địa điểm bảo hiểm</h4>
              <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '1.25rem' }}>
                Hợp đồng cần ít nhất 1 địa điểm và mỗi địa điểm cần tối thiểu 1 gói bảo hiểm để có thể báo giá (QUOTED).
              </p>
              <Link to="/policies/locations" className="btn btn-primary" style={{ fontSize: '0.85rem' }}>
                Thêm địa điểm bảo hiểm
              </Link>
            </div>
          ) : (
            policy.locations.map((loc, index) => {
              const locationSubtotal =
                loc.coverages?.reduce((acc, cov) => acc + (cov.premium || 0), 0) || 0;

              return (
                <div
                  key={loc.locationId || index}
                  className="glass-card"
                  style={{
                    padding: '1.5rem',
                    border: '1px solid var(--border-subtle)',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '1.25rem',
                  }}
                >
                  {/* Location Header */}
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1rem', paddingBottom: '1rem', borderBottom: '1px solid var(--border-subtle)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                      <div
                        style={{
                          width: '36px',
                          height: '36px',
                          borderRadius: 'var(--radius-md)',
                          background: 'rgba(99, 102, 241, 0.15)',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          color: 'var(--primary)',
                          fontWeight: 700,
                        }}
                      >
                        #{index + 1}
                      </div>
                      <div>
                        <h4 style={{ fontSize: '1.05rem', margin: 0, fontWeight: 700, color: 'var(--text-primary)' }}>
                          {loc.address || `Địa điểm ${index + 1}`}
                        </h4>
                        <span style={{ fontSize: '0.775rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
                          ID: {loc.locationId || `LOC-${index + 1}`}
                        </span>
                      </div>
                    </div>

                    <div style={{ textAlign: 'right' }}>
                      <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', display: 'block' }}>PHÍ PHÂN BỔ ĐỊA ĐIỂM</span>
                      <span style={{ fontSize: '1.15rem', fontWeight: 800, color: '#10b981', fontFamily: 'var(--font-mono)' }}>
                        {formatVND(locationSubtotal)}
                      </span>
                    </div>
                  </div>

                  {/* Location Physical Specs */}
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
                      gap: '0.75rem',
                      background: 'rgba(15, 23, 42, 0.6)',
                      padding: '0.85rem 1rem',
                      borderRadius: 'var(--radius-md)',
                      fontSize: '0.8rem',
                    }}
                  >
                    <div>
                      <span style={{ color: 'var(--text-muted)', display: 'block' }}>Kết cấu xây dựng:</span>
                      <b>{loc.constructionType || '—'}</b>
                    </div>
                    <div>
                      <span style={{ color: 'var(--text-muted)', display: 'block' }}>Năm xây dựng:</span>
                      <b>{loc.yearBuilt || '—'}</b>
                    </div>
                    <div>
                      <span style={{ color: 'var(--text-muted)', display: 'block' }}>Số tầng:</span>
                      <b>{loc.numberOfFloors || '—'}</b>
                    </div>
                    <div>
                      <span style={{ color: 'var(--text-muted)', display: 'block' }}>Mục đích sử dụng:</span>
                      <b>{loc.occupancyType || '—'}</b>
                    </div>
                    <div>
                      <span style={{ color: 'var(--text-muted)', display: 'block' }}>Diện tích (sqft):</span>
                      <b>{loc.squareFootage ? loc.squareFootage.toLocaleString() : '—'}</b>
                    </div>
                    <div>
                      <span style={{ color: 'var(--text-muted)', display: 'block' }}>Chữa cháy tự động:</span>
                      <span className={`badge ${loc.sprinklered ? 'badge-success' : 'badge-secondary'}`}>
                        {loc.sprinklered ? 'Có trang bị' : 'Không có'}
                      </span>
                    </div>
                  </div>

                  {/* Coverages Table */}
                  <div>
                    <h5 style={{ fontSize: '0.875rem', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '0.75rem' }}>
                      Các gói phạm vi bảo hiểm ({loc.coverages?.length || 0}):
                    </h5>

                    {(!loc.coverages || loc.coverages.length === 0) ? (
                      <div style={{ textAlign: 'center', padding: '1.25rem', border: '1px dashed var(--border-subtle)', borderRadius: 'var(--radius-md)', color: 'var(--text-muted)', fontSize: '0.825rem' }}>
                        Chưa thiết lập phạm vi bảo hiểm cho địa điểm này.
                      </div>
                    ) : (
                      <div className="data-table-container">
                        <table className="data-table" style={{ fontSize: '0.825rem' }}>
                          <thead>
                            <tr>
                              <th>Mã gói</th>
                              <th>Tên phạm vi bảo hiểm</th>
                              <th style={{ textAlign: 'right' }}>Hạn mức bồi thường</th>
                              <th style={{ textAlign: 'right' }}>Mức khấu trừ</th>
                              <th style={{ textAlign: 'right' }}>Tỷ lệ phí (%)</th>
                              <th style={{ textAlign: 'right' }}>Phí bảo hiểm</th>
                            </tr>
                          </thead>
                          <tbody>
                            {loc.coverages.map((cov, cIdx) => (
                              <tr key={cov.coverageCode || cIdx}>
                                <td>
                                  <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, color: 'var(--primary)' }}>
                                    {cov.coverageCode}
                                  </span>
                                </td>
                                <td>{cov.coverageName || '—'}</td>
                                <td style={{ textAlign: 'right', fontWeight: 600 }}>{formatVND(cov.limitAmount)}</td>
                                <td style={{ textAlign: 'right', color: 'var(--text-secondary)' }}>{formatVND(cov.deductibleAmount)}</td>
                                <td style={{ textAlign: 'right', color: 'var(--text-muted)' }}>{cov.rate ? `${cov.rate}%` : '—'}</td>
                                <td style={{ textAlign: 'right', fontWeight: 700, color: '#10b981', fontFamily: 'var(--font-mono)' }}>
                                  {formatVND(cov.premium)}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    )}
                  </div>
                </div>
              );
            })
          )}
        </div>
      )}

      {/* =========================================================
          TAB 3: LỊCH SỬ SỔ CÁI GIAO DỊCH & AUDIT LOG (P09 TIMELINE)
          ========================================================= */}
      {activeTab === 'timeline' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
              Nhật ký sổ cái giao dịch bất biến (Audit Trail Ledger P09) ghi nhận toàn bộ vòng đời hợp đồng:
            </span>
            <button
              onClick={fetchHistory}
              className="btn btn-secondary"
              style={{ padding: '0.4rem 0.8rem', fontSize: '0.8rem' }}
            >
              <RefreshCw size={14} className={loadingHistory ? 'spinner' : ''} />
              <span>Làm mới nhật ký</span>
            </button>
          </div>

          {loadingHistory ? (
            <div style={{ textAlign: 'center', padding: '3rem 1rem' }}>
              <div className="spinner" style={{ margin: '0 auto 1rem', width: '28px', height: '28px' }} />
              <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>Đang tải sổ cái giao dịch...</p>
            </div>
          ) : transactions.length === 0 ? (
            <div className="glass-card" style={{ textAlign: 'center', padding: '3rem 1rem' }}>
              <History size={40} color="var(--text-muted)" style={{ margin: '0 auto 1rem' }} />
              <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>Chưa có bản ghi giao dịch nào cho hợp đồng này.</p>
            </div>
          ) : (
            <div className="timeline-track">
              {transactions.map((txn, idx) => (
                <div key={txn.id || idx} className="timeline-node">
                  <div className="timeline-bullet">
                    {getTransactionIcon(txn.transactionType)}
                  </div>

                  <div className="timeline-card">
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem', marginBottom: '0.5rem' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                        <span className="badge badge-primary" style={{ fontSize: '0.75rem' }}>
                          {txn.transactionType}
                        </span>
                        {txn.version && (
                          <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', fontWeight: 600 }}>
                            Phiên bản v{txn.version}
                          </span>
                        )}
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                        <Clock size={13} />
                        <span>{formatDateTime(txn.timestamp)}</span>
                      </div>
                    </div>

                    <div style={{ fontSize: '0.9rem', color: 'var(--text-primary)', marginBottom: '0.5rem', fontWeight: 500 }}>
                      {txn.description || 'Thao tác nghiệp vụ hệ thống'}
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                      <span>Thực hiện bởi: <b style={{ color: 'var(--text-secondary)' }}>{txn.performedBy || txn.actor || 'System'}</b></span>
                    </div>

                    {txn.changes && Object.keys(txn.changes).length > 0 && (
                      <div
                        style={{
                          marginTop: '0.75rem',
                          padding: '0.6rem 0.85rem',
                          background: 'rgba(0, 0, 0, 0.35)',
                          borderRadius: 'var(--radius-sm)',
                          fontSize: '0.75rem',
                          fontFamily: 'var(--font-mono)',
                          color: '#94a3b8',
                          overflowX: 'auto',
                        }}
                      >
                        {JSON.stringify(txn.changes, null, 2)}
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* =========================================================
          TAB 4: PHIÊN BẢN & SO SÁNH DIFF (P08 VERSIONS & DIFF)
          ========================================================= */}
      {activeTab === 'versions' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.75rem' }}>
          {/* Version Diff Tool Bar */}
          <div
            className="glass-card"
            style={{
              padding: '1.5rem',
              border: '1px solid rgba(99, 102, 241, 0.3)',
              background: 'linear-gradient(180deg, rgba(17, 24, 39, 0.75) 0%, rgba(15, 23, 42, 0.6) 100%)',
            }}
          >
            <h3 style={{ fontSize: '1.05rem', fontWeight: 700, marginBottom: '0.5rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <ArrowRightLeft size={18} color="var(--primary)" />
              <span>Công cụ So sánh Thay đổi giữa 2 Phiên bản (Version Diff)</span>
            </h3>
            <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '1.25rem' }}>
              Truy vết các trường dữ liệu và mức chênh lệch phí bảo hiểm (Δ) giữa các phiên bản phụ lục (Endorsements).
            </p>

            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', flexWrap: 'wrap' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <span style={{ fontSize: '0.825rem', color: 'var(--text-secondary)' }}>Phiên bản cũ (v1):</span>
                <select
                  value={diffV1}
                  onChange={(e) => setDiffV1(Number(e.target.value))}
                  className="form-control"
                  style={{ width: '130px', padding: '0.4rem 0.6rem', fontSize: '0.85rem' }}
                >
                  {versions.map((v) => (
                    <option key={v.version} value={v.version}>
                      v{v.version} ({v.endorsementType || 'Snapshot'})
                    </option>
                  ))}
                </select>
              </div>

              <ArrowRight size={18} color="var(--text-muted)" />

              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <span style={{ fontSize: '0.825rem', color: 'var(--text-secondary)' }}>Phiên bản mới (v2):</span>
                <select
                  value={diffV2}
                  onChange={(e) => setDiffV2(Number(e.target.value))}
                  className="form-control"
                  style={{ width: '130px', padding: '0.4rem 0.6rem', fontSize: '0.85rem' }}
                >
                  {versions.map((v) => (
                    <option key={v.version} value={v.version}>
                      v{v.version} ({v.endorsementType || 'Snapshot'})
                    </option>
                  ))}
                </select>
              </div>

              <button
                onClick={handleCompareVersions}
                disabled={comparing}
                className="btn btn-primary"
                style={{ fontSize: '0.85rem', padding: '0.45rem 1rem' }}
              >
                {comparing ? <RefreshCw className="animate-spin" size={15} /> : <ArrowRightLeft size={15} />}
                <span>So sánh Diff</span>
              </button>
            </div>

            {/* Diff Comparison Output Panel */}
            {diffResult && (
              <div style={{ marginTop: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                {/* Premium Delta Summary */}
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
                    gap: '1rem',
                    background: 'rgba(15, 23, 42, 0.75)',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: 'var(--radius-md)',
                    padding: '1rem',
                    textAlign: 'center',
                  }}
                >
                  <div>
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Phí phiên bản v{diffResult.fromVersion || diffV1}</div>
                    <div style={{ fontSize: '1.1rem', fontWeight: 700, color: 'var(--text-primary)', fontFamily: 'var(--font-mono)' }}>
                      {formatVND(diffResult.premiumBefore || diffResult.v1Premium)}
                    </div>
                  </div>

                  <div>
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Phí phiên bản v{diffResult.toVersion || diffV2}</div>
                    <div style={{ fontSize: '1.1rem', fontWeight: 700, color: 'var(--text-primary)', fontFamily: 'var(--font-mono)' }}>
                      {formatVND(diffResult.premiumAfter || diffResult.v2Premium)}
                    </div>
                  </div>

                  <div>
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Chênh lệch phí (Δ)</div>
                    <div
                      style={{
                        fontSize: '1.1rem',
                        fontWeight: 700,
                        color: (diffResult.premiumDifference || 0) >= 0 ? '#10b981' : '#ef4444',
                        fontFamily: 'var(--font-mono)',
                      }}
                    >
                      {(diffResult.premiumDifference || 0) >= 0 ? '+' : ''}
                      {formatVND(diffResult.premiumDifference || 0)}
                    </div>
                  </div>
                </div>

                {/* Diff Field Changes List */}
                <h4 style={{ fontSize: '0.9rem', fontWeight: 600, color: 'var(--text-primary)' }}>
                  Chi tiết các trường thay đổi ({diffResult.changes?.length || Object.keys(diffResult.differences || {}).length}):
                </h4>

                {diffResult.changes && diffResult.changes.length > 0 ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
                    {diffResult.changes.map((ch, idx) => (
                      <div key={idx} className="diff-item">
                        <div style={{ fontFamily: 'var(--font-mono)', fontWeight: 600, color: 'var(--primary)' }}>
                          {ch.field}
                        </div>
                        <div className="diff-old">
                          <span style={{ fontSize: '0.7rem', display: 'block', opacity: 0.8 }}>v{diffV1}:</span>
                          {String(ch.oldValue ?? '—')}
                        </div>
                        <div className="diff-new">
                          <span style={{ fontSize: '0.7rem', display: 'block', opacity: 0.8 }}>v{diffV2}:</span>
                          {String(ch.newValue ?? '—')}
                        </div>
                      </div>
                    ))}
                  </div>
                ) : diffResult.differences && Object.keys(diffResult.differences).length > 0 ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
                    {Object.entries(diffResult.differences).map(([k, diff], idx) => (
                      <div key={idx} className="diff-item">
                        <div style={{ fontFamily: 'var(--font-mono)', fontWeight: 600, color: 'var(--primary)' }}>
                          {k}
                        </div>
                        <div className="diff-old">
                          <span style={{ fontSize: '0.7rem', display: 'block', opacity: 0.8 }}>v{diffV1}:</span>
                          {JSON.stringify(diff.from ?? diff.v1)}
                        </div>
                        <div className="diff-new">
                          <span style={{ fontSize: '0.7rem', display: 'block', opacity: 0.8 }}>v{diffV2}:</span>
                          {JSON.stringify(diff.to ?? diff.v2)}
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div style={{ padding: '1rem', background: 'rgba(16, 185, 129, 0.1)', border: '1px solid rgba(16, 185, 129, 0.25)', borderRadius: 'var(--radius-md)', color: '#34d399', fontSize: '0.825rem' }}>
                    Không có sự khác biệt dữ liệu nào giữa phiên bản v{diffV1} và v{diffV2}.
                  </div>
                )}
              </div>
            )}
          </div>

          {/* List of Version Snapshots */}
          <div>
            <h3 style={{ fontSize: '1.05rem', fontWeight: 700, marginBottom: '1rem' }}>
              Danh sách Snapshot Phiên bản đã lưu ({versions.length} bản ghi)
            </h3>

            {loadingVersions ? (
              <div style={{ textAlign: 'center', padding: '2rem' }}>
                <div className="spinner" style={{ margin: '0 auto 0.5rem', width: '24px', height: '24px' }} />
                <span style={{ fontSize: '0.825rem', color: 'var(--text-muted)' }}>Đang tải snapshot phiên bản...</span>
              </div>
            ) : versions.length === 0 ? (
              <div className="glass-card" style={{ textAlign: 'center', padding: '2.5rem 1rem' }}>
                <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', margin: 0 }}>
                  Hợp đồng đang ở phiên bản ban đầu (v1). Chưa có bản chụp snapshot sửa đổi bổ sung (Endorsement).
                </p>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem' }}>
                {versions.map((ver) => {
                  const isCurrent = ver.version === policy.version;
                  return (
                    <div
                      key={ver.id || ver.version}
                      className="glass-card"
                      style={{
                        padding: '1.25rem',
                        border: isCurrent ? '1px solid var(--primary)' : '1px solid var(--border-subtle)',
                        background: isCurrent ? 'rgba(99, 102, 241, 0.08)' : 'rgba(17, 24, 39, 0.7)',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        flexWrap: 'wrap',
                        gap: '1rem',
                      }}
                    >
                      <div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '0.35rem' }}>
                          <span className="badge badge-primary" style={{ fontSize: '0.8rem' }}>
                            Phiên bản v{ver.version}
                          </span>
                          {isCurrent && (
                            <span style={{ fontSize: '0.75rem', color: '#10b981', fontWeight: 700 }}>
                              ● Đang hiệu lực
                            </span>
                          )}
                          <span style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-primary)' }}>
                            {ver.endorsementReason || 'Snapshot lưu trữ tự động'}
                          </span>
                        </div>

                        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                          Lưu bởi: <b>{ver.createdBy || ver.endorsedBy || 'System'}</b> •{' '}
                          {formatDateTime(ver.createdAt || ver.timestamp)}
                        </div>
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', gap: '1.25rem' }}>
                        <div style={{ textAlign: 'right' }}>
                          <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', display: 'block' }}>PHÍ PHIÊN BẢN</span>
                          <span style={{ fontSize: '1rem', fontWeight: 700, color: '#10b981', fontFamily: 'var(--font-mono)' }}>
                            {formatVND(ver.totalPremium || ver.policySnapshot?.totalPremium)}
                          </span>
                        </div>

                        <div style={{ display: 'flex', gap: '0.4rem' }}>
                          <button
                            onClick={() => handleDownloadCOI(ver.version)}
                            className="btn btn-secondary"
                            style={{ padding: '0.35rem 0.65rem', fontSize: '0.75rem' }}
                            title="Tải COI PDF phiên bản này"
                          >
                            <Download size={13} color="var(--primary)" />
                            <span>COI PDF</span>
                          </button>

                          <button
                            onClick={() => handleDownloadSummary(ver.version)}
                            className="btn btn-secondary"
                            style={{ padding: '0.35rem 0.65rem', fontSize: '0.75rem' }}
                            title="Tải Summary PDF phiên bản này"
                          >
                            <FileText size={13} color="#10b981" />
                            <span>Summary PDF</span>
                          </button>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      )}

      {/* =========================================================
          MODALS
          ========================================================= */}
      {/* 1. Status Transition Modal */}
      <StatusTransitionModal
        policy={policy}
        targetStatus={transitionTarget}
        isOpen={showTransitionModal}
        onClose={() => setShowTransitionModal(false)}
        onSuccess={handlePolicyUpdated}
      />

      {/* 2. Endorsement Modal */}
      <EndorsementModal
        policy={policy}
        isOpen={showEndorsementModal}
        onClose={() => setShowEndorsementModal(false)}
        onSuccess={handlePolicyUpdated}
      />
    </div>
  );
};
