import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import {
  MapPin, Plus, Pencil, Trash2, ChevronDown, History,
  FileText, ArrowLeft, CreditCard, Zap, Send, SlidersHorizontal,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import {
  policyService, policyLocationService, policyCoverageService,
} from '../services/api';
import { isBackendMissing, BACKEND_TODO_HINT } from '../utils/backend';
import { StatusBadge, LifecycleStepper } from '../components/Status';
import { Modal } from '../components/Modal';
import { PaymentModal } from '../components/PaymentModal';
import { EmptyState, Skeleton } from '../components/ui';
import { formatMoney, formatDate, formatDateTime } from '../utils/format';

const TABS = [
  { key: 'overview', label: 'Tổng quan' },
  { key: 'locations', label: 'Địa điểm & gói bảo hiểm' },
  { key: 'history', label: 'Lịch sử' },
];

const COVERAGE_CATALOG = [
  { code: 'PROP_FIRE', name: 'Cháy nổ tài sản', type: 'PROPERTY' },
  { code: 'PROP_WATER', name: 'Ngập nước', baseRate: 0.05, type: 'PROPERTY' },
  { code: 'LIAB_PUBLIC', name: 'Trách nhiệm công cộng', type: 'LIABILITY' },
  { code: 'WCOMP', name: 'Tai nạn lao động', type: 'WORKERS_COMP' },
];

const ENDORSE_TYPES = [
  { key: 'them-dia-diem', label: 'Thêm địa điểm' },
  { key: 'tang-limit', label: 'Tăng hạn mức' },
  { key: 'giam-deductible', label: 'Giảm mức khấu trừ' },
];

const safeList = (v) => (Array.isArray(v) ? v : []);

/**
 * Chi tiết hợp đồng: xem, sửa địa điểm/gói bảo hiểm, chuyển trạng thái vòng đời.
 */
export function PolicyDetailPage() {
  const { policyNumber } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const { isAdmin } = useAuth();

  const [policy, setPolicy] = useState(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [tab, setTab] = useState('overview');
  const [payOpen, setPayOpen] = useState(false);
  const [busy, setBusy] = useState(false);

  // Modals
  const [locModal, setLocModal] = useState(null);   // {mode:'add'} | {mode:'edit', loc}
  const [covModal, setCovModal] = useState(null);   // {mode:'add', locId} | {mode:'edit', locId, cov}
  const [delLoc, setDelLoc] = useState(null);       // locId
  const [delCov, setDelCov] = useState(null);       // {locId, cov}
  const [delPolicy, setDelPolicy] = useState(false);
  const [endorseOpen, setEndorseOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setNotFound(false);
    try {
      const res = await policyService.getPolicyByNumber(policyNumber);
      setPolicy(res?.data ?? null);
    } catch (err) {
      if (err.status === 404) setNotFound(true);
      else toast.error(err.message || 'Không tải được hợp đồng.');
    } finally {
      setLoading(false);
    }
  }, [policyNumber, toast]);

  useEffect(() => { load(); }, [load]);

  const failToast = (err, actionName) => {
    if (isBackendMissing(err)) toast.info(`${BACKEND_TODO_HINT}`);
    else toast.error(err.message || `Không thực hiện được: ${actionName}.`);
  };

  // ---- Vòng đời -----------------------------------------------------------
  const doTransition = async (toStatus, okMsg) => {
    setBusy(true);
    try {
      await policyService.transition(policyNumber, toStatus);
      toast.success(okMsg);
      await load();
      return true;
    } catch (err) {
      failToast(err, 'chuyển trạng thái');
      return false;
    } finally {
      setBusy(false);
    }
  };

  const requestQuote = () => doTransition('QUOTED', 'Đã gửi yêu cầu báo giá.');
  const acceptQuote = async () => {
    // QUOTED -> BOUND (chấp nhận báo giá) rồi mở modal thanh toán
    const ok = await doTransition('BOUND', 'Đã chấp nhận báo giá. Vui lòng thanh toán để kích hoạt hợp đồng.');
    if (ok) setPayOpen(true);
  };
  const confirmPayment = async () => {
    // "Tôi đã chuyển khoản" -> BOUND -> ACTIVE
    const ok = await doTransition('ACTIVE', 'Thanh toán thành công. Hợp đồng đã có hiệu lực.');
    if (ok) setPayOpen(false);
  };
  const adminActivate = async () => {
    if (!window.confirm('Kích hoạt trực tiếp hợp đồng này mà không qua thanh toán?')) return;
    await doTransition('ACTIVE', 'Hợp đồng đã được kích hoạt.');
  };
  const deletePolicy = async () => {
    setBusy(true);
    try {
      await policyService.deletePolicy(policyNumber);
      toast.success('Đã xóa hợp đồng.');
      navigate('/');
    } catch (err) {
      failToast(err, 'xóa hợp đồng');
    } finally {
      setBusy(false);
      setDelPolicy(false);
    }
  };

  // ---- Location CRUD ------------------------------------------------------
  const submitLocation = async (data) => {
    setBusy(true);
    try {
      if (locModal.mode === 'add') {
        await policyLocationService.addLocation(policyNumber, data);
        toast.success('Đã thêm địa điểm.');
      } else {
        await policyLocationService.updateLocation(policyNumber, locModal.loc.locationId, data);
        toast.success('Đã cập nhật địa điểm.');
      }
      setLocModal(null);
      await load();
    } catch (err) {
      failToast(err, 'lưu địa điểm');
    } finally {
      setBusy(false);
    }
  };

  const deleteLocation = async () => {
    setBusy(true);
    try {
      await policyLocationService.removeLocation(policyNumber, delLoc);
      toast.success('Đã xóa địa điểm.');
      setDelLoc(null);
      await load();
    } catch (err) {
      failToast(err, 'xóa địa điểm');
    } finally {
      setBusy(false);
    }
  };

  // ---- Coverage CRUD ------------------------------------------------------
  const submitCoverage = async (locId, data) => {
    setBusy(true);
    try {
      if (covModal.mode === 'add') {
        await policyCoverageService.addCoverage(policyNumber, locId, data);
        toast.success('Đã thêm gói bảo hiểm.');
      } else {
        await policyCoverageService.updateCoverage(policyNumber, locId, covModal.cov.coverageCode, data);
        toast.success('Đã cập nhật gói bảo hiểm.');
      }
      setCovModal(null);
      await load();
    } catch (err) {
      failToast(err, 'lưu gói bảo hiểm');
    } finally {
      setBusy(false);
    }
  };

  const deleteCoverage = async () => {
    setBusy(true);
    try {
      await policyCoverageService.removeCoverage(policyNumber, delCov.locId, delCov.cov.coverageCode);
      toast.success('Đã xóa gói bảo hiểm.');
      setDelCov(null);
      await load();
    } catch (err) {
      failToast(err, 'xóa gói bảo hiểm');
    } finally {
      setBusy(false);
    }
  };

  // ---- Endorsement --------------------------------------------------------
  const submitEndorse = async ({ type, description }) => {
    setBusy(true);
    try {
      await policyService.endorse(policyNumber, { type, description });
      toast.success('Đã gửi yêu cầu điều chỉnh.');
      setEndorseOpen(false);
      await load();
    } catch (err) {
      failToast(err, 'gửi yêu cầu điều chỉnh');
    } finally {
      setBusy(false);
    }
  };

  // -------------------------------------------------------------------------
  if (loading) {
    return (
      <>
        <Skeleton height={28} width="40%" style={{ marginBottom: '1rem' }} />
        <Skeleton height={60} style={{ marginBottom: '1.5rem' }} />
        <Skeleton height={220} />
      </>
    );
  }

  if (notFound || !policy) {
    return (
      <EmptyState
        icon={<FileText size={40} />}
        title="Không tìm thấy hợp đồng"
        desc={`Hợp đồng ${policyNumber} không tồn tại hoặc bạn không có quyền xem.`}
        action={<Link className="btn btn-primary" to="/"><ArrowLeft size={16} /> Về trang chủ</Link>}
      />
    );
  }

  const status = policy.status;
  const locations = safeList(policy.locations);

  return (
    <>
      {/* Header */}
      <div className="glass-card" style={{ marginBottom: '1.5rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.9rem', flexWrap: 'wrap', marginBottom: '1rem' }}>
          <span className="policy-number" style={{ fontSize: '1.35rem' }}>{policy.policyNumber}</span>
          <StatusBadge status={status} />
          {policy.version != null && (
            <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
              Phiên bản v{policy.version}
            </span>
          )}
          <span style={{ marginLeft: 'auto', textAlign: 'right' }}>
            <span style={{ display: 'block', fontSize: '0.8rem', color: 'var(--text-secondary)' }}>Tổng phí</span>
            <strong style={{ fontSize: '1.5rem', color: '#1663d9' }}>{formatMoney(policy.totalPremium)}</strong>
          </span>
        </div>
        <LifecycleStepper status={status} />
      </div>

      {/* Action bar theo trạng thái */}
      <ActionBar
        status={status}
        isAdmin={isAdmin}
        busy={busy}
        onRequestQuote={requestQuote}
        onAcceptQuote={acceptQuote}
        onPayNow={() => setPayOpen(true)}
        onAdminActivate={adminActivate}
        onEndorse={() => setEndorseOpen(true)}
        onDelete={() => setDelPolicy(true)}
      />

      {/* Tabs */}
      <div className="tabs" role="tablist" aria-label="Chi tiết hợp đồng">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            role="tab"
            aria-selected={tab === t.key}
            className={`tab${tab === t.key ? ' active' : ''}`}
            onClick={() => setTab(t.key)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'overview' && <OverviewTab policy={policy} locations={locations} />}

      {tab === 'locations' && (
        <LocationsTab
          locations={locations}
          onAddLocation={() => setLocModal({ mode: 'add' })}
          onEditLocation={(loc) => setLocModal({ mode: 'edit', loc })}
          onDeleteLocation={(loc) => setDelLoc(loc.locationId)}
          onAddCoverage={(loc) => setCovModal({ mode: 'add', locId: loc.locationId })}
          onEditCoverage={(loc, cov) => setCovModal({ mode: 'edit', locId: loc.locationId, cov })}
          onDeleteCoverage={(loc, cov) => setDelCov({ locId: loc.locationId, cov })}
        />
      )}

      {tab === 'history' && <HistoryTab policyNumber={policyNumber} currentPolicy={policy} />}

      {/* Modals */}
      <LocationFormModal
        open={!!locModal}
        onClose={() => setLocModal(null)}
        initial={locModal?.mode === 'edit' ? locModal.loc : null}
        onSubmit={submitLocation}
        busy={busy}
      />
      <CoverageFormModal
        open={!!covModal}
        onClose={() => setCovModal(null)}
        mode={covModal?.mode}
        existing={covModal?.mode === 'edit' ? covModal.cov : null}
        onSubmit={(data) => submitCoverage(covModal.locId, data)}
        busy={busy}
      />
      <Modal
        open={delLoc != null}
        onClose={() => setDelLoc(null)}
        title="Xóa địa điểm"
        footer={
          <>
            <button className="btn btn-secondary" onClick={() => setDelLoc(null)}>Hủy</button>
            <button className="btn btn-danger" onClick={deleteLocation} disabled={busy}>
              {busy ? <span className="spinner" /> : null} Xóa
            </button>
          </>
        }
      >
        <p>Bạn có chắc muốn xóa địa điểm này cùng toàn bộ gói bảo hiểm của nó? Thao tác không thể hoàn tác.</p>
      </Modal>
      <Modal
        open={delCov != null}
        onClose={() => setDelCov(null)}
        title="Xóa gói bảo hiểm"
        footer={
          <>
            <button className="btn btn-secondary" onClick={() => setDelCov(null)}>Hủy</button>
            <button className="btn btn-danger" onClick={deleteCoverage} disabled={busy}>
              {busy ? <span className="spinner" /> : null} Xóa
            </button>
          </>
        }
      >
        <p>Bạn có chắc muốn xóa gói <strong>{delCov?.cov?.coverageName}</strong>? Thao tác không thể hoàn tác.</p>
      </Modal>
      <Modal
        open={delPolicy}
        onClose={() => setDelPolicy(false)}
        title="Xóa hợp đồng"
        footer={
          <>
            <button className="btn btn-secondary" onClick={() => setDelPolicy(false)}>Hủy</button>
            <button className="btn btn-danger" onClick={deletePolicy} disabled={busy}>
              {busy ? <span className="spinner" /> : null} Xóa hợp đồng
            </button>
          </>
        }
      >
        <p>Hợp đồng <strong className="policy-number">{policyNumber}</strong> đang ở trạng thái nháp và sẽ bị xóa vĩnh viễn.</p>
      </Modal>
      <EndorseModal open={endorseOpen} onClose={() => setEndorseOpen(false)} onSubmit={submitEndorse} busy={busy} />

      <PaymentModal
        open={payOpen}
        onClose={() => setPayOpen(false)}
        policy={policy}
        onConfirmed={confirmPayment}
      />
    </>
  );
}

// ---------------------------------------------------------------- Action bar
function ActionBar({ status, isAdmin, busy, onRequestQuote, onAcceptQuote, onPayNow, onAdminActivate, onEndorse, onDelete }) {
  const btn = (props, children) => (
    <button type="button" className="btn" disabled={busy} {...props}>{children}</button>
  );
  return (
    <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', marginBottom: '1.5rem' }}>
      {status === 'DRAFT' && (
        <>
          {btn({ className: 'btn btn-primary', onClick: onRequestQuote }, <><Send size={16} /> Yêu cầu báo giá</>)}
          {btn({ className: 'btn btn-danger', onClick: onDelete }, <><Trash2 size={16} /> Xóa hợp đồng</>)}
        </>
      )}
      {status === 'QUOTED' && btn({ className: 'btn btn-primary', onClick: onAcceptQuote }, <><Check size={16} /> Chấp nhận & thanh toán</>)}
      {status === 'BOUND' && (
        <>
          {btn({ className: 'btn btn-primary', onClick: onPayNow }, <><CreditCard size={16} /> Thanh toán ngay</>)}
          {isAdmin && btn({ className: 'btn btn-secondary', onClick: onAdminActivate }, <><Zap size={16} /> Kích hoạt trực tiếp</>)}
        </>
      )}
      {status === 'ACTIVE' && btn({ className: 'btn btn-secondary', onClick: onEndorse }, <><SlidersHorizontal size={16} /> Yêu cầu điều chỉnh</>)}
    </div>
  );
}

// ---------------------------------------------------------------- Overview
function OverviewTab({ policy, locations }) {
  const ins = policy.insured || {};
  const locPremium = (l) => safeList(l.coverages).reduce((s, c) => s + Number(c.premium || 0), 0);
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '1.25rem' }}>
      <div className="glass-card">
        <h3 style={{ marginBottom: '1rem' }}>Người được bảo hiểm</h3>
        <dl className="kv">
          <dt>Họ tên</dt><dd>{ins.name || '—'}</dd>
          <dt>Email</dt><dd>{ins.email || '—'}</dd>
          <dt>Điện thoại</dt><dd>{ins.phone || '—'}</dd>
          <dt>Địa chỉ</dt><dd>{ins.address || '—'}</dd>
          <dt>Loại khách hàng</dt><dd>{ins.type === 'DOANH_NGHIEP' ? 'Doanh nghiệp' : 'Cá nhân'}</dd>
        </dl>
      </div>
      <div className="glass-card">
        <h3 style={{ marginBottom: '1rem' }}>Thời hạn hợp đồng</h3>
        <dl className="kv">
          <dt>Ngày hiệu lực</dt><dd>{formatDate(policy.effectiveDate)}</dd>
          <dt>Ngày hết hạn</dt><dd>{formatDate(policy.expiryDate)}</dd>
          <dt>Ngày chốt báo giá</dt><dd>{formatDateTime(policy.boundAt || policy.paymentAt)}</dd>
          <dt>Hạn thanh toán</dt><dd>{formatDate(policy.paymentDueDate)}</dd>
        </dl>
      </div>
      <div className="glass-card">
        <h3 style={{ marginBottom: '1rem' }}>Chi tiết phí</h3>
        <dl className="kv">
          {locations.map((l, i) => (
            <React.Fragment key={l.locationId ?? i}>
              <dt>{l.name || `Địa điểm ${i + 1}`}</dt>
              <dd>{formatMoney(locPremium(l))}</dd>
            </React.Fragment>
          ))}
          <dt style={{ borderTop: '1px solid var(--border-subtle)', paddingTop: '0.6rem' }}><strong>Tổng cộng</strong></dt>
          <dd style={{ borderTop: '1px solid var(--border-subtle)', paddingTop: '0.6rem' }}>
            <strong style={{ color: '#1663d9' }}>{formatMoney(policy.totalPremium)}</strong>
          </dd>
        </dl>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- Locations
