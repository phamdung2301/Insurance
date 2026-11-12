import React, { useState, useEffect, useMemo, useRef } from 'react';
import { getUserMessage } from '../utils/messages';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Flame, Scale, HardHat, Truck, MapPin, Plus, Trash2,
  ArrowLeft, ArrowRight, Check, Save, Send, FileText,
} from 'lucide-react';
import { TERMS_VERSION, TERMS_SECTIONS } from '../config/legal';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { policyService, publicAgentService } from '../services/api';
import { getAgentRef } from '../utils/agentRef';
import { AgentPicker } from '../components/AgentPicker';
import { isBackendMissing } from '../utils/backend';
import { formatMoney, isValidEmail } from '../utils/format';
import {
  MAX_LIMIT,
  normalizePhone,
  normalizeSpaces,
  isValidAddress,
  isValidLocationName,
  isValidPersonName,
  isValidVnPhone,
} from '../utils/validation';
import { COVERAGE_RATES, estimatePremium } from '../utils/premium';
import MoneyInput from '../components/MoneyInput';

const TERM_MONTHS = 12;
/** Tối đa 10 địa điểm mỗi hợp đồng (N-07). */
const MAX_LOCATIONS = 10;

/** 4 sản phẩm bảo hiểm ở bước 1. */
const PRODUCTS = [
  { key: 'chay-no-tai-san', name: 'Bảo hiểm cháy nổ tài sản', desc: 'Bảo vệ nhà xưởng, kho bãi, văn phòng trước cháy nổ', icon: Flame },
  { key: 'trach-nhiem-cong-cong', name: 'Bảo hiểm trách nhiệm công cộng', desc: 'Bảo vệ trách nhiệm pháp lý với bên thứ ba', icon: Scale },
  { key: 'tai-nan-lao-dong', name: 'Bảo hiểm tai nạn lao động', desc: 'Bảo vệ người lao động tại nơi làm việc', icon: HardHat },
  { key: 'hang-hoa-van-chuyen', name: 'Bảo hiểm hàng hóa vận chuyển', desc: 'Bảo vệ hàng hóa trong quá trình vận chuyển', icon: Truck },
];

/** Catalog gói bảo hiểm ở bước 3 — baseRate đồng bộ BE PremiumCalculationService.BASE_RATES. */
const COVERAGE_CATALOG = [
  { code: 'PROP_FIRE', name: 'Cháy nổ tài sản', baseRate: COVERAGE_RATES.PROP_FIRE, type: 'PROPERTY' },
  { code: 'PROP_WATER', name: 'Ngập nước', baseRate: COVERAGE_RATES.PROP_WATER, type: 'PROPERTY' },
  { code: 'LIAB_PUBLIC', name: 'Trách nhiệm công cộng', baseRate: COVERAGE_RATES.LIAB_PUBLIC, type: 'LIABILITY' },
  { code: 'WCOMP', name: 'Tai nạn lao động', baseRate: COVERAGE_RATES.WCOMP, type: 'WORKERS_COMP' },
];

const STEP_LABELS = ['Chọn sản phẩm', 'Thông tin & địa điểm', 'Gói bảo hiểm & phí', 'Xác nhận'];

const emptyCoverage = () => ({ checked: false, limit: '', deductible: '' });
const emptyLocation = () => ({
  tenDiaDiem: '',
  diaChi: '',
  coverages: Object.fromEntries(COVERAGE_CATALOG.map((c) => [c.code, emptyCoverage()])),
});

