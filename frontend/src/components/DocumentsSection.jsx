import React, { useState, useEffect } from 'react';
import { Download } from 'lucide-react';
import { documentService, downloadBlob } from '../services/api';
import { paymentApi } from '../services/paymentApi';
import { useToast } from '../context/ToastContext';
import { getUserMessage } from '../utils/messages';
import { formatDateTime } from '../utils/format';

/**
 * Trung tâm tài liệu của một hợp đồng: chứng nhận bảo hiểm (COI), tóm tắt
 * quyền lợi và hóa đơn — tải PDF một chạm, trạng thái loading + thông báo
 * lỗi chuẩn (getUserMessage). Dùng chung cho PolicyDetailPage; kèm danh sách
 * tài liệu hệ thống đã sinh (GET /policies/{n}/documents).
 */
export function DocumentsSection({ policyNumber }) {
  const toast = useToast();
  const [docs, setDocs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(null);

  useEffect(() => {
    (async () => {
      try {
        const res = await documentService.list(policyNumber);
        const d = res?.data;
        setDocs(Array.isArray(d) ? d : d?.documents ?? []);
      } catch {
        setDocs([]);
      } finally {
        setLoading(false);
      }
    })();
  }, [policyNumber]);

  const dl = async (kind, label) => {
    setBusy(kind);
    try {
      let res;
      if (kind === 'cert') res = await documentService.downloadCertificate(policyNumber);
      else if (kind === 'summary') res = await documentService.downloadSummary(policyNumber);
      else res = await paymentApi.downloadInvoice(policyNumber);
      downloadBlob(res?.data ?? res, `${policyNumber}-${label}.pdf`);
      toast.success(`Đã tải ${label}.`);
    } catch (err) {
      toast.error(getUserMessage(err, `Không tải được ${label}.`));
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="glass-card" style={{ marginTop: '1.5rem' }}>
      <h3 style={{ marginBottom: '1rem' }}>Tài liệu hợp đồng</h3>
      <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', marginBottom: docs.length ? '1rem' : 0 }}>
        <button className="btn btn-primary" disabled={busy} onClick={() => dl('cert', 'chung-nhan')}>
          {busy === 'cert' ? <span className="spinner" /> : <Download size={16} />} Chứng nhận bảo hiểm (PDF)
        </button>
        <button className="btn btn-secondary" disabled={busy} onClick={() => dl('summary', 'tom-tat')}>
          {busy === 'summary' ? <span className="spinner" /> : <Download size={16} />} Tóm tắt quyền lợi (PDF)
        </button>
        <button className="btn btn-secondary" disabled={busy} onClick={() => dl('invoice', 'hoa-don')}>
          {busy === 'invoice' ? <span className="spinner" /> : <Download size={16} />} Hóa đơn (PDF)
        </button>
      </div>
      {loading ? (
        <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Đang tải danh sách tài liệu…</p>
      ) : docs.length === 0 ? (
        <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Chưa có tài liệu đính kèm nào.</p>
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr><th>Tài liệu</th><th>Loại</th><th>Phiên bản</th><th>Ngày tạo</th></tr>
            </thead>
            <tbody>
              {docs.map((d, i) => (
                <tr key={d.documentId ?? i}>
                  <td>{d.title ?? d.fileName ?? '—'}</td>
                  <td>{d.documentType ?? '—'}</td>
                  <td>{d.version ?? '—'}</td>
                  <td>{formatDateTime(d.generatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export default DocumentsSection;
