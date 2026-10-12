import React, { useState, useEffect } from 'react';
import { X, ShieldAlert, Sparkles, Send, CheckCircle2, AlertCircle, FileText, Calendar } from 'lucide-react';
import { endorsementService } from '../services/api';
import { useToast } from '../context/ToastContext';

export const EndorsementModal = ({ policy, isOpen, onClose, onSuccess }) => {
  const { showToast } = useToast();
  const [submitting, setSubmitting] = useState(false);

  const [form, setForm] = useState({
    reason: '',
    description: '',
    endorsementType: 'GENERAL',
    actor: 'Chuyên viên QLRR',
    effectiveDate: new Date().toISOString().split('T')[0],
    insuredName: '',
    insuredEmail: '',
    insuredPhone: '',
    insuredAddress: '',
  });

  useEffect(() => {
    if (policy) {
      setForm({
        reason: '',
        description: '',
        endorsementType: 'GENERAL',
        actor: 'Chuyên viên QLRR',
        effectiveDate: new Date().toISOString().split('T')[0],
        insuredName: policy?.insured?.name || '',
        insuredEmail: policy?.insured?.email || '',
        insuredPhone: policy?.insured?.phone || '',
        insuredAddress: policy?.insured?.address || '',
      });
    }
  }, [policy, isOpen]);

  if (!isOpen || !policy) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!form.reason.trim()) {
      showToast('Vui lòng nhập lý do sửa đổi bổ sung (Endorsement Reason)', 'warning');
      return;
    }

    try {
      setSubmitting(true);
      const payload = {
        reason: form.reason.trim(),
        endorsementReason: form.reason.trim(), // Dual support for reason / endorsementReason
        description: form.description?.trim() || `Phụ lục ${form.endorsementType}`,
        actor: form.actor.trim() || 'Chuyên viên QLRR',
        effectiveDate: form.effectiveDate ? `${form.effectiveDate}T00:00:00Z` : undefined,
        insured: {
          insuredId: policy.insured?.insuredId,
          name: form.insuredName.trim(),
          email: form.insuredEmail.trim(),
          phone: form.insuredPhone.trim(),
          address: form.insuredAddress.trim(),
          type: policy.insured?.type || 'BUSINESS',
        },
      };

      const res = await endorsementService.createEndorsement(policy.policyNumber, payload);
      const newVersion = res.data?.version || (policy.version ? policy.version + 1 : 2);
      showToast(`Tạo Endorsement thành công! Hợp đồng đã nâng lên phiên bản v${newVersion}`, 'success');
      onSuccess?.(res.data);
      onClose();
    } catch (err) {
      showToast(err.message || 'Không thể tạo Endorsement. Hợp đồng phải ở trạng thái ACTIVE.', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const nextVersion = (policy.version || 1) + 1;

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
          maxWidth: '680px',
          maxHeight: '90vh',
          overflowY: 'auto',
          borderRadius: 'var(--radius-xl)',
          padding: '1.75rem',
          border: '1px solid rgba(99, 102, 241, 0.35)',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.7)',
        }}
      >
        {/* Modal Header */}
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
              <Sparkles size={20} color="var(--primary)" />
              <h3 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 700 }}>
                Tạo Sửa đổi Bổ sung (Endorsement P08)
              </h3>
            </div>
            <p style={{ margin: 0, fontSize: '0.825rem', color: 'var(--text-muted)' }}>
              Nâng cấp hợp đồng <b style={{ color: 'var(--primary)', fontFamily: 'var(--font-mono)' }}>{policy.policyNumber}</b> từ <b>v{policy.version || 1}</b> lên <b style={{ color: '#10b981' }}>v{nextVersion}</b>
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

        {/* Audit preservation banner */}
        <div
          style={{
            background: 'rgba(99, 102, 241, 0.1)',
            border: '1px solid rgba(99, 102, 241, 0.25)',
            borderRadius: 'var(--radius-md)',
            padding: '0.75rem 1rem',
            marginBottom: '1.25rem',
            fontSize: '0.8rem',
            color: 'var(--text-secondary)',
            display: 'flex',
            alignItems: 'flex-start',
            gap: '0.6rem',
          }}
        >
          <CheckCircle2 size={18} color="#6366f1" style={{ flexShrink: 0, marginTop: '2px' }} />
          <div>
            <b>Bảo toàn lịch sử:</b> Hệ thống sẽ tự động lưu trữ bản chụp (snapshot) của phiên bản hiện tại v{policy.version || 1} vào kho lưu trữ bất biến và ghi nhận sổ cái giao dịch audit ledger trước khi áp dụng thay đổi.
          </div>
        </div>

        {/* Warning if policy is not ACTIVE */}
        {policy.status !== 'ACTIVE' && (
          <div
            style={{
              background: 'rgba(239, 68, 68, 0.1)',
              border: '1px solid rgba(239, 68, 68, 0.3)',
              borderRadius: 'var(--radius-md)',
              padding: '0.75rem 1rem',
              marginBottom: '1.25rem',
              fontSize: '0.8rem',
              color: '#f87171',
              display: 'flex',
              alignItems: 'center',
              gap: '0.6rem',
            }}
          >
            <AlertCircle size={18} color="#ef4444" style={{ flexShrink: 0 }} />
            <span>
              Lưu ý: Hợp đồng đang ở trạng thái <b>{policy.status}</b>. Quy tắc nghiệp vụ quy định chỉ các hợp đồng đã có hiệu lực (<b>ACTIVE</b>) mới có thể thực hiện Endorsement.
            </span>
          </div>
        )}

        {/* Form */}
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {/* Endorsement Reason */}
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label" style={{ fontWeight: 600 }}>
              Lý do sửa đổi bổ sung (Endorsement Reason) *
            </label>
            <textarea
              value={form.reason}
              onChange={(e) => setForm((p) => ({ ...p, reason: e.target.value }))}
              placeholder="Ví dụ: Cập nhật địa chỉ kinh doanh mới của bên mua theo giấy phép đăng ký mới..."
              required
              rows={2}
              className="form-control"
              style={{ resize: 'vertical' }}
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            {/* Endorsement Type */}
            <div className="form-group" style={{ marginBottom: 0 }}>
              <label className="form-label">Loại Endorsement</label>
              <select
                value={form.endorsementType}
                onChange={(e) => setForm((p) => ({ ...p, endorsementType: e.target.value }))}
                className="form-control"
              >
                <option value="GENERAL">Chung (General Endorsement)</option>
                <option value="FINANCIAL">Tài chính / Phí bảo hiểm (Financial)</option>
                <option value="ADMINISTRATIVE">Hành chính / Thông tin (Administrative)</option>
                <option value="ADDRESS_CHANGE">Thay đổi địa chỉ (Address Change)</option>
                <option value="COVERAGE_ADJUSTMENT">Điều chỉnh phạm vi bảo hiểm</option>
              </select>
            </div>

            {/* Actor */}
            <div className="form-group" style={{ marginBottom: 0 }}>
              <label className="form-label">Người thực hiện (Actor)</label>
              <input
                type="text"
                value={form.actor}
                onChange={(e) => setForm((p) => ({ ...p, actor: e.target.value }))}
                placeholder="Tên chuyên viên / Underwriter"
                className="form-control"
              />
            </div>
          </div>

          {/* Description & Effective Date */}
          <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 0.8fr', gap: '1rem' }}>
            <div className="form-group" style={{ marginBottom: 0 }}>
              <label className="form-label">Ghi chú chi tiết phụ lục</label>
              <input
                type="text"
                value={form.description}
                onChange={(e) => setForm((p) => ({ ...p, description: e.target.value }))}
                placeholder="Ghi chú bổ sung hoặc số công văn phụ lục..."
                className="form-control"
              />
            </div>

            <div className="form-group" style={{ marginBottom: 0 }}>
              <label className="form-label">Ngày hiệu lực phụ lục</label>
              <input
                type="date"
                value={form.effectiveDate}
                onChange={(e) => setForm((p) => ({ ...p, effectiveDate: e.target.value }))}
                className="form-control"
              />
            </div>
          </div>

          {/* Insured Section */}
          <div style={{ marginTop: '0.5rem', paddingTop: '0.85rem', borderTop: '1px dashed var(--border-subtle)' }}>
            <h4 style={{ fontSize: '0.875rem', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '0.75rem', display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
              <FileText size={15} color="var(--primary)" />
              <span>Thông tin Bên mua bảo hiểm cần sửa đổi (Insured)</span>
            </h4>

            <div className="form-group" style={{ marginBottom: '0.75rem' }}>
              <label className="form-label" style={{ fontSize: '0.8rem' }}>Tên bên mua bảo hiểm</label>
              <input
                type="text"
                value={form.insuredName}
                onChange={(e) => setForm((p) => ({ ...p, insuredName: e.target.value }))}
                className="form-control"
              />
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '0.75rem' }}>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label" style={{ fontSize: '0.8rem' }}>Email liên hệ</label>
                <input
                  type="email"
                  value={form.insuredEmail}
                  onChange={(e) => setForm((p) => ({ ...p, insuredEmail: e.target.value }))}
                  className="form-control"
                />
              </div>

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label" style={{ fontSize: '0.8rem' }}>Số điện thoại</label>
                <input
                  type="text"
                  value={form.insuredPhone}
                  onChange={(e) => setForm((p) => ({ ...p, insuredPhone: e.target.value }))}
                  className="form-control"
                />
              </div>
            </div>

            <div className="form-group" style={{ marginBottom: 0 }}>
              <label className="form-label" style={{ fontSize: '0.8rem' }}>Địa chỉ trụ sở / cư trú</label>
              <input
                type="text"
                value={form.insuredAddress}
                onChange={(e) => setForm((p) => ({ ...p, insuredAddress: e.target.value }))}
                className="form-control"
              />
            </div>
          </div>

          {/* Footer Actions */}
          <div
            style={{
              display: 'flex',
              justifyContent: 'flex-end',
              gap: '0.75rem',
              marginTop: '1.25rem',
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
              disabled={submitting || policy.status !== 'ACTIVE'}
            >
              {submitting ? (
                <>
                  <div className="spinner" />
                  <span>Đang ghi nhận Endorsement...</span>
                </>
              ) : (
                <>
                  <Send size={16} />
                  <span>Xác nhận & Nâng lên v{nextVersion}</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
