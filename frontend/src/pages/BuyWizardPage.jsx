import React, { useState, useEffect, useMemo, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Flame, Scale, HardHat, Truck, MapPin, Plus, Trash2,
  ArrowLeft, ArrowRight, Check, Save, Send,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { policyService, excelService } from '../services/api';
import { isBackendMissing } from '../utils/backend';
import { formatMoney, isValidEmail, isValidPhone } from '../utils/format';

const TERM_MONTHS = 12;

/** 4 sản phẩm bảo hiểm ở bước 1. */
const PRODUCTS = [
  { key: 'chay-no-tai-san', name: 'Bảo hiểm cháy nổ tài sản', desc: 'Bảo vệ nhà xưởng, kho bãi, văn phòng trước cháy nổ', icon: Flame },
  { key: 'trach-nhiem-cong-cong', name: 'Bảo hiểm trách nhiệm công cộng', desc: 'Bảo vệ trách nhiệm pháp lý với bên thứ ba', icon: Scale },
  { key: 'tai-nan-lao-dong', name: 'Bảo hiểm tai nạn lao động', desc: 'Bảo vệ người lao động tại nơi làm việc', icon: HardHat },
  { key: 'hang-hoa-van-chuyen', name: 'Bảo hiểm hàng hóa vận chuyển', desc: 'Bảo vệ hàng hóa trong quá trình vận chuyển', icon: Truck },
];

/** Catalog gói bảo hiểm ở bước 3. */
const COVERAGE_CATALOG = [
  { code: 'PROP_FIRE', name: 'Cháy nổ tài sản', baseRate: 0.075, type: 'PROPERTY' },
  { code: 'PROP_WATER', name: 'Ngập nước', baseRate: 0.05, type: 'PROPERTY' },
  { code: 'LIAB_PUBLIC', name: 'Trách nhiệm công cộng', baseRate: 0.12, type: 'LIABILITY' },
  { code: 'WCOMP', name: 'Tai nạn lao động', baseRate: 0.2, type: 'WORKERS_COMP' },
];

const STEP_LABELS = ['Chọn sản phẩm', 'Thông tin & địa điểm', 'Gói bảo hiểm & phí', 'Xác nhận'];

const emptyCoverage = () => ({ checked: false, limit: '', deductible: '' });
const emptyLocation = () => ({
  tenDiaDiem: '',
  diaChi: '',
  coverages: Object.fromEntries(COVERAGE_CATALOG.map((c) => [c.code, emptyCoverage()])),
});

/**
 * Công thức ước tính phí nội bộ (dùng khi backend chưa có API tính phí):
 * premium = limit × baseRate/100 × (1 − min(deductible/limit, 0.5) × 0.5)
 */
export function estimatePremium(limit, baseRate, termMonths = TERM_MONTHS, deductible = 0) {
  const L = Number(limit);
  const D = Number(deductible) || 0;
  const T = Number(termMonths) || TERM_MONTHS;
  if (!Number.isFinite(L) || L <= 0 || !Number.isFinite(baseRate)) return 0;
  const ratio = L > 0 ? Math.min(D / L, 0.5) : 0;
  return (L * (baseRate / 100) * (1 - ratio * 0.5) * (T / 12));
}

/** Bóc tách kết quả POST /excel/calculate theo vài dạng envelope có thể có. */
function parseServerPremiums(data) {
  if (!data || typeof data !== 'object') return null;
  const body = data.premiums ?? data.data ?? null;
  const out = {};
  if (Array.isArray(body)) {
    let ok = false;
    for (const it of body) {
      const key = it.locationId != null ? `${it.locationId}|${it.coverageCode}` : it.coverageCode;
      if (key && Number.isFinite(Number(it.premium))) { out[key] = Number(it.premium); ok = true; }
    }
    return ok ? out : null;
  }
  if (body && typeof body === 'object') {
    let ok = false;
    for (const [k, v] of Object.entries(body)) {
      if (Number.isFinite(Number(v))) { out[k] = Number(v); ok = true; }
    }
    return ok ? out : null;
  }
  return null;
}

export function BuyWizardPage() {
  const { user } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();

  const [step, setStep] = useState(0);
  const [product, setProduct] = useState(null);
  const [insured, setInsured] = useState({
    hoTen: user?.fullName || '',
    email: user?.email || '',
    phone: '',
    diaChi: '',
    loaiKH: 'CA_NHAN',
  });
  const [locations, setLocations] = useState([emptyLocation()]);
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);
  const [serverPrem, setServerPrem] = useState({});

  const setIns = (k, v) => setInsured((p) => ({ ...p, [k]: v }));
  const setLoc = (i, k, v) => setLocations((p) => p.map((l, j) => (j === i ? { ...l, [k]: v } : l)));
  const setCov = (i, code, k, v) => setLocations((p) =>
    p.map((l, j) => (j === i
      ? { ...l, coverages: { ...l.coverages, [code]: { ...l.coverages[code], [k]: v } } }
      : l)));

  const addLocation = () => setLocations((p) => [...p, emptyLocation()]);
  const removeLocation = (i) => setLocations((p) => (p.length <= 1 ? p : p.filter((_, j) => j !== i)));

  // ---- Ước tính phí -------------------------------------------------------
  const flatKey = useMemo(
    () => JSON.stringify(locations.map((l) => Object.entries(l.coverages)
      .filter(([, c]) => c.checked)
      .map(([code, c]) => [code, c.limit, c.deductible]))),
    [locations],
  );

  // Thử tính phí bằng backend (debounce); thất bại thì giữ công thức nội bộ.
  useEffect(() => {
    const checked = [];
    locations.forEach((l, li) => {
      Object.entries(l.coverages).forEach(([code, c]) => {
        if (c.checked && Number(c.limit) > 0) {
          checked.push({
            locationId: li + 1,
            coverageCode: code,
            limit: Number(c.limit),
            deductible: Number(c.deductible) || 0,
            termMonths: TERM_MONTHS,
          });
        }
      });
    });
    if (checked.length === 0) { setServerPrem({}); return; }
    const t = setTimeout(async () => {
      try {
        // Backend /excel/calculate chỉ nhận 1 coverage/lần -> gọi từng cái rồi gộp
        const results = await Promise.all(
          checked.map((c) =>
            excelService.calculate({
              coverageCode: c.coverageCode,
              limit: c.limit,
              deductible: c.deductible,
              termMonths: c.termMonths,
            }).then((res) => ({ key: `${c.locationId}|${c.coverageCode}`, data: res?.data }))
             .catch(() => null)
          )
        );
        const merged = {};
        for (const r of results) {
          if (!r) continue;
          const d = r.data;
          const premium = d?.calculatedPremium ?? d?.premium ?? d?.totalPremium;
          if (Number.isFinite(Number(premium))) merged[r.key] = Number(premium);
        }
        if (Object.keys(merged).length) setServerPrem(merged);
      } catch {
        setServerPrem({}); // lỗi -> dùng ước tính nội bộ
      }
    }, 700);
    return () => clearTimeout(t);
  }, [flatKey]); // eslint-disable-line react-hooks/exhaustive-deps

  const coveragePremium = (li, code, c) => {
    const key = `${li + 1}|${code}`;
    if (Number.isFinite(serverPrem[key])) return serverPrem[key];
    const cat = COVERAGE_CATALOG.find((x) => x.code === code);
    return estimatePremium(c.limit, cat.baseRate, TERM_MONTHS, c.deductible);
  };

  const totalPremium = useMemo(() => {
    let total = 0;
    locations.forEach((l, li) => {
      Object.entries(l.coverages).forEach(([code, c]) => {
        if (c.checked && Number(c.limit) > 0) total += coveragePremium(li, code, c);
      });
    });
    return total;
  }, [locations, serverPrem]); // eslint-disable-line react-hooks/exhaustive-deps

  // ---- Validate -----------------------------------------------------------
  const validateStep = (s) => {
    const e = {};
    if (s === 0) {
      if (!product) e.product = 'Vui lòng chọn một sản phẩm bảo hiểm.';
    }
    if (s === 1) {
      if (!insured.hoTen.trim()) e.hoTen = 'Vui lòng nhập họ tên người được bảo hiểm.';
      if (!isValidEmail(insured.email)) e.email = 'Email chưa đúng định dạng.';
      if (!isValidPhone(insured.phone)) e.phone = 'Số điện thoại chưa hợp lệ.';
      if (!insured.diaChi.trim()) e.diaChi = 'Vui lòng nhập địa chỉ liên hệ.';
      locations.forEach((l, i) => {
        if (!l.tenDiaDiem.trim()) e[`loc${i}_name`] = 'Vui lòng nhập tên địa điểm.';
        if (!l.diaChi.trim()) e[`loc${i}_addr`] = 'Vui lòng nhập địa chỉ của địa điểm.';
      });
    }
    if (s === 2) {
      let anyChecked = false;
      locations.forEach((l, i) => {
        Object.entries(l.coverages).forEach(([code, c]) => {
          if (!c.checked) return;
          anyChecked = true;
          const limit = Number(c.limit);
          const ded = Number(c.deductible) || 0;
          if (!Number.isFinite(limit) || limit <= 0) {
            e[`cov${i}_${code}_limit`] = 'Hạn mức phải lớn hơn 0.';
          } else if (ded >= limit) {
            e[`cov${i}_${code}_deductible`] = 'Mức khấu trừ phải nhỏ hơn hạn mức.';
          }
        });
      });
      if (!anyChecked) e.coverages = 'Vui lòng chọn ít nhất một gói bảo hiểm cho ít nhất một địa điểm.';
    }
    setErrors(e);
    if (Object.keys(e).length > 0) toast.error('Vui lòng kiểm tra lại các thông tin chưa hợp lệ.');
    return Object.keys(e).length === 0;
  };

  const dirRef = useRef(1); // hướng chuyển bước: 1 = tới, -1 = lui
  const next = () => { dirRef.current = 1; if (validateStep(step)) setStep((s) => Math.min(s + 1, 3)); };
  const back = () => { dirRef.current = -1; setStep((s) => Math.max(s - 1, 0)); };

  // ---- Payload & submit ---------------------------------------------------
  /** Sinh số hợp đồng duy nhất (backend yêu cầu client cung cấp). */
  const genPolicyNumber = () => {
    const d = new Date();
    const ymd = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`;
    const rnd = Math.random().toString(36).slice(2, 6).toUpperCase();
    return `HD-${ymd}-${rnd}`;
  };

  const buildPayload = () => ({
    policyNumber: genPolicyNumber(),
    insured: {
      name: insured.hoTen.trim(),
      email: insured.email.trim().toLowerCase(),
      phone: insured.phone.trim(),
      address: insured.diaChi.trim(),
      type: insured.loaiKH,
    },
    locations: locations.map((l, i) => ({
      locationId: i + 1,
      name: l.tenDiaDiem.trim(),
      address: l.diaChi.trim(),
      coverages: Object.entries(l.coverages)
        .filter(([, c]) => c.checked && Number(c.limit) > 0)
        .map(([code, c]) => {
          const cat = COVERAGE_CATALOG.find((x) => x.code === code);
          return {
            coverageCode: code,
            coverageName: cat.name,
            coverageType: cat.type,
            limit: Number(c.limit),
            deductible: Number(c.deductible) || 0,
            termMonths: TERM_MONTHS,
            premium: Math.round(coveragePremium(i, code, c)),
          };
        }),
    })),
  });

  const doCreate = async () => {
    setSaving(true);
    try {
      const res = await policyService.createPolicy(buildPayload());
      const n = res?.data?.policyNumber ?? res?.policyNumber;
      if (!n) throw new Error('Máy chủ không trả về số hợp đồng. Vui lòng kiểm tra lại trong trang chủ.');
      return n;
    } finally {
      setSaving(false);
    }
  };

  const saveDraft = async () => {
    try {
      const n = await doCreate();
      toast.success(`Đã lưu bản nháp hợp đồng ${n}.`);
      navigate(`/hop-dong/${n}`);
    } catch (err) {
      toast.error(err.message || 'Không lưu được bản nháp.');
    }
  };

  const requestQuote = async () => {
    try {
      const n = await doCreate();
      try {
        await policyService.transition(n, 'QUOTED');
        toast.success(`Đã gửi yêu cầu báo giá cho hợp đồng ${n}.`);
      } catch (tErr) {
        if (isBackendMissing(tErr)) {
          toast.info('Đã lưu bản nháp. Backend chưa hỗ trợ chuyển trạng thái tự động.');
        } else {
          toast.error(tErr.message || 'Không gửi được yêu cầu báo giá.');
        }
      }
      navigate(`/hop-dong/${n}`);
    } catch (err) {
      toast.error(err.message || 'Không tạo được hợp đồng.');
    }
  };

  const err = (k) => errors[k] && <div className="form-error">{errors[k]}</div>;

  return (
    <>
      <div className="page-header">
        <h1>Mua bảo hiểm</h1>
        <p>Hoàn tất 4 bước để tạo hợp đồng bảo hiểm mới cho bạn.</p>
      </div>

      {/* Progress */}
      <div className="wizard-progress" role="list" aria-label="Tiến trình mua bảo hiểm">
        {STEP_LABELS.map((label, i) => (
          <div key={label} className={`wizard-step${i < step ? ' done' : ''}${i === step ? ' current' : ''}`} role="listitem">
            <motion.div
              className="w-dot"
              initial={false}
              animate={{ scale: i === step ? 1.12 : 1 }}
              transition={{ type: 'spring', stiffness: 380, damping: 20 }}
            >
              {i < step ? <Check size={16} /> : i + 1}
            </motion.div>
            <div className="w-label">{label}</div>
            {i < STEP_LABELS.length - 1 && <div className="wizard-line" />}
          </div>
        ))}
      </div>

      {/* Các bước — trượt theo hướng đi */}
      <AnimatePresence mode="wait" initial={false}>
        <motion.div
          key={step}
          initial={{ opacity: 0, x: 48 * dirRef.current }}
          animate={{ opacity: 1, x: 0 }}
          exit={{ opacity: 0, x: -48 * dirRef.current }}
          transition={{ duration: 0.32, ease: [0.22, 1, 0.36, 1] }}
        >
      {/* BƯỚC 1: Chọn sản phẩm */}
      {step === 0 && (
        <div>
          <div className="product-grid">
            {PRODUCTS.map((p) => (
              <button
                key={p.key}
                type="button"
                className={`glass-card product-card${product === p.key ? ' selected' : ''}`}
                onClick={() => setProduct(p.key)}
                aria-pressed={product === p.key}
              >
                <p.icon size={32} style={{ color: product === p.key ? '#1663d9' : 'var(--text-secondary)', marginBottom: '0.75rem' }} />
                <div style={{ fontWeight: 700, marginBottom: '0.4rem' }}>{p.name}</div>
                <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{p.desc}</div>
              </button>
            ))}
          </div>
          {err('product')}
        </div>
      )}

      {/* BƯỚC 2: Thông tin & địa điểm */}
      {step === 1 && (
        <div className="glass-card">
          <h3 style={{ marginBottom: '1.25rem' }}>Người được bảo hiểm</h3>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '1rem', marginBottom: '1.75rem' }}>
            <div className="form-group">
              <label className="form-label" htmlFor="ins-name">Họ và tên *</label>
              <input id="ins-name" className="form-control" value={insured.hoTen} onChange={(e) => setIns('hoTen', e.target.value)} placeholder="Nguyễn Văn A" />
              {err('hoTen')}
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="ins-email">Email *</label>
              <input id="ins-email" type="email" className="form-control" value={insured.email} onChange={(e) => setIns('email', e.target.value)} placeholder="ban@example.com" />
              {err('email')}
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="ins-phone">Số điện thoại *</label>
              <input id="ins-phone" className="form-control" value={insured.phone} onChange={(e) => setIns('phone', e.target.value)} placeholder="09xxxxxxxx" />
              {err('phone')}
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="ins-type">Loại khách hàng</label>
              <select id="ins-type" className="form-control" value={insured.loaiKH} onChange={(e) => setIns('loaiKH', e.target.value)}>
                <option value="CA_NHAN">Cá nhân</option>
                <option value="DOANH_NGHIEP">Doanh nghiệp</option>
              </select>
            </div>
            <div className="form-group" style={{ gridColumn: '1 / -1' }}>
              <label className="form-label" htmlFor="ins-addr">Địa chỉ liên hệ *</label>
              <input id="ins-addr" className="form-control" value={insured.diaChi} onChange={(e) => setIns('diaChi', e.target.value)} placeholder="Số nhà, đường, phường/xã, quận/huyện, tỉnh/thành" />
              {err('diaChi')}
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
            <h3>Địa điểm được bảo hiểm</h3>
            <button type="button" className="btn btn-secondary btn-sm" onClick={addLocation}>
              <Plus size={15} /> Thêm địa điểm
            </button>
          </div>
          {locations.map((l, i) => (
            <div key={i} className="coverage-option" style={{ marginBottom: '1rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '0.9rem' }}>
                <MapPin size={18} style={{ color: '#6ee7b7', flexShrink: 0 }} />
                <strong>Địa điểm {i + 1}</strong>
                {locations.length > 1 && (
                  <button
                    type="button"
                    className="btn btn-ghost btn-icon btn-sm"
                    style={{ marginLeft: 'auto' }}
                    onClick={() => removeLocation(i)}
                    aria-label={`Xóa địa điểm ${i + 1}`}
                    title="Xóa địa điểm"
                  >
                    <Trash2 size={16} />
                  </button>
                )}
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '1rem' }}>
                <div className="form-group" style={{ marginBottom: 0 }}>
                  <label className="form-label" htmlFor={`loc${i}-name`}>Tên địa điểm *</label>
                  <input id={`loc${i}-name`} className="form-control" value={l.tenDiaDiem} onChange={(e) => setLoc(i, 'tenDiaDiem', e.target.value)} placeholder="Ví dụ: Kho hàng Quận 7" />
                  {err(`loc${i}_name`)}
                </div>
                <div className="form-group" style={{ marginBottom: 0 }}>
                  <label className="form-label" htmlFor={`loc${i}-addr`}>Địa chỉ *</label>
                  <input id={`loc${i}-addr`} className="form-control" value={l.diaChi} onChange={(e) => setLoc(i, 'diaChi', e.target.value)} placeholder="Số nhà, đường, phường/xã…" />
                  {err(`loc${i}_addr`)}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* BƯỚC 3: Gói bảo hiểm & phí */}
      {step === 2 && (
        <div>
          {locations.map((l, i) => (
            <div key={i} className="glass-card" style={{ marginBottom: '1.25rem' }}>
              <h3 style={{ marginBottom: '1rem' }}>
                <MapPin size={17} style={{ verticalAlign: '-3px', marginRight: 6, color: '#6ee7b7' }} />
                {l.tenDiaDiem || `Địa điểm ${i + 1}`}
              </h3>
              {COVERAGE_CATALOG.map((cat) => {
                const c = l.coverages[cat.code];
                const open = c.checked;
                return (
                  <div key={cat.code} className={`coverage-option${open ? ' open' : ''}`}>
                    <label className="coverage-head">
                      <input
                        type="checkbox"
                        checked={open}
                        onChange={(e) => setCov(i, cat.code, 'checked', e.target.checked)}
                        aria-label={`Chọn gói ${cat.name} cho ${l.tenDiaDiem || `địa điểm ${i + 1}`}`}
                      />
                      <span className="c-name">{cat.name}</span>
                      <span className="c-code">{cat.code}</span>
                    </label>
                    {open && (
                      <div className="coverage-fields">
                        <div className="form-group" style={{ marginBottom: 0 }}>
                          <label className="form-label">Hạn mức bảo hiểm (VND) *</label>
                          <input
                            type="number" min="1" className="form-control"
                            value={c.limit}
                            onChange={(e) => setCov(i, cat.code, 'limit', e.target.value)}
                            placeholder="Ví dụ: 1000000000"
                          />
                          {err(`cov${i}_${cat.code}_limit`)}
                        </div>
                        <div className="form-group" style={{ marginBottom: 0 }}>
                          <label className="form-label">Mức khấu trừ (VND)</label>
                          <input
                            type="number" min="0" className="form-control"
                            value={c.deductible}
                            onChange={(e) => setCov(i, cat.code, 'deductible', e.target.value)}
                            placeholder="Ví dụ: 10000000"
                          />
                          {err(`cov${i}_${cat.code}_deductible`)}
                        </div>
                        <div className="coverage-premium">
                          Phí ước tính: {formatMoney(Number(c.limit) > 0 ? coveragePremium(i, cat.code, c) : 0)}
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          ))}
          {err('coverages')}
          <div className="glass-card" style={{ marginTop: '1rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '1rem', flexWrap: 'wrap' }}>
              <div>
                <div style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>Tổng phí ước tính</div>
                <div className="form-hint">Phí ước tính, có thể thay đổi khi thẩm định.</div>
              </div>
              <div style={{ fontSize: '1.75rem', fontWeight: 800, color: '#6ee7b7' }}>{formatMoney(totalPremium)}</div>
            </div>
          </div>
        </div>
      )}

      {/* BƯỚC 4: Xác nhận */}
      {step === 3 && (
        <div className="glass-card">
          <h3 style={{ marginBottom: '1.25rem' }}>Xác nhận thông tin hợp đồng</h3>

          <h4 style={{ marginBottom: '0.6rem' }}>Người được bảo hiểm</h4>
          <dl className="kv" style={{ marginBottom: '1.5rem' }}>
            <dt>Họ tên</dt><dd>{insured.hoTen}</dd>
            <dt>Email</dt><dd>{insured.email}</dd>
            <dt>Điện thoại</dt><dd>{insured.phone}</dd>
            <dt>Địa chỉ</dt><dd>{insured.diaChi}</dd>
            <dt>Loại khách hàng</dt><dd>{insured.loaiKH === 'DOANH_NGHIEP' ? 'Doanh nghiệp' : 'Cá nhân'}</dd>
            <dt>Sản phẩm</dt><dd>{PRODUCTS.find((p) => p.key === product)?.name}</dd>
          </dl>

          <h4 style={{ marginBottom: '0.6rem' }}>Địa điểm & gói bảo hiểm</h4>
          <div className="table-wrap" style={{ marginBottom: '1.5rem' }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Địa điểm</th>
                  <th>Gói bảo hiểm</th>
                  <th>Hạn mức</th>
                  <th>Khấu trừ</th>
                  <th>Phí ước tính</th>
                </tr>
              </thead>
              <tbody>
                {locations.map((l, i) =>
                  Object.entries(l.coverages)
                    .filter(([, c]) => c.checked && Number(c.limit) > 0)
                    .map(([code, c]) => (
                      <tr key={`${i}-${code}`}>
                        <td>{l.tenDiaDiem}</td>
                        <td>{COVERAGE_CATALOG.find((x) => x.code === code)?.name}</td>
                        <td>{formatMoney(c.limit)}</td>
                        <td>{formatMoney(Number(c.deductible) || 0)}</td>
                        <td>{formatMoney(coveragePremium(i, code, c))}</td>
                      </tr>
                    )),
                )}
              </tbody>
            </table>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'baseline', gap: '1rem', marginBottom: '1.75rem' }}>
            <span style={{ color: 'var(--text-secondary)' }}>Tổng phí:</span>
            <span style={{ fontSize: '1.75rem', fontWeight: 800, color: '#6ee7b7' }}>{formatMoney(totalPremium)}</span>
          </div>
          <p className="form-hint" style={{ marginBottom: '1rem' }}>
            Phí trên là ước tính, có thể thay đổi khi thẩm định. Bản nháp có thể chỉnh sửa sau.
          </p>

          <div className="wizard-actions" style={{ marginTop: 0 }}>
            <button type="button" className="btn btn-secondary" onClick={back} disabled={saving}>
              <ArrowLeft size={16} /> Quay lại
            </button>
            <div style={{ display: 'flex', gap: '0.8rem', flexWrap: 'wrap' }}>
              <button type="button" className="btn btn-ghost" onClick={saveDraft} disabled={saving}>
                {saving ? <span className="spinner" /> : <Save size={16} />}
                Lưu nháp
              </button>
              <button type="button" className="btn btn-primary" onClick={requestQuote} disabled={saving}>
                {saving ? <span className="spinner" /> : <Send size={16} />}
                Gửi yêu cầu báo giá
              </button>
            </div>
          </div>
        </div>
      )}

        </motion.div>
      </AnimatePresence>

      {/* Điều hướng bước */}
      {step < 3 && (
        <div className="wizard-actions">
          <button type="button" className="btn btn-secondary" onClick={back} disabled={step === 0}>
            <ArrowLeft size={16} /> Quay lại
          </button>
          <button type="button" className="btn btn-primary" onClick={next}>
            Tiếp tục <ArrowRight size={16} />
          </button>
        </div>
      )}
    </>
  );
}
