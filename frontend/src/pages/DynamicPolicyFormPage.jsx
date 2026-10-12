import React, { useState, useEffect } from 'react';
import { useNavigate, useParams, Link } from 'react-router-dom';
import { useToast } from '../context/ToastContext';
import { policyService, policyLocationService, policyCoverageService } from '../services/api';
import {
  Shield,
  User,
  MapPin,
  FileCheck2,
  ChevronRight,
  ChevronLeft,
  Plus,
  Trash2,
  Building,
  DollarSign,
  AlertCircle,
  Save,
  Send,
  Sparkles,
  ArrowLeft,
  CheckCircle2,
  Flame,
  Layers,
  Calendar,
  Phone,
  Mail,
  Home,
} from 'lucide-react';

const COVERAGE_OPTIONS = [
  { code: 'PROP_FIRE', name: 'Bảo hiểm Cháy nổ & Rủi ro đặc biệt', defaultRate: 0.0015 },
  { code: 'PROP_THEFT', name: 'Bảo hiểm Trộm cắp tài sản', defaultRate: 0.002 },
  { code: 'GL', name: 'Bảo hiểm Trách nhiệm công cộng (General Liability)', defaultRate: 0.001 },
  { code: 'EL', name: 'Bảo hiểm Trách nhiệm người sử dụng LĐ (Employer Liability)', defaultRate: 0.0012 },
  { code: 'WC', name: 'Bảo hiểm Tai nạn lao động (Workers Comp)', defaultRate: 0.0018 },
  { code: 'CYBER', name: 'Bảo hiểm An ninh mạng & Dữ liệu số (Cyber Risk)', defaultRate: 0.0035 },
];

