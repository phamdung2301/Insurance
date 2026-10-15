import React, { useState, useEffect } from 'react';
import { Zap, Gauge, Info, Play } from 'lucide-react';
import { benchmarkService } from '../services/api';
import { EmptyState } from '../components/ui';
import { BACKEND_TODO_HINT, isBackendMissing } from '../utils/backend';
import { useToast } from '../context/ToastContext';

function ResultTable({ rows }) {
  if (!rows || rows.length === 0) {
    return <div style={{ color: 'var(--text-muted)', fontSize: '0.88rem', padding: '1rem 0' }}>Chưa có dữ liệu.</div>;
  }
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th>Truy vấn</th>
            <th>Kế hoạch</th>
            <th className="num">Bản ghi quét</th>
            <th className="num">Khóa index</th>
            <th className="num">Trả về</th>
            <th className="num">Thời gian (ms)</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i}>
              <td>{r?.query ?? r?.name ?? `Truy vấn ${i + 1}`}</td>
              <td style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{r?.winningPlan ?? r?.plan ?? '—'}</td>
              <td className="num">{r?.docsExamined ?? '—'}</td>
              <td className="num">{r?.keysExamined ?? '—'}</td>
              <td className="num">{r?.nReturned ?? r?.returned ?? '—'}</td>
              <td className="num">{r?.executionTimeMillis ?? r?.timeMs ?? r?.ms ?? '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function BenchmarkPage() {
  const toast = useToast();

  const [genLoading, setGenLoading] = useState(false);
  const [genMissing, setGenMissing] = useState(false);

  const [runLoading, setRunLoading] = useState(false);
  const [runMissing, setRunMissing] = useState(false);
  // result: { items: [...] } | { before: [...], after: [...] } | null
  const [result, setResult] = useState(null);

  const handleGenerate = async () => {
    setGenLoading(true);
    setGenMissing(false);
    try {
      await benchmarkService.generate50k();
      toast.success('Đã gửi yêu cầu, quá trình chạy nền ở backend.');
    } catch (err) {
      if (isBackendMissing(err)) setGenMissing(true);
      else toast.error(err.message);
    } finally {
      setGenLoading(false);
    }
  };

  const handleRun = async () => {
    setRunLoading(true);
    setRunMissing(false);
    try {
      const res = await benchmarkService.run();
      const d = res?.data ?? res;
      const arr = (x) => (Array.isArray(x) ? x : []);
      if (d && (d.before || d.after)) {
        setResult({ before: arr(d.before), after: arr(d.after) });
      } else {
        setResult({ items: arr(d?.results ?? d?.queries ?? d) });
      }
      toast.success('Đã chạy đo hiệu năng xong.');
    } catch (err) {
      if (isBackendMissing(err)) {
        setRunMissing(true);
        setResult(null);
      } else {
        toast.error(err.message);
      }
    } finally {
      setRunLoading(false);
    }
  };

  return (
    <div>
      <div className="page-header">
        <h1>Kiểm thử hiệu năng</h1>
        <p>Sinh dữ liệu kiểm thử và đo hiệu năng truy vấn của hệ thống.</p>
      </div>

      <div className="glass-card" style={{ marginBottom: '1.25rem' }}>
        <h3 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <Zap size={20} /> Sinh dữ liệu kiểm thử
        </h3>
        <p className="form-hint" style={{ marginBottom: '1rem' }}>
          Tạo 50.000 hợp đồng ngẫu nhiên để kiểm thử hiệu năng. Quá trình chạy nền ở backend,
          bạn có thể tiếp tục công việc khác trong lúc chờ.
        </p>
        <button className="btn btn-primary" onClick={handleGenerate} disabled={genLoading}>
          <Play size={16} /> {genLoading ? 'Đang gửi yêu cầu…' : 'Bắt đầu sinh'}
        </button>
        {genMissing && (
          <div className="alert alert-info" style={{ marginTop: '1rem', marginBottom: 0 }}>
            <Info size={18} style={{ flexShrink: 0, marginTop: '0.1rem' }} />
            <span>{BACKEND_TODO_HINT}</span>
          </div>
        )}
      </div>

      <div className="glass-card">
        <h3 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <Gauge size={20} /> Chạy benchmark
        </h3>
        <p className="form-hint" style={{ marginBottom: '1rem' }}>
          Chạy bộ truy vấn đo hiệu năng và so sánh kết quả trước/sau khi tối ưu.
        </p>
        <button className="btn btn-secondary" onClick={handleRun} disabled={runLoading}>
          <Gauge size={16} /> {runLoading ? 'Đang đo…' : 'Chạy đo hiệu năng'}
        </button>

        <div style={{ marginTop: '1.25rem' }}>
          {runMissing ? (
            <EmptyState
              icon={<Gauge size={28} />}
              title="Backend chưa cung cấp API benchmark"
              desc={BACKEND_TODO_HINT}
            />
          ) : result ? (
            result.before || result.after ? (
              <>
                <h4 style={{ marginBottom: '0.5rem' }}>Trước khi tạo chỉ mục</h4>
                <ResultTable rows={result.before} />
                <h4 style={{ margin: '1.25rem 0 0.5rem' }}>Sau khi tạo chỉ mục</h4>
                <ResultTable rows={result.after} />
              </>
            ) : result.items.length > 0 ? (
              <ResultTable rows={result.items} />
            ) : (
              <EmptyState
                icon={<Gauge size={28} />}
                title="Không có kết quả"
                desc="Backend không trả về dữ liệu đo hiệu năng."
              />
            )
          ) : (
            !runLoading && (
              <div style={{ color: 'var(--text-muted)', fontSize: '0.88rem' }}>
                Nhấn “Chạy đo hiệu năng” để xem kết quả tại đây.
              </div>
            )
          )}
        </div>
        <StatsCard />
      </div>
    </div>
  );
}

/** Thống kê nhanh từ GET /benchmark/stats. */
function StatsCard() {
  const [stats, setStats] = useState(null);
  useEffect(() => {
    (async () => {
      try {
        const res = await benchmarkService.stats();
        setStats(res?.data ?? null);
      } catch {
        setStats(null);
      }
    })();
  }, []);
  if (!stats || typeof stats !== 'object') return null;
  const entries = Object.entries(stats).filter(([, v]) => v !== null && typeof v !== 'object');
  if (entries.length === 0) return null;
  return (
    <div className="glass-card" style={{ marginTop: '1.25rem' }}>
      <h3 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
        <Info size={20} /> Thống kê hệ thống
      </h3>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: '0.75rem', marginTop: '1rem' }}>
        {entries.map(([k, v]) => (
          <div key={k} style={{ padding: '0.75rem 1rem', borderRadius: '0.75rem', background: 'rgba(255,255,255,0.03)', border: '1px solid var(--border-subtle)' }}>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginBottom: '0.25rem' }}>{k}</div>
            <div style={{ fontSize: '1.15rem', fontWeight: 700 }}>
              {typeof v === 'number' ? v.toLocaleString('vi-VN') : String(v)}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
