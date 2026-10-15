import React, { useState } from 'react';
import { QrCode, Building2, Copy, Check } from 'lucide-react';
import { Modal } from './Modal';
import { useToast } from '../context/ToastContext';
import { formatMoney } from '../utils/format';

/**
 * Modal thanh toán (SRS FR-PAY-01): hiện số tiền, STK thụ hưởng,
 * nội dung chuyển khoản và QR. onConfirmed() khi khách bấm "Tôi đã chuyển khoản".
 */
export function PaymentModal({ open, onClose, policy, onConfirmed }) {
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState('');

  const amount = policy?.totalPremium ?? 0;
  const bank = {
    bankName: import.meta.env.VITE_BANK_NAME || 'Vietcombank',
    accountNo: import.meta.env.VITE_BANK_ACCOUNT || '0123456789',
    accountName: import.meta.env.VITE_BANK_HOLDER || 'CONG TY BAO HIEM INSURTECH',
  };
  const reference = policy?.policyNumber || '';

  const copy = async (text, key) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(key);
      setTimeout(() => setCopied(''), 1500);
    } catch {
      toast.error('Không thể sao chép. Vui lòng ghi lại thủ công.');
    }
  };

  const handleConfirm = async () => {
    setBusy(true);
    try {
      await onConfirmed();
    } finally {
      setBusy(false);
    }
  };

  const Row = ({ k, v, copyKey }) => (
    <div className="bank-line">
      <span className="k">{k}</span>
      <span style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
        <span className="v">{v}</span>
        <button
          className="btn btn-ghost btn-icon btn-sm"
          onClick={() => copy(v, copyKey)}
          aria-label={`Sao chép ${k}`}
          title="Sao chép"
        >
          {copied === copyKey ? <Check size={14} color="#1663d9" /> : <Copy size={14} />}
        </button>
      </span>
    </div>
  );

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Thanh toán phí bảo hiểm"
      footer={
        <>
          <button className="btn btn-secondary" onClick={onClose} disabled={busy}>Để sau</button>
          <button className="btn btn-primary" onClick={handleConfirm} disabled={busy}>
            {busy ? <span className="spinner" /> : null}
            Tôi đã chuyển khoản
          </button>
        </>
      }
    >
      <div style={{ textAlign: 'center', marginBottom: '1.25rem' }}>
        <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '0.25rem' }}>
          Số tiền cần thanh toán — HĐ {policy?.policyNumber}
        </div>
        <div style={{ fontSize: '2rem', fontWeight: 800, color: '#1663d9' }}>{formatMoney(amount)}</div>
      </div>

      <div className="qr-box" style={{ marginBottom: '1.25rem' }}>
        <div>
          <QrCode size={44} style={{ margin: '0 auto 0.5rem' }} />
          <div>Quét mã để thanh toán<br />{formatMoney(amount)}</div>
        </div>
      </div>
      <p className="form-hint" style={{ textAlign: 'center', marginBottom: '1.25rem' }}>
        Mã QR demo — khi vận hành thực tế sẽ hiển thị mã VietQR chính xác theo số tiền.
      </p>

      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem', color: 'var(--text-secondary)', fontSize: '0.85rem', fontWeight: 700 }}>
        <Building2 size={16} /> Chuyển khoản ngân hàng
      </div>
      <div style={{ background: 'rgba(255,255,255,0.02)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-md)', padding: '0.5rem 1rem' }}>
        <Row k="Ngân hàng" v={bank.bankName} copyKey="bank" />
        <Row k="Số tài khoản" v={bank.accountNo} copyKey="acc" />
        <Row k="Chủ tài khoản" v={bank.accountName} copyKey="holder" />
        <Row k="Nội dung CK" v={reference} copyKey="ref" />
      </div>
      <p className="form-hint" style={{ marginTop: '0.75rem' }}>
        Vui lòng ghi đúng nội dung chuyển khoản là <strong style={{ color: 'var(--text-primary)' }}>{reference}</strong> để
        hệ thống đối soát tự động. Sau khi chuyển khoản, bấm “Tôi đã chuyển khoản” để kích hoạt hợp đồng.
      </p>
    </Modal>
  );
}
