import React, { useState, useEffect } from 'react';
import { Download, Upload, FileSpreadsheet, Calculator, Info, TableProperties } from 'lucide-react';
import { excelService, downloadBlob } from '../services/api';
import { rateImportService } from '../services/rateImportApi';
import { BACKEND_TODO_HINT, isBackendMissing } from '../utils/backend';
import { formatMoney, formatDateTime } from '../utils/format';
import { useToast } from '../context/ToastContext';

const BASE_RATES = { PROP_FIRE: 0.075, PROP_WATER: 0.05, LIAB_PUBLIC: 0.12, WCOMP: 0.2 };
const COVERAGE_OPTIONS = [
  { value: 'PROP_FIRE', label: 'Tài sản — Cháy nổ' },
  { value: 'PROP_WATER', label: 'Tài sản — Thiệt hại do nước' },
  { value: 'LIAB_PUBLIC', label: 'Trách nhiệm công cộng' },
  { value: 'WCOMP', label: 'Tai nạn lao động' },
];

/** Công thức ước tính cục bộ khi backend chưa có API tính phí. */
function estimatePremium({ coverageCode, limit, deductible }) {
  const baseRate = BASE_RATES[coverageCode] ?? 0.1;
  const l = Number(limit) || 0;
  const d = Number(deductible) || 0;
  if (l <= 0) return 0;
  return (l * baseRate) / 100 * (1 - Math.min(d / l, 0.5) * 0.5);
}

function MissingAlert() {
  return (
    <div className="alert alert-info" style={{ marginTop: '1rem', marginBottom: 0 }}>
      <Info size={18} style={{ flexShrink: 0, marginTop: '0.1rem' }} />
      <span>{BACKEND_TODO_HINT}</span>
    </div>
  );
}