export const DynamicPolicyFormPage = () => {
  const navigate = useNavigate();
  const { policyNumber: paramPolicyNumber } = useParams();
  const { showToast } = useToast();

  const isEditMode = Boolean(paramPolicyNumber);

  // Stepper state: 1 -> Insured, 2 -> Locations, 3 -> Coverages, 4 -> Review & Finalize
  const [currentStep, setCurrentStep] = useState(1);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // Policy Form State
  const [formData, setFormData] = useState({
    policyNumber: '',
    status: 'DRAFT',
    effectiveDate: '',
    expirationDate: '',
    insured: {
      insuredName: '',
      insuredType: 'BUSINESS',
      email: '',
      phone: '',
      address: {
        street: '',
        city: 'Hà Nội',
        state: '',
        postalCode: '100000',
        country: 'Việt Nam',
      },
    },
    locations: [
      {
        locationId: 'LOC-1',
        locationName: 'Trụ sở chính',
        address: {
          street: '123 Phố Huế, Hoàn Kiếm',
          city: 'Hà Nội',
          state: '',
          postalCode: '100000',
          country: 'Việt Nam',
        },
        constructionType: 'NON_COMBUSTIBLE',
        fireProtectionTier: 1,
        occupancyType: 'OFFICE',
        coverages: [
          {
            coverageCode: 'PROP_FIRE',
            coverageName: 'Bảo hiểm Cháy nổ & Rủi ro đặc biệt',
            limit: 500000000,
            deductible: 5000000,
            premium: 750000,
            termMonths: 12,
          },
        ],
      },
    ],
  });

  // Active Location Index for Step 3 Coverage Editor
  const [activeLocIndex, setActiveLocIndex] = useState(0);

  // Load existing policy if in Edit Mode
  useEffect(() => {
    if (isEditMode) {
      loadExistingPolicy(paramPolicyNumber);
    } else {
      // Auto-generate random policy number if new
      const randomNum = `POL-${new Date().getFullYear()}-${Math.floor(1000 + Math.random() * 9000)}`;
      const today = new Date().toISOString().split('T')[0];
      const nextYear = new Date(Date.now() + 365 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];

      setFormData((prev) => ({
        ...prev,
        policyNumber: randomNum,
        effectiveDate: today,
        expirationDate: nextYear,
      }));
    }
  }, [isEditMode, paramPolicyNumber]);

  const loadExistingPolicy = async (policyId) => {
    try {
      setLoading(true);
      const res = await policyService.getPolicyById(policyId);
      if (res && res.data) {
        const pol = res.data;
        setFormData({
          policyNumber: pol.policyNumber || policyId,
          status: pol.status || 'DRAFT',
          effectiveDate: pol.effectiveDate ? pol.effectiveDate.substring(0, 10) : '',
          expirationDate: pol.expirationDate ? pol.expirationDate.substring(0, 10) : '',
          insured: {
            insuredName: pol.insured?.insuredName || '',
            insuredType: pol.insured?.insuredType || 'BUSINESS',
            email: pol.insured?.email || '',
            phone: pol.insured?.phone || '',
            address: {
              street: pol.insured?.address?.street || '',
              city: pol.insured?.address?.city || 'Hà Nội',
              state: pol.insured?.address?.state || '',
              postalCode: pol.insured?.address?.postalCode || '100000',
              country: pol.insured?.address?.country || 'Việt Nam',
            },
          },
          locations: (pol.locations && pol.locations.length > 0)
            ? pol.locations.map((loc, idx) => ({
                locationId: loc.locationId || `LOC-${idx + 1}`,
                locationName: loc.locationName || `Địa điểm ${idx + 1}`,
                address: loc.address || { street: '', city: 'Hà Nội', state: '', postalCode: '', country: 'Việt Nam' },
                constructionType: loc.constructionType || 'NON_COMBUSTIBLE',
                fireProtectionTier: loc.fireProtectionTier || 1,
                occupancyType: loc.occupancyType || 'OFFICE',
                coverages: (loc.coverages || []).map((cov) => ({
                  coverageCode: cov.coverageCode,
                  coverageName: cov.coverageName || cov.coverageCode,
                  limit: cov.limit || 0,
                  deductible: cov.deductible || 0,
                  premium: cov.premium || 0,
                  termMonths: cov.termMonths || 12,
                })),
              }))
            : [
                {
                  locationId: 'LOC-1',
                  locationName: 'Trụ sở chính',
                  address: { street: '', city: 'Hà Nội', state: '', postalCode: '', country: 'Việt Nam' },
                  constructionType: 'NON_COMBUSTIBLE',
                  fireProtectionTier: 1,
                  occupancyType: 'OFFICE',
                  coverages: [],
                },
              ],
        });
      }
    } catch (err) {
      showToast(err.message || 'Không thể tải thông tin hợp đồng', 'error');
    } finally {
      setLoading(false);
    }
  };

  // Helper: Live Premium Calculation
  const calculateCoveragePremium = (limit, termMonths, coverageCode) => {
    const opt = COVERAGE_OPTIONS.find((c) => c.code === coverageCode);
    const rate = opt ? opt.defaultRate : 0.0015;
    const months = termMonths || 12;
    return Math.round(Number(limit || 0) * rate * (months / 12));
  };

  // Total Premium across all locations and coverages
  const calculateTotalPremium = () => {
    let total = 0;
    (formData.locations || []).forEach((loc) => {
      (loc.coverages || []).forEach((cov) => {
        total += Number(cov.premium || 0);
      });
    });
    return total;
  };

  // ---------------- Handlers for Locations ----------------
  const handleAddLocation = () => {
    const nextIdx = formData.locations.length + 1;
    const newLocation = {
      locationId: `LOC-${nextIdx}`,
      locationName: `Chi nhánh ${nextIdx}`,
      address: {
        street: '',
        city: 'Hà Nội',
        state: '',
        postalCode: '100000',
        country: 'Việt Nam',
      },
      constructionType: 'NON_COMBUSTIBLE',
      fireProtectionTier: 1,
      occupancyType: 'OFFICE',
      coverages: [],
    };
    setFormData((prev) => ({
      ...prev,
      locations: [...prev.locations, newLocation],
    }));
    setActiveLocIndex(formData.locations.length);
  };

  const handleRemoveLocation = (indexToRemove) => {
    if (formData.locations.length <= 1) {
      showToast('Hợp đồng phải có ít nhất 1 địa điểm được bảo hiểm.', 'warning');
      return;
    }
    const updated = formData.locations.filter((_, idx) => idx !== indexToRemove);
    setFormData((prev) => ({ ...prev, locations: updated }));
    if (activeLocIndex >= updated.length) {
      setActiveLocIndex(Math.max(0, updated.length - 1));
    }
  };

  const handleUpdateLocation = (index, field, value) => {
    const updated = [...formData.locations];
    if (field.startsWith('address.')) {
      const addressKey = field.split('.')[1];
      updated[index] = {
        ...updated[index],
        address: { ...updated[index].address, [addressKey]: value },
      };
    } else {
      updated[index] = { ...updated[index], [field]: value };
    }
    setFormData((prev) => ({ ...prev, locations: updated }));
  };

  // ---------------- Handlers for Coverages ----------------
  const handleAddCoverage = (locIndex) => {
    const targetLoc = formData.locations[locIndex];
    if (!targetLoc) return;

    // Pick first coverage code not yet added in this location
    const existingCodes = (targetLoc.coverages || []).map((c) => c.coverageCode);
    const availableOption = COVERAGE_OPTIONS.find((opt) => !existingCodes.includes(opt.code)) || COVERAGE_OPTIONS[0];

    const defaultLimit = 500000000;
    const defaultDeductible = 5000000;
    const defaultTerm = 12;
    const defaultPrem = calculateCoveragePremium(defaultLimit, defaultTerm, availableOption.code);

    const newCoverage = {
      coverageCode: availableOption.code,
      coverageName: availableOption.name,
      limit: defaultLimit,
      deductible: defaultDeductible,
      premium: defaultPrem,
      termMonths: defaultTerm,
    };

    const updatedLocs = [...formData.locations];
    updatedLocs[locIndex] = {
      ...targetLoc,
      coverages: [...(targetLoc.coverages || []), newCoverage],
    };

    setFormData((prev) => ({ ...prev, locations: updatedLocs }));
  };

  const handleRemoveCoverage = (locIndex, covIndex) => {
    const updatedLocs = [...formData.locations];
    const targetLoc = updatedLocs[locIndex];
    if (!targetLoc) return;

    targetLoc.coverages = targetLoc.coverages.filter((_, idx) => idx !== covIndex);
    setFormData((prev) => ({ ...prev, locations: updatedLocs }));
  };

  const handleUpdateCoverage = (locIndex, covIndex, field, value) => {
    const updatedLocs = [...formData.locations];
    const targetLoc = updatedLocs[locIndex];
    if (!targetLoc) return;

    const targetCov = { ...targetLoc.coverages[covIndex] };

    if (field === 'coverageCode') {
      const opt = COVERAGE_OPTIONS.find((c) => c.code === value);
      targetCov.coverageCode = value;
      targetCov.coverageName = opt ? opt.name : value;
      targetCov.premium = calculateCoveragePremium(targetCov.limit, targetCov.termMonths, value);
    } else if (field === 'limit' || field === 'termMonths') {
      const numVal = Number(value);
      targetCov[field] = numVal;
      targetCov.premium = calculateCoveragePremium(
        field === 'limit' ? numVal : targetCov.limit,
        field === 'termMonths' ? numVal : targetCov.termMonths,
        targetCov.coverageCode
      );
    } else {
      targetCov[field] = value;
    }

    targetLoc.coverages[covIndex] = targetCov;
    setFormData((prev) => ({ ...prev, locations: updatedLocs }));
  };

  // ---------------- Validation ----------------
  const validateStep = (step) => {
    if (step === 1) {
      if (!formData.policyNumber.trim()) {
        showToast('Vui lòng nhập số hợp đồng (Policy Number).', 'error');
        return false;
      }
      if (!formData.insured.insuredName.trim()) {
        showToast('Vui lòng nhập tên người được bảo hiểm.', 'error');
        return false;
      }
      if (!formData.insured.email.trim()) {
        showToast('Vui lòng nhập email liên hệ.', 'error');
        return false;
      }
      return true;
    }

    if (step === 2) {
      if (formData.locations.length === 0) {
        showToast('Hợp đồng cần ít nhất 1 địa điểm.', 'error');
        return false;
      }
      for (let i = 0; i < formData.locations.length; i++) {
        const loc = formData.locations[i];
        if (!loc.locationName.trim()) {
          showToast(`Vui lòng nhập tên cho Địa điểm ${i + 1}.`, 'error');
          return false;
        }
      }
      return true;
    }

    if (step === 3) {
      // Must have at least 1 coverage in total
      const totalCovs = formData.locations.reduce((acc, loc) => acc + (loc.coverages?.length || 0), 0);
      if (totalCovs === 0) {
        showToast('Vui lòng thêm ít nhất 1 hạng mục bảo hiểm (Coverage).', 'error');
        return false;
      }
      return true;
    }

    return true;
  };

  const handleNextStep = () => {
    if (validateStep(currentStep)) {
      setCurrentStep((prev) => Math.min(4, prev + 1));
    }
  };

  const handlePrevStep = () => {
    setCurrentStep((prev) => Math.max(1, prev - 1));
  };

  // ---------------- Submission (Save DRAFT or Submit QUOTED) ----------------
  const handleSubmitPolicy = async (targetStatus = 'DRAFT') => {
    if (!validateStep(1) || !validateStep(2) || !validateStep(3)) return;

    try {
      setSubmitting(true);
      const totalPremium = calculateTotalPremium();

      const payload = {
        policyNumber: formData.policyNumber,
        status: targetStatus,
        effectiveDate: formData.effectiveDate ? `${formData.effectiveDate}T00:00:00` : null,
        expirationDate: formData.expirationDate ? `${formData.expirationDate}T23:59:59` : null,
        totalPremium: totalPremium,
        insured: {
          insuredName: formData.insured.insuredName,
          insuredType: formData.insured.insuredType,
          email: formData.insured.email,
          phone: formData.insured.phone,
          address: {
            street: formData.insured.address.street,
            city: formData.insured.address.city,
            state: formData.insured.address.state,
            postalCode: formData.insured.address.postalCode,
            country: formData.insured.address.country,
          },
        },
        locations: formData.locations.map((loc, idx) => ({
          locationId: loc.locationId || `LOC-${idx + 1}`,
          locationName: loc.locationName,
          address: {
            street: loc.address?.street || '',
            city: loc.address?.city || '',
            state: loc.address?.state || '',
            postalCode: loc.address?.postalCode || '',
            country: loc.address?.country || 'Việt Nam',
          },
          constructionType: loc.constructionType,
          fireProtectionTier: Number(loc.fireProtectionTier),
          occupancyType: loc.occupancyType,
          coverages: (loc.coverages || []).map((cov) => ({
            coverageCode: cov.coverageCode,
            coverageName: cov.coverageName,
            limit: Number(cov.limit),
            deductible: Number(cov.deductible),
            premium: Number(cov.premium),
            termMonths: Number(cov.termMonths),
          })),
        })),
      };

      if (isEditMode) {
        await policyService.updatePolicy(formData.policyNumber, payload);
        showToast(`Đã cập nhật hợp đồng ${formData.policyNumber} thành công!`, 'success');
      } else {
        await policyService.createPolicy(payload);
        showToast(`Đã tạo mới hợp đồng ${formData.policyNumber} thành công!`, 'success');
      }

      // If user selected QUOTED from DRAFT, perform status transition
      if (targetStatus === 'QUOTED' && (!isEditMode || formData.status === 'DRAFT')) {
        try {
          await policyService.transitionStatus(formData.policyNumber, {
            toStatus: 'QUOTED',
            reason: 'Chuyển trạng thái yêu cầu báo phí từ Dynamic Form UI03',
          });
          showToast(`Hợp đồng ${formData.policyNumber} đã được chuyển sang trạng thái QUOTED!`, 'info');
        } catch (transitionErr) {
          console.warn('Transition to QUOTED warning:', transitionErr);
        }
      }

      navigate('/policies');
    } catch (err) {
      showToast(err.message || 'Lỗi khi lưu hợp đồng bảo hiểm', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const steps = [
    { num: 1, title: 'Người được Bảo hiểm', desc: 'Thông tin khách hàng & thời hạn', icon: User },
    { num: 2, title: 'Địa điểm Được Bảo hiểm', desc: 'Danh sách cơ sở & rủi ro', icon: MapPin },
    { num: 3, title: 'Hạng mục Bảo hiểm', desc: 'Quyền lợi & Phí bảo hiểm', icon: Shield },
    { num: 4, title: 'Xác nhận & Nộp đơn', desc: 'Tổng kết & Phê duyệt', icon: FileCheck2 },
  ];

  if (loading) {
    return (
      <div style={{ padding: '3rem', textAlign: 'center', color: 'var(--text-secondary)' }}>
        <div className="spinner" style={{ margin: '0 auto 1rem', width: '32px', height: '32px' }}></div>
        <p>Đang tải dữ liệu hợp đồng...</p>
      </div>
    );
  }

  return (
    <div style={{ padding: '2rem', maxWidth: '1280px', margin: '0 auto' }}>
      {/* Top Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.5rem' }}>
            <Link to="/policies" className="btn btn-secondary" style={{ padding: '0.4rem 0.6rem' }}>
              <ArrowLeft size={16} />
            </Link>
            <h1 style={{ fontSize: '1.6rem', fontWeight: 700, margin: 0, color: 'var(--text-primary)' }}>
              {isEditMode ? `Chỉnh sửa Hợp đồng: ${formData.policyNumber}` : 'Tạo Hợp đồng Bảo hiểm Mới'}
            </h1>
          </div>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', margin: 0 }}>
            Triển khai quy trình biểu mẫu động (UI03: Dynamic Multi-Step Form) quản lý Khách hàng, Địa điểm và Gói bảo hiểm.
          </p>
        </div>

        {/* Total Premium Live Pill */}
        <div
          className="glass-card"
          style={{
            padding: '0.75rem 1.25rem',
            display: 'flex',
            alignItems: 'center',
            gap: '1rem',
            background: 'rgba(16, 185, 129, 0.08)',
            borderColor: 'rgba(16, 185, 129, 0.3)',
          }}
        >
          <div>
            <span style={{ fontSize: '0.725rem', color: 'var(--text-muted)', display: 'block', textTransform: 'uppercase' }}>
              Ước tính Tổng Phí
            </span>
            <span style={{ fontSize: '1.35rem', fontWeight: 700, color: '#10b981' }}>
              {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(calculateTotalPremium())}
            </span>
          </div>
          <DollarSign size={28} color="#10b981" />
        </div>
      </div>

      {/* Stepper Navigation Progress Bar */}
      <div
        className="glass-card"
        style={{
          padding: '1.25rem 2rem',
          marginBottom: '2rem',
          display: 'grid',
          gridTemplateColumns: 'repeat(4, 1fr)',
          gap: '1rem',
        }}
      >
        {steps.map((st) => {
          const Icon = st.icon;
          const isActive = currentStep === st.num;
          const isCompleted = currentStep > st.num;
          return (
            <div
              key={st.num}
              onClick={() => {
                if (st.num < currentStep || validateStep(currentStep)) {
                  setCurrentStep(st.num);
                }
              }}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '0.85rem',
                cursor: 'pointer',
                opacity: isActive || isCompleted ? 1 : 0.45,
                transition: 'all var(--transition-fast)',
              }}
            >
              <div
                style={{
                  width: '38px',
                  height: '38px',
                  borderRadius: '50%',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  background: isActive
                    ? 'var(--primary-gradient)'
                    : isCompleted
                    ? '#10b981'
                    : 'rgba(255, 255, 255, 0.08)',
                  color: '#ffffff',
                  fontWeight: 700,
                  fontSize: '0.9rem',
                  boxShadow: isActive ? 'var(--shadow-glow)' : 'none',
                }}
              >
                {isCompleted ? <CheckCircle2 size={20} /> : <Icon size={18} />}
              </div>
              <div style={{ display: 'flex', flexDirection: 'column' }}>
                <span style={{ fontSize: '0.85rem', fontWeight: 600, color: isActive ? '#818cf8' : 'var(--text-primary)' }}>
                  Bước {st.num}: {st.title}
                </span>
                <span style={{ fontSize: '0.725rem', color: 'var(--text-muted)' }}>{st.desc}</span>
              </div>
            </div>
          );
        })}
      </div>

      {/* Main Dynamic Form Card */}
      <div className="glass-card" style={{ padding: '2rem', marginBottom: '2rem' }}>
        {/* ================= STEP 1: INSURED & TIMELINE ================= */}
        {currentStep === 1 && (
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1.5rem' }}>
              <User size={20} color="#6366f1" />
              <h2 style={{ fontSize: '1.25rem', fontWeight: 600, margin: 0 }}>Bước 1: Thông tin Người được Bảo hiểm & Hiệu lực</h2>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1.25rem' }}>
              {/* Policy Number */}
              <div>
                <label className="form-label">Mã Hợp đồng (Policy Number) *</label>
                <input
                  type="text"
                  className="form-control"
                  value={formData.policyNumber}
                  disabled={isEditMode}
                  onChange={(e) => setFormData({ ...formData, policyNumber: e.target.value })}
                  placeholder="VD: POL-2026-001"
                />
              </div>

              {/* Insured Type */}
              <div>
                <label className="form-label">Loại Khách hàng *</label>
                <select
                  className="form-control"
                  value={formData.insured.insuredType}
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      insured: { ...formData.insured, insuredType: e.target.value },
                    })
                  }
                >
                  <option value="BUSINESS">BUSINESS (Doanh nghiệp / Tổ chức)</option>
                  <option value="INDIVIDUAL">INDIVIDUAL (Cá nhân)</option>
                </select>
              </div>

              {/* Insured Name */}
              <div style={{ gridColumn: 'span 2' }}>
                <label className="form-label">Tên Doanh nghiệp / Khách hàng *</label>
                <input
                  type="text"
                  className="form-control"
                  value={formData.insured.insuredName}
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      insured: { ...formData.insured, insuredName: e.target.value },
                    })
                  }
                  placeholder="VD: Tập đoàn Công nghệ Tài chính Alpha"
                />
              </div>

              {/* Email */}
              <div>
                <label className="form-label">Email Đại diện *</label>
                <div style={{ position: 'relative' }}>
                  <input
                    type="email"
                    className="form-control"
                    value={formData.insured.email}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        insured: { ...formData.insured, email: e.target.value },
                      })
                    }
                    placeholder="contact@alpha.com"
                  />
                  <Mail size={16} color="var(--text-muted)" style={{ position: 'absolute', right: 12, top: 12 }} />
                </div>
              </div>

              {/* Phone */}
              <div>
                <label className="form-label">Số điện thoại</label>
                <div style={{ position: 'relative' }}>
                  <input
                    type="text"
                    className="form-control"
                    value={formData.insured.phone}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        insured: { ...formData.insured, phone: e.target.value },
                      })
                    }
                    placeholder="0987654321"
                  />
                  <Phone size={16} color="var(--text-muted)" style={{ position: 'absolute', right: 12, top: 12 }} />
                </div>
              </div>

              {/* Effective Date */}
              <div>
                <label className="form-label">Ngày bắt đầu hiệu lực (Effective Date)</label>
                <input
                  type="date"
                  className="form-control"
                  value={formData.effectiveDate}
                  onChange={(e) => setFormData({ ...formData, effectiveDate: e.target.value })}
                />
              </div>

              {/* Expiration Date */}
              <div>
                <label className="form-label">Ngày hết hạn (Expiration Date)</label>
                <input
                  type="date"
                  className="form-control"
                  value={formData.expirationDate}
                  onChange={(e) => setFormData({ ...formData, expirationDate: e.target.value })}
                />
              </div>
            </div>

            {/* Address Details */}
            <div
              style={{
                marginTop: '1.5rem',
                padding: '1.25rem',
                borderRadius: 'var(--radius-md)',
                background: 'rgba(255, 255, 255, 0.02)',
                border: '1px solid var(--border-subtle)',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
                <Home size={16} color="#818cf8" />
                <span style={{ fontSize: '0.9rem', fontWeight: 600 }}>Địa chỉ trụ sở chính</span>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem' }}>
                <div style={{ gridColumn: 'span 2' }}>
                  <label className="form-label">Địa chỉ đường / số nhà</label>
                  <input
                    type="text"
                    className="form-control"
                    value={formData.insured.address.street}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        insured: {
                          ...formData.insured,
                          address: { ...formData.insured.address, street: e.target.value },
                        },
                      })
                    }
                    placeholder="Số 88 đường Láng Hạ, Đống Đa"
                  />
                </div>
                <div>
                  <label className="form-label">Thành phố / Tỉnh</label>
                  <input
                    type="text"
                    className="form-control"
                    value={formData.insured.address.city}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        insured: {
                          ...formData.insured,
                          address: { ...formData.insured.address, city: e.target.value },
                        },
                      })
                    }
                    placeholder="Hà Nội"
                  />
                </div>
                <div>
                  <label className="form-label">Mã bưu điện (Postal Code)</label>
                  <input
                    type="text"
                    className="form-control"
                    value={formData.insured.address.postalCode}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        insured: {
                          ...formData.insured,
                          address: { ...formData.insured.address, postalCode: e.target.value },
                        },
                      })
                    }
                    placeholder="100000"
                  />
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ================= STEP 2: DYNAMIC LOCATIONS ================= */}
        {currentStep === 2 && (
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <MapPin size={20} color="#6366f1" />
                <h2 style={{ fontSize: '1.25rem', fontWeight: 600, margin: 0 }}>
                  Bước 2: Quản lý Địa điểm Được Bảo hiểm ({formData.locations.length} địa điểm)
                </h2>
              </div>

              <button onClick={handleAddLocation} className="btn btn-primary" style={{ fontSize: '0.85rem' }}>
                <Plus size={16} />
                <span>Thêm Địa điểm</span>
              </button>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
              {formData.locations.map((loc, idx) => (
                <div
                  key={idx}
                  className="glass-card"
                  style={{
                    padding: '1.5rem',
                    background: 'rgba(255, 255, 255, 0.02)',
                    borderColor: 'var(--border-subtle)',
                    position: 'relative',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                      <span
                        style={{
                          width: '26px',
                          height: '26px',
                          borderRadius: '50%',
                          background: 'rgba(99, 102, 241, 0.2)',
                          color: '#a5b4fc',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontSize: '0.8rem',
                          fontWeight: 700,
                        }}
                      >
                        {idx + 1}
                      </span>
                      <span style={{ fontWeight: 600, fontSize: '0.95rem' }}>
                        {loc.locationName || `Địa điểm ${idx + 1}`} ({loc.locationId})
                      </span>
                      <span className="badge badge-info" style={{ fontSize: '0.7rem' }}>
                        {loc.coverages?.length || 0} hạng mục bảo hiểm
                      </span>
                    </div>

                    <button
                      onClick={() => handleRemoveLocation(idx)}
                      disabled={formData.locations.length <= 1}
                      className="btn btn-secondary"
                      style={{
                        padding: '0.35rem 0.6rem',
                        color: '#f87171',
                        borderColor: 'rgba(239, 68, 68, 0.3)',
                      }}
                    >
                      <Trash2 size={14} />
                      <span>Xóa</span>
                    </button>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1rem' }}>
                    <div>
                      <label className="form-label">Tên định danh Địa điểm *</label>
                      <input
                        type="text"
                        className="form-control"
                        value={loc.locationName}
                        onChange={(e) => handleUpdateLocation(idx, 'locationName', e.target.value)}
                        placeholder="VD: Nhà máy số 1"
                      />
                    </div>

                    <div>
                      <label className="form-label">Mã Địa điểm (ID)</label>
                      <input
                        type="text"
                        className="form-control"
                        value={loc.locationId}
                        onChange={(e) => handleUpdateLocation(idx, 'locationId', e.target.value)}
                        placeholder="LOC-1"
                      />
                    </div>

                    <div>
                      <label className="form-label">Loại kết cấu xây dựng</label>
                      <select
                        className="form-control"
                        value={loc.constructionType}
                        onChange={(e) => handleUpdateLocation(idx, 'constructionType', e.target.value)}
                      >
                        <option value="FRAME">Khung gỗ (FRAME)</option>
                        <option value="JOISTED_MASONRY">Gạch tường chịu lực (JOISTED_MASONRY)</option>
                        <option value="NON_COMBUSTIBLE">Kết cấu không cháy (NON_COMBUSTIBLE)</option>
                        <option value="FIRE_RESISTIVE">Chống cháy cao cấp (FIRE_RESISTIVE)</option>
                      </select>
                    </div>

                    <div>
                      <label className="form-label">Cấp độ PCCC (Fire Tier 1-5)</label>
                      <select
                        className="form-control"
                        value={loc.fireProtectionTier}
                        onChange={(e) => handleUpdateLocation(idx, 'fireProtectionTier', parseInt(e.target.value))}
                      >
                        <option value="1">Cấp 1 - Trang bị chữa cháy tự động Sprinkler toàn diện</option>
                        <option value="2">Cấp 2 - Đội PCCC cơ sở & trụ cứu hỏa sẵn sàng</option>
                        <option value="3">Cấp 3 - Bình chữa cháy cơ bản</option>
                        <option value="4">Cấp 4 - Cách xa trạm PCCC &gt; 10km</option>
                        <option value="5">Cấp 5 - Nguy cơ rủi ro cao</option>
                      </select>
                    </div>

                    <div style={{ gridColumn: 'span 2' }}>
                      <label className="form-label">Địa chỉ cụ thể</label>
                      <input
                        type="text"
                        className="form-control"
                        value={loc.address?.street || ''}
                        onChange={(e) => handleUpdateLocation(idx, 'address.street', e.target.value)}
                        placeholder="Khu công nghiệp Bắc Thăng Long, Đông Anh, Hà Nội"
                      />
                    </div>

                    <div>
                      <label className="form-label">Thành phố</label>
                      <input
                        type="text"
                        className="form-control"
                        value={loc.address?.city || ''}
                        onChange={(e) => handleUpdateLocation(idx, 'address.city', e.target.value)}
                        placeholder="Hà Nội"
                      />
                    </div>

                    <div>
                      <label className="form-label">Mục đích sử dụng (Occupancy)</label>
                      <select
                        className="form-control"
                        value={loc.occupancyType}
                        onChange={(e) => handleUpdateLocation(idx, 'occupancyType', e.target.value)}
                      >
                        <option value="OFFICE">Văn phòng (OFFICE)</option>
                        <option value="RETAIL">Cửa hàng bán lẻ (RETAIL)</option>
                        <option value="WAREHOUSE">Kho lưu trữ (WAREHOUSE)</option>
                        <option value="INDUSTRIAL">Nhà máy sản xuất (INDUSTRIAL)</option>
                      </select>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ================= STEP 3: DYNAMIC COVERAGES PER LOCATION ================= */}
        {currentStep === 3 && (
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <Shield size={20} color="#6366f1" />
                <h2 style={{ fontSize: '1.25rem', fontWeight: 600, margin: 0 }}>
                  Bước 3: Hạng mục & Phí Bảo hiểm theo từng Địa điểm
                </h2>
              </div>
            </div>

            {/* Location selector tabs */}
            <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1.5rem', flexWrap: 'wrap' }}>
              {formData.locations.map((loc, idx) => (
                <button
                  key={idx}
                  type="button"
                  onClick={() => setActiveLocIndex(idx)}
                  className={`btn ${activeLocIndex === idx ? 'btn-primary' : 'btn-secondary'}`}
                  style={{ fontSize: '0.85rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}
                >
                  <MapPin size={14} />
                  <span>{loc.locationName || `Địa điểm ${idx + 1}`}</span>
                  <span
                    style={{
                      background: 'rgba(0,0,0,0.25)',
                      padding: '0.1rem 0.4rem',
                      borderRadius: '10px',
                      fontSize: '0.75rem',
                    }}
                  >
                    {loc.coverages?.length || 0}
                  </span>
                </button>
              ))}
            </div>

            {/* Current Active Location's Coverages */}
            {formData.locations[activeLocIndex] && (
              <div
                className="glass-card"
                style={{
                  padding: '1.5rem',
                  background: 'rgba(255, 255, 255, 0.02)',
                  borderColor: 'rgba(99, 102, 241, 0.25)',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
                  <div>
                    <h3 style={{ fontSize: '1.05rem', margin: 0, fontWeight: 600 }}>
                      Các gói quyền lợi tại: {formData.locations[activeLocIndex].locationName}
                    </h3>
                    <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
                      Địa chỉ: {formData.locations[activeLocIndex].address?.street || 'Chưa nhập'} - {formData.locations[activeLocIndex].address?.city}
                    </span>
                  </div>

                  <button
                    onClick={() => handleAddCoverage(activeLocIndex)}
                    className="btn btn-primary"
                    style={{ fontSize: '0.825rem', padding: '0.4rem 0.85rem' }}
                  >
                    <Plus size={15} />
                    <span>Thêm Hạng mục Bảo hiểm</span>
                  </button>
                </div>

                {(!formData.locations[activeLocIndex].coverages || formData.locations[activeLocIndex].coverages.length === 0) ? (
                  <div
                    style={{
                      padding: '2.5rem',
                      textAlign: 'center',
                      border: '1px dashed var(--border-subtle)',
                      borderRadius: 'var(--radius-md)',
                    }}
                  >
                    <Shield size={36} color="var(--text-muted)" style={{ margin: '0 auto 0.75rem' }} />
                    <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', marginBottom: '1rem' }}>
                      Địa điểm này chưa có gói quyền lợi nào.
                    </p>
                    <button
                      onClick={() => handleAddCoverage(activeLocIndex)}
                      className="btn btn-secondary"
                      style={{ fontSize: '0.85rem' }}
                    >
                      <Plus size={15} /> Thêm quyền lợi đầu tiên
                    </button>
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                    {formData.locations[activeLocIndex].coverages.map((cov, cIdx) => (
                      <div
                        key={cIdx}
                        style={{
                          padding: '1.25rem',
                          borderRadius: 'var(--radius-md)',
                          background: 'rgba(15, 23, 42, 0.65)',
                          border: '1px solid var(--border-subtle)',
                          display: 'grid',
                          gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr)) 40px',
                          gap: '1rem',
                          alignItems: 'center',
                        }}
                      >
                        {/* Coverage Code selection */}
                        <div>
                          <label className="form-label" style={{ fontSize: '0.75rem' }}>Loại Bảo hiểm</label>
                          <select
                            className="form-control"
                            value={cov.coverageCode}
                            onChange={(e) => handleUpdateCoverage(activeLocIndex, cIdx, 'coverageCode', e.target.value)}
                            style={{ fontSize: '0.825rem' }}
                          >
                            {COVERAGE_OPTIONS.map((opt) => (
                              <option key={opt.code} value={opt.code}>
                                {opt.code} - {opt.name}
                              </option>
                            ))}
                          </select>
                        </div>

                        {/* Limit */}
                        <div>
                          <label className="form-label" style={{ fontSize: '0.75rem' }}>Hạn mức bồi thường (VND)</label>
                          <input
                            type="number"
                            step="10000000"
                            className="form-control"
                            value={cov.limit}
                            onChange={(e) => handleUpdateCoverage(activeLocIndex, cIdx, 'limit', e.target.value)}
                            style={{ fontSize: '0.825rem' }}
                          />
                        </div>

                        {/* Deductible */}
                        <div>
                          <label className="form-label" style={{ fontSize: '0.75rem' }}>Mức khấu trừ (Deductible)</label>
                          <input
                            type="number"
                            step="1000000"
                            className="form-control"
                            value={cov.deductible}
                            onChange={(e) => handleUpdateCoverage(activeLocIndex, cIdx, 'deductible', e.target.value)}
                            style={{ fontSize: '0.825rem' }}
                          />
                        </div>

                        {/* Term Months */}
                        <div>
                          <label className="form-label" style={{ fontSize: '0.75rem' }}>Thời hạn (Tháng)</label>
                          <input
                            type="number"
                            min="1"
                            max="36"
                            className="form-control"
                            value={cov.termMonths}
                            onChange={(e) => handleUpdateCoverage(activeLocIndex, cIdx, 'termMonths', e.target.value)}
                            style={{ fontSize: '0.825rem' }}
                          />
                        </div>

                        {/* Computed Premium */}
                        <div>
                          <label className="form-label" style={{ fontSize: '0.75rem', color: '#10b981' }}>
                            Phí tính toán (VND)
                          </label>
                          <div
                            style={{
                              padding: '0.55rem 0.75rem',
                              background: 'rgba(16, 185, 129, 0.1)',
                              border: '1px solid rgba(16, 185, 129, 0.25)',
                              borderRadius: 'var(--radius-sm)',
                              color: '#10b981',
                              fontWeight: 700,
                              fontSize: '0.85rem',
                            }}
                          >
                            {new Intl.NumberFormat('vi-VN').format(cov.premium || 0)} đ
                          </div>
                        </div>

                        {/* Remove Button */}
                        <div style={{ display: 'flex', justifyContent: 'center' }}>
                          <button
                            type="button"
                            onClick={() => handleRemoveCoverage(activeLocIndex, cIdx)}
                            style={{
                              background: 'none',
                              border: 'none',
                              color: '#f87171',
                              cursor: 'pointer',
                              padding: '0.35rem',
                              borderRadius: '4px',
                            }}
                            title="Xóa hạng mục"
                          >
                            <Trash2 size={18} />
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        {/* ================= STEP 4: REVIEW & SUBMISSION ================= */}
        {currentStep === 4 && (
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1.5rem' }}>
              <FileCheck2 size={20} color="#10b981" />
              <h2 style={{ fontSize: '1.25rem', fontWeight: 600, margin: 0 }}>
                Bước 4: Tổng kết Hợp đồng & Đệ trình (Summary & Actions)
              </h2>
            </div>

            {/* High-level Summary Cards */}
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
                gap: '1.25rem',
                marginBottom: '2rem',
              }}
            >
              {/* Card 1: Insured Info */}
              <div
                className="glass-card"
                style={{
                  padding: '1.25rem',
                  background: 'rgba(255, 255, 255, 0.02)',
                  borderColor: 'var(--border-subtle)',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.75rem', color: '#818cf8' }}>
                  <User size={18} />
                  <span style={{ fontWeight: 600, fontSize: '0.9rem' }}>Bên được Bảo hiểm</span>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem', fontSize: '0.85rem' }}>
                  <div><span style={{ color: 'var(--text-muted)' }}>Mã HĐ:</span> <b>{formData.policyNumber}</b></div>
                  <div><span style={{ color: 'var(--text-muted)' }}>Tên:</span> <b>{formData.insured.insuredName}</b> ({formData.insured.insuredType})</div>
                  <div><span style={{ color: 'var(--text-muted)' }}>Email:</span> {formData.insured.email}</div>
                  <div><span style={{ color: 'var(--text-muted)' }}>Điện thoại:</span> {formData.insured.phone || 'Chưa cung cấp'}</div>
                  <div><span style={{ color: 'var(--text-muted)' }}>Trụ sở:</span> {formData.insured.address.street}, {formData.insured.address.city}</div>
                </div>
              </div>

              {/* Card 2: Timeline */}
              <div
                className="glass-card"
                style={{
                  padding: '1.25rem',
                  background: 'rgba(255, 255, 255, 0.02)',
                  borderColor: 'var(--border-subtle)',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.75rem', color: '#818cf8' }}>
                  <Calendar size={18} />
                  <span style={{ fontWeight: 600, fontSize: '0.9rem' }}>Thời hạn & Trạng thái</span>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem', fontSize: '0.85rem' }}>
                  <div>
                    <span style={{ color: 'var(--text-muted)' }}>Trạng thái hiện tại:</span>{' '}
                    <span className="badge badge-info" style={{ marginLeft: '0.35rem' }}>{formData.status}</span>
                  </div>
                  <div><span style={{ color: 'var(--text-muted)' }}>Hiệu lực từ:</span> {formData.effectiveDate || 'Ngay khi phát hành'}</div>
                  <div><span style={{ color: 'var(--text-muted)' }}>Hết hạn vào:</span> {formData.expirationDate || '12 tháng sau'}</div>
                  <div><span style={{ color: 'var(--text-muted)' }}>Quy mô:</span> {formData.locations.length} địa điểm, {formData.locations.reduce((a, b) => a + (b.coverages?.length || 0), 0)} quyền lợi</div>
                </div>
              </div>

              {/* Card 3: Total Premium Highlights */}
              <div
                className="glass-card"
                style={{
                  padding: '1.25rem',
                  background: 'rgba(16, 185, 129, 0.05)',
                  borderColor: 'rgba(16, 185, 129, 0.3)',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.75rem', color: '#10b981' }}>
                  <DollarSign size={18} />
                  <span style={{ fontWeight: 600, fontSize: '0.9rem' }}>Tổng Phí Bảo Hiểm Toàn Phần</span>
                </div>
                <div style={{ fontSize: '1.75rem', fontWeight: 800, color: '#10b981', marginBottom: '0.5rem' }}>
                  {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(calculateTotalPremium())}
                </div>
                <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                  Phí đã tính tự động theo biểu tỷ lệ kỹ thuật và thời hạn quy định.
                </span>
              </div>
            </div>

            {/* Detailed Structure Breakdown */}
            <div style={{ marginBottom: '2rem' }}>
              <h3 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '1rem' }}>Chi tiết Cấu trúc Rủi ro & Địa điểm</h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                {formData.locations.map((loc, idx) => (
                  <div
                    key={idx}
                    style={{
                      padding: '1rem 1.25rem',
                      borderRadius: 'var(--radius-md)',
                      background: 'rgba(255, 255, 255, 0.015)',
                      border: '1px solid var(--border-subtle)',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                      <span style={{ fontWeight: 600, fontSize: '0.9rem' }}>
                        {idx + 1}. {loc.locationName} ({loc.locationId}) - {loc.address?.street}, {loc.address?.city}
                      </span>
                      <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                        Kết cấu: {loc.constructionType} | Tier: {loc.fireProtectionTier}
                      </span>
                    </div>

                    <div style={{ paddingLeft: '1rem', display: 'flex', flexDirection: 'column', gap: '0.35rem' }}>
                      {(loc.coverages || []).map((cov, cIdx) => (
                        <div
                          key={cIdx}
                          style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            fontSize: '0.8rem',
                            color: 'var(--text-secondary)',
                          }}
                        >
                          <span>• {cov.coverageName} ({cov.coverageCode}) - Hạn mức: {new Intl.NumberFormat('vi-VN').format(cov.limit)} đ</span>
                          <span style={{ color: '#10b981', fontWeight: 600 }}>{new Intl.NumberFormat('vi-VN').format(cov.premium)} đ</span>
                        </div>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Submission Actions */}
            <div
              style={{
                display: 'flex',
                justifyContent: 'flex-end',
                gap: '1rem',
                paddingTop: '1.5rem',
                borderTop: '1px solid var(--border-subtle)',
              }}
            >
              <button
                type="button"
                disabled={submitting}
                onClick={() => handleSubmitPolicy('DRAFT')}
                className="btn btn-secondary"
                style={{ padding: '0.65rem 1.25rem', fontSize: '0.9rem' }}
              >
                <Save size={16} />
                <span>{isEditMode ? 'Lưu Bản Cập Nhật (DRAFT)' : 'Lưu Bản Nháp (DRAFT)'}</span>
              </button>

              <button
                type="button"
                disabled={submitting}
                onClick={() => handleSubmitPolicy('QUOTED')}
                className="btn btn-primary"
                style={{ padding: '0.65rem 1.5rem', fontSize: '0.9rem' }}
              >
                <Send size={16} />
                <span>{submitting ? 'Đang xử lý...' : 'Nộp Đơn Yêu Cầu Báo Phí (Submit QUOTED)'}</span>
              </button>
            </div>
          </div>
        )}

        {/* Stepper Bottom Controls */}
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginTop: '2rem',
            paddingTop: '1.25rem',
            borderTop: '1px solid var(--border-subtle)',
          }}
        >
          <button
            type="button"
            onClick={handlePrevStep}
            disabled={currentStep === 1}
            className="btn btn-secondary"
            style={{ padding: '0.5rem 1rem', fontSize: '0.85rem' }}
          >
            <ChevronLeft size={16} />
            <span>Quay lại</span>
          </button>

          <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
            Bước {currentStep} trên 4
          </span>

          {currentStep < 4 ? (
            <button
              type="button"
              onClick={handleNextStep}
              className="btn btn-primary"
              style={{ padding: '0.5rem 1.25rem', fontSize: '0.85rem' }}
            >
              <span>Tiếp theo</span>
              <ChevronRight size={16} />
            </button>
          ) : (
            <div />
          )}
        </div>
      </div>
    </div>
  );
};
