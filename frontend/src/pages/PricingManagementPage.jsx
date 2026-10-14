import React, { useState, useEffect } from 'react';
import { excelService } from '../services/api';
import { useToast } from '../context/ToastContext';
import {
  FileSpreadsheet,
  Download,
  Upload,
  Calculator,
  RefreshCw,
  Sparkles,
  Shield,
  Layers,
  CheckCircle2,
  AlertCircle,
  FileCheck
} from 'lucide-react';

export const PricingManagementPage = () => {
  const { showToast } = useToast();

  const [rates, setRates] = useState([]);
  const [loadingRates, setLoadingRates] = useState(false);

  // Upload state
  const [uploading, setUploading] = useState(false);
  const [uploadResult, setUploadResult] = useState(null);

  // Instant Calculator state (FR-CALC-05)
  const [calcForm, setCalcForm] = useState({
    coverageCode: 'PROP_FIRE',
    limit: 1000000000,
    deductible: 5000000,
    termMonths: 12,
    riskFactor: 1.0,
  });
  const [calculating, setCalculating] = useState(false);
  const [calcResult, setCalcResult] = useState(null);

  const fetchRates = async () => {
    try {
      setLoadingRates(true);
      const res = await excelService.getRates();
      setRates(res.data || []);
    } catch (err) {
      showToast(err.message || 'Không thể tải danh mục biểu phí', 'error');
    } finally {
      setLoadingRates(false);
    }
  };

  useEffect(() => {
    fetchRates();
  }, []);

  const handleDownloadTemplate = async () => {
    try {
      showToast('Đang tạo và tải file template Excel...', 'info');
      await excelService.downloadTemplate();
      showToast('Tải template Excel thành công!', 'success');
    } catch (err) {
      showToast(err.message || 'Lỗi khi tải template Excel', 'error');
    }
  };

  const handleFileUpload = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const formData = new FormData();
    formData.append('file', file);

    try {
      setUploading(true);
      const res = await excelService.importRates(formData);
      setUploadResult(res.data);
      showToast(res.message || 'Cập nhật biểu phí từ Excel thành công!', 'success');
      fetchRates();
    } catch (err) {
      showToast(err.message || 'Lỗi khi import file Excel', 'error');
    } finally {
      setUploading(false);
      e.target.value = '';
    }
  };

  const handleCalculate = async (e) => {
    e.preventDefault();
    try {
      setCalculating(true);
      const res = await excelService.calculate({
        coverageCode: calcForm.coverageCode,
        limit: Number(calcForm.limit),
        deductible: Number(calcForm.deductible),
        termMonths: Number(calcForm.termMonths),
        riskFactor: Number(calcForm.riskFactor),
      });
      setCalcResult(res.data);
      showToast('Tính phí bảo hiểm thành công!', 'success');
    } catch (err) {
      showToast(err.message || 'Lỗi khi tính toán phí bảo hiểm', 'error');
    } finally {
      setCalculating(false);
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
              <FileSpreadsheet size={13} /> Section 4.6: Pricing Engine & Excel Integration
            </span>
          </div>
          <h1 style={{ fontSize: '1.85rem', fontWeight: 700, margin: 0 }}>
            Quản lý Biểu phí & Tích hợp Ma trận Excel
          </h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', margin: 0 }}>
            Tải mẫu ma trận định phí, import cập nhật tỷ lệ phí động không cần build lại code, và công cụ tính phí tức thời.
          </p>
        </div>

        <div style={{ display: 'flex', gap: '0.75rem' }}>
          <button
            onClick={handleDownloadTemplate}
            className="btn btn-secondary"
            style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}
          >
            <Download size={15} />
            <span>Tải Template Ma trận (.xlsx)</span>
          </button>

          <label
            className="btn btn-primary"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.5rem',
              cursor: uploading ? 'not-allowed' : 'pointer',
              margin: 0,
            }}
          >
            <Upload size={15} />
            <span>{uploading ? 'Đang đọc Excel...' : 'Import Cập nhật Biểu phí'}</span>
            <input
              type="file"
              accept=".xlsx, .xls"
              onChange={handleFileUpload}
              disabled={uploading}
              style={{ display: 'none' }}
            />
          </label>
        </div>
      </div>

      {uploadResult && (
        <div
          className="glass-card"
          style={{
            padding: '1rem 1.25rem',
            marginBottom: '1.5rem',
            background: 'rgba(16, 185, 129, 0.08)',
            borderColor: 'rgba(16, 185, 129, 0.3)',
            display: 'flex',
            alignItems: 'center',
            gap: '0.75rem',
          }}
        >
          <CheckCircle2 size={20} color="#10b981" />
          <div style={{ fontSize: '0.85rem' }}>
            <b>Kết quả Import:</b> {uploadResult.message}. Các mã đã cập nhật:{' '}
            <b style={{ color: '#10b981' }}>{uploadResult.updatedCodes?.join(', ')}</b>.
          </div>
        </div>
      )}

      {/* Main Grid: Active Rate Catalog & Instant Calculator */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(440px, 1fr))',
          gap: '1.5rem',
        }}
      >
        {/* Left: Active Rate Catalog */}
        <div className="glass-card" style={{ padding: '1.5rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
              <Layers size={18} color="#818cf8" />
              <h2 style={{ fontSize: '1.1rem', fontWeight: 600, margin: 0 }}>
                Biểu phí Cơ sở Hiện hành ({rates.length} danh mục)
              </h2>
            </div>
            <button
              onClick={fetchRates}
              className="btn btn-secondary"
              style={{ padding: '0.35rem 0.65rem', fontSize: '0.75rem' }}
            >
              <RefreshCw size={13} className={loadingRates ? 'spinner' : ''} />
            </button>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem' }}>
            {rates.map((item) => (
              <div
                key={item.coverageCode}
                style={{
                  padding: '1rem',
                  borderRadius: 'var(--radius-md)',
                  background: 'rgba(255, 255, 255, 0.02)',
                  border: '1px solid var(--border-subtle)',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.35rem' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <span className="badge badge-primary" style={{ fontSize: '0.725rem' }}>
                      {item.coverageCode}
                    </span>
                    <span style={{ fontWeight: 600, fontSize: '0.875rem' }}>{item.coverageName}</span>
                  </div>
                  <span style={{ color: '#10b981', fontWeight: 700, fontSize: '0.95rem' }}>
                    {(item.baseRate * 100).toFixed(2)}% ({item.baseRate})
                  </span>
                </div>

                <div style={{ fontSize: '0.775rem', color: 'var(--text-secondary)', marginBottom: '0.35rem' }}>
                  {item.description}
                </div>

                <div
                  style={{
                    display: 'flex',
                    gap: '1rem',
                    fontSize: '0.725rem',
                    color: 'var(--text-muted)',
                    borderTop: '1px solid rgba(255, 255, 255, 0.04)',
                    paddingTop: '0.35rem',
                  }}
                >
                  <span>Hạn mức: {new Intl.NumberFormat('vi-VN').format(item.minLimit)}đ - {new Intl.NumberFormat('vi-VN').format(item.maxLimit)}đ</span>
                  <span>Khấu trừ mặc định: {new Intl.NumberFormat('vi-VN').format(item.defaultDeductible)}đ</span>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Right: Instant Premium Calculator (FR-CALC-05) */}
        <div className="glass-card" style={{ padding: '1.5rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '1.25rem' }}>
            <Calculator size={18} color="#06b6d4" />
            <h2 style={{ fontSize: '1.1rem', fontWeight: 600, margin: 0 }}>
              Công cụ Tính phí Bảo hiểm Tức thời (FR-CALC-05)
            </h2>
          </div>

          <form onSubmit={handleCalculate} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <div>
              <label className="form-label">Chọn Gói bảo hiểm (Coverage Code) *</label>
              <select
                className="form-control"
                value={calcForm.coverageCode}
                onChange={(e) => setCalcForm({ ...calcForm, coverageCode: e.target.value })}
              >
                {rates.map((r) => (
                  <option key={r.coverageCode} value={r.coverageCode}>
                    {r.coverageCode} - {r.coverageName} (Tỷ lệ: {(r.baseRate * 100).toFixed(2)}%)
                  </option>
                ))}
              </select>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
              <div>
                <label className="form-label">Hạn mức bồi thường (VND) *</label>
                <input
                  type="number"
                  step="10000000"
                  className="form-control"
                  value={calcForm.limit}
                  onChange={(e) => setCalcForm({ ...calcForm, limit: Number(e.target.value) })}
                  required
                />
              </div>

              <div>
                <label className="form-label">Mức khấu trừ (VND)</label>
                <input
                  type="number"
                  step="1000000"
                  className="form-control"
                  value={calcForm.deductible}
                  onChange={(e) => setCalcForm({ ...calcForm, deductible: Number(e.target.value) })}
                />
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
              <div>
                <label className="form-label">Thời hạn bảo hiểm (Tháng)</label>
                <input
                  type="number"
                  min="1"
                  max="36"
                  className="form-control"
                  value={calcForm.termMonths}
                  onChange={(e) => setCalcForm({ ...calcForm, termMonths: Number(e.target.value) })}
                  required
                />
              </div>

              <div>
                <label className="form-label">Hệ số rủi ro (Risk Factor)</label>
                <input
                  type="number"
                  step="0.05"
                  min="0.5"
                  max="3.0"
                  className="form-control"
                  value={calcForm.riskFactor}
                  onChange={(e) => setCalcForm({ ...calcForm, riskFactor: Number(e.target.value) })}
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={calculating}
              className="btn btn-primary"
              style={{ marginTop: '0.5rem', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem' }}
            >
              <Calculator size={16} />
              <span>{calculating ? 'Đang tính toán...' : 'Tính phí ngay'}</span>
            </button>
          </form>

          {/* Calculator Output Card */}
          {calcResult && (
            <div
              style={{
                marginTop: '1.5rem',
                padding: '1.25rem',
                borderRadius: 'var(--radius-md)',
                background: 'rgba(16, 185, 129, 0.08)',
                border: '1px solid rgba(16, 185, 129, 0.3)',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                <span style={{ fontSize: '0.8rem', color: '#10b981', fontWeight: 600 }}>KẾT QUẢ ĐỊNH PHÍ CHUẨN:</span>
                <span className="badge badge-info">{calcResult.coverageCode}</span>
              </div>

              <div style={{ fontSize: '1.85rem', fontWeight: 800, color: '#10b981', marginBottom: '0.5rem' }}>
                {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(calcResult.calculatedPremium)}
              </div>

              <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
                <div>Công thức: <code>{calcResult.calculationFormula}</code></div>
                <div>Tỷ lệ phí áp dụng: <b>{(calcResult.appliedRate * 100).toFixed(4)}%</b> (Gốc: {(calcResult.baseRate * 100).toFixed(4)}%)</div>
                <div>Thời hạn: <b>{calcResult.termMonths} tháng</b> • Mức khấu trừ: <b>{new Intl.NumberFormat('vi-VN').format(calcResult.deductible)} đ</b></div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