export function ExcelToolsPage() {
  const toast = useToast();

  // (a) Tải file mẫu
  const [tplLoading, setTplLoading] = useState(false);
  const [tplMissing, setTplMissing] = useState(false);

  // (b) Cập nhật bảng giá
  const [file, setFile] = useState(null);
  const [fileKey, setFileKey] = useState(0);
  const [impLoading, setImpLoading] = useState(false);
  const [impMissing, setImpMissing] = useState(false);

  // (c) Xuất bảng tính phí
  const [policyNumber, setPolicyNumber] = useState('');
  const [expLoading, setExpLoading] = useState(false);
  const [expMissing, setExpMissing] = useState(false);

  // (d) Tính phí nhanh
  const [calcForm, setCalcForm] = useState({ coverageCode: 'PROP_FIRE', limit: '', deductible: '', termMonths: '12' });
  const [calcLoading, setCalcLoading] = useState(false);
  const [calcResult, setCalcResult] = useState(null); // { value, estimated }
  const [calcMissing, setCalcMissing] = useState(false);
  const [calcError, setCalcError] = useState('');

  const handleTemplate = async () => {
    setTplLoading(true);
    setTplMissing(false);
    try {
      const blob = await excelService.downloadTemplate();
      downloadBlob(blob, 'BangGiaMau.xlsx');
      toast.success('Đã tải file mẫu bảng giá.');
    } catch (err) {
      if (isBackendMissing(err)) setTplMissing(true);
      else toast.error(err.message);
    } finally {
      setTplLoading(false);
    }
  };

  const handleImport = async () => {
    if (!file) {
      toast.error('Vui lòng chọn file Excel (.xlsx) trước khi tải lên.');
      return;
    }
    setImpLoading(true);
    setImpMissing(false);
    try {
      const res = await excelService.importRates(file);
      toast.success(res?.message || 'Cập nhật bảng giá thành công.');
      setFile(null);
      setFileKey((k) => k + 1);
    } catch (err) {
      if (isBackendMissing(err)) setImpMissing(true);
      else toast.error(err.message);
    } finally {
      setImpLoading(false);
    }
  };

  const handleExport = async () => {
    const n = policyNumber.trim();
    if (!n) {
      toast.error('Vui lòng nhập mã hợp đồng.');
      return;
    }
    setExpLoading(true);
    setExpMissing(false);
    try {
      const blob = await excelService.downloadBreakdown(n);
      downloadBlob(blob, `BangTinhPhi_${n}.xlsx`);
      toast.success('Đã xuất bảng tính phí.');
    } catch (err) {
      if (isBackendMissing(err)) setExpMissing(true);
      else toast.error(err.message);
    } finally {
      setExpLoading(false);
    }
  };

  const setCalc = (key) => (e) => {
    setCalcForm((f) => ({ ...f, [key]: e.target.value }));
    setCalcResult(null);
  };

  const handleCalculate = async (e) => {
    e.preventDefault();
    const limit = Number(calcForm.limit);
    const deductible = Number(calcForm.deductible);
    const termMonths = Number(calcForm.termMonths);
    if (!limit || limit <= 0) { setCalcError('Vui lòng nhập mức bảo hiểm lớn hơn 0.'); return; }
    if (deductible < 0) { setCalcError('Mức khấu trừ không được âm.'); return; }
    if (deductible >= limit) { setCalcError('Mức khấu trừ phải nhỏ hơn mức bảo hiểm.'); return; }
    if (!termMonths || termMonths <= 0) { setCalcError('Vui lòng nhập thời hạn hợp đồng hợp lệ.'); return; }
    setCalcError('');
    setCalcLoading(true);
    setCalcMissing(false);
    try {
      const res = await excelService.calculate({
        coverageCode: calcForm.coverageCode,
        limit,
        deductible,
        termMonths,
      });
      const d = res?.data;
      const value = d?.premium ?? d?.totalPremium ?? (typeof d === 'number' ? d : null);
      setCalcResult({ value, estimated: false });
    } catch (err) {
      if (isBackendMissing(err)) {
        setCalcResult({
          value: estimatePremium({ coverageCode: calcForm.coverageCode, limit, deductible }),
          estimated: true,
        });
        setCalcMissing(true);
      } else {
        toast.error(err.message);
      }
    } finally {
      setCalcLoading(false);
    }
  };

  return (
    <div>
      <div className="page-header">
        <h1>Bảng giá Excel</h1>
        <p>Tải file mẫu, cập nhật bảng giá, xuất bảng tính phí và tính phí nhanh.</p>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '1.25rem' }}>
        {/* (a) Tải file mẫu */}
        <div className="glass-card">
          <h3 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <FileSpreadsheet size={20} /> Tải file mẫu bảng giá
          </h3>
          <p className="form-hint" style={{ marginBottom: '1rem' }}>
            Tải file Excel mẫu để điền bảng giá theo đúng định dạng hệ thống yêu cầu.
          </p>
          <button className="btn btn-primary" onClick={handleTemplate} disabled={tplLoading}>
            <Download size={16} /> {tplLoading ? 'Đang tải…' : 'Tải file mẫu'}
          </button>
          {tplMissing && <MissingAlert />}
        </div>

        {/* (b) Cập nhật bảng giá */}
        <div className="glass-card">
          <h3 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Upload size={20} /> Cập nhật bảng giá
          </h3>
          <p className="form-hint" style={{ marginBottom: '1rem' }}>
            Chọn file Excel đã điền theo file mẫu rồi tải lên để cập nhật bảng giá.
          </p>
          <div className="form-group">
            <input
              key={fileKey}
              type="file"
              accept=".xlsx,.xls"
              className="form-control"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              aria-label="Chọn file Excel bảng giá"
            />
          </div>
          <button className="btn btn-secondary" onClick={handleImport} disabled={impLoading || !file}>
            <Upload size={16} /> {impLoading ? 'Đang tải lên…' : 'Tải lên'}
          </button>
          {impMissing && <MissingAlert />}
        </div>

        {/* (c) Xuất bảng tính phí */}
        <div className="glass-card">
          <h3 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <FileSpreadsheet size={20} /> Xuất bảng tính phí hợp đồng
          </h3>
          <p className="form-hint" style={{ marginBottom: '1rem' }}>
            Nhập mã hợp đồng để xuất file Excel chi tiết cách tính phí.
          </p>
          <div className="form-group">
            <label className="form-label">Mã hợp đồng</label>
            <input
              className="form-control"
              placeholder="VD: HD-0001"
              value={policyNumber}
              onChange={(e) => setPolicyNumber(e.target.value)}
            />
          </div>
          <button className="btn btn-secondary" onClick={handleExport} disabled={expLoading}>
            <Download size={16} /> {expLoading ? 'Đang xuất…' : 'Xuất Excel'}
          </button>
          {expMissing && <MissingAlert />}
        </div>

        {/* (d) Tính phí nhanh */}
        <div className="glass-card">
          <h3 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Calculator size={20} /> Tính phí nhanh
          </h3>
          <p className="form-hint" style={{ marginBottom: '1rem' }}>
            Ước tính phí cho một gói bảo hiểm dựa trên bảng giá hiện hành.
          </p>
          <form onSubmit={handleCalculate}>
            <div className="form-group">
              <label className="form-label">Gói bảo hiểm</label>
              <select className="form-control" value={calcForm.coverageCode} onChange={setCalc('coverageCode')}>
                {COVERAGE_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
            </div>
            <div className="form-row">
              <div className="form-group">
                <label className="form-label">Mức bảo hiểm</label>
                <input
                  type="number"
                  min="0"
                  className="form-control"
                  placeholder="VD: 1000000"
                  value={calcForm.limit}
                  onChange={setCalc('limit')}
                />
              </div>
              <div className="form-group">
                <label className="form-label">Mức khấu trừ</label>
                <input
                  type="number"
                  min="0"
                  className="form-control"
                  placeholder="VD: 10000"
                  value={calcForm.deductible}
                  onChange={setCalc('deductible')}
                />
              </div>
            </div>
            <div className="form-group">
              <label className="form-label">Thời hạn (tháng)</label>
              <input
                type="number"
                min="1"
                className="form-control"
                value={calcForm.termMonths}
                onChange={setCalc('termMonths')}
              />
            </div>
            {calcError && <div className="form-error" style={{ marginBottom: '0.75rem' }}>{calcError}</div>}
            <button type="submit" className="btn btn-primary" disabled={calcLoading}>
              <Calculator size={16} /> {calcLoading ? 'Đang tính…' : 'Tính phí'}
            </button>
          </form>
          {calcResult && (
            <div style={{ marginTop: '1rem', padding: '0.9rem 1.1rem', borderRadius: '0.75rem', background: 'rgba(255,255,255,0.03)', border: '1px solid var(--border-subtle)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.25rem' }}>
                <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Phí bảo hiểm</span>
                {calcResult.estimated && <span className="badge badge-info">Ước tính</span>}
              </div>
              <div style={{ fontSize: '1.4rem', fontWeight: 800 }}>
                {calcResult.value === null ? '—' : formatMoney(calcResult.value)}
              </div>
              {calcResult.estimated && (
                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
                  Giá trị ước tính cục bộ — backend chưa hỗ trợ tính phí trực tuyến.
                </div>
              )}
            </div>
          )}
          {calcMissing && <MissingAlert />}
        </div>

        {/* (e) Bảng giá hiện hành */}
        <RateMatrix />

        {/* (f) Lịch sử import biểu phí (đợt 3C, việc 2) */}
        <ImportHistory refreshKey={fileKey} />
      </div>
    </div>
  );
}

