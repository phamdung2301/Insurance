import React, { useState, useEffect } from 'react';
import { useToast } from '../context/ToastContext';
import { policyService } from '../services/api';
import {
  X,
  History,
  GitBranch,
  FileEdit,
  ArrowRight,
  Shield,
  Clock,
  User,
  CheckCircle2,
  AlertTriangle,
  RefreshCw,
  GitCompare,
  Plus,
} from 'lucide-react';

export const PolicyHistoryAndEndorsementModal = ({ policy, isOpen, onClose, onPolicyUpdated }) => {
  const { showToast } = useToast();
  const [activeTab, setActiveTab] = useState('history'); // 'history' | 'versions' | 'endorse'

  // History State (P09 Transaction Ledger)
  const [history, setHistory] = useState([]);
  const [loadingHistory, setLoadingHistory] = useState(false);

  // Versions State (P08 Endorsement Snapshots)
  const [versions, setVersions] = useState([]);
  const [loadingVersions, setLoadingVersions] = useState(false);
  const [compareV1, setCompareV1] = useState(1);
  const [compareV2, setCompareV2] = useState(1);
  const [comparisonResult, setComparisonResult] = useState(null);
  const [comparing, setComparing] = useState(false);

  // Endorsement State (P08)
  const [endorsementForm, setEndorsementForm] = useState({
    reason: 'Sửa đổi điều khoản hạn mức bảo hiểm theo thỏa thuận phụ lục',
    effectiveDate: new Date().toISOString().split('T')[0],
    premiumAdjustment: '1000000',
    endorsementNotes: 'Cập nhật điều chỉnh theo phụ lục sửa đổi mới nhất.',
  });
  const [endorsing, setEndorsing] = useState(false);

  // Status transition state (quick advance to ACTIVE if needed)
  const [transitioning, setTransitioning] = useState(false);

  useEffect(() => {
    if (isOpen && policy?.policyNumber) {
      fetchHistory();
      fetchVersions();
    }
  }, [isOpen, policy?.policyNumber]);

  const fetchHistory = async () => {
    try {
      setLoadingHistory(true);
      const res = await policyService.getHistory(policy.policyNumber);
      if (res && res.data) {
        setHistory(res.data);
      }
    } catch (err) {
      showToast(err.message || 'Không thể tải lịch sử giao dịch', 'error');
    } finally {
      setLoadingHistory(false);
    }
  };

  const fetchVersions = async () => {
    try {
      setLoadingVersions(true);
      const res = await policyService.getVersions(policy.policyNumber);
      if (res && res.data) {
        const vList = res.data;
        setVersions(vList);
        if (vList.length >= 2) {
          setCompareV1(vList[vList.length - 1].version);
          setCompareV2(vList[0].version);
        } else if (vList.length === 1) {
          setCompareV1(vList[0].version);
          setCompareV2(vList[0].version);
        }
      }
    } catch (err) {
      showToast(err.message || 'Không thể tải danh sách phiên bản', 'error');
    } finally {
      setLoadingVersions(false);
    }
  };

  const handleCompare = async () => {
    try {
      setComparing(true);
      const res = await policyService.compareVersions(policy.policyNumber, compareV1, compareV2);
      if (res && res.data) {
        setComparisonResult(res.data);
      }
    } catch (err) {
      showToast(err.message || 'Lỗi khi so sánh phiên bản', 'error');
    } finally {
      setComparing(false);
    }
  };

  const handleApplyEndorsement = async (e) => {
    e.preventDefault();
    if (!endorsementForm.reason.trim()) {
      showToast('Vui lòng nhập lý do sửa đổi hợp đồng (Endorsement reason).', 'warning');
      return;
    }

    try {
      setEndorsing(true);
      const changesPayload = {
        reason: endorsementForm.reason,
        notes: endorsementForm.endorsementNotes,
        premiumAdjustment: Number(endorsementForm.premiumAdjustment || 0),
        modifiedAt: new Date().toISOString(),
      };

      const res = await policyService.applyEndorsement(policy.policyNumber, {
        reason: endorsementForm.reason,
        effectiveDate: `${endorsementForm.effectiveDate}T00:00:00`,
        changes: changesPayload,
      });

      showToast(`Tạo phụ lục sửa đổi (Endorsement) thành công! Phiên bản mới đã được lưu.`, 'success');
      fetchVersions();
      fetchHistory();
      if (onPolicyUpdated) onPolicyUpdated(res?.data);
      setActiveTab('versions');
    } catch (err) {
      showToast(err.message || 'Không thể thực hiện Endorsement. Hợp đồng phải ở trạng thái ACTIVE.', 'error');
    } finally {
      setEndorsing(false);
    }
  };

  // Helper to quickly transition status (e.g. to ACTIVE so endorsement can be applied)
  const handleQuickTransition = async (targetStatus) => {
    try {
      setTransitioning(true);
      await policyService.transitionStatus(policy.policyNumber, {
        toStatus: targetStatus,
        reason: `Chuyển trạng thái sang ${targetStatus} để kiểm thử luồng nghiệp vụ`,
      });
      showToast(`Chuyển trạng thái hợp đồng thành ${targetStatus} thành công!`, 'success');
      fetchHistory();
      if (onPolicyUpdated) onPolicyUpdated({ ...policy, status: targetStatus });
    } catch (err) {
      showToast(err.message || `Không thể chuyển sang trạng thái ${targetStatus}`, 'error');
    } finally {
      setTransitioning(false);
    }
  };

  if (!isOpen || !policy) return null;

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0, 0, 0, 0.75)',
        backdropFilter: 'blur(6px)',
        zIndex: 1000,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '1.5rem',
      }}
    >
      <div
        className="glass-card"
        style={{
          width: '100%',
          maxWidth: '900px',
          maxHeight: '90vh',
          display: 'flex',
          flexDirection: 'column',
          padding: 0,
          overflow: 'hidden',
          borderColor: 'rgba(99, 102, 241, 0.3)',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.7)',
        }}
      >
        {/* Modal Header */}
        <div
          style={{
            padding: '1.25rem 1.75rem',
            borderBottom: '1px solid var(--border-subtle)',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            background: 'rgba(15, 23, 42, 0.65)',
          }}
        >
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
              <h2 style={{ fontSize: '1.25rem', margin: 0, fontWeight: 700 }}>
                Hợp đồng: {policy.policyNumber}
              </h2>
              <span className="badge badge-info">{policy.status}</span>
              <span className="badge badge-primary">Version {policy.version || 1}</span>
            </div>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
              Chủ hợp đồng: {policy.insured?.insuredName || 'N/A'} • Tổng phí:{' '}
              {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(policy.totalPremium || 0)}
            </span>
          </div>

          <button
            onClick={onClose}
            style={{
              background: 'none',
              border: 'none',
              color: 'var(--text-muted)',
              cursor: 'pointer',
              padding: '0.5rem',
              borderRadius: 'var(--radius-sm)',
            }}
          >
            <X size={20} />
          </button>
        </div>

        {/* Tab Selector */}
        <div
          style={{
            display: 'flex',
            borderBottom: '1px solid var(--border-subtle)',
            background: 'rgba(11, 15, 25, 0.5)',
            padding: '0 1.5rem',
          }}
        >
          <button
            onClick={() => setActiveTab('history')}
            style={{
              padding: '0.85rem 1.25rem',
              background: 'none',
              border: 'none',
              borderBottom: activeTab === 'history' ? '2px solid #6366f1' : '2px solid transparent',
              color: activeTab === 'history' ? '#ffffff' : 'var(--text-secondary)',
              fontWeight: 600,
              fontSize: '0.875rem',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
            }}
          >
            <History size={16} />
            <span>Sổ cái Giao dịch (Ledger P09)</span>
          </button>

          <button
            onClick={() => setActiveTab('versions')}
            style={{
              padding: '0.85rem 1.25rem',
              background: 'none',
              border: 'none',
              borderBottom: activeTab === 'versions' ? '2px solid #6366f1' : '2px solid transparent',
              color: activeTab === 'versions' ? '#ffffff' : 'var(--text-secondary)',
              fontWeight: 600,
              fontSize: '0.875rem',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
            }}
          >
            <GitBranch size={16} />
            <span>Phiên bản & So sánh (P08)</span>
          </button>

          <button
            onClick={() => setActiveTab('endorse')}
            style={{
              padding: '0.85rem 1.25rem',
              background: 'none',
              border: 'none',
              borderBottom: activeTab === 'endorse' ? '2px solid #6366f1' : '2px solid transparent',
              color: activeTab === 'endorse' ? '#ffffff' : 'var(--text-secondary)',
              fontWeight: 600,
              fontSize: '0.875rem',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
            }}
          >
            <FileEdit size={16} />
            <span>Tạo Endorsement Phụ lục (P08)</span>
          </button>
        </div>

        {/* Tab Body */}
        <div style={{ padding: '1.5rem', overflowY: 'auto', flex: 1 }}>
          {/* ================= TAB 1: LEDGER HISTORY (P09) ================= */}
          {activeTab === 'history' && (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
                  Ghi nhận đầy đủ audit trail các thao tác (Create, Update, Status Change, Endorsement, Nested Location/Coverage):
                </span>
                <button onClick={fetchHistory} className="btn btn-secondary" style={{ padding: '0.3rem 0.6rem', fontSize: '0.75rem' }}>
                  <RefreshCw size={13} className={loadingHistory ? 'spinner' : ''} />
                  <span>Làm mới</span>
                </button>
              </div>

              {loadingHistory ? (
                <div style={{ textAlign: 'center', padding: '2rem' }}>
                  <div className="spinner" style={{ margin: '0 auto 0.5rem', width: '24px', height: '24px' }}></div>
                  <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Đang tải sổ cái giao dịch...</span>
                </div>
              ) : history.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '2rem', border: '1px dashed var(--border-subtle)', borderRadius: 'var(--radius-md)' }}>
                  <History size={32} color="var(--text-muted)" style={{ margin: '0 auto 0.5rem' }} />
                  <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>Chưa có bản ghi giao dịch nào cho hợp đồng này.</p>
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                  {history.map((item, idx) => (
                    <div
                      key={item.id || idx}
                      style={{
                        padding: '1rem',
                        borderRadius: 'var(--radius-md)',
                        background: 'rgba(255, 255, 255, 0.02)',
                        border: '1px solid var(--border-subtle)',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '0.4rem',
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                          <span className="badge badge-primary" style={{ fontSize: '0.725rem' }}>
                            {item.transactionType}
                          </span>
                          <span style={{ fontSize: '0.8rem', fontWeight: 600 }}>
                            Version: {item.version || 1}
                          </span>
                        </div>
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                          {item.timestamp ? new Date(item.timestamp).toLocaleString('vi-VN') : 'Vừa xong'}
                        </span>
                      </div>

                      <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
                        <span style={{ color: 'var(--text-muted)' }}>Người thực hiện:</span> <b>{item.performedBy || 'System'}</b>
                      </div>

                      {item.changes && Object.keys(item.changes).length > 0 && (
                        <div
                          style={{
                            fontSize: '0.75rem',
                            background: 'rgba(0, 0, 0, 0.25)',
                            padding: '0.5rem',
                            borderRadius: '4px',
                            fontFamily: 'monospace',
                            color: '#94a3b8',
                            overflowX: 'auto',
                          }}
                        >
                          {JSON.stringify(item.changes)}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* ================= TAB 2: VERSIONS & COMPARISON (P08) ================= */}
          {activeTab === 'versions' && (
            <div>
              <div style={{ marginBottom: '1.5rem' }}>
                <h3 style={{ fontSize: '0.95rem', fontWeight: 600, marginBottom: '0.5rem' }}>
                  So sánh khác biệt giữa 2 phiên bản (Version Comparison)
                </h3>
                <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', flexWrap: 'wrap' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Từ Phiên bản:</span>
                    <input
                      type="number"
                      min="1"
                      className="form-control"
                      value={compareV1}
                      onChange={(e) => setCompareV1(Number(e.target.value))}
                      style={{ width: '70px', padding: '0.35rem 0.5rem', fontSize: '0.8rem' }}
                    />
                  </div>

                  <ArrowRight size={16} color="var(--text-muted)" />

                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Đến Phiên bản:</span>
                    <input
                      type="number"
                      min="1"
                      className="form-control"
                      value={compareV2}
                      onChange={(e) => setCompareV2(Number(e.target.value))}
                      style={{ width: '70px', padding: '0.35rem 0.5rem', fontSize: '0.8rem' }}
                    />
                  </div>

                  <button
                    onClick={handleCompare}
                    disabled={comparing}
                    className="btn btn-primary"
                    style={{ padding: '0.35rem 0.75rem', fontSize: '0.8rem' }}
                  >
                    <GitCompare size={14} />
                    <span>{comparing ? 'Đang so sánh...' : 'So sánh Diff'}</span>
                  </button>
                </div>

                {/* Diff Output */}
                {comparisonResult && (
                  <div
                    style={{
                      marginTop: '1rem',
                      padding: '1rem',
                      borderRadius: 'var(--radius-md)',
                      background: 'rgba(15, 23, 42, 0.8)',
                      border: '1px solid rgba(99, 102, 241, 0.25)',
                    }}
                  >
                    <div style={{ fontWeight: 600, fontSize: '0.85rem', marginBottom: '0.5rem', color: '#818cf8' }}>
                      Kết quả so sánh giữa v{comparisonResult.version1} và v{comparisonResult.version2}:
                    </div>
                    {Object.keys(comparisonResult.differences || {}).length === 0 ? (
                      <span style={{ fontSize: '0.8rem', color: '#10b981' }}>
                        Không có sự thay đổi nội dung nào giữa hai phiên bản.
                      </span>
                    ) : (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem' }}>
                        {Object.entries(comparisonResult.differences).map(([k, diff]) => (
                          <div key={k} style={{ fontSize: '0.8rem', display: 'flex', gap: '0.5rem' }}>
                            <span style={{ color: 'var(--text-muted)', minWidth: '120px' }}>{k}:</span>
                            <span style={{ color: '#f87171' }}>v{comparisonResult.version1}: {JSON.stringify(diff.from)}</span>
                            <ArrowRight size={14} color="var(--text-muted)" style={{ margin: '0 0.25rem' }} />
                            <span style={{ color: '#10b981' }}>v{comparisonResult.version2}: {JSON.stringify(diff.to)}</span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>

              {/* Version Snapshots List */}
              <h3 style={{ fontSize: '0.95rem', fontWeight: 600, marginBottom: '0.5rem' }}>
                Danh sách Snapshot Phiên bản đã lưu ({versions.length} bản ghi)
              </h3>

              {loadingVersions ? (
                <div style={{ textAlign: 'center', padding: '1.5rem' }}>
                  <div className="spinner" style={{ margin: '0 auto 0.5rem', width: '20px', height: '20px' }}></div>
                  <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Đang tải snapshot...</span>
                </div>
              ) : versions.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '1.5rem', border: '1px dashed var(--border-subtle)', borderRadius: 'var(--radius-md)' }}>
                  <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', margin: 0 }}>
                    Hợp đồng đang ở phiên bản đầu tiên (v1), chưa có snapshot lịch sử sửa đổi (endorsement).
                  </p>
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                  {versions.map((ver, idx) => (
                    <div
                      key={ver.id || idx}
                      style={{
                        padding: '1rem',
                        borderRadius: 'var(--radius-md)',
                        background: 'rgba(255, 255, 255, 0.02)',
                        border: '1px solid var(--border-subtle)',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                      }}
                    >
                      <div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.25rem' }}>
                          <span className="badge badge-primary">Version {ver.version}</span>
                          <span style={{ fontSize: '0.85rem', fontWeight: 600 }}>{ver.endorsementReason || 'Snapshot lưu trữ'}</span>
                        </div>
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                          Lưu bởi: {ver.endorsedBy || 'System'} • {ver.timestamp ? new Date(ver.timestamp).toLocaleString('vi-VN') : ''}
                        </span>
                      </div>

                      <div style={{ textAlign: 'right' }}>
                        <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', display: 'block' }}>PHÍ PHIÊN BẢN NÀY</span>
                        <span style={{ fontSize: '0.95rem', fontWeight: 700, color: '#10b981' }}>
                          {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(ver.policySnapshot?.totalPremium || 0)}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* ================= TAB 3: APPLY ENDORSEMENT (P08) ================= */}
          {activeTab === 'endorse' && (
            <div>
              {/* Quick status transition bar if policy is NOT ACTIVE */}
              {policy.status !== 'ACTIVE' && (
                <div
                  className="glass-card"
                  style={{
                    padding: '1rem 1.25rem',
                    marginBottom: '1.5rem',
                    background: 'rgba(245, 158, 11, 0.08)',
                    borderColor: 'rgba(245, 158, 11, 0.3)',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    flexWrap: 'wrap',
                    gap: '1rem',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                    <AlertTriangle size={20} color="#f59e0b" />
                    <div>
                      <div style={{ fontSize: '0.85rem', fontWeight: 600, color: '#f59e0b' }}>
                        Hợp đồng hiện tại đang ở trạng thái: {policy.status}
                      </div>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                        Theo quy tắc nghiệp vụ (FR-END-01), chỉ các hợp đồng đang có hiệu lực (ACTIVE) mới được áp dụng Endorsement.
                      </div>
                    </div>
                  </div>

                  <div style={{ display: 'flex', gap: '0.5rem' }}>
                    {policy.status === 'DRAFT' && (
                      <button
                        onClick={() => handleQuickTransition('QUOTED')}
                        disabled={transitioning}
                        className="btn btn-secondary"
                        style={{ fontSize: '0.75rem', padding: '0.35rem 0.7rem' }}
                      >
                        Chuyển QUOTED
                      </button>
                    )}
                    {policy.status === 'QUOTED' && (
                      <button
                        onClick={() => handleQuickTransition('BOUND')}
                        disabled={transitioning}
                        className="btn btn-secondary"
                        style={{ fontSize: '0.75rem', padding: '0.35rem 0.7rem' }}
                      >
                        Chuyển BOUND
                      </button>
                    )}
                    {(policy.status === 'BOUND' || policy.status === 'DRAFT' || policy.status === 'QUOTED') && (
                      <button
                        onClick={async () => {
                          // Stepwise transition if DRAFT
                          if (policy.status === 'DRAFT') {
                            await handleQuickTransition('QUOTED');
                            await handleQuickTransition('BOUND');
                          } else if (policy.status === 'QUOTED') {
                            await handleQuickTransition('BOUND');
                          }
                          await handleQuickTransition('ACTIVE');
                        }}
                        disabled={transitioning}
                        className="btn btn-primary"
                        style={{ fontSize: '0.75rem', padding: '0.35rem 0.7rem' }}
                      >
                        Kích hoạt ngay sang ACTIVE
                      </button>
                    )}
                  </div>
                </div>
              )}

              <form onSubmit={handleApplyEndorsement} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                <div>
                  <label className="form-label">Lý do Sửa đổi Hợp đồng (Endorsement Reason) *</label>
                  <input
                    type="text"
                    className="form-control"
                    value={endorsementForm.reason}
                    onChange={(e) => setEndorsementForm({ ...endorsementForm, reason: e.target.value })}
                    placeholder="VD: Điều chỉnh tăng hạn mức bảo hiểm cơ sở số 1"
                    required
                  />
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                  <div>
                    <label className="form-label">Ngày hiệu lực phụ lục (Effective Date)</label>
                    <input
                      type="date"
                      className="form-control"
                      value={endorsementForm.effectiveDate}
                      onChange={(e) => setEndorsementForm({ ...endorsementForm, effectiveDate: e.target.value })}
                    />
                  </div>

                  <div>
                    <label className="form-label">Điều chỉnh Phí phụ lục (VND)</label>
                    <input
                      type="number"
                      step="100000"
                      className="form-control"
                      value={endorsementForm.premiumAdjustment}
                      onChange={(e) => setEndorsementForm({ ...endorsementForm, premiumAdjustment: e.target.value })}
                      placeholder="VD: 1000000"
                    />
                  </div>
                </div>

                <div>
                  <label className="form-label">Ghi chú & Chi tiết thay đổi phụ lục</label>
                  <textarea
                    rows={3}
                    className="form-control"
                    value={endorsementForm.endorsementNotes}
                    onChange={(e) => setEndorsementForm({ ...endorsementForm, endorsementNotes: e.target.value })}
                    placeholder="Mô tả chi tiết những nội dung đã sửa đổi..."
                  />
                </div>

                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem', marginTop: '1rem' }}>
                  <button type="button" onClick={onClose} className="btn btn-secondary">
                    Đóng
                  </button>
                  <button
                    type="submit"
                    disabled={endorsing || policy.status !== 'ACTIVE'}
                    className="btn btn-primary"
                    style={{ padding: '0.5rem 1.25rem' }}
                  >
                    <FileEdit size={16} />
                    <span>{endorsing ? 'Đang thực hiện...' : 'Lưu Endorsement & Tăng Version'}</span>
                  </button>
                </div>
              </form>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