function LocationsTab({ locations, onAddLocation, onEditLocation, onDeleteLocation, onAddCoverage, onEditCoverage, onDeleteCoverage }) {
  const [openId, setOpenId] = useState(locations[0]?.locationId ?? 0);

  useEffect(() => {
    if (!locations.some((l) => (l.locationId ?? 0) === openId)) {
      setOpenId(locations[0]?.locationId ?? 0);
    }
  }, [locations, openId]);

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '1rem' }}>
        <button type="button" className="btn btn-secondary btn-sm" onClick={onAddLocation}>
          <Plus size={15} /> Thêm địa điểm
        </button>
      </div>
      {locations.length === 0 && (
        <EmptyState
          icon={<MapPin size={40} />}
          title="Chưa có địa điểm nào"
          desc="Thêm địa điểm đầu tiên cho hợp đồng này."
          action={<button className="btn btn-primary" onClick={onAddLocation}>Thêm địa điểm</button>}
        />
      )}
      {locations.map((l, i) => {
        const lid = l.locationId ?? i;
        const open = openId === lid;
        const covs = safeList(l.coverages);
        const total = covs.reduce((s, c) => s + Number(c.premium || 0), 0);
        return (
          <div key={lid} className="accordion">
            <button
              type="button"
              className="accordion-head"
              onClick={() => setOpenId(open ? null : lid)}
              aria-expanded={open}
            >
              <span>
                <MapPin size={16} style={{ verticalAlign: '-3px', marginRight: 8, color: '#1663d9' }} />
                {l.name || `Địa điểm ${i + 1}`} — <span style={{ color: 'var(--text-secondary)', fontWeight: 400 }}>{l.address}</span>
              </span>
              <span style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                <strong style={{ color: '#1663d9' }}>{formatMoney(total)}</strong>
                <ChevronDown size={18} style={{ transform: open ? 'rotate(180deg)' : 'none', transition: 'transform 0.2s' }} />
              </span>
            </button>
            {open && (
              <div className="accordion-body">
                <div style={{ display: 'flex', gap: '0.6rem', marginBottom: '1rem', flexWrap: 'wrap' }}>
                  <button type="button" className="btn btn-ghost btn-sm" onClick={() => onEditLocation(l)} aria-label={`Sửa địa điểm ${l.name || i + 1}`}>
                    <Pencil size={14} /> Sửa địa điểm
                  </button>
                  <button type="button" className="btn btn-ghost btn-sm" onClick={() => onDeleteLocation(l)} aria-label={`Xóa địa điểm ${l.name || i + 1}`}>
                    <Trash2 size={14} /> Xóa địa điểm
                  </button>
                </div>
                <div className="table-wrap">
                  <table className="table">
                    <thead>
                      <tr>
                        <th>Mã gói</th>
                        <th>Tên gói</th>
                        <th>Hạn mức</th>
                        <th>Khấu trừ</th>
                        <th>Phí</th>
                        <th style={{ width: 110 }}>Thao tác</th>
                      </tr>
                    </thead>
                    <tbody>
                      {covs.map((c) => (
                        <tr key={c.coverageCode}>
                          <td><span className="policy-number" style={{ fontSize: '0.8rem' }}>{c.coverageCode}</span></td>
                          <td>{c.coverageName}</td>
                          <td>{formatMoney(c.limit)}</td>
                          <td>{formatMoney(c.deductible)}</td>
                          <td><strong style={{ color: '#1663d9' }}>{formatMoney(c.premium)}</strong></td>
                          <td>
                            <div style={{ display: 'flex', gap: '0.4rem' }}>
                              <button
                                type="button" className="btn btn-ghost btn-icon btn-sm"
                                onClick={() => onEditCoverage(l, c)}
                                aria-label={`Sửa gói ${c.coverageName}`} title="Sửa"
                              >
                                <Pencil size={14} />
                              </button>
                              <button
                                type="button" className="btn btn-ghost btn-icon btn-sm"
                                onClick={() => onDeleteCoverage(l, c)}
                                aria-label={`Xóa gói ${c.coverageName}`} title="Xóa"
                              >
                                <Trash2 size={14} />
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))}
                      {covs.length === 0 && (
                        <tr><td colSpan={6} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                          Chưa có gói bảo hiểm nào cho địa điểm này.
                        </td></tr>
                      )}
                    </tbody>
                  </table>
                </div>
                <button type="button" className="btn btn-secondary btn-sm" style={{ marginTop: '1rem' }} onClick={() => onAddCoverage(l)}>
                  <Plus size={15} /> Thêm gói bảo hiểm
                </button>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}

// ---------------------------------------------------------------- Forms
function LocationFormModal({ open, onClose, initial, onSubmit, busy }) {
  const [name, setName] = useState('');
  const [address, setAddress] = useState('');
  useEffect(() => {
    if (open) {
      setName(initial?.name || '');
      setAddress(initial?.address || '');
    }
  }, [open, initial]);
  const submit = (e) => {
    e.preventDefault();
    if (!name.trim() || !address.trim()) return;
    onSubmit({ name: name.trim(), address: address.trim() });
  };
  return (
    <Modal
      open={open}
      onClose={onClose}
      title={initial ? 'Sửa địa điểm' : 'Thêm địa điểm'}
      footer={
        <>
          <button className="btn btn-secondary" onClick={onClose} disabled={busy}>Hủy</button>
          <button className="btn btn-primary" onClick={submit} disabled={busy || !name.trim() || !address.trim()}>
            {busy ? <span className="spinner" /> : null} Lưu
          </button>
        </>
      }
    >
      <div className="form-group">
        <label className="form-label" htmlFor="loc-name">Tên địa điểm *</label>
        <input id="loc-name" className="form-control" value={name} onChange={(e) => setName(e.target.value)} placeholder="Ví dụ: Kho hàng Quận 7" />
      </div>
      <div className="form-group">
        <label className="form-label" htmlFor="loc-addr">Địa chỉ *</label>
        <input id="loc-addr" className="form-control" value={address} onChange={(e) => setAddress(e.target.value)} placeholder="Số nhà, đường, phường/xã…" />
      </div>
    </Modal>
  );
}

function CoverageFormModal({ open, onClose, mode, existing, onSubmit, busy }) {
  const [code, setCode] = useState(COVERAGE_CATALOG[0].code);
  const [limit, setLimit] = useState('');
  const [deductible, setDeductible] = useState('');
  const [termMonths, setTermMonths] = useState(12);
  useEffect(() => {
    if (open) {
      setCode(existing?.coverageCode || COVERAGE_CATALOG[0].code);
      setLimit(existing?.limit ?? '');
      setDeductible(existing?.deductible ?? '');
      setTermMonths(existing?.termMonths ?? 12);
    }
  }, [open, existing]);
  const limitNum = Number(limit);
  const dedNum = Number(deductible) || 0;
  const valid = Number.isFinite(limitNum) && limitNum > 0 && dedNum < limitNum;
  const submit = () => {
    if (!valid) return;
    const cat = COVERAGE_CATALOG.find((x) => x.code === code) || {};
    onSubmit({
      coverageCode: code,
      coverageName: cat.name || existing?.coverageName || code,
      coverageType: cat.type || existing?.coverageType,
      limit: limitNum,
      deductible: dedNum,
      termMonths: Number(termMonths) || 12,
    });
  };
  return (
    <Modal
      open={open}
      onClose={onClose}
      title={mode === 'edit' ? 'Sửa gói bảo hiểm' : 'Thêm gói bảo hiểm'}
      footer={
        <>
          <button className="btn btn-secondary" onClick={onClose} disabled={busy}>Hủy</button>
          <button className="btn btn-primary" onClick={submit} disabled={busy || !valid}>
            {busy ? <span className="spinner" /> : null} Lưu
          </button>
        </>
      }
    >
      <div className="form-group">
        <label className="form-label" htmlFor="cov-code">Gói bảo hiểm</label>
        <select
          id="cov-code" className="form-control" value={code}
          onChange={(e) => setCode(e.target.value)} disabled={mode === 'edit'}
        >
          {COVERAGE_CATALOG.map((c) => (
            <option key={c.code} value={c.code}>{c.name} ({c.code})</option>
          ))}
        </select>
      </div>
      <div className="form-group">
        <label className="form-label" htmlFor="cov-limit">Hạn mức bảo hiểm (VND) *</label>
        <input id="cov-limit" type="number" min="1" className="form-control" value={limit} onChange={(e) => setLimit(e.target.value)} />
      </div>
      <div className="form-group">
        <label className="form-label" htmlFor="cov-ded">Mức khấu trừ (VND)</label>
        <input id="cov-ded" type="number" min="0" className="form-control" value={deductible} onChange={(e) => setDeductible(e.target.value)} />
      </div>
      <div className="form-group">
        <label className="form-label" htmlFor="cov-term">Thời hạn (tháng)</label>
        <input id="cov-term" type="number" min="1" className="form-control" value={termMonths} onChange={(e) => setTermMonths(e.target.value)} />
      </div>
      {!valid && limit !== '' && (
        <div className="form-error">Hạn mức phải lớn hơn 0 và lớn hơn mức khấu trừ.</div>
      )}
    </Modal>
  );
}

function EndorseModal({ open, onClose, onSubmit, busy }) {
  const [type, setType] = useState(ENDORSE_TYPES[0].key);
  const [description, setDescription] = useState('');
  useEffect(() => {
    if (open) { setType(ENDORSE_TYPES[0].key); setDescription(''); }
  }, [open ]);
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Yêu cầu điều chỉnh hợp đồng"
      footer={
        <>
          <button className="btn btn-secondary" onClick={onClose} disabled={busy}>Hủy</button>
          <button className="btn btn-primary" onClick={() => onSubmit({ type, description: description.trim() })} disabled={busy || !description.trim()}>
            {busy ? <span className="spinner" /> : null} Gửi yêu cầu
          </button>
        </>
      }
    >
      <div className="form-group">
        <label className="form-label" htmlFor="end-type">Loại điều chỉnh</label>
        <select id="end-type" className="form-control" value={type} onChange={(e) => setType(e.target.value)}>
          {ENDORSE_TYPES.map((t) => <option key={t.key} value={t.key}>{t.label}</option>)}
        </select>
      </div>
      <div className="form-group">
        <label className="form-label" htmlFor="end-desc">Mô tả chi tiết *</label>
        <textarea
          id="end-desc" className="form-control" rows={4}
          value={description} onChange={(e) => setDescription(e.target.value)}
          placeholder="Ví dụ: Bổ sung kho hàng mới tại KCN Tân Bình, tăng hạn mức cháy nổ lên 2 tỷ đồng…"
        />
      </div>
      <p className="form-hint">Yêu cầu của bạn sẽ được đội ngũ thẩm định xem xét và phản hồi.</p>
    </Modal>
  );
}

// ---------------------------------------------------------------- History
const flattenCoverages = (snapshot) => {
  const map = {};
  safeList(snapshot?.locations).forEach((l, li) => {
    const lid = l.locationId ?? li;
    safeList(l.coverages).forEach((c) => {
      map[`${lid}|${c.coverageCode}`] = {
        locationId: lid,
        locationName: l.name || `Địa điểm ${lid}`,
        ...c,
      };
    });
  });
  return map;
};

function HistoryTab({ policyNumber, currentPolicy }) {
  const toast = useToast();
  const [versions, setVersions] = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [unavailable, setUnavailable] = useState(false);
  const [selected, setSelected] = useState(null); // { version, snapshot }

  useEffect(() => {
    (async () => {
      setLoading(true);
      const [vRes, hRes] = await Promise.allSettled([
        policyService.getVersions(policyNumber),
        policyService.getHistory(policyNumber),
      ]);
      const vMissing = vRes.status === 'rejected' && isBackendMissing(vRes.reason);
      const hMissing = hRes.status === 'rejected' && isBackendMissing(hRes.reason);
      if (vMissing && hMissing) {
        setUnavailable(true);
      } else {
        if (vRes.status === 'fulfilled') {
          const v = vRes.value?.data;
          setVersions(Array.isArray(v) ? v : v?.versions ?? []);
        } else if (!vMissing) {
          toast.error(vRes.reason?.message || 'Không tải được lịch sử phiên bản.');
        }
        if (hRes.status === 'fulfilled') {
          const h = hRes.value?.data;
          const list = Array.isArray(h) ? h : h?.transactions ?? h?.history ?? [];
          setTransactions(list);
        } else if (!hMissing) {
          toast.error(hRes.reason?.message || 'Không tải được lịch sử giao dịch.');
        }
      }
      setLoading(false);
    })();
  }, [policyNumber, toast]);

  const viewVersion = async (v) => {
    const ver = v.version ?? v.number;
    try {
      const res = await policyService.getVersion(policyNumber, ver);
      const body = res?.data;
      setSelected({ version: ver, snapshot: body?.data ?? body ?? {} });
    } catch (err) {
      if (isBackendMissing(err)) toast.info(BACKEND_TODO_HINT);
      else toast.error(err.message || 'Không tải được chi tiết phiên bản.');
    }
  };

  if (loading) return (<><Skeleton height={24} width="35%" style={{ marginBottom: '1rem' }} /><Skeleton height={120} /></>);
  if (unavailable) {
    return (
      <EmptyState
        icon={<History size={40} />}
        title="Chưa có lịch sử để hiển thị"
        desc="Lịch sử phiên bản sẽ hiển thị khi backend bổ sung API."
      />
    );
  }

  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '1.25rem' }}>
      {/* Phiên bản */}
      <div className="glass-card">
        <h3 style={{ marginBottom: '1rem' }}>Các phiên bản</h3>
        {versions.length === 0 ? (
          <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Chưa có phiên bản nào được lưu.</p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
            {versions.map((v, i) => {
              const ver = v.version ?? v.number ?? i + 1;
              const active = selected?.version === ver;
              return (
                <button
                  key={ver}
                  type="button"
                  className={`coverage-option${active ? ' open' : ''}`}
                  style={{ cursor: 'pointer', textAlign: 'left', width: '100%', background: 'none', color: 'var(--text-primary)', fontFamily: 'var(--font-main)' }}
                  onClick={() => viewVersion(v)}
                  aria-pressed={active}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <strong>Phiên bản v{ver}</strong>
                    <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                      {formatDateTime(v.createdAt || v.updatedAt)}
                    </span>
                  </div>
                  {v.actor && <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginTop: '0.25rem' }}>Người thực hiện: {v.actor}</div>}
                </button>
              );
            })}
          </div>
        )}
        {selected && (
          <div style={{ marginTop: '1.25rem' }}>
            <h4 style={{ marginBottom: '0.75rem' }}>So sánh v{selected.version} với hiện tại</h4>
            <VersionDiff snapshot={selected.snapshot} current={currentPolicy} />
          </div>
        )}
      </div>

      {/* Giao dịch */}
      <div className="glass-card">
        <h3 style={{ marginBottom: '1rem' }}>Lịch sử giao dịch</h3>
        {transactions.length === 0 ? (
          <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Chưa có giao dịch nào được ghi nhận.</p>
        ) : (
          <div className="timeline">
            {transactions.map((t, i) => (
              <div key={t.id ?? i} className="timeline-item">
                <div className="t-time">{formatDateTime(t.createdAt || t.timestamp || t.at)}</div>
                <div className="t-title">{t.type || t.title || 'Thao tác'}</div>
                <div className="t-desc">{t.description || t.note || ''}</div>
                {t.actor && <div className="t-desc">Người thực hiện: {t.actor}</div>}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

/** Bảng so sánh gói bảo hiểm giữa snapshot version cũ và policy hiện tại. */
function VersionDiff({ snapshot, current }) {
  const oldMap = flattenCoverages(snapshot);
  const newMap = flattenCoverages(current);
  const keys = Array.from(new Set([...Object.keys(oldMap), ...Object.keys(newMap)]));

  if (keys.length === 0) return <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Không có dữ liệu gói bảo hiểm để so sánh.</p>;

  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th>Gói bảo hiểm</th>
            <th>Hạn mức</th>
            <th>Khấu trừ</th>
            <th>Phí</th>
            <th>Thay đổi</th>
          </tr>
        </thead>
        <tbody>
          {keys.map((k) => {
            const o = oldMap[k];
            const n = newMap[k];
            let cls = '';
            let note = '—';
            if (o && !n) { cls = 'diff-removed'; note = 'Đã xóa'; }
            else if (!o && n) { cls = 'diff-added'; note = 'Thêm mới'; }
            else if (o.limit !== n.limit || o.deductible !== n.deductible) { cls = 'diff-changed'; note = 'Điều chỉnh'; }
            const show = n || o;
            return (
              <tr key={k} className={cls}>
                <td>{show.locationName}<br /><small className="policy-number" style={{ fontSize: '0.75rem' }}>{show.coverageCode}</small></td>
                <td>
                  {o && <div style={{ textDecoration: o && !n ? 'line-through' : 'none' }}>{formatMoney(o.limit)}</div>}
                  {n && <div><strong>{formatMoney(n.limit)}</strong></div>}
                </td>
                <td>
                  {o && <div style={{ textDecoration: o && !n ? 'line-through' : 'none' }}>{formatMoney(o.deductible)}</div>}
                  {n && <div><strong>{formatMoney(n.deductible)}</strong></div>}
                </td>
                <td>
                  {o && <div style={{ textDecoration: o && !n ? 'line-through' : 'none' }}>{formatMoney(o.premium)}</div>}
                  {n && <div><strong>{formatMoney(n.premium)}</strong></div>}
                </td>
                <td>{note}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
