import React, { useState } from 'react';
import { X, ShieldAlert, Sparkles, Send, CheckCircle2 } from 'lucide-react';
import { endorsementService } from '../services/api';
import { useToast } from '../context/ToastContext';

export const EndorsementModal = ({ policy, isOpen, onClose, onSuccess }) => {
  const { showToast } = useToast();
  const [submitting, setSubmitting] = useState(false);

  const [form, setForm] = useState({
    endorsementReason: '',
    endorsementType: 'GENERAL',
    actor: 'Chuyên viên QLRR',
    insuredName: policy?.insured?.name || '',
    insuredEmail: policy?.insured?.email || '',
    insuredPhone: policy?.insured?.phone || '',
    insuredAddress: policy?.insured?.address || '',
  });

  if (!isOpen || !policy) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!form.endorsementReason.trim()) {
      showToast('Vui lòng nhập lý do sửa đổi bổ sung (Endorsement Reason)', 'warning');
      return;
    }

    try {
      setSubmitting(true);
      const payload = {
        endorsementReason: form.endorsementReason.trim(),
        endorsementType: form.endorsementType,
        actor: form.actor.trim() || 'System',
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
      showToast(`Tạo Endorsement thành công! Hợp đồng đã nâng lên version v${res.data?.version || (policy.version + 1)}`, 'success');
      onSuccess?.();
      onClose();
    } catch (err) {
      showToast(err.message || 'Không thể tạo Endorsement', 'error');
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
          maxWidth: '650px',
          maxHeight: '90vh',
          overflowY: 'auto',
          borderRadius: 'var(--radius-xl)',
          padding: '1.75rem',
          border: '1px solid rgba(59, 130, 246, 0.3)',
          boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.5), 0 8px 10px -6px rgba(0, 0, 0, 0.5)',
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
                Tạo Sửa đổi Bổ sung (Endorsement)
              </h3>
            </div>
            <p style={{ margin: 0, fontSize: '0.825rem', color: 'var(--text-muted)' }}>
              Tạo phiên bản mới <b>v{(policy.version || 1) + 1}</b> cho hợp đồng <b>{policy.policyNumber}</b> (Snapshot cũ được lưu trữ toàn vẹn)
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

        {/* Warning Badge */}
        <div
          style={{
            background: 'rgba(59, 130, 246, 0.1)',
            border: '1px solid rgba(59, 130, 246, 0.25)',
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
          <CheckCircle2 size={18} color="#3b82f6" style={{ flexShrink: 0 }} />
          <span>
            Hệ thống sẽ tự động lưu trữ bản chụp (snapshot) của phiên bản hiện tại trước khi cập nhật, đảm bảo tính bất biến của dữ liệu lịch sử bảo hiểm.
          </span>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {/* Reason */}
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label" style={{ fontWeight: 600 }}>
              Lý do sửa đổi bổ sung (Endorsement Reason) *
            </label>
            <textarea
              value={form.endorsementReason}
              onChange={(e) => setForm((p) => ({ ...p, endorsementReason: e.target.value }))}
              placeholder="Ví dụ: Cập nhật địa chỉ kinh doanh mới và thay đổi thông tin liên hệ đại diện..."
              required
              rows={3}
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
                <option value="GENERAL">Chung (General)</option>
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

          <div style={{ marginTop: '0.5rem', paddingTop: '0.75rem', borderTop: '1px dashed var(--border-subtle)' }}>
            <h4 style={{ fontSize: '0.9rem', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '0.75rem' }}>
              Cập nhật thông tin Bên mua bảo hiểm (Insured Info)
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
                <label className="form-label" style={{ fontSize: '0.8rem' }}>Email</label>
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
              <label className="form-label" style={{ fontSize: '0.8rem' }}>Địa chỉ</label>
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
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? (
                <>
                  <div className="spinner" />
                  <span>Đang xử lý Endorsement...</span>
                </>
              ) : (
                <>
                  <Send size={16} />
                  <span>Xác nhận & Nâng Version</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
