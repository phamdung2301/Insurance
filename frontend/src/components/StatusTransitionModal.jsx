import React, { useState, useEffect } from 'react';
import {
  X,
  ArrowRight,
  ShieldCheck,
  AlertTriangle,
  Calendar,
  User,
  Send,
  CheckCircle2,
  AlertCircle,
  HelpCircle,
} from 'lucide-react';
import { policyService } from '../services/api';
import { useToast } from '../context/ToastContext';

export const StatusTransitionModal = ({
  policy,
  targetStatus,
  isOpen,
  onClose,
  onSuccess,
}) => {
  const { showToast } = useToast();
  const [submitting, setSubmitting] = useState(false);
  const [reason, setReason] = useState('');
  const [actor, setActor] = useState('Chuyên viên QLRR');
  const [paymentDueDate, setPaymentDueDate] = useState('');

  // Default payment due date to 7 days from now for BOUND
  useEffect(() => {
    if (isOpen) {
      setReason('');
      setActor('Chuyên viên QLRR');
      if (targetStatus === 'BOUND') {
        const nextWeek = new Date();
        nextWeek.setDate(nextWeek.getDate() + 7);
        setPaymentDueDate(nextWeek.toISOString().split('T')[0]);
      } else {
        setPaymentDueDate('');
      }
    }
  }, [isOpen, targetStatus]);

  if (!isOpen || !policy || !targetStatus) return null;

  // Validation check for QUOTED
  const hasLocations = policy.locations && policy.locations.length > 0;
  const allLocationsHaveCoverages =
    hasLocations &&
    policy.locations.every((loc) => loc.coverages && loc.coverages.length > 0);
  const quotedValid = targetStatus !== 'QUOTED' || allLocationsHaveCoverages;

  const getStatusLabel = (status) => {
    switch (status) {
      case 'DRAFT':
        return 'Bản nháp (DRAFT)';
      case 'QUOTED':
        return 'Đã báo giá (QUOTED)';
      case 'BOUND':
        return 'Đã ràng buộc (BOUND)';
      case 'ACTIVE':
        return 'Đang hiệu lực (ACTIVE)';
      case 'CANCELLED':
        return 'Đã hủy bỏ (CANCELLED)';
      case 'EXPIRED':
        return 'Đã hết hạn (EXPIRED)';
      default:
        return status;
    }
  };

  const getStatusBadgeClass = (status) => {
    switch (status) {
      case 'ACTIVE':
        return 'badge-success';
      case 'QUOTED':
        return 'badge-info';
      case 'BOUND':
        return 'badge-primary';
      case 'DRAFT':
        return 'badge-warning';
      case 'CANCELLED':
        return 'badge-danger';
      case 'EXPIRED':
      default:
        return 'badge-secondary';
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (targetStatus === 'QUOTED' && !quotedValid) {
      showToast(
        'Không thể chuyển sang QUOTED: Cần có ít nhất 1 địa điểm và mỗi địa điểm phải có ít nhất 1 gói bảo hiểm!',
        'error'
      );
      return;
    }

    try {
      setSubmitting(true);
      const payload = {
        targetStatus,
        toStatus: targetStatus,
        reason: reason.trim() || `Chuyển trạng thái sang ${targetStatus}`,
        paymentDueDate:
          targetStatus === 'BOUND' && paymentDueDate
            ? `${paymentDueDate}T23:59:59Z`
            : undefined,
      };

      const res = await policyService.transitionStatus(policy.policyNumber, payload);
      showToast(
        `Chuyển trạng thái hợp đồng ${policy.policyNumber} sang ${targetStatus} thành công!`,
        'success'
      );
      onSuccess?.(res.data);
      onClose();
    } catch (err) {
      showToast(err.message || `Không thể chuyển trạng thái sang ${targetStatus}`, 'error');
    } finally {
      setSubmitting(false);
    }
  };

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
          maxWidth: '560px',
          borderRadius: 'var(--radius-xl)',
          padding: '1.75rem',
          border: '1px solid rgba(99, 102, 241, 0.35)',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.7)',
        }}
      >
        {/* Header */}
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-start',
            marginBottom: '1.25rem',
            paddingBottom: '1rem',
            borderBottom: '1px solid var(--border-subtle)',
          }}
        >
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.25rem' }}>
              <ShieldCheck size={20} color="var(--primary)" />
              <h3 style={{ margin: 0, fontSize: '1.2rem', fontWeight: 700 }}>
                Xác nhận Chuyển trạng thái Hợp đồng
              </h3>
            </div>
            <p style={{ margin: 0, fontSize: '0.825rem', color: 'var(--text-muted)' }}>
              Mã số hợp đồng:{' '}
              <b style={{ color: 'var(--primary)', fontFamily: 'var(--font-mono)' }}>
                {policy.policyNumber}
              </b>
            </p>
          </div>
          <button
            onClick={onClose}
            className="btn btn-secondary"
            style={{ padding: '0.4rem', borderRadius: '50%' }}
            disabled={submitting}
          >
            <X size={18} />
          </button>
        </div>

        {/* Transition Visualizer */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: '1.25rem',
            background: 'rgba(15, 23, 42, 0.6)',
            padding: '1.25rem',
            borderRadius: 'var(--radius-lg)',
            border: '1px solid var(--border-subtle)',
            marginBottom: '1.25rem',
          }}
        >
          <div style={{ textAlign: 'center' }}>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginBottom: '0.35rem' }}>
              Hiện tại
            </div>
            <span className={`badge ${getStatusBadgeClass(policy.status)}`} style={{ fontSize: '0.85rem' }}>
              {policy.status}
            </span>
          </div>

          <div
            style={{
              width: '36px',
              height: '36px',
              borderRadius: '50%',
              background: 'rgba(99, 102, 241, 0.15)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'var(--primary)',
            }}
          >
            <ArrowRight size={18} />
          </div>

          <div style={{ textAlign: 'center' }}>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginBottom: '0.35rem' }}>
              Mục tiêu
            </div>
            <span className={`badge ${getStatusBadgeClass(targetStatus)}`} style={{ fontSize: '0.85rem' }}>
              {targetStatus}
            </span>
          </div>
        </div>

        {/* QUOTED Constraint Warning */}
        {targetStatus === 'QUOTED' && !quotedValid && (
          <div
            style={{
              background: 'rgba(239, 68, 68, 0.1)',
              border: '1px solid rgba(239, 68, 68, 0.3)',
              borderRadius: 'var(--radius-md)',
              padding: '0.85rem 1rem',
              marginBottom: '1.25rem',
              fontSize: '0.825rem',
              color: '#f87171',
              display: 'flex',
              alignItems: 'flex-start',
              gap: '0.6rem',
            }}
          >
            <AlertCircle size={20} color="#ef4444" style={{ flexShrink: 0, marginTop: '2px' }} />
            <div>
              <b>Quy tắc chuyển sang QUOTED chưa thỏa:</b>
              <div style={{ marginTop: '0.25rem' }}>
                Hợp đồng phải có tối thiểu 1 địa điểm bảo hiểm và mỗi địa điểm phải có ít nhất 1 phạm vi bảo hiểm (Coverages). Hiện tại:{' '}
                <b>{policy.locations?.length || 0} địa điểm</b>. Vui lòng thêm địa điểm và gói bảo hiểm trước khi phát hành báo giá.
              </div>
            </div>
          </div>
        )}

        {/* BOUND Note */}
        {targetStatus === 'BOUND' && (
          <div
            style={{
              background: 'rgba(6, 182, 212, 0.1)',
              border: '1px solid rgba(6, 182, 212, 0.25)',
              borderRadius: 'var(--radius-md)',
              padding: '0.75rem 1rem',
              marginBottom: '1.25rem',
              fontSize: '0.8rem',
              color: 'var(--text-secondary)',
              display: 'flex',
              alignItems: 'center',
              gap: '0.6rem',
            }}
          >
            <CheckCircle2 size={18} color="#06b6d4" style={{ flexShrink: 0 }} />
            <span>
              Chuyển sang <b>BOUND</b> sẽ ghi nhận ngày ràng buộc (boundDate = Hiện tại) và tạo thời hạn thanh toán phí bảo hiểm.
            </span>
          </div>
        )}

        {/* ACTIVE Note */}
        {targetStatus === 'ACTIVE' && (
          <div
            style={{
              background: 'rgba(16, 185, 129, 0.1)',
              border: '1px solid rgba(16, 185, 129, 0.25)',
              borderRadius: 'var(--radius-md)',
              padding: '0.75rem 1rem',
              marginBottom: '1.25rem',
              fontSize: '0.8rem',
              color: 'var(--text-secondary)',
              display: 'flex',
              alignItems: 'center',
              gap: '0.6rem',
            }}
          >
            <CheckCircle2 size={18} color="#10b981" style={{ flexShrink: 0 }} />
            <span>
              Kích hoạt <b>ACTIVE</b> sẽ khởi động hiệu lực bảo hiểm và thiết lập thời hạn kết thúc (+365 ngày nếu chưa có).
            </span>
          </div>
        )}

        {/* Form */}
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {/* Reason */}
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label">Lý do / Căn cứ chuyển trạng thái *</label>
            <textarea
              rows={2}
              className="form-control"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder={`Nhập lý do chuyển trạng thái sang ${targetStatus}...`}
              required
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: targetStatus === 'BOUND' ? '1fr 1fr' : '1fr', gap: '1rem' }}>
            {/* Actor */}
            <div className="form-group" style={{ marginBottom: 0 }}>
              <label className="form-label">Người thực hiện (Actor)</label>
              <input
                type="text"
                className="form-control"
                value={actor}
                onChange={(e) => setActor(e.target.value)}
                placeholder="Tên chuyên viên phê duyệt"
              />
            </div>

            {/* Payment Due Date for BOUND */}
            {targetStatus === 'BOUND' && (
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">Hạn thanh toán phí (Due Date)</label>
                <input
                  type="date"
                  className="form-control"
                  value={paymentDueDate}
                  onChange={(e) => setPaymentDueDate(e.target.value)}
                  required
                />
              </div>
            )}
          </div>

          {/* Actions */}
          <div
            style={{
              display: 'flex',
              justifyContent: 'flex-end',
              gap: '0.75rem',
              marginTop: '1rem',
              paddingTop: '1rem',
              borderTop: '1px solid var(--border-subtle)',
            }}
          >
            <button
              type="button"
              onClick={onClose}
              className="btn btn-secondary"
              disabled={submitting}
            >
              Hủy
            </button>
            <button
              type="submit"
              className="btn btn-primary"
              disabled={submitting || (targetStatus === 'QUOTED' && !quotedValid)}
            >
              {submitting ? (
                <>
                  <div className="spinner" />
                  <span>Đang xử lý chuyển đổi...</span>
                </>
              ) : (
                <>
                  <Send size={16} />
                  <span>Xác nhận chuyển sang {targetStatus}</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
