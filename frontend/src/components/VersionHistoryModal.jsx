import React, { useState, useEffect } from 'react';
import {
  X,
  History,
  GitCommit,
  ArrowRightLeft,
  Calendar,
  User,
  Layers,
  ArrowUpRight,
  ArrowDownRight,
  Clock,
  ShieldCheck,
  CheckCircle2,
  FileText,
  DollarSign,
  MapPin,
  RefreshCw,
  Download,
} from 'lucide-react';
import { endorsementService, documentService } from '../services/api';
import { useToast } from '../context/ToastContext';

export const VersionHistoryModal = ({ policy, isOpen, onClose }) => {
  const { showToast } = useToast();

  const [activeTab, setActiveTab] = useState('versions'); // 'versions' | 'compare' | 'transactions'
  const [loading, setLoading] = useState(false);

  // Data states
  const [versions, setVersions] = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [selectedSnapshot, setSelectedSnapshot] = useState(null);

  // Compare states
  const [v1, setV1] = useState(1);
  const [v2, setV2] = useState(2);
  const [diffResult, setDiffResult] = useState(null);
  const [comparing, setComparing] = useState(false);

  useEffect(() => {
    if (isOpen && policy?.policyNumber) {
      loadHistoryData();
    }
  }, [isOpen, policy?.policyNumber]);

  const loadHistoryData = async () => {
    if (!policy?.policyNumber) return;
    try {
      setLoading(true);
      const [verRes, txnRes] = await Promise.all([
        endorsementService.getVersions(policy.policyNumber),
        endorsementService.getHistory(policy.policyNumber),
      ]);

      const loadedVersions = verRes.data || [];
      setVersions(loadedVersions);
      setTransactions(txnRes.data || []);

      if (loadedVersions.length >= 2) {
        setV1(loadedVersions[loadedVersions.length - 1].version);
        setV2(loadedVersions[0].version);
      } else if (loadedVersions.length === 1) {
        setV1(loadedVersions[0].version);
        setV2(loadedVersions[0].version);
      }
    } catch (err) {
      showToast('Không thể tải lịch sử phiên bản: ' + err.message, 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleCompare = async () => {
    if (!v1 || !v2) return;
    try {
      setComparing(true);
      const res = await endorsementService.compareVersions(policy.policyNumber, v1, v2);
      setDiffResult(res.data);
    } catch (err) {
      showToast('Không thể so sánh 2 phiên bản: ' + err.message, 'error');
    } finally {
      setComparing(false);
    }
  };

  if (!isOpen || !policy) return null;

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.75)',
        backdropFilter: 'blur(6px)',
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        zIndex: 9999,
        padding: '1rem',
      }}
    >
      <div
        className="glass-card"
        style={{
          width: '100%',
          maxWidth: '850px',
          maxHeight: '90vh',
          display: 'flex',
          flexDirection: 'column',
          borderRadius: 'var(--radius-xl)',
          padding: '1.75rem',
          border: '1px solid rgba(59, 130, 246, 0.3)',
          boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.5)',
        }}
      >
        {/* Header */}
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-start',
            marginBottom: '1rem',
            paddingBottom: '0.75rem',
            borderBottom: '1px solid var(--border-subtle)',
          }}
        >
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.25rem' }}>
              <History size={22} color="var(--primary)" />
              <h3 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 700 }}>
                Lịch sử Phiên bản & Endorsements
              </h3>
              <span
                style={{
                  fontSize: '0.75rem',
                  padding: '0.2rem 0.6rem',
                  borderRadius: 'var(--radius-full)',
                  background: 'rgba(59, 130, 246, 0.15)',
                  color: 'var(--primary)',
                  fontWeight: 600,
                }}
              >
                {policy.policyNumber}
              </span>
            </div>
            <p style={{ margin: 0, fontSize: '0.825rem', color: 'var(--text-muted)' }}>
              Theo dõi toàn bộ lịch sử sửa đổi bổ sung (Audit Trail) và các bản chụp snapshot của hợp đồng.
            </p>
          </div>
          <button
            onClick={onClose}
            className="btn btn-secondary"
            style={{ padding: '0.4rem', borderRadius: '50%' }}
          >
            <X size={18} />
          </button>
        </div>

        {/* Tab Switcher */}
        <div
          style={{
            display: 'flex',
            gap: '0.5rem',
            marginBottom: '1.25rem',
            borderBottom: '1px solid var(--border-subtle)',
            paddingBottom: '0.5rem',
          }}
        >
          <button
            onClick={() => setActiveTab('versions')}
            className="btn"
            style={{
              padding: '0.45rem 0.9rem',
              fontSize: '0.85rem',
              fontWeight: 600,
              borderRadius: 'var(--radius-md)',
              background: activeTab === 'versions' ? 'var(--primary-gradient)' : 'transparent',
              color: activeTab === 'versions' ? '#ffffff' : 'var(--text-secondary)',
            }}
          >
            <Layers size={16} />
            <span>Danh sách Phiên bản ({versions.length})</span>
          </button>

          <button
            onClick={() => {
              setActiveTab('compare');
              if (!diffResult && versions.length >= 2) {
                handleCompare();
              }
            }}
            className="btn"
            style={{
              padding: '0.45rem 0.9rem',
              fontSize: '0.85rem',
              fontWeight: 600,
              borderRadius: 'var(--radius-md)',
              background: activeTab === 'compare' ? 'var(--primary-gradient)' : 'transparent',
              color: activeTab === 'compare' ? '#ffffff' : 'var(--text-secondary)',
            }}
          >
            <ArrowRightLeft size={16} />
            <span>So sánh Diff Phiên bản</span>
          </button>

          <button
            onClick={() => setActiveTab('transactions')}
            className="btn"
            style={{
              padding: '0.45rem 0.9rem',
              fontSize: '0.85rem',
              fontWeight: 600,
              borderRadius: 'var(--radius-md)',
              background: activeTab === 'transactions' ? 'var(--primary-gradient)' : 'transparent',
              color: activeTab === 'transactions' ? '#ffffff' : 'var(--text-secondary)',
            }}
          >
            <Clock size={16} />
            <span>Nhật ký Giao dịch ({transactions.length})</span>
          </button>
        </div>

        {/* Modal Body with Scroll */}
        <div style={{ flex: 1, overflowY: 'auto', paddingRight: '0.25rem' }}>
          {loading ? (
            <div style={{ textAlign: 'center', padding: '3rem 1rem' }}>
              <div className="spinner" style={{ margin: '0 auto 1rem' }} />
              <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Đang tải lịch sử phiên bản...</p>
            </div>
          ) : activeTab === 'versions' ? (
            /* TAB 1: VERSIONS LIST */
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              {versions.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                  Chưa có lịch sử phiên bản nào được ghi nhận.
                </div>
              ) : (
                versions.map((ver) => {
                  const isCurrent = ver.version === policy.version;
                  return (
                    <div
                      key={ver.id || ver.version}
                      style={{
                        background: isCurrent ? 'rgba(59, 130, 246, 0.08)' : 'var(--bg-input)',
                        border: isCurrent ? '1px solid var(--primary)' : '1px solid var(--border-subtle)',
                        borderRadius: 'var(--radius-lg)',
                        padding: '1.25rem',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '0.75rem',
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                          <span
                            style={{
                              background: isCurrent ? 'var(--primary-gradient)' : 'rgba(255, 255, 255, 0.1)',
                              color: '#ffffff',
                              fontWeight: 700,
                              fontSize: '0.85rem',
                              padding: '0.25rem 0.6rem',
                              borderRadius: 'var(--radius-md)',
                            }}
                          >
                            v{ver.version}
                          </span>
                          {isCurrent && (
                            <span style={{ fontSize: '0.75rem', color: '#10b981', fontWeight: 600 }}>
                              ● Hiện hành (Active)
                            </span>
                          )}
                          <span
                            style={{
                              fontSize: '0.75rem',
                              padding: '0.2rem 0.5rem',
                              borderRadius: 'var(--radius-sm)',
                              background: 'rgba(255, 255, 255, 0.05)',
                              color: 'var(--text-muted)',
                            }}
                          >
                            {ver.endorsementType || 'GENERAL'}
                          </span>
                        </div>

                        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '0.3rem' }}>
                            <Calendar size={14} />
                            <span>{ver.createdAt ? new Date(ver.createdAt).toLocaleString() : '—'}</span>
                          </div>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '0.3rem' }}>
                            <User size={14} />
                            <span>{ver.createdBy || 'System'}</span>
                          </div>
                        </div>
                      </div>

                      {/* Reason */}
                      <div style={{ fontSize: '0.9rem', color: 'var(--text-primary)', fontWeight: 500 }}>
                        <span style={{ color: 'var(--text-muted)', marginRight: '0.5rem' }}>Lý do / Nội dung:</span>
                        {ver.endorsementReason || 'Bản chụp khởi tạo ban đầu'}
                      </div>

                      {/* Premium Summary & Snapshot Toggle */}
                      <div
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          paddingTop: '0.5rem',
                          borderTop: '1px dashed var(--border-subtle)',
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', fontSize: '0.85rem' }}>
                          <span>
                            Tổng phí:{' '}
                            <b style={{ color: '#10b981' }}>
                              {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(ver.totalPremium || 0)}
                            </b>
                          </span>
                          {ver.premiumChange !== undefined && ver.premiumChange !== 0 && (
                            <span
                              style={{
                                color: ver.premiumChange > 0 ? '#10b981' : '#ef4444',
                                display: 'flex',
                                alignItems: 'center',
                                gap: '0.2rem',
                                fontSize: '0.8rem',
                                fontWeight: 600,
                              }}
                            >
                              {ver.premiumChange > 0 ? <ArrowUpRight size={14} /> : <ArrowDownRight size={14} />}
                              {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(ver.premiumChange)}
                            </span>
                          )}
                        </div>

                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
                          <button
                            onClick={() => {
                              showToast(`Đang tải Giấy chứng nhận PDF v${ver.version}...`, 'info');
                              documentService.downloadCertificate(policy.policyNumber, ver.version)
                                .catch(e => showToast(e.message || 'Lỗi tải PDF', 'error'));
                            }}
                            className="btn btn-secondary"
                            style={{ padding: '0.3rem 0.6rem', fontSize: '0.775rem' }}
                            title="Tải Giấy chứng nhận bảo hiểm (Certificate PDF)"
                          >
                            <Download size={13} color="var(--primary)" />
                            <span>COI PDF</span>
                          </button>

                          <button
                            onClick={() => {
                              showToast(`Đang tải Bảng tóm tắt PDF v${ver.version}...`, 'info');
                              documentService.downloadSummary(policy.policyNumber, ver.version)
                                .catch(e => showToast(e.message || 'Lỗi tải PDF', 'error'));
                            }}
                            className="btn btn-secondary"
                            style={{ padding: '0.3rem 0.6rem', fontSize: '0.775rem' }}
                            title="Tải Bảng tóm tắt hợp đồng (Policy Summary PDF)"
                          >
                            <FileText size={13} color="#10b981" />
                            <span>Summary PDF</span>
                          </button>

                          <button
                            onClick={() => setSelectedSnapshot(selectedSnapshot?.version === ver.version ? null : ver)}
                            className="btn btn-secondary"
                            style={{ padding: '0.3rem 0.6rem', fontSize: '0.775rem' }}
                          >
                            {selectedSnapshot?.version === ver.version ? 'Ẩn chi tiết' : 'Xem Snapshot'}
                          </button>
                        </div>
                      </div>

                      {/* Snapshot Expanded Details */}
                      {selectedSnapshot?.version === ver.version && (
                        <div
                          style={{
                            background: 'rgba(0, 0, 0, 0.25)',
                            borderRadius: 'var(--radius-md)',
                            padding: '0.75rem 1rem',
                            marginTop: '0.5rem',
                            fontSize: '0.8rem',
                            color: 'var(--text-secondary)',
                          }}
                        >
                          <div style={{ fontWeight: 600, color: 'var(--text-primary)', marginBottom: '0.4rem' }}>
                            Dữ liệu bên mua bảo hiểm thời điểm v{ver.version}:
                          </div>
                          <div>Tên: {ver.policySnapshot?.insured?.name || '—'}</div>
                          <div>Email: {ver.policySnapshot?.insured?.email || '—'} | SĐT: {ver.policySnapshot?.insured?.phone || '—'}</div>
                          <div>Địa chỉ: {ver.policySnapshot?.insured?.address || '—'}</div>
                          <div style={{ marginTop: '0.4rem' }}>
                            Số lượng địa điểm: {ver.policySnapshot?.locations?.length || 0}
                          </div>
                        </div>
                      )}
                    </div>
                  );
                })
              )}
            </div>
          ) : activeTab === 'compare' ? (
            /* TAB 2: VERSION DIFF COMPARE */
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '1rem',
                  background: 'var(--bg-input)',
                  padding: '1rem',
                  borderRadius: 'var(--radius-lg)',
                }}
              >
                <div style={{ flex: 1 }}>
                  <label className="form-label" style={{ fontSize: '0.775rem' }}>Phiên bản gốc (v1)</label>
                  <select
                    value={v1}
                    onChange={(e) => setV1(Number(e.target.value))}
                    className="form-control"
                  >
                    {versions.map((v) => (
                      <option key={v.version} value={v.version}>
                        v{v.version} ({v.endorsementType || 'GENERAL'})
                      </option>
                    ))}
                  </select>
                </div>

                <ArrowRightLeft size={20} color="var(--primary)" style={{ marginTop: '1.25rem' }} />

                <div style={{ flex: 1 }}>
                  <label className="form-label" style={{ fontSize: '0.775rem' }}>Phiên bản so sánh (v2)</label>
                  <select
                    value={v2}
                    onChange={(e) => setV2(Number(e.target.value))}
                    className="form-control"
                  >
                    {versions.map((v) => (
                      <option key={v.version} value={v.version}>
                        v{v.version} ({v.endorsementType || 'GENERAL'})
                      </option>
                    ))}
                  </select>
                </div>

                <button
                  onClick={handleCompare}
                  className="btn btn-primary"
                  style={{ marginTop: '1.25rem', whiteSpace: 'nowrap' }}
                  disabled={comparing}
                >
                  {comparing ? <RefreshCw className="animate-spin" size={16} /> : <ArrowRightLeft size={16} />}
                  <span>So sánh Diff</span>
                </button>
              </div>

              {diffResult && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                  {/* Premium summary diff card */}
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: '1fr 1fr 1fr',
                      gap: '1rem',
                      background: 'rgba(59, 130, 246, 0.08)',
                      border: '1px solid rgba(59, 130, 246, 0.2)',
                      borderRadius: 'var(--radius-lg)',
                      padding: '1rem',
                      textAlign: 'center',
                    }}
                  >
                    <div>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Phí v{diffResult.fromVersion}</div>
                      <div style={{ fontSize: '1rem', fontWeight: 700, color: 'var(--text-primary)' }}>
                        {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(diffResult.premiumBefore || 0)}
                      </div>
                    </div>
                    <div>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Phí v{diffResult.toVersion}</div>
                      <div style={{ fontSize: '1rem', fontWeight: 700, color: 'var(--text-primary)' }}>
                        {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(diffResult.premiumAfter || 0)}
                      </div>
                    </div>
                    <div>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Chênh lệch (Δ)</div>
                      <div
                        style={{
                          fontSize: '1rem',
                          fontWeight: 700,
                          color: diffResult.premiumDifference >= 0 ? '#10b981' : '#ef4444',
                        }}
                      >
                        {diffResult.premiumDifference >= 0 ? '+' : ''}
                        {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(diffResult.premiumDifference || 0)}
                      </div>
                    </div>
                  </div>

                  {/* Changes detail list */}
                  <div style={{ fontWeight: 600, fontSize: '0.9rem', color: 'var(--text-primary)' }}>
                    Các trường dữ liệu có sự thay đổi ({diffResult.changes?.length || 0}):
                  </div>

                  {diffResult.changes?.length === 0 ? (
                    <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                      Không có trường thông tin nào khác biệt giữa 2 phiên bản này.
                    </div>
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                      {diffResult.changes?.map((ch, idx) => (
                        <div
                          key={idx}
                          style={{
                            background: 'var(--bg-input)',
                            border: '1px solid var(--border-subtle)',
                            borderRadius: 'var(--radius-md)',
                            padding: '0.75rem 1rem',
                            display: 'flex',
                            flexDirection: 'column',
                            gap: '0.35rem',
                          }}
                        >
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem', color: 'var(--primary)', fontWeight: 600 }}>
                              {ch.field}
                            </span>
                            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{ch.description}</span>
                          </div>
                          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem', fontSize: '0.8rem' }}>
                            <div style={{ background: 'rgba(239, 68, 68, 0.1)', padding: '0.4rem 0.6rem', borderRadius: 'var(--radius-sm)', color: '#f87171' }}>
                              <b>Cũ (v{diffResult.fromVersion}):</b> {String(ch.oldValue ?? '—')}
                            </div>
                            <div style={{ background: 'rgba(16, 185, 129, 0.1)', padding: '0.4rem 0.6rem', borderRadius: 'var(--radius-sm)', color: '#34d399' }}>
                              <b>Mới (v{diffResult.toVersion}):</b> {String(ch.newValue ?? '—')}
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          ) : (
            /* TAB 3: TRANSACTION AUDIT TIMELINE */
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              {transactions.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                  Chưa có nhật ký giao dịch nào.
                </div>
              ) : (
                transactions.map((txn, index) => (
                  <div
                    key={txn.id || index}
                    style={{
                      display: 'flex',
                      gap: '1rem',
                      alignItems: 'flex-start',
                      background: 'var(--bg-input)',
                      border: '1px solid var(--border-subtle)',
                      borderRadius: 'var(--radius-lg)',
                      padding: '1rem',
                    }}
                  >
                    <div
                      style={{
                        width: '36px',
                        height: '36px',
                        borderRadius: '50%',
                        background: 'rgba(59, 130, 246, 0.15)',
                        color: 'var(--primary)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        flexShrink: 0,
                      }}
                    >
                      <GitCommit size={18} />
                    </div>

                    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '0.3rem' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span
                          style={{
                            fontWeight: 700,
                            fontSize: '0.85rem',
                            color: txn.transactionType === 'ENDORSEMENT' ? '#10b981' : 'var(--primary)',
                          }}
                        >
                          {txn.transactionType}
                        </span>
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                          {txn.timestamp ? new Date(txn.timestamp).toLocaleString() : '—'}
                        </span>
                      </div>

                      <div style={{ fontSize: '0.85rem', color: 'var(--text-primary)' }}>
                        {txn.description}
                      </div>

                      <div style={{ display: 'flex', gap: '1rem', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                        <span>Thực hiện: <b>{txn.actor || 'System'}</b></span>
                        {txn.version && <span>Version: <b>v{txn.version}</b></span>}
                      </div>
                    </div>
                  </div>
                ))
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
