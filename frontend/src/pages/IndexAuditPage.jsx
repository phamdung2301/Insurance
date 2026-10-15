import React, { useState, useEffect } from 'react';
import { Database, RefreshCw, AlertTriangle, CheckCircle2, Info } from 'lucide-react';
import { indexAuditService } from '../services/api';
import { BACKEND_TODO_HINT, isBackendMissing } from '../utils/backend';
import { useToast } from '../context/ToastContext';
import { EmptyState } from '../components/ui';
import { formatDateTime } from '../utils/format';

const STATUS_META = {
  ACTIVE_OPTIMIZED: { label: 'Tối ưu', cls: 'badge-success' },
  REDUNDANT: { label: 'Dư thừa', cls: 'badge-warning' },
  MISSING: { label: 'Còn thiếu', cls: 'badge-danger' },
};

export function IndexAuditPage() {
  const toast = useToast();
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(true);
  const [missing, setMissing] = useState(false);
  const [syncing, setSyncing] = useState(false);

  const load = async () => {
    setLoading(true);
    setMissing(false);
    try {
      const res = await indexAuditService.audit();
      setReport(res?.data ?? null);
    } catch (err) {
      if (isBackendMissing(err)) setMissing(true);
      else toast.error(err.message || 'Không tải được báo cáo kiểm tra index.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const handleSync = async () => {
    setSyncing(true);
    try {
      const res = await indexAuditService.sync();
      toast.success(res?.data?.message ?? res?.data ?? 'Đã đồng bộ index.');
      await load();
    } catch (err) {
      toast.error(err.message || 'Đồng bộ index thất bại.');
    } finally {
      setSyncing(false);
    }
  };

  const stats = report ? [
    { label: 'Collections đã kiểm tra', value: report.totalCollectionsAudited ?? 0 },
    { label: 'Tổng số index', value: report.totalIndexes ?? 0 },
    { label: 'Index phức hợp', value: report.totalCompoundIndexes ?? 0 },
    { label: 'Index multikey', value: report.totalMultikeyIndexes ?? 0 },
    { label: 'Index dư thừa', value: report.redundantIndexesDetected ?? 0 },
  ] : [];

  return (
    <div>
      <div className="page-header">
        <h1>Kiểm tra index cơ sở dữ liệu</h1>
        <p>Rà soát chỉ mục MongoDB trên các collection và đồng bộ khi cần.</p>
      </div>

      <div style={{ display: 'flex', gap: '0.75rem', marginBottom: '1.25rem', flexWrap: 'wrap' }}>
        <button className="btn btn-secondary" onClick={load} disabled={loading}>
          <RefreshCw size={16} /> {loading ? 'Đang tải…' : 'Tải lại báo cáo'}
        </button>
        <button className="btn btn-primary" onClick={handleSync} disabled={syncing || missing}>
          {syncing ? <span className="spinner" /> : <Database size={16} />} {syncing ? 'Đang đồng bộ…' : 'Đồng bộ index'}
        </button>
      </div>

      {missing ? (
        <EmptyState
          icon={<Database size={28} />}
          title="Backend chưa cung cấp API kiểm tra index"
          desc={BACKEND_TODO_HINT}
        />
      ) : loading ? (
        <p style={{ color: 'var(--text-muted)' }}>Đang tải báo cáo…</p>
      ) : !report ? (
        <EmptyState icon={<Database size={28} />} title="Không có dữ liệu" desc="Backend không trả về báo cáo." />
      ) : (
        <>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: '0.75rem', marginBottom: '1.25rem' }}>
            {stats.map((s) => (
              <div key={s.label} className="glass-card" style={{ padding: '0.9rem 1.1rem', marginBottom: 0 }}>
                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginBottom: '0.25rem' }}>{s.label}</div>
                <div style={{ fontSize: '1.4rem', fontWeight: 800 }}>{Number(s.value).toLocaleString('vi-VN')}</div>
              </div>
            ))}
          </div>

          {report.auditTimestamp && (
            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '1.25rem' }}>
              Kiểm tra lúc: {formatDateTime(report.auditTimestamp)}
              {report.status && <> · Trạng thái: <strong>{report.status}</strong></>}
            </p>
          )}

          {Array.isArray(report.recommendations) && report.recommendations.length > 0 && (
            <div className="glass-card" style={{ marginBottom: '1.25rem', borderLeft: '4px solid var(--warning, #c98a00)' }}>
              <h3 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.75rem' }}>
                <AlertTriangle size={20} /> Khuyến nghị
              </h3>
              <ul style={{ margin: 0, paddingLeft: '1.25rem', fontSize: '0.9rem' }}>
                {report.recommendations.map((r, i) => <li key={i} style={{ marginBottom: '0.35rem' }}>{r}</li>)}
              </ul>
            </div>
          )}

          {Array.isArray(report.collections) && report.collections.map((c) => (
            <div key={c.collectionName} className="glass-card" style={{ marginBottom: '1.25rem' }}>
              <h3 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.25rem' }}>
                <Database size={18} /> {c.collectionName}
              </h3>
              <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '0.75rem' }}>
                {c.indexCount ?? (c.indexes?.length ?? 0)} index
              </p>
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Tên index</th>
                      <th>Khóa</th>
                      <th>Loại</th>
                      <th>Trạng thái</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(c.indexes ?? []).map((ix, i) => {
                      const meta = STATUS_META[ix.status] ?? { label: ix.status ?? '—', cls: 'badge-info' };
                      const kinds = [
                        ix.isCompound && 'Phức hợp',
                        ix.isMultikeyElemMatch && 'Multikey',
                        ix.unique && 'Unique',
                        ix.sparse && 'Sparse',
                      ].filter(Boolean).join(', ');
                      return (
                        <tr key={ix.name ?? i}>
                          <td style={{ fontFamily: 'monospace', fontSize: '0.82rem' }}>{ix.name ?? '—'}</td>
                          <td style={{ fontFamily: 'monospace', fontSize: '0.82rem' }}>
                            {ix.keyPattern ? Object.entries(ix.keyPattern).map(([k, v]) => `${k}:${v}`).join(', ') : '—'}
                          </td>
                          <td style={{ fontSize: '0.85rem' }}>{kinds || '—'}</td>
                          <td>
                            <span className={`badge ${meta.cls}`} title={ix.purpose ?? ''}>{meta.label}</span>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          ))}

          {(!report.collections || report.collections.length === 0) && (
            <EmptyState icon={<CheckCircle2 size={28} />} title="Không có collection nào" desc="Báo cáo không liệt kê collection." />
          )}
        </>
      )}
    </div>
  );
}
