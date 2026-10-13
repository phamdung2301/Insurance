import React, { useState, useEffect } from 'react';
import { benchmarkService } from '../services/api';
import { useToast } from '../context/ToastContext';
import {
  Gauge,
  Database,
  Play,
  Zap,
  CheckCircle2,
  AlertTriangle,
  RefreshCw,
  Cpu,
  Layers,
  ArrowRight,
  TrendingDown,
  Clock,
  Sparkles
} from 'lucide-react';

export const BenchmarkPage = () => {
  const { showToast } = useToast();

  const [stats, setStats] = useState(null);
  const [loadingStats, setLoadingStats] = useState(false);

  // Generate state
  const [generating, setGenerating] = useState(false);
  const [generateCount, setGenerateCount] = useState(50000);
  const [generateResult, setGenerateResult] = useState(null);

  // Benchmark Run state
  const [running, setRunning] = useState(false);
  const [benchmarkReport, setBenchmarkReport] = useState(null);

  const fetchStats = async () => {
    try {
      setLoadingStats(true);
      const res = await benchmarkService.getStats();
      setStats(res.data);
    } catch (err) {
      showToast(err.message || 'Không thể tải thống kê MongoDB', 'error');
    } finally {
      setLoadingStats(false);
    }
  };

  useEffect(() => {
    fetchStats();
  }, []);

  const handleGenerate50k = async () => {
    try {
      setGenerating(true);
      showToast(`Đang khởi tạo ${generateCount.toLocaleString()} bản ghi Hợp đồng thử nghiệm trong MongoDB...`, 'info');
      const res = await benchmarkService.generate50k(generateCount);
      setGenerateResult(res.data);
      showToast(res.message || 'Đã tạo thành công dữ liệu kiểm thử tải!', 'success');
      fetchStats();
    } catch (err) {
      showToast(err.message || 'Lỗi khi tạo dữ liệu kiểm thử', 'error');
    } finally {
      setGenerating(false);
    }
  };

  const handleRunBenchmark = async () => {
    try {
      setRunning(true);
      showToast('Đang thực thi 3 truy vấn kiểm chuẩn và phân tích Execution Plan...', 'info');
      const res = await benchmarkService.runBenchmark();
      setBenchmarkReport(res.data);
      showToast('Thực thi kiểm chuẩn thành công!', 'success');
    } catch (err) {
      showToast(err.message || 'Lỗi khi chạy benchmark', 'error');
    } finally {
      setRunning(false);
    }
  };

  return (
    <div style={{ padding: '2rem', maxWidth: '1400px', margin: '0 auto' }}>
      {/* Header */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: '2rem',
          flexWrap: 'wrap',
          gap: '1rem',
        }}
      >
        <div>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.4rem' }}>
            <span className="badge badge-primary">
              <Gauge size={13} /> Section 4.8: Performance Benchmarking & Indexing
            </span>
          </div>
          <h1 style={{ fontSize: '1.85rem', fontWeight: 700, margin: 0 }}>
            Kiểm chuẩn Hiệu năng Dữ liệu Lớn (50.000+ Records)
          </h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', margin: 0 }}>
            Khởi tạo 50.000 hợp đồng ngẫu nhiên và phân tích Execution Plan Metrics (winningPlan, totalDocsExamined, executionTimeMillis, IXSCAN vs COLLSCAN).
          </p>
        </div>

        <button
          onClick={fetchStats}
          disabled={loadingStats}
          className="btn btn-secondary"
          style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}
        >
          <RefreshCw size={15} className={loadingStats ? 'spinner' : ''} />
          <span>Làm mới Trạng thái</span>
        </button>
      </div>

      {/* Database Status Cards */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
          gap: '1.25rem',
          marginBottom: '2rem',
        }}
      >
        <div className="glass-card" style={{ padding: '1.25rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
            <span style={{ fontSize: '0.775rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>TẬP DỮ LIỆU HIỆN TẠI</span>
            <Database size={20} color="#818cf8" />
          </div>
          <div style={{ fontSize: '1.85rem', fontWeight: 800, color: 'var(--text-primary)' }}>
            {(stats?.totalPolicies || 0).toLocaleString('vi-VN')}
          </div>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
            Bản ghi trong collection <code>policies</code>
          </span>
        </div>

        <div className="glass-card" style={{ padding: '1.25rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
            <span style={{ fontSize: '0.775rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>CHỈ MỤC HOẠT ĐỘNG (INDEXES)</span>
            <Layers size={20} color="#10b981" />
          </div>
          <div style={{ fontSize: '1.85rem', fontWeight: 800, color: '#10b981' }}>
            {stats?.indexCount || 0}
          </div>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
            Compound & Multikey Indexes được đồng bộ
          </span>
        </div>

        <div className="glass-card" style={{ padding: '1.25rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
            <span style={{ fontSize: '0.775rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>TIÊU CHUẨN HIỆU NĂNG NFR-PERF-01</span>
            <Zap size={20} color="#06b6d4" />
          </div>
          <div style={{ fontSize: '1.85rem', fontWeight: 800, color: '#06b6d4' }}>
            &lt; 200 ms
          </div>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
            Thời gian phản hồi truy vấn có chỉ mục trên 50k policies
          </span>
        </div>

        <div className="glass-card" style={{ padding: '1.25rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
            <span style={{ fontSize: '0.775rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>TRẠNG THÁI ENGINE</span>
            <Cpu size={20} color="#f59e0b" />
          </div>
          <div style={{ fontSize: '1.35rem', fontWeight: 700, color: '#f59e0b' }}>
            MongoDB WiredTiger
          </div>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
            Hỗ trợ IXSCAN B-Tree Indexing & Compression
          </span>
        </div>
      </div>

      {/* Control Action Panel */}
      <div
        className="glass-card"
        style={{
          padding: '1.75rem',
          marginBottom: '2rem',
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
          gap: '1.5rem',
          alignItems: 'center',
        }}
      >
        {/* Action 1: Generate 50,000 Records */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Database size={18} color="#818cf8" />
            <span style={{ fontWeight: 600, fontSize: '0.95rem' }}>
              Bước 1: Khởi tạo 50.000 Hợp đồng Thử nghiệm (FR-BENCH-01)
            </span>
          </div>
          <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', margin: 0 }}>
            Tự động sinh dữ liệu theo lô (Batch insert 2.000 docs/lô) đầy đủ cấu trúc Insured, Locations, Coverages.
          </p>

          <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
            <select
              value={generateCount}
              onChange={(e) => setGenerateCount(Number(e.target.value))}
              className="form-control"
              style={{ width: '160px', fontSize: '0.85rem' }}
            >
              <option value="5000">5.000 bản ghi</option>
              <option value="10000">10.000 bản ghi</option>
              <option value="25000">25.000 bản ghi</option>
              <option value="50000">50.000 bản ghi (SRS)</option>
            </select>

            <button
              onClick={handleGenerate50k}
              disabled={generating}
              className="btn btn-primary"
              style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}
            >
              <Sparkles size={16} />
              <span>{generating ? 'Đang tạo dữ liệu...' : 'Sinh dữ liệu ngẫu nhiên'}</span>
            </button>
          </div>
        </div>

        {/* Action 2: Run Benchmark Suite */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Play size={18} color="#10b981" />
            <span style={{ fontWeight: 600, fontSize: '0.95rem' }}>
              Bước 2: Thực thi Kiểm chuẩn Hiệu năng (FR-BENCH-02, 03)
            </span>
          </div>
          <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', margin: 0 }}>
            Đo đạc 3 câu truy vấn trọng yếu: Status filter, Date range, và Compound search filter with sort.
          </p>

          <button
            onClick={handleRunBenchmark}
            disabled={running}
            className="btn btn-primary"
            style={{
              background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '0.5rem',
              maxWidth: '260px',
            }}
          >
            <Gauge size={16} />
            <span>{running ? 'Đang đo đạc...' : 'Chạy Kiểm chuẩn Ngay'}</span>
          </button>
        </div>
      </div>

      {generateResult && (
        <div
          className="glass-card"
          style={{
            padding: '1rem 1.25rem',
            marginBottom: '1.5rem',
            background: 'rgba(99, 102, 241, 0.08)',
            borderColor: 'rgba(99, 102, 241, 0.3)',
            display: 'flex',
            alignItems: 'center',
            gap: '0.75rem',
            fontSize: '0.85rem',
          }}
        >
          <CheckCircle2 size={20} color="#818cf8" />
          <div>
            <b>Hoàn tất nạp dữ liệu:</b> {generateResult.message} (Tốc độ nạp:{' '}
            <b style={{ color: '#818cf8' }}>{Math.round(generateResult.throughputDocsPerSecond || 0).toLocaleString()} docs/giây</b>).
          </div>
        </div>
      )}

      {/* Benchmark Report Display */}
      {benchmarkReport && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
          <div
            className="glass-card"
            style={{
              padding: '1.25rem 1.5rem',
              background: 'rgba(16, 185, 129, 0.08)',
              borderColor: 'rgba(16, 185, 129, 0.3)',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              flexWrap: 'wrap',
              gap: '1rem',
            }}
          >
            <div>
              <div style={{ fontWeight: 700, fontSize: '1.1rem', color: '#10b981', marginBottom: '0.25rem' }}>
                Báo cáo Kết quả Kiểm chuẩn Hiệu năng (Execution Plan Metrics)
              </div>
              <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
                {benchmarkReport.benchmarkSummary}
              </div>
            </div>

            <div style={{ textAlign: 'right' }}>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', display: 'block' }}>
                THỜI GIAN TRUNG BÌNH
              </span>
              <span style={{ fontSize: '1.75rem', fontWeight: 800, color: '#10b981' }}>
                {benchmarkReport.averageExecutionTimeMillis} ms
              </span>
            </div>
          </div>

          {/* Individual Query Metric Cards */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            {benchmarkReport.queryMetrics.map((qm, idx) => (
              <div
                key={idx}
                className="glass-card"
                style={{
                  padding: '1.25rem 1.5rem',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  flexWrap: 'wrap',
                  gap: '1.25rem',
                }}
              >
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem', flex: 1, minWidth: '320px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                    <span style={{ fontWeight: 700, fontSize: '0.95rem' }}>{qm.queryName}</span>
                    <span
                      className={`badge ${qm.indexUsed ? 'badge-success' : 'badge-danger'}`}
                      style={{ fontSize: '0.725rem' }}
                    >
                      {qm.winningPlan}
                    </span>
                  </div>
                  <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                    Bộ lọc: <code>{qm.queryFilter}</code>
                  </div>
                  <div style={{ fontSize: '0.75rem', color: qm.indexUsed ? '#10b981' : '#f87171' }}>
                    {qm.optimizationStatus}
                  </div>
                </div>

                {/* Metrics Breakdown Grid */}
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(4, 1fr)',
                    gap: '1.5rem',
                    textAlign: 'center',
                  }}
                >
                  <div>
                    <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', display: 'block' }}>THỜI GIAN (MS)</span>
                    <span
                      style={{
                        fontSize: '1.25rem',
                        fontWeight: 800,
                        color: qm.executionTimeMillis < 200 ? '#10b981' : '#f59e0b',
                      }}
                    >
                      {qm.executionTimeMillis} ms
                    </span>
                  </div>

                  <div>
                    <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', display: 'block' }}>DOCS ĐÃ DUYỆT</span>
                    <span style={{ fontSize: '1.1rem', fontWeight: 700 }}>
                      {qm.totalDocsExamined.toLocaleString('vi-VN')}
                    </span>
                  </div>

                  <div>
                    <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', display: 'block' }}>KEYS ĐÃ DUYỆT</span>
                    <span style={{ fontSize: '1.1rem', fontWeight: 700, color: '#818cf8' }}>
                      {qm.totalKeysExamined.toLocaleString('vi-VN')}
                    </span>
                  </div>

                  <div>
                    <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', display: 'block' }}>KẾT QUẢ TRẢ VỀ</span>
                    <span style={{ fontSize: '1.1rem', fontWeight: 700, color: '#06b6d4' }}>
                      {qm.nReturned.toLocaleString('vi-VN')}
                    </span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};