/** (f) Lịch sử import biểu phí: người import, thời gian, tên file, số rate. */
function ImportHistory({ refreshKey }) {
  const [imports, setImports] = useState([]);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    (async () => {
      setLoading(true);
      try {
        const res = await rateImportService.listImports();
        const d = res?.data;
        setImports(Array.isArray(d) ? d : []);
      } catch {
        setImports([]);
      } finally {
        setLoading(false);
      }
    })();
  }, [refreshKey]);
  return (
    <div className="glass-card">
      <h3 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
        <FileSpreadsheet size={20} /> Lịch sử import biểu phí
      </h3>
      <p className="form-hint" style={{ marginBottom: '1rem' }}>
        Các lần cập nhật bảng giá thành công, mới nhất trước.
      </p>
      {loading ? (
        <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Đang tải lịch sử…</p>
      ) : imports.length === 0 ? (
        <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Chưa có lần import nào được ghi nhận.</p>
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Người import</th>
                <th>Thời gian</th>
                <th>Tên file</th>
                <th className="num">Số rate</th>
              </tr>
            </thead>
            <tbody>
              {imports.map((imp, i) => (
                <tr key={imp.id ?? i}>
                  <td>{imp.importedBy ?? '—'}</td>
                  <td style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{formatDateTime(imp.importedAt)}</td>
                  <td style={{ fontFamily: 'monospace' }}>{imp.fileName ?? '—'}</td>
                  <td className="num">{imp.ratesCount ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

/** (e) Bảng giá hiện hành từ backend. */
function RateMatrix() {
  const [rates, setRates] = useState([]);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    (async () => {
      try {
        const res = await excelService.getRates();
        const d = res?.data;
        setRates(Array.isArray(d) ? d : d?.rates ?? []);
      } catch {
        setRates([]);
      } finally {
        setLoading(false);
      }
    })();
  }, []);
  return (
    <div className="glass-card">
      <h3 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
        <TableProperties size={20} /> Bảng giá hiện hành
      </h3>
      <p className="form-hint" style={{ marginBottom: '1rem' }}>
        Biểu giá gốc đang áp dụng cho từng gói bảo hiểm.
      </p>
      {loading ? (
        <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Đang tải bảng giá…</p>
      ) : rates.length === 0 ? (
        <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Chưa có dữ liệu bảng giá.</p>
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Mã gói</th>
                <th>Tên gói</th>
                <th className="num">Tỉ lệ gốc (%)</th>
                <th className="num">Hạn mức tối thiểu</th>
                <th className="num">Hạn mức tối đa</th>
                <th className="num">Khấu trừ mặc định</th>
              </tr>
            </thead>
            <tbody>
              {rates.map((r, i) => (
                <tr key={r.coverageCode ?? i}>
                  <td style={{ fontFamily: 'monospace' }}>{r.coverageCode}</td>
                  <td>{r.coverageName ?? r.description ?? '—'}</td>
                  <td className="num">{r.baseRate ?? '—'}</td>
                  <td className="num">{r.minLimit != null ? formatMoney(r.minLimit) : '—'}</td>
                  <td className="num">{r.maxLimit != null ? formatMoney(r.maxLimit) : '—'}</td>
                  <td className="num">{r.defaultDeductible != null ? formatMoney(r.defaultDeductible) : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