/** Ước tính phí dùng chung — xem utils/premium.js (đồng bộ công thức BE). */

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
  const [dupWarnings, setDupWarnings] = useState({});
  const [termsError, setTermsError] = useState(null);
  const [saving, setSaving] = useState(false);

  // SaaS Phase 2 Track C: dấu đại lý từ link /a/<slug> (localStorage
  // 'agentRef'). Banner minh bạch: resolve tên đại lý để khách biết mình mua
  // qua ai (không gắn lén). Resolve lỗi 410/404 → banner cảnh báo (lúc tạo
  // policy server vẫn báo lỗi rõ ràng); không có agentRef → picker chọn
  // đại lý (server vẫn 400 nếu cố tạo khi chưa chọn — Q3 cấm mua vãng lai).
  const [agentRef] = useState(() => getAgentRef());
  // Điều khoản & chính sách: bắt buộc tick đồng ý ở bước Xác nhận trước khi
  // gửi yêu cầu (server chặn 400 nếu thiếu — không tin nút bấm).
  const [termsAgreed, setTermsAgreed] = useState(false);
  const [agentBanner, setAgentBanner] = useState(null); // { name } | { dead: true } | null
  useEffect(() => {
    if (!agentRef) return;
    let cancelled = false;
    (async () => {
      try {
        const res = await publicAgentService.resolveSlug(agentRef);
        const data = res?.data ?? res;
        if (!cancelled) setAgentBanner({ name: data?.displayName || agentRef });
      } catch (err) {
        if (!cancelled && (err?.status === 410 || err?.status === 404)) setAgentBanner({ dead: true });
      }
    })();
    return () => { cancelled = true; };
  }, [agentRef]);

  // Chặn chuyển bước / tạo hợp đồng khi chưa có đại lý hợp lệ — báo NGAY
  // tại khu vực chọn đại lý (đầu trang, trước bước 1), không đợi tới bước
  // Xác nhận mới toast lỗi server. agentReady = có agentRef và link chưa
  // chết; lúc banner đang resolve (chưa rõ sống/chết) thì cho qua, server
  // là lưới cuối.
  const agentSectionRef = useRef(null);
  const [agentError, setAgentError] = useState(null);
  const agentReady = !!agentRef && !agentBanner?.dead;
  useEffect(() => { if (agentReady) setAgentError(null); }, [agentReady]);
  const requireAgent = () => {
    if (agentReady) { setAgentError(null); return true; }
    setAgentError(agentBanner?.dead
      ? 'Trang đại lý bạn vừa mở hiện không còn hoạt động. Hãy chọn một đại lý khác ở phía trên để tiếp tục.'
      : 'Bạn chưa chọn đại lý đồng hành. Hãy chọn một đại lý ở phía trên để tiếp tục.');
    agentSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    return false;
  };

  const setIns = (k, v) => setInsured((p) => ({ ...p, [k]: v }));
  const setLoc = (i, k, v) => setLocations((p) => p.map((l, j) => (j === i ? { ...l, [k]: v } : l)));
  const setCov = (i, code, k, v) => setLocations((p) =>
    p.map((l, j) => (j === i
      ? { ...l, coverages: { ...l.coverages, [code]: { ...l.coverages[code], [k]: v } } }
      : l)));

  const addLocation = () => {
    if (locations.length >= MAX_LOCATIONS) {
      toast.error('Mỗi hợp đồng tối đa 10 địa điểm.');
      return;
    }
    setLocations((p) => [...p, emptyLocation()]);
  };
  // N-05: xóa địa điểm có dữ liệu thì hỏi xác nhận trước (không hoàn tác được).
  const removeLocation = (i) => {
    if (locations.length <= 1) return;
    const target = locations[i];
    const hasData = normalizeSpaces(target.tenDiaDiem) || normalizeSpaces(target.diaChi)
      || Object.values(target.coverages).some((c) => c.checked || c.limit !== '' || c.deductible !== '');
    if (hasData && !window.confirm(
      `Xóa địa điểm ${i + 1}${target.tenDiaDiem ? ` (${target.tenDiaDiem})` : ''} cùng toàn bộ gói bảo hiểm đã nhập?`,
    )) return;
    setLocations((p) => p.filter((_, j) => j !== i));
  };

  // ---- Ước tính phí -------------------------------------------------------
  // Chỉ dùng công thức FE đã đồng bộ BE (utils/premium.js). Luồng gọi phí
  // server POST /excel/calculate đã bỏ (N-09): không có controller nào map
  // /excel/** trong BE nên phí server không bao giờ về được.
  const coveragePremium = (li, code, c) => {
    const cat = COVERAGE_CATALOG.find((x) => x.code === code);
    return estimatePremium({
      limit: c.limit,
      baseRate: cat.baseRate,
      termMonths: TERM_MONTHS,
      coverageType: cat.type,
      deductible: c.deductible,
    });
  };

  const totalPremium = useMemo(() => {
    let total = 0;
    locations.forEach((l, li) => {
      Object.entries(l.coverages).forEach(([code, c]) => {
        if (c.checked && Number(c.limit) > 0) total += coveragePremium(li, code, c);
      });
    });
    return total;
  }, [locations]); // eslint-disable-line react-hooks/exhaustive-deps

  // ---- Validate -----------------------------------------------------------
  /** Lỗi của 1 coverage đã tick: { limit?: string, deductible?: string }. */
  const coverageFieldErrors = (c) => {
    const limit = Number(c.limit);
    const ded = Number(c.deductible) || 0;
    if (c.limit === '' || c.limit == null || !Number.isFinite(limit) || limit <= 0) {
      return { limit: 'Hạn mức phải lớn hơn 0.' };
    }
    if (!Number.isInteger(limit)) {
      return { limit: 'Hạn mức chỉ nhập số nguyên (VND), không nhập số lẻ.' };
    }
    if (limit > MAX_LIMIT) {
      return { limit: 'Hạn mức tối đa 10 tỷ VND cho mỗi quyền lợi.' };
    }
    if (!Number.isInteger(ded)) {
      return { deductible: 'Mức khấu trừ chỉ nhập số nguyên (VND), không nhập số lẻ.' };
    }
    if (ded < 0) {
      return { deductible: 'Mức khấu trừ không được âm.' };
    }
    if (ded >= limit) {
      return { deductible: 'Mức khấu trừ phải nhỏ hơn hạn mức.' };
    }
    return {};
  };

  /** Cảnh báo nhẹ (không chặn) khi trùng tên/địa chỉ trong cùng hợp đồng (N-11). */
  const findDuplicateWarnings = () => {
    const w = {};
    const seenNames = new Map();
    const seenAddrs = new Map();
    locations.forEach((l, i) => {
      const nm = normalizeSpaces(l.tenDiaDiem).toLowerCase();
      const ad = normalizeSpaces(l.diaChi).toLowerCase();
      if (nm) {
        if (seenNames.has(nm)) {
          w[`loc${i}_name`] = 'Tên địa điểm này trùng với một địa điểm khác trong hợp đồng.';
          w[`loc${seenNames.get(nm)}_name`] = 'Tên địa điểm này trùng với một địa điểm khác trong hợp đồng.';
        } else seenNames.set(nm, i);
      }
      if (ad) {
        if (seenAddrs.has(ad)) {
          w[`loc${i}_addr`] = 'Địa chỉ này trùng với một địa điểm khác trong hợp đồng.';
          w[`loc${seenAddrs.get(ad)}_addr`] = 'Địa chỉ này trùng với một địa điểm khác trong hợp đồng.';
        } else seenAddrs.set(ad, i);
      }
    });
    return w;
  };

  /** Thu thập lỗi của 1 bước (không set state, không toast) — dùng chung cho gate từng bước và validate toàn bộ lúc submit. */
  const collectStepErrors = (s) => {
    const e = {};
    if (s === 0) {
      if (!product) e.product = 'Vui lòng chọn một sản phẩm bảo hiểm.';
    }
    if (s === 1) {
      const name = normalizeSpaces(insured.hoTen);
      if (!name) e.hoTen = 'Vui lòng nhập họ tên người được bảo hiểm.';
      else if (!isValidPersonName(name)) e.hoTen = 'Tên phải từ 2 đến 100 ký tự, chỉ gồm chữ cái và khoảng trắng.';
      if (!isValidEmail(insured.email)) e.email = 'Email chưa đúng định dạng.';
      else if (insured.email.trim().length > 100) e.email = 'Email tối đa 100 ký tự.';
      if (!isValidVnPhone(insured.phone)) e.phone = 'Số điện thoại phải là số di động Việt Nam 10 chữ số, bắt đầu bằng 03, 05, 07, 08 hoặc 09.';
      const addr = normalizeSpaces(insured.diaChi);
      if (!addr) e.diaChi = 'Vui lòng nhập địa chỉ liên hệ.';
      else if (!isValidAddress(addr)) e.diaChi = 'Địa chỉ cần ghi rõ số nhà, đường, phường/xã, quận/huyện, tỉnh/thành (tối thiểu 10 ký tự).';
      locations.forEach((l, i) => {
        const locName = normalizeSpaces(l.tenDiaDiem);
        if (!locName) e[`loc${i}_name`] = 'Vui lòng nhập tên địa điểm.';
        else if (!isValidLocationName(locName)) e[`loc${i}_name`] = 'Tên địa điểm phải từ 3 đến 100 ký tự.';
        const locAddr = normalizeSpaces(l.diaChi);
        if (!locAddr) e[`loc${i}_addr`] = 'Vui lòng nhập địa chỉ của địa điểm.';
        else if (!isValidAddress(locAddr)) e[`loc${i}_addr`] = 'Địa chỉ cần ghi rõ số nhà, đường, phường/xã, quận/huyện, tỉnh/thành (tối thiểu 10 ký tự).';
      });
    }
    if (s === 2) {
      let anyChecked = false;
      locations.forEach((l, i) => {
        Object.entries(l.coverages).forEach(([code, c]) => {
          if (!c.checked) return;
          anyChecked = true;
          const ce = coverageFieldErrors(c);
          if (ce.limit) e[`cov${i}_${code}_limit`] = ce.limit;
          if (ce.deductible) e[`cov${i}_${code}_deductible`] = ce.deductible;
        });
      });
      if (!anyChecked) e.coverages = 'Vui lòng chọn ít nhất một gói bảo hiểm cho ít nhất một địa điểm.';
    }
    return e;
  };

  const validateStep = (s) => {
    const e = collectStepErrors(s);
    setErrors(e);
    setDupWarnings(s === 1 ? findDuplicateWarnings() : {});
    if (Object.keys(e).length > 0) toast.error('Vui lòng kiểm tra lại các thông tin chưa hợp lệ.');
    return Object.keys(e).length === 0;
  };

  /** Validate toàn bộ các bước (B-04) — gọi trước doCreate() ở cả 2 nút submit; lỗi thì quay về bước đầu tiên có lỗi. */
  const validateAll = () => {
    const e = { ...collectStepErrors(0), ...collectStepErrors(1), ...collectStepErrors(2) };
    setErrors(e);
    setDupWarnings(findDuplicateWarnings());
    if (Object.keys(e).length === 0) return true;
    toast.error('Vui lòng kiểm tra lại các thông tin chưa hợp lệ.');
    dirRef.current = -1;
    if (e.product) setStep(0);
    else if (Object.keys(e).some((k) => ['hoTen', 'email', 'phone', 'diaChi'].includes(k) || k.startsWith('loc'))) setStep(1);
    else setStep(2);
    return false;
  };

  /** Validate 1 field lúc blur (N-03): email/SĐT. */
  const validateField = (key) => {
    setErrors((prev) => {
      const next = { ...prev };
      if (key === 'email') {
        if (!isValidEmail(insured.email)) next.email = 'Email chưa đúng định dạng.';
        else if (insured.email.trim().length > 100) next.email = 'Email tối đa 100 ký tự.';
        else delete next.email;
      }
      if (key === 'phone') {
        if (!isValidVnPhone(insured.phone)) next.phone = 'Số điện thoại phải là số di động Việt Nam 10 chữ số, bắt đầu bằng 03, 05, 07, 08 hoặc 09.';
        else delete next.phone;
      }
      return next;
    });
  };

  /** Validate 1 coverage lúc blur khỏi ô tiền (N-03). */
  const validateMoneyField = (i, code) => {
    const c = locations[i]?.coverages[code];
    if (!c || !c.checked) return;
    const ce = coverageFieldErrors(c);
    setErrors((prev) => {
      const next = { ...prev };
      const lk = `cov${i}_${code}_limit`;
      const dk = `cov${i}_${code}_deductible`;
      if (ce.limit) next[lk] = ce.limit; else delete next[lk];
      if (ce.deductible) next[dk] = ce.deductible; else delete next[dk];
      return next;
    });
  };

  const dirRef = useRef(1); // hướng chuyển bước: 1 = tới, -1 = lui
  const next = () => {
    dirRef.current = 1;
    // Điều kiện tiên quyết của cả luồng (đại lý) thuộc về bước 1: chưa đủ
    // thì báo ngay tại khu vực chọn đại lý, không cho đi tiếp.
    if (step === 0 && !requireAgent()) return;
    if (validateStep(step)) setStep((s) => Math.min(s + 1, 3));
  };
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
    termsAccepted: termsAgreed === true,
    termsVersion: termsAgreed ? TERMS_VERSION : null,
    // Sản phẩm đã chọn ở bước 1 (label hiển thị) — trước đây không gửi lên server.
    product: PRODUCTS.find((p) => p.key === product)?.name ?? null,
    policyNumber: genPolicyNumber(),
    // Track C: gửi kèm slug đại lý từ agentRef — server resolve + validate
    // (chỉ ACTIVE), gắn agentId, bỏ qua mọi agentId client gửi. Wizard đã
    // chặn ở bước 1 + kiểm tra lại lúc submit khi chưa có đại lý hợp lệ
    // (requireAgent), nên server 400 thiếu slug chỉ còn là lưới an toàn
    // cuối — không phải nơi đầu tiên user biết mình thiếu đại lý.
    agentSlug: agentRef || undefined,
    insured: {
      name: normalizeSpaces(insured.hoTen),
      email: insured.email.trim().toLowerCase(),
      phone: normalizePhone(insured.phone),
      address: normalizeSpaces(insured.diaChi),
      type: insured.loaiKH,
    },
    locations: locations.map((l, i) => ({
      locationId: i + 1,
      name: normalizeSpaces(l.tenDiaDiem),
      address: normalizeSpaces(l.diaChi),
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

  // Kiểm tra lại đại lý trước khi tạo (link có thể chết giữa chừng sau khi
  // đã qua bước 1): chưa đủ thì quay về bước 1 + báo tại khu vực chọn đại
  // lý, không submit rồi mới toast lỗi ở bước Xác nhận.
  const guardAgentForSubmit = () => {
    if (requireAgent()) return true;
    dirRef.current = -1;
    setStep(0);
    return false;
  };

  // B-04/N-02: kiểm tra lại điều khoản trong code (không chỉ dựa nút disabled).
  const guardTermsForSubmit = () => {
    if (termsAgreed === true) { setTermsError(null); return true; }
    setTermsError('Vui lòng đọc và đồng ý Điều khoản & Chính sách bảo hiểm trước khi gửi.');
    return false;
  };

  const saveDraft = async () => {
    if (!guardAgentForSubmit()) return;
    if (!validateAll()) return;
    if (!guardTermsForSubmit()) return;
    try {
      const n = await doCreate();
      toast.success(`Đã lưu bản nháp hợp đồng ${n}.`);
      navigate(`/hop-dong/${n}`);
    } catch (err) {
      toast.error(getUserMessage(err, 'Không lưu được bản nháp.'));
    }
  };

  const requestQuote = async () => {
    if (!guardAgentForSubmit()) return;
    if (!validateAll()) return;
    if (!guardTermsForSubmit()) return;
    try {
      const n = await doCreate();
      try {
        await policyService.transition(n, 'QUOTED');
        toast.success(`Đã gửi yêu cầu báo giá cho hợp đồng ${n}.`);
      } catch (tErr) {
        if (isBackendMissing(tErr)) {
          toast.info('Đã lưu bản nháp. Backend chưa hỗ trợ chuyển trạng thái tự động.');
        } else {
          toast.error(getUserMessage(tErr, 'Không gửi được yêu cầu báo giá.'));
        }
      }
      navigate(`/hop-dong/${n}`);
    } catch (err) {
      toast.error(getUserMessage(err, 'Không tạo được hợp đồng.'));
    }
  };

  const err = (k) => errors[k] && <div className="form-error">{errors[k]}</div>;
  const warn = (k) => dupWarnings[k] && <div className="form-hint" style={{ color: '#f59e0b' }}>{dupWarnings[k]}</div>;

  return (
    <>
      <div className="page-header">
        <h1>Mua bảo hiểm</h1>
        <p>Hoàn tất 4 bước để tạo hợp đồng bảo hiểm mới cho bạn.</p>
      </div>

      {/* Picker chọn đại lý — khách chưa có link vẫn mua được qua một đại lý
          mình chọn (Q3 vẫn giữ: mua luôn gắn với một đại lý). Nút bấm mở
          trang storefront /a/<slug> của đại lý đó, trang này lưu agentRef
          rồi mới cho mua — không gắn lén, không mua vãng lai. */}
      <div ref={agentSectionRef}>
      {!agentRef && (
        <AgentPicker
          title="Chọn một đại lý đồng hành để bắt đầu mua bảo hiểm"
          description="Bạn sẽ tới trang của đại lý đó, rồi bấm “Mua bảo hiểm qua đại lý này” để tiếp tục."
        />
      )}
      {agentRef && agentBanner?.dead && (
        <>
          <p className="form-error" style={{ fontSize: '0.95rem' }}>
            Trang đại lý bạn vừa mở hiện không còn hoạt động.
          </p>
          <AgentPicker title="Chọn một đại lý khác để tiếp tục mua bảo hiểm" />
        </>
      )}
      {agentRef && agentBanner?.name && (
        <p style={{ fontSize: '0.95rem' }}>
          Bạn đang mua qua đại lý {agentBanner.name}.
        </p>
      )}
      {agentError && (
        <p className="form-error" style={{ fontSize: '0.95rem' }}>
          {agentError}
        </p>
      )}
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
              <input id="ins-name" className="form-control" value={insured.hoTen} onChange={(e) => setIns('hoTen', e.target.value)} placeholder="Nguyễn Văn A" maxLength={100} />
              {err('hoTen')}
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="ins-email">Email *</label>
              <input id="ins-email" type="email" className="form-control" value={insured.email} onChange={(e) => setIns('email', e.target.value)} onBlur={() => validateField('email')} placeholder="ban@example.com" maxLength={100} />
              {err('email')}
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="ins-phone">Số điện thoại *</label>
              <input id="ins-phone" type="tel" inputMode="tel" maxLength={13} className="form-control" value={insured.phone} onChange={(e) => setIns('phone', e.target.value)} onBlur={() => validateField('phone')} placeholder="09xxxxxxxx" />
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
              <input id="ins-addr" className="form-control" value={insured.diaChi} onChange={(e) => setIns('diaChi', e.target.value)} placeholder="Số nhà, đường, phường/xã, quận/huyện, tỉnh/thành" maxLength={255} />
              {err('diaChi')}
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
            <h3>Địa điểm được bảo hiểm</h3>
            <button
              type="button"
              className="btn btn-secondary btn-sm"
              onClick={addLocation}
              disabled={locations.length >= MAX_LOCATIONS}
              title={locations.length >= MAX_LOCATIONS ? 'Mỗi hợp đồng tối đa 10 địa điểm.' : undefined}
            >
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
                  <input id={`loc${i}-name`} className="form-control" value={l.tenDiaDiem} onChange={(e) => setLoc(i, 'tenDiaDiem', e.target.value)} placeholder="Ví dụ: Kho hàng Quận 7" maxLength={100} />
                  {err(`loc${i}_name`)}
                  {warn(`loc${i}_name`)}
                </div>
                <div className="form-group" style={{ marginBottom: 0 }}>
                  <label className="form-label" htmlFor={`loc${i}-addr`}>Địa chỉ *</label>
                  <input id={`loc${i}-addr`} className="form-control" value={l.diaChi} onChange={(e) => setLoc(i, 'diaChi', e.target.value)} placeholder="Số nhà, đường, phường/xã…" maxLength={255} />
                  {err(`loc${i}_addr`)}
                  {warn(`loc${i}_addr`)}
                </div>
              </div>
            </div>
          ))}
          {locations.length >= MAX_LOCATIONS && (
            <p className="form-hint">Đã đạt tối đa 10 địa điểm cho mỗi hợp đồng.</p>
          )}
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
                          <MoneyInput
                            className="form-control"
                            value={c.limit}
                            onChange={(v) => setCov(i, cat.code, 'limit', v)}
                            onBlur={() => validateMoneyField(i, cat.code)}
                            placeholder="Ví dụ: 1.000.000.000"
                            max={MAX_LIMIT}
                          />
                          {err(`cov${i}_${cat.code}_limit`)}
                        </div>
                        <div className="form-group" style={{ marginBottom: 0 }}>
                          <label className="form-label">Mức khấu trừ (VND)</label>
                          <MoneyInput
                            className="form-control"
                            value={c.deductible}
                            onChange={(v) => setCov(i, cat.code, 'deductible', v)}
                            onBlur={() => validateMoneyField(i, cat.code)}
                            placeholder="Ví dụ: 10.000.000"
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
            <dt>Thời hạn</dt><dd>12 tháng</dd>
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

          {/* Điều khoản & chính sách — bắt buộc đọc + đồng ý trước khi gửi
              yêu cầu báo giá (cổng vào bước thanh toán) */}
          <div style={{ border: '1px solid var(--border-subtle)', borderRadius: '10px', padding: '1rem 1.1rem', marginBottom: '1.25rem' }}>
            <h4 style={{ marginTop: 0, marginBottom: '0.6rem', display: 'flex', alignItems: 'center', gap: '0.45rem', flexWrap: 'wrap' }}>
              <FileText size={16} /> Điều khoản &amp; Chính sách bảo hiểm
              <span className="badge badge-info">{TERMS_VERSION}</span>
            </h4>
            <div style={{ maxHeight: 240, overflowY: 'auto', fontSize: '0.85rem', color: 'var(--text-secondary)', padding: '0.75rem 0.95rem', background: 'rgba(0,0,0,0.06)', borderRadius: '8px' }}>
              {TERMS_SECTIONS.map((sec) => (
                <div key={sec.title} style={{ marginBottom: '0.9rem' }}>
                  <strong style={{ color: 'var(--text-primary)' }}>{sec.title}</strong>
                  {sec.body.map((p, i) => <p key={i} style={{ margin: '0.35rem 0 0' }}>{p}</p>)}
                </div>
              ))}
            </div>
            <label style={{ display: 'flex', alignItems: 'flex-start', gap: '0.55rem', marginTop: '0.8rem', fontSize: '0.9rem', cursor: 'pointer' }}>
              <input
                type="checkbox"
                checked={termsAgreed}
                onChange={(e) => { setTermsAgreed(e.target.checked); if (e.target.checked) setTermsError(null); }}
                style={{ marginTop: '0.2rem' }}
              />
              <span>
                Tôi đã đọc, hiểu và đồng ý <strong>Điều khoản &amp; Chính sách bảo hiểm</strong> (phiên bản {TERMS_VERSION}),
                cam kết thông tin khai báo trung thực và chấp nhận quy trình bồi thường, thanh toán, bảo vệ dữ liệu nêu trên.
              </span>
            </label>
          </div>

          <div className="wizard-actions" style={{ marginTop: 0 }}>
            <button type="button" className="btn btn-secondary" onClick={back} disabled={saving}>
              <ArrowLeft size={16} /> Quay lại
            </button>
            <div style={{ display: 'flex', gap: '0.8rem', flexWrap: 'wrap' }}>
              <button
                type="button"
                className="btn btn-ghost"
                onClick={saveDraft}
                disabled={saving || !termsAgreed}
                title={termsAgreed ? undefined : 'Hãy đọc và đồng ý Điều khoản & Chính sách trước'}
              >
                {saving ? <span className="spinner" /> : <Save size={16} />}
                Lưu nháp
              </button>
              <button
                type="button"
                className="btn btn-primary"
                onClick={requestQuote}
                disabled={saving || !termsAgreed}
                title={termsAgreed ? undefined : 'Hãy đọc và đồng ý Điều khoản & Chính sách trước'}
              >
                {saving ? <span className="spinner" /> : <Send size={16} />}
                Gửi yêu cầu báo giá
              </button>
            </div>
          </div>
          {!termsAgreed && (
            <p className="form-hint" style={{ marginTop: '0.75rem' }}>
              Vui lòng đọc và đồng ý Điều khoản &amp; Chính sách bảo hiểm trước khi gửi.
            </p>
          )}
          {termsError && (
            <p className="form-error" style={{ marginTop: '0.75rem' }}>{termsError}</p>
          )}
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
