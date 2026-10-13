import React, { useState } from 'react';
import {
  X,
  QrCode,
  Building,
  CreditCard,
  Copy,
  CheckCircle2,
  Clock,
  ShieldCheck,
  Zap,
  UserCheck,
  AlertCircle
} from 'lucide-react';
import { policyService } from '../services/api';
import { useToast } from '../context/ToastContext';
import { useAuth } from '../context/AuthContext';

export const PaymentModal = ({ policy, isOpen, onClose, onSuccess }) => {
  const { showToast } = useToast();
  const { user } = useAuth();
  const [submitting, setSubmitting] = useState(false);
  const [copiedField, setCopiedField] = useState('');

  if (!isOpen || !policy) return null;

  const isAdmin = user?.roles?.includes('ROLE_ADMIN');

  const bankName = 'Ngân hàng TMCP Quân Đội (MBBank)';
  const accountNumber = '999988886666';
  const accountHolder = 'CTY CP BAO HIEM CONG NGHE INSURTECH';
  const amount = policy.totalPremium || 0;
  const transferMemo = `THANH TOAN ${policy.policyNumber}`;

  // VietQR Quick Image Generator URL
  const qrUrl = `https://img.vietqr.io/image/MB-${accountNumber}-compact2.png?amount=${Math.round(amount)}&addInfo=${encodeURIComponent(transferMemo)}&accountName=${encodeURIComponent(accountHolder)}`;

  const handleCopy = (text, fieldName) => {
    navigator.clipboard.writeText(text);
    setCopiedField(fieldName);
    showToast(`Đã sao chép ${fieldName}`, 'success');
    setTimeout(() => setCopiedField(''), 2000);
  };

  // Channel 1: Customer Simulated Payment (FR-PAY-02)
  const handleCustomerPayment = async () => {
    try {
      setSubmitting(true);
      await policyService.transitionStatus(policy.policyNumber, {
        toStatus: 'ACTIVE',
        reason: 'Khách hàng hoàn tất thanh toán trực tuyến qua cổng QR Banking (Simulated Payment Channel 1)',
        actor: user?.email || policy.insured?.email || 'Customer (Online Payment)',
      });

      showToast(`Thanh toán thành công! Hợp đồng ${policy.policyNumber} đã có hiệu lực (ACTIVE) 365 ngày.`, 'success');
      if (onSuccess) onSuccess();
      onClose();
    } catch (err) {
      showToast(err.message || 'Lỗi khi kích hoạt thanh toán', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  // Channel 2: Admin Offline Counter Activation (FR-PAY-03)
  const handleAdminOfflineActivation = async () => {
    try {
      setSubmitting(true);
      await policyService.transitionStatus(policy.policyNumber, {
        toStatus: 'ACTIVE',
        reason: 'Kế toán/Quản trị viên kích hoạt trực tiếp tại quầy sau khi đối soát sao kê tiền về (Admin Offline Activation Channel 2)',
        actor: user?.email || 'Admin/Accounting Staff',
      });

      showToast(`Kích hoạt tại quầy thành công! Hợp đồng ${policy.policyNumber} đã chuyển sang ACTIVE.`, 'success');
      if (onSuccess) onSuccess();
      onClose();
    } catch (err) {
      showToast(err.message || 'Lỗi khi kích hoạt tại quầy', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(0, 0, 0, 0.75)',
        backdropFilter: 'blur(6px)',
        zIndex: 1100,
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
          maxWidth: '680px',
          maxHeight: '92vh',
          overflowY: 'auto',
          padding: 0,
          borderRadius: 'var(--radius-lg)',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.7)',
          border: '1px solid rgba(99, 102, 241, 0.3)',
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
            background: 'rgba(15, 23, 42, 0.75)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <div
              style={{
                width: '38px',
                height: '38px',
                borderRadius: '50%',
                background: 'rgba(16, 185, 129, 0.15)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#10b981',
              }}
            >
              <QrCode size={20} />
            </div>
            <div>
              <h2 style={{ fontSize: '1.2rem', margin: 0, fontWeight: 700 }}>
                Hướng dẫn & Thanh toán Hợp đồng
              </h2>
              <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
                Mã HĐ: <b style={{ color: 'var(--text-primary)' }}>{policy.policyNumber}</b> (Trạng thái: BOUND)
              </span>
            </div>
          </div>

          <button
            onClick={onClose}
            style={{
              background: 'none',
              border: 'none',
              color: 'var(--text-muted)',
              cursor: 'pointer',
              padding: '0.4rem',
            }}
          >
            <X size={20} />
          </button>
        </div>

        {/* Modal Body */}
        <div style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          {/* Payment Due Date Warning */}
          <div
            style={{
              padding: '0.75rem 1rem',
              borderRadius: 'var(--radius-md)',
              background: 'rgba(245, 158, 11, 0.1)',
              border: '1px solid rgba(245, 158, 11, 0.3)',
              display: 'flex',
              alignItems: 'center',
              gap: '0.75rem',
              fontSize: '0.825rem',
              color: '#f59e0b',
            }}
          >
            <Clock size={18} style={{ flexShrink: 0 }} />
            <div>
              Hạn chót thanh toán phí: <b>{policy.paymentDueDate ? new Date(policy.paymentDueDate).toLocaleDateString('vi-VN') : '7 ngày kể từ khi ràng buộc'}</b>.
              Sau thời hạn này, quyền giữ phí ưu đãi sẽ tự động hết hạn.
            </div>
          </div>

          {/* QR Code and Bank Account Details Grid */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
              gap: '1.5rem',
              alignItems: 'center',
            }}
          >
            {/* VietQR Display Card */}
            <div
              style={{
                background: '#ffffff',
                padding: '1.25rem',
                borderRadius: 'var(--radius-md)',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.4)',
              }}
            >
              <img
                src={qrUrl}
                alt="VietQR Payment"
                style={{
                  width: '100%',
                  maxWidth: '240px',
                  height: 'auto',
                  borderRadius: '8px',
                }}
              />
              <span style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '0.5rem', textAlign: 'center' }}>
                Quét mã qua ứng dụng ngân hàng bất kỳ để thanh toán nhanh
              </span>
            </div>

            {/* Transfer Details List */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              <div
                style={{
                  padding: '0.75rem',
                  borderRadius: 'var(--radius-sm)',
                  background: 'rgba(255, 255, 255, 0.03)',
                  border: '1px solid var(--border-subtle)',
                }}
              >
                <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>NGÂN HÀNG THỤ HƯỞNG</div>
                <div style={{ fontSize: '0.85rem', fontWeight: 600 }}>{bankName}</div>
              </div>

              <div
                style={{
                  padding: '0.75rem',
                  borderRadius: 'var(--radius-sm)',
                  background: 'rgba(255, 255, 255, 0.03)',
                  border: '1px solid var(--border-subtle)',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}
              >
                <div>
                  <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>SỐ TÀI KHOẢN</div>
                  <div style={{ fontSize: '1.05rem', fontWeight: 700, letterSpacing: '0.05em', color: '#818cf8' }}>
                    {accountNumber}
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => handleCopy(accountNumber, 'Số tài khoản')}
                  className="btn btn-secondary"
                  style={{ padding: '0.35rem 0.6rem', fontSize: '0.75rem' }}
                >
                  <Copy size={13} />
                  <span>{copiedField === 'Số tài khoản' ? 'Đã chép' : 'Sao chép'}</span>
                </button>
              </div>

              <div
                style={{
                  padding: '0.75rem',
                  borderRadius: 'var(--radius-sm)',
                  background: 'rgba(255, 255, 255, 0.03)',
                  border: '1px solid var(--border-subtle)',
                }}
              >
                <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>CHỦ TÀI KHOẢN</div>
                <div style={{ fontSize: '0.85rem', fontWeight: 600 }}>{accountHolder}</div>
              </div>

              <div
                style={{
                  padding: '0.75rem',
                  borderRadius: 'var(--radius-sm)',
                  background: 'rgba(16, 185, 129, 0.06)',
                  border: '1px solid rgba(16, 185, 129, 0.3)',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}
              >
                <div>
                  <div style={{ fontSize: '0.7rem', color: '#10b981' }}>SỐ TIỀN THANH TOÁN</div>
                  <div style={{ fontSize: '1.25rem', fontWeight: 800, color: '#10b981' }}>
                    {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount)}
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => handleCopy(Math.round(amount).toString(), 'Số tiền')}
                  className="btn btn-secondary"
                  style={{ padding: '0.35rem 0.6rem', fontSize: '0.75rem' }}
                >
                  <Copy size={13} />
                  <span>{copiedField === 'Số tiền' ? 'Đã chép' : 'Sao chép'}</span>
                </button>
              </div>

              <div
                style={{
                  padding: '0.75rem',
                  borderRadius: 'var(--radius-sm)',
                  background: 'rgba(255, 255, 255, 0.03)',
                  border: '1px solid var(--border-subtle)',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}
              >
                <div>
                  <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>NỘI DUNG CHUYỂN KHOẢN</div>
                  <div style={{ fontSize: '0.9rem', fontWeight: 700, color: '#f59e0b' }}>
                    {transferMemo}
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => handleCopy(transferMemo, 'Nội dung chuyển khoản')}
                  className="btn btn-secondary"
                  style={{ padding: '0.35rem 0.6rem', fontSize: '0.75rem' }}
                >
                  <Copy size={13} />
                  <span>{copiedField === 'Nội dung chuyển khoản' ? 'Đã chép' : 'Sao chép'}</span>
                </button>
              </div>
            </div>
          </div>

          {/* Action Channels (FR-PAY-02 & FR-PAY-03) */}
          <div
            style={{
              paddingTop: '1rem',
              borderTop: '1px solid var(--border-subtle)',
              display: 'flex',
              flexDirection: 'column',
              gap: '0.75rem',
            }}
          >
            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              Lựa chọn kênh xác nhận thanh toán (2-Channel Activation System):
            </div>

            <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', justifyContent: 'flex-end' }}>
              <button
                type="button"
                onClick={onClose}
                className="btn btn-secondary"
                disabled={submitting}
              >
                Đóng
              </button>

              {/* Channel 1: Customer Simulated Online Payment (FR-PAY-02) */}
              <button
                type="button"
                onClick={handleCustomerPayment}
                disabled={submitting}
                className="btn btn-primary"
                style={{
                  background: 'linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%)',
                  boxShadow: '0 4px 14px rgba(59, 130, 246, 0.4)',
                }}
              >
                <Zap size={16} />
                <span>{submitting ? 'Đang kích hoạt...' : 'Kênh 1: Khách hàng Xác nhận Chuyển khoản'}</span>
              </button>

              {/* Channel 2: Admin Offline Counter Activation (FR-PAY-03) */}
              <button
                type="button"
                onClick={handleAdminOfflineActivation}
                disabled={submitting}
                className="btn btn-primary"
                style={{
                  background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
                  boxShadow: '0 4px 14px rgba(16, 185, 129, 0.4)',
                }}
              >
                <ShieldCheck size={16} />
                <span>{submitting ? 'Đang kích hoạt...' : 'Kênh 2: Kế toán Kích hoạt tại quầy'}</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
