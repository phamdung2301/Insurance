import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import {
  ShieldAlert, ArrowLeft, CheckCircle2, XCircle, Clock, DollarSign,
  FileText, Image as ImageIcon, Plus, Upload, Trash2, AlertCircle,
  Eye, Check, X, CreditCard, ChevronRight, Loader2
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { claimApi } from '../services/claimApi';
import { formatMoney, formatDateTime } from '../utils/format';
import { Modal } from '../components/Modal';
import { Skeleton } from '../components/ui';

const STATUS_CONFIG = {
  SUBMITTED: { label: 'Mới gửi', color: '#d97706', bg: '#fef3c7' },
  UNDER_REVIEW: { label: 'Đang thẩm định', color: '#2563eb', bg: '#dbeafe' },
  APPROVED: { label: 'Đã duyệt', color: '#059669', bg: '#d1fae5' },
  REJECTED: { label: 'Từ chối', color: '#dc2626', bg: '#fee2e2' },
  PAID: { label: 'Đã chi trả', color: '#047857', bg: '#a7f3d0' },
};

function SecureImage({ claimId, photoId, alt, onClick }) {
  const [src, setSrc] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    let objectUrl = null;
    claimApi.getPhotoBlobUrl(claimId, photoId)
      .then((url) => {
        if (active) {
          objectUrl = url;
          setSrc(url);
          setLoading(false);
        }
      })
      .catch(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [claimId, photoId]);

  if (loading) {
    return <div className="skeleton" style={{ width: '100%', height: '140px', borderRadius: '6px' }} />;
  }
  if (!src) {
    return (
      <div style={{ width: '100%', height: '140px', background: '#f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#94a3b8', borderRadius: '6px', fontSize: '0.8rem' }}>
        Lỗi tải ảnh
      </div>
    );
  }
  return (
    <img
      src={src}
      alt={alt}
      onClick={() => onClick(src)}
      style={{ width: '100%', height: '140px', objectFit: 'cover', borderRadius: '6px', cursor: 'pointer', transition: 'transform 0.2s ease' }}
      className="claim-photo-thumb"
    />
  );
}

export function ClaimDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const { user, hasRole } = useAuth();

  const [claim, setClaim] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  // Lightbox Modal
  const [lightboxSrc, setLightboxSrc] = useState(null);

  // ADMIN Action Modals
  const [approveModal, setApproveModal] = useState(false);
  const [approvedAmount, setApprovedAmount] = useState('');
  const [handlerNote, setHandlerNote] = useState('');

  const [rejectModal, setRejectModal] = useState(false);
  const [rejectReason, setRejectReason] = useState('');

  // Add more photos
  const [addPhotosModal, setAddPhotosModal] = useState(false);
  const [morePhotos, setMorePhotos] = useState([]);
  const addPhotosInputRef = useRef(null);

  const isAdmin = hasRole('ROLE_ADMIN');
  // Nút "Tiếp nhận thẩm định" dùng API intake mới: nhân viên xử lý
  // (UNDERWRITER hoặc ADMIN). Phê duyệt/từ chối/chi trả vẫn ADMIN-only
  // (service checkAdmin) nên các nút đó vẫn gate riêng bằng isAdmin.
  const isStaff = isAdmin || hasRole('ROLE_UNDERWRITER');

  const loadClaim = useCallback(async () => {
    try {
      setLoading(true);
      const res = await claimApi.getClaimById(id);
      setClaim(res.data || res);
    } catch (err) {
      console.error(err);
      toast.error(err.message || 'Không thể tải chi tiết yêu cầu bồi thường.');
    } finally {
      setLoading(false);
    }
  }, [id, toast]);

  useEffect(() => {
    loadClaim();
  }, [loadClaim]);

  // Hành động của ADMIN
  const handleReview = async () => {
    try {
      setBusy(true);
      await claimApi.intakeClaim(id);
      toast.success('Đã tiếp nhận hồ sơ sang trạng thái thẩm định.');
      await loadClaim();
    } catch (err) {
      toast.error(err.message || 'Thao tác thất bại.');
    } finally {
      setBusy(false);
    }
  };

  const handleApproveSubmit = async (e) => {
    e.preventDefault();
    const amount = parseFloat(approvedAmount);
    if (isNaN(amount) || amount <= 0) {
      toast.error('Vui lòng nhập số tiền duyệt hợp lệ (> 0).');
      return;
    }
    if (amount > claim.claimedAmount) {
      toast.error(`Số tiền duyệt (${formatMoney(amount)}) không được vượt quá số tiền yêu cầu (${formatMoney(claim.claimedAmount)}).`);
      return;
    }

    try {
      setBusy(true);
      await claimApi.approveClaim(id, {
        approvedAmount: amount,
        handlerNote: handlerNote.trim() || undefined,
      });
      toast.success('Đã phê duyệt yêu cầu bồi thường thành công.');
      setApproveModal(false);
      await loadClaim();
    } catch (err) {
      toast.error(err.message || 'Thao tác phê duyệt thất bại.');
    } finally {
      setBusy(false);
    }
  };

  const handleRejectSubmit = async (e) => {
    e.preventDefault();
    if (!rejectReason.trim()) {
      toast.error('Lý do từ chối là bắt buộc.');
      return;
    }

    try {
      setBusy(true);
      await claimApi.rejectClaim(id, {
        rejectReason: rejectReason.trim(),
      });
      toast.success('Đã từ chối yêu cầu bồi thường.');
      setRejectModal(false);
      await loadClaim();
    } catch (err) {
      toast.error(err.message || 'Thao tác từ chối thất bại.');
    } finally {
      setBusy(false);
    }
  };

  const handlePay = async () => {
    if (!window.confirm(`Xác nhận đã chi trả số tiền bồi thường ${formatMoney(claim.approvedAmount)} cho khách hàng?`)) {
      return;
    }
    try {
      setBusy(true);
      await claimApi.payClaim(id);
      toast.success('Đã xác nhận chi trả bồi thường thành công!');
      await loadClaim();
    } catch (err) {
      toast.error(err.message || 'Thao tác chi trả thất bại.');
    } finally {
      setBusy(false);
    }
  };

  // Thêm ảnh bổ sung
  const handleAddPhotosSubmit = async (e) => {
    e.preventDefault();
    if (morePhotos.length === 0) return;

    const currentCount = claim.photos ? claim.photos.length : 0;
    if (currentCount + morePhotos.length > 10) {
      toast.error(`Tổng số ảnh sau khi thêm sẽ là ${currentCount + morePhotos.length}, vượt quá tối đa 10 ảnh.`);
      return;
    }

    try {
      setBusy(true);
      const formData = new FormData();
      morePhotos.forEach((file) => {
        formData.append('photos', file);
      });
      await claimApi.addPhotos(id, formData);
      toast.success('Đã bổ sung ảnh thành công.');
      setMorePhotos([]);
      setAddPhotosModal(false);
      await loadClaim();
    } catch (err) {
      toast.error(err.message || 'Không thể bổ sung ảnh.');
    } finally {
      setBusy(false);
    }
  };

  if (loading) {
    return (
      <div className="container" style={{ maxWidth: '1000px', margin: '0 auto', padding: '1.5rem 1rem' }}>
        <Skeleton height={35} width="40%" style={{ marginBottom: '1rem' }} />
        <Skeleton height={120} style={{ marginBottom: '1.5rem' }} />
        <Skeleton height={200} />
      </div>
    );
  }

  if (!claim) {
    return (
      <div className="container" style={{ maxWidth: '1000px', margin: '0 auto', padding: '1.5rem 1rem' }}>
        <div className="glass-card" style={{ padding: '2rem', textAlign: 'center' }}>
          <h2>Không tìm thấy hồ sơ bồi thường</h2>
          <p style={{ color: 'var(--text-secondary)' }}>Hồ sơ không tồn tại hoặc bạn không có quyền xem.</p>
          <Link to="/claims" className="btn btn-primary" style={{ marginTop: '1rem' }}>
            <ArrowLeft size={16} /> Quay lại danh sách
          </Link>
        </div>
      </div>
    );
  }

  const isOwner = user?.email && claim.claimantEmail && user.email.toLowerCase() === claim.claimantEmail.toLowerCase();
  // Tràn số: số rất dài (VD 100 nghìn tỷ -> "100.000.000.000.000 ₫")
  // không còn bị cắt ở mép thẻ: grid item minWidth 0 + break-anywhere +
  // title tooltip đủ giá trị + font co dần theo độ dài chuỗi.
  const claimedFormatted = formatMoney(claim.claimedAmount);
  const approvedFormatted = claim.approvedAmount ? formatMoney(claim.approvedAmount) : null;
  const amountFontSize = (s) => (s && s.length > 24 ? '1.05rem' : s && s.length > 18 ? '1.25rem' : '1.45rem');
  const amountStyle = (color) => ({
    fontSize: '1.45rem',
    color,
    overflowWrap: 'anywhere',
    wordBreak: 'break-all',
    lineHeight: 1.3,
  });
  const canAddMorePhotos = isOwner && (claim.photos ? claim.photos.length : 0) < 10 && !['PAID', 'REJECTED'].includes(claim.status);
  const statusCfg = STATUS_CONFIG[claim.status] || { label: claim.status, color: '#64748b', bg: '#f1f5f9' };

  return (
    <div className="container" style={{ maxWidth: '1000px', margin: '0 auto', padding: '1.5rem 1rem' }}>
      {/* Header bar */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem', marginBottom: '1.5rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.85rem' }}>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => navigate('/claims')}
            style={{ padding: '0.4rem 0.6rem' }}
          >
            <ArrowLeft size={18} />
          </button>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
              <h1 style={{ fontSize: '1.5rem', fontWeight: 600, margin: 0 }}>
                Hồ sơ bồi thường #{claim.id.substring(0, 8)}
              </h1>
              <span style={{
                padding: '0.2rem 0.65rem',
                borderRadius: '9999px',
                fontSize: '0.8rem',
                fontWeight: 600,
                color: statusCfg.color,
                backgroundColor: statusCfg.bg,
              }}>
                {statusCfg.label}
              </span>
            </div>
            <div style={{ color: 'var(--text-secondary)', fontSize: '0.85rem', marginTop: '0.2rem' }}>
              Hợp đồng:{' '}
              <Link to={`/policies/${claim.policyNumber}`} style={{ color: '#1663d9', fontWeight: 500, textDecoration: 'none' }}>
                {claim.policyNumber}
              </Link>
              {' '}• Người yêu cầu: <strong>{claim.claimantEmail}</strong>
              {claim.createdAt && ` • Ngày gửi: ${formatDateTime(claim.createdAt)}`}
            </div>
          </div>
        </div>

        {/* Action bar dành cho nhân viên xử lý (UNDERWRITER/ADMIN);
            riêng phê duyệt/từ chối/chi trả vẫn chỉ ADMIN (xem isAdmin). */}
        {isStaff && (
          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
            {claim.status === 'SUBMITTED' && (
              <button
                type="button"
                className="btn btn-primary"
                onClick={handleReview}
                disabled={busy}
                style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}
              >
                <Clock size={15} /> Tiếp nhận thẩm định
              </button>
            )}

            {isAdmin && claim.status === 'UNDER_REVIEW' && (
              <>
                <button
                  type="button"
                  className="btn btn-success"
                  onClick={() => {
                    setApprovedAmount(claim.claimedAmount.toString());
                    setApproveModal(true);
                  }}
                  disabled={busy}
                  style={{ backgroundColor: '#059669', borderColor: '#059669', color: '#fff', display: 'flex', alignItems: 'center', gap: '0.35rem' }}
                >
                  <Check size={15} /> Phê duyệt bồi thường
                </button>
                <button
                  type="button"
                  className="btn btn-danger"
                  onClick={() => setRejectModal(true)}
                  disabled={busy}
                  style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}
                >
                  <X size={15} /> Từ chối
                </button>
              </>
            )}

            {isAdmin && claim.status === 'APPROVED' && (
              <button
                type="button"
                className="btn btn-primary"
                onClick={handlePay}
                disabled={busy}
                style={{ backgroundColor: '#047857', borderColor: '#047857', color: '#fff', display: 'flex', alignItems: 'center', gap: '0.35rem' }}
              >
                <CreditCard size={15} /> Xác nhận chi trả
              </button>
            )}
          </div>
        )}
      </div>

      {/* Cảnh báo nếu REJECTED */}
      {claim.status === 'REJECTED' && claim.rejectReason && (
        <div style={{
          backgroundColor: '#fef2f2',
          border: '1px solid #fecaca',
          borderRadius: '8px',
          padding: '1.25rem',
          marginBottom: '1.5rem',
          display: 'flex',
          gap: '0.75rem',
          alignItems: 'flex-start'
        }}>
          <AlertCircle size={22} color="#dc2626" style={{ flexShrink: 0, marginTop: '2px' }} />
          <div>
            <strong style={{ color: '#dc2626', fontSize: '0.95rem', display: 'block', marginBottom: '0.25rem' }}>
              Yêu cầu bồi thường đã bị từ chối
            </strong>
            <p style={{ margin: 0, color: '#991b1b', fontSize: '0.9rem', whiteSpace: 'pre-line' }}>
              {claim.rejectReason}
            </p>
          </div>
        </div>
      )}

      {/* Tổng quan tài chính & Thông tin */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1rem', marginBottom: '1.5rem' }}>
        <div className="glass-card" style={{ padding: '1.25rem', minWidth: 0 }}>
          <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', display: 'block', marginBottom: '0.25rem' }}>
            Số tiền yêu cầu bồi thường
          </span>
          <strong title={claimedFormatted} style={{ ...amountStyle('#1663d9'), fontSize: amountFontSize(claimedFormatted) }}>
            {claimedFormatted}
          </strong>
        </div>

        <div className="glass-card" style={{ padding: '1.25rem', minWidth: 0 }}>
          <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', display: 'block', marginBottom: '0.25rem' }}>
            Số tiền được phê duyệt
          </span>
          <strong title={approvedFormatted || undefined} style={{ ...amountStyle(claim.approvedAmount ? '#059669' : 'var(--text-secondary)'), fontSize: approvedFormatted ? amountFontSize(approvedFormatted) : '1.45rem' }}>
            {approvedFormatted || 'Chưa xác định'}
          </strong>
        </div>

        <div className="glass-card" style={{ padding: '1.25rem' }}>
          <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', display: 'block', marginBottom: '0.25rem' }}>
            Trạng thái thanh toán
          </span>
          <strong style={{ fontSize: '1.15rem', color: claim.status === 'PAID' ? '#047857' : 'inherit' }}>
            {claim.status === 'PAID' ? 'Đã chi trả tiền' : claim.status === 'APPROVED' ? 'Chờ kế toán chi trả' : 'Đang xử lý'}
          </strong>
        </div>
      </div>

      {/* Chi tiết thiệt hại */}
      <div className="glass-card" style={{ padding: '1.5rem', marginBottom: '1.5rem' }}>
        <h3 style={{ fontSize: '1.1rem', fontWeight: 600, marginTop: 0, marginBottom: '0.75rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <FileText size={18} color="#1663d9" />
          Mô tả thiệt hại và sự việc
        </h3>
        <p style={{ margin: 0, fontSize: '0.92rem', lineHeight: '1.6', whiteSpace: 'pre-line', color: 'var(--text-primary)' }}>
          {claim.damageDescription}
        </p>

        {claim.handlerNote && (
          <div style={{ marginTop: '1.25rem', padding: '0.85rem 1rem', backgroundColor: 'var(--bg-secondary, #f8fafc)', borderRadius: '6px', borderLeft: '4px solid #1663d9' }}>
            <strong style={{ fontSize: '0.85rem', color: '#1663d9', display: 'block', marginBottom: '0.2rem' }}>
              Ghi chú của Quản trị viên (ADMIN):
            </strong>
            <p style={{ margin: 0, fontSize: '0.88rem', color: 'var(--text-secondary)' }}>
              {claim.handlerNote}
            </p>
          </div>
        )}
      </div>

      {/* Gallery ảnh bằng chứng */}
      <div className="glass-card" style={{ padding: '1.5rem', marginBottom: '1.5rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h3 style={{ fontSize: '1.1rem', fontWeight: 600, margin: 0, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <ImageIcon size={18} color="#1663d9" />
            Ảnh bằng chứng hiện trường ({claim.photos ? claim.photos.length : 0}/10 ảnh)
          </h3>
          {canAddMorePhotos && (
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => setAddPhotosModal(true)}
              style={{ fontSize: '0.8rem', padding: '0.35rem 0.65rem', display: 'flex', alignItems: 'center', gap: '0.3rem' }}
            >
              <Plus size={14} /> Bổ sung ảnh
            </button>
          )}
        </div>

        {(!claim.photos || claim.photos.length === 0) ? (
          <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-secondary)', fontSize: '0.9rem' }}>
            Không có ảnh bằng chứng nào được đính kèm.
          </div>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))', gap: '1rem' }}>
            {claim.photos.map((photo) => (
              <div
                key={photo.id}
                style={{
                  border: '1px solid var(--border-color, #e2e8f0)',
                  borderRadius: '8px',
                  overflow: 'hidden',
                  backgroundColor: '#fff',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
                  position: 'relative',
                }}
              >
                <SecureImage
                  claimId={claim.id}
                  photoId={photo.id}
                  alt={photo.fileName}
                  onClick={(src) => setLightboxSrc(src)}
                />
                <div style={{ padding: '0.4rem 0.6rem', fontSize: '0.75rem' }}>
                  <div style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontWeight: 500 }} title={photo.fileName}>
                    {photo.fileName}
                  </div>
                  <div style={{ color: 'var(--text-secondary)', display: 'flex', justifyContent: 'space-between', marginTop: '2px' }}>
                    <span>{(photo.fileSize / (1024 * 1024)).toFixed(2)} MB</span>
                    <button
                      type="button"
                      onClick={() => setLightboxSrc(`/api/claims/${claim.id}/photos/${photo.id}`)}
                      style={{ background: 'none', border: 'none', color: '#1663d9', cursor: 'pointer', padding: 0 }}
                      title="Xem phóng to"
                    >
                      <Eye size={13} />
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Lịch sử trạng thái (Timeline) */}
      <div className="glass-card" style={{ padding: '1.5rem', marginBottom: '1.5rem' }}>
        <h3 style={{ fontSize: '1.1rem', fontWeight: 600, marginTop: 0, marginBottom: '1rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <Clock size={18} color="#1663d9" />
          Tiến trình xử lý hồ sơ
        </h3>

        {(!claim.statusHistory || claim.statusHistory.length === 0) ? (
          <div style={{ color: 'var(--text-secondary)', fontSize: '0.85rem' }}>Chưa có lịch sử trạng thái.</div>
        ) : (
          <div style={{ position: 'relative', paddingLeft: '1.5rem' }}>
            <div style={{
              position: 'absolute',
              top: '8px',
              bottom: '8px',
              left: '7px',
              width: '2px',
              backgroundColor: 'var(--border-color, #e2e8f0)'
            }} />
            {claim.statusHistory.map((h, i) => {
              const hCfg = STATUS_CONFIG[h.toStatus] || { label: h.toStatus, color: '#64748b', bg: '#f1f5f9' };
              return (
                <div key={i} style={{ position: 'relative', marginBottom: '1.25rem' }}>
                  <div style={{
                    position: 'absolute',
                    left: '-1.5rem',
                    top: '3px',
                    width: '12px',
                    height: '12px',
                    borderRadius: '50%',
                    backgroundColor: hCfg.color,
                    border: '2px solid #fff',
                    boxShadow: '0 0 0 2px ' + hCfg.bg,
                  }} />
                  <div style={{ fontSize: '0.85rem' }}>
                    <span style={{ fontWeight: 600, color: hCfg.color }}>
                      {hCfg.label}
                    </span>
                    <span style={{ color: 'var(--text-secondary)', marginLeft: '0.5rem' }}>
                      bởi <strong>{h.changedBy || 'Hệ thống'}</strong>
                    </span>
                    <span style={{ color: 'var(--text-secondary)', marginLeft: '0.5rem' }}>
                      • {h.changedAt ? formatDateTime(h.changedAt) : '—'}
                    </span>
                  </div>
                  {h.note && (
                    <div style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', marginTop: '0.2rem' }}>
                      {h.note}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Lightbox Modal */}
      {lightboxSrc && (
        <div
          onClick={() => setLightboxSrc(null)}
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(0,0,0,0.85)',
            zIndex: 9999,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '1rem',
          }}
        >
          <img
            src={lightboxSrc}
            alt="Phóng to"
            style={{ maxWidth: '90vw', maxHeight: '90vh', objectFit: 'contain', borderRadius: '4px' }}
          />
        </div>
      )}

      {/* Modal Phê duyệt */}
      <Modal
        open={approveModal}
        onClose={() => setApproveModal(false)}
        title="Phê duyệt yêu cầu bồi thường"
      >
        <form onSubmit={handleApproveSubmit}>
          <div className="form-group" style={{ marginBottom: '1rem' }}>
            <label className="form-label" style={{ fontWeight: 600, display: 'block', marginBottom: '0.3rem' }}>
              Số tiền duyệt bồi thường (VNĐ) <span style={{ color: 'red' }}>*</span>
            </label>
            <input
              type="number"
              className="form-control"
              value={approvedAmount}
              onChange={(e) => setApprovedAmount(e.target.value)}
              max={claim.claimedAmount}
              min="1"
              step="any"
              required
              style={{ width: '100%', padding: '0.5rem' }}
            />
            <small title={claimedFormatted} style={{ color: 'var(--text-secondary)', display: 'block', marginTop: '0.25rem', overflowWrap: 'anywhere', wordBreak: 'break-all' }}>
              Tối đa bằng số tiền yêu cầu: {claimedFormatted}
            </small>
          </div>

          <div className="form-group" style={{ marginBottom: '1.25rem' }}>
            <label className="form-label" style={{ fontWeight: 600, display: 'block', marginBottom: '0.3rem' }}>
              Ghi chú thẩm định
            </label>
            <textarea
              className="form-control"
              rows={3}
              value={handlerNote}
              onChange={(e) => setHandlerNote(e.target.value)}
              placeholder="VD: Đã đối chiếu hóa đơn sửa chữa và trừ khấu trừ..."
              style={{ width: '100%', padding: '0.5rem' }}
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setApproveModal(false)} disabled={busy}>
              Hủy
            </button>
            <button type="submit" className="btn btn-success" disabled={busy} style={{ backgroundColor: '#059669', borderColor: '#059669', color: '#fff' }}>
              {busy ? 'Đang duyệt...' : 'Xác nhận duyệt'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Modal Từ chối */}
      <Modal
        open={rejectModal}
        onClose={() => setRejectModal(false)}
        title="Từ chối yêu cầu bồi thường"
      >
        <form onSubmit={handleRejectSubmit}>
          <div className="form-group" style={{ marginBottom: '1.25rem' }}>
            <label className="form-label" style={{ fontWeight: 600, display: 'block', marginBottom: '0.3rem' }}>
              Lý do từ chối <span style={{ color: 'red' }}>*</span>
            </label>
            <textarea
              className="form-control"
              rows={4}
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              placeholder="Nêu rõ lý do từ chối (điều khoản loại trừ, sự kiện không thuộc phạm vi...)"
              required
              style={{ width: '100%', padding: '0.5rem' }}
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setRejectModal(false)} disabled={busy}>
              Hủy
            </button>
            <button type="submit" className="btn btn-danger" disabled={busy}>
              {busy ? 'Đang xử lý...' : 'Xác nhận từ chối'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Modal Bổ sung ảnh */}
      <Modal
        open={addPhotosModal}
        onClose={() => setAddPhotosModal(false)}
        title="Bổ sung ảnh bằng chứng"
      >
        <form onSubmit={handleAddPhotosSubmit}>
          <div className="form-group" style={{ marginBottom: '1.25rem' }}>
            <label className="form-label" style={{ fontWeight: 600, display: 'block', marginBottom: '0.3rem' }}>
              Chọn ảnh bổ sung (Tối đa {10 - (claim.photos ? claim.photos.length : 0)} ảnh nữa)
            </label>
            <input
              ref={addPhotosInputRef}
              type="file"
              multiple
              accept="image/jpeg,image/png,image/webp"
              onChange={(e) => {
                if (e.target.files) {
                  setMorePhotos(Array.from(e.target.files));
                }
              }}
              style={{ width: '100%' }}
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setAddPhotosModal(false)} disabled={busy}>
              Hủy
            </button>
            <button type="submit" className="btn btn-primary" disabled={busy || morePhotos.length === 0}>
              {busy ? 'Đang tải lên...' : 'Tải ảnh lên'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}

export default ClaimDetailPage;

