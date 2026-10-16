import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import {
  MapPin, Plus, Pencil, Trash2, ChevronDown, History,
  FileText, ArrowLeft, CreditCard, Zap, Send, SlidersHorizontal, Download, Check,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import {
  policyService, policyLocationService, policyCoverageService,
  documentService, downloadBlob,
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

// Danh mục coverage dùng cho endorsement (đồng bộ với BuyWizardPage)
const COVERAGE_CATALOG = [
  { code: 'PROP_FIRE', name: 'Cháy nổ tài sản', baseRate: 0.075, type: 'PROPERTY' },
  { code: 'PROP_WATER', name: 'Ngập nước', baseRate: 0.05, type: 'PROPERTY' },
  { code: 'LIAB_PUBLIC', name: 'Trách nhiệm công cộng', baseRate: 0.12, type: 'LIABILITY' },
  { code: 'WCOMP', name: 'Tai nạn lao động', baseRate: 0.2, type: 'WORKERS_COMP' },
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
  const submitEndorse = async ({ reason, description, locations }) => {
    setBusy(true);
    try {
      await policyService.endorse(policyNumber, { reason, description, locations });
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
      <EndorseModal open={endorseOpen} onClose={() => setEndorseOpen(false)} onSubmit={submitEndorse} busy={busy} policy={policy} />

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

// ---------------------------------------------------------------- Endorse wizard
// Luong 3 buoc: chon thao tac -> them dia diem / sua coverage -> xem lai & xac nhan
// Lưu ý contract BE: POST /policies/{n}/endorsements nhận {reason*, description, locations[]}
// và REPLACE toàn bộ locations → FE phải gửi lại FULL danh sách (cũ đã sửa + mới).
function EndorseModal({ open, onClose, onSubmit, busy, policy }) {
  const [step, setStep] = useState(1);
  const [mode, setMode] = useState('add-location'); // 'add-location' | 'edit-coverage'

  // Bước 2a: địa điểm mới
  const [newAddress, setNewAddress] = useState('');
  const [newCoverages, setNewCoverages] = useState([]); // [{coverageCode, limit, deductible}]
  const [ncCode, setNcCode] = useState(COVERAGE_CATALOG[0].code);
  const [ncLimit, setNcLimit] = useState('');
  const [ncDeduct, setNcDeduct] = useState('');

  // Bước 2b: sửa địa điểm hiện có
  const [editLocId, setEditLocId] = useState('');
  const [editCoverages, setEditCoverages] = useState([]); // working copy [{...coverage}]
  const [ecCode, setEcCode] = useState(COVERAGE_CATALOG[0].code);
  const [ecLimit, setEcLimit] = useState('');
  const [ecDeduct, setEcDeduct] = useState('');

  // Bước 3: xác nhận
  const [reason, setReason] = useState('');
  const [description, setDescription] = useState('');

  const locations = policy?.locations || [];
  const hasLocations = locations.length > 0;

  // Reset khi mở modal
  useEffect(() => {
    if (open) {
      setStep(1);
      setMode(hasLocations ? 'add-location' : 'add-location');
      setNewAddress(''); setNewCoverages([]);
      setNcCode(COVERAGE_CATALOG[0].code); setNcLimit(''); setNcDeduct('');
      setEditLocId(locations[0]?.locationId != null ? String(locations[0].locationId) : '');
      setEcCode(COVERAGE_CATALOG[0].code); setEcLimit(''); setEcDeduct('');
      setReason(''); setDescription('');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  // Nạp coverages của địa điểm đang sửa vào working copy
  useEffect(() => {
    const loc = locations.find((l) => String(l.locationId) === String(editLocId));
    setEditCoverages((loc?.coverages || []).map((c) => ({ ...c })));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editLocId, open]);

  const fmtMoney = (v) => (v == null || v === '' ? '—' : Number(v).toLocaleString('vi-VN') + ' ₫');

  // --- Bước 2a handlers ---
  const addNewCoverage = () => {
    if (newCoverages.some((c) => c.coverageCode === ncCode)) return;
    const limit = Number(ncLimit);
    if (!ncLimit || Number.isNaN(limit) || limit <= 0) return;
    const deduct = Number(ncDeduct);
    setNewCoverages([...newCoverages, {
      coverageCode: ncCode,
      limit,
      deductible: Number.isNaN(deduct) || deduct < 0 ? 0 : deduct,
    }]);
    setNcLimit(''); setNcDeduct('');
  };
  const removeNewCoverage = (code) => setNewCoverages(newCoverages.filter((c) => c.coverageCode !== code));
  const canGoStep3FromAdd = newAddress.trim() !== '' && newCoverages.length > 0;

  // --- Bước 2b handlers ---
  const updateEditCoverage = (code, field, value) => {
    setEditCoverages(editCoverages.map((c) => (c.coverageCode === code ? { ...c, [field]: value } : c)));
  };
  const removeEditCoverage = (code) => setEditCoverages(editCoverages.filter((c) => c.coverageCode !== code));
  const addEditCoverage = () => {
    if (editCoverages.some((c) => c.coverageCode === ecCode)) return;
    const limit = Number(ecLimit);
    if (!ecLimit || Number.isNaN(limit) || limit <= 0) return;
    const cat = COVERAGE_CATALOG.find((x) => x.code === ecCode);
    const deduct = Number(ecDeduct);
    setEditCoverages([...editCoverages, {
      coverageCode: ecCode,
      coverageName: cat?.name || ecCode,
      coverageType: cat?.type || 'PROPERTY',
      limit,
      deductible: Number.isNaN(deduct) || deduct < 0 ? 0 : deduct,
      termMonths: 12,
      baseRate: cat?.baseRate || 0,
    }]);
    setEcLimit(''); setEcDeduct('');
  };
  const selectedLocation = locations.find((l) => String(l.locationId) === String(editLocId));

  // --- Dựng full locations gửi BE ---
  const buildLocations = () => {
    const base = locations.map((l) => ({
      locationId: l.locationId,
      address: l.address,
      coverages: (l.coverages || []).map((c) => ({
        coverageCode: c.coverageCode,
        coverageName: c.coverageName,
        coverageType: c.coverageType,
        limit: Number(c.limit) || 0,
        deductible: Number(c.deductible) || 0,
        termMonths: c.termMonths || 12,
        baseRate: c.baseRate || 0,
      })),
    }));
    if (mode === 'add-location') {
      const maxId = base.reduce((m, l) => Math.max(m, Number(l.locationId) || 0), 0);
      base.push({
        locationId: maxId + 1,
        address: newAddress.trim(),
        coverages: newCoverages.map((nc) => {
          const cat = COVERAGE_CATALOG.find((x) => x.code === nc.coverageCode);
          return {
            coverageCode: nc.coverageCode,
            coverageName: cat?.name || nc.coverageCode,
            coverageType: cat?.type || 'PROPERTY',
            limit: Number(nc.limit) || 0,
            deductible: Number(nc.deductible) || 0,
            termMonths: 12,
            baseRate: cat?.baseRate || 0,
          };
        }),
      });
    } else {
      const idx = base.findIndex((l) => String(l.locationId) === String(editLocId));
      if (idx >= 0) {
        base[idx].coverages = editCoverages.map((c) => ({
          coverageCode: c.coverageCode,
          coverageName: c.coverageName,
          coverageType: c.coverageType,
          limit: Number(c.limit) || 0,
          deductible: Number(c.deductible) || 0,
          termMonths: c.termMonths || 12,
          baseRate: c.baseRate || 0,
        }));
      }
    }
    return base;
  };

  const handleConfirm = () => {
    if (!reason.trim() || busy) return;
    onSubmit({ reason: reason.trim(), description: description.trim(), locations: buildLocations() });
  };

  const stepTitles = { 1: 'Chọn thao tác', 2: mode === 'add-location' ? 'Thêm địa điểm mới' : 'Chỉnh sửa coverage', 3: 'Xem lại & xác nhận' };

  const renderStepIndicator = () => (
    <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1.25rem' }}>
      {[1, 2, 3].map((s) => (
        <div key={s} style={{ flex: 1, textAlign: 'center' }}>
          <div style={{
            height: '4px', borderRadius: '2px', marginBottom: '0.35rem',
            background: s <= step ? 'var(--primary, #1663d9)' : '#e5e7eb',
          }} />
          <span style={{ fontSize: '0.75rem', color: s === step ? 'var(--primary, #1663d9)' : '#9ca3af', fontWeight: s === step ? 600 : 400 }}>
            {stepTitles[s]}
          </span>
        </div>
      ))}
    </div>
  );

  const coverageRow = (c, onRemove, onLimitChange, onDeductChange, editable) => {
    const cat = COVERAGE_CATALOG.find((x) => x.code === c.coverageCode);
    return (
      <div key={c.coverageCode} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '0.5rem', border: '1px solid #e5e7eb', borderRadius: '8px', marginBottom: '0.5rem' }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontWeight: 600, fontSize: '0.9rem' }}>{cat?.name || c.coverageName || c.coverageCode}</div>
          <div style={{ fontSize: '0.75rem', color: '#6b7280' }}>{c.coverageCode}</div>
        </div>
        {editable ? (
          <>
            <input type="number" min="0" className="form-control" style={{ width: '130px' }} placeholder="Hạn mức"
              value={c.limit ?? ''} onChange={(e) => onLimitChange(c.coverageCode, e.target.value)} />
            <input type="number" min="0" className="form-control" style={{ width: '110px' }} placeholder="Khấu trừ"
              value={c.deductible ?? ''} onChange={(e) => onDeductChange(c.coverageCode, e.target.value)} />
          </>
        ) : (
          <div style={{ fontSize: '0.8rem', color: '#374151', textAlign: 'right' }}>
            <div>Hạn mức: {fmtMoney(c.limit)}</div>
            <div>Khấu trừ: {fmtMoney(c.deductible)}</div>
          </div>
        )}
        <button type="button" className="btn btn-secondary btn-sm" onClick={() => onRemove(c.coverageCode)} disabled={busy}>Xóa</button>
      </div>
    );
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Yêu cầu điều chỉnh hợp đồng"
      footer={
        <>
          {step > 1 && <button className="btn btn-secondary" onClick={() => setStep(step - 1)} disabled={busy}>Quay lại</button>}
          <button className="btn btn-secondary" onClick={onClose} disabled={busy}>Hủy</button>
          {step === 1 && <button className="btn btn-primary" onClick={() => setStep(2)} disabled={busy}>Tiếp tục</button>}
          {step === 2 && mode === 'add-location' && (
            <button className="btn btn-primary" onClick={() => setStep(3)} disabled={busy || !canGoStep3FromAdd}
              title={!canGoStep3FromAdd ? 'Nhập địa chỉ và thêm ít nhất 1 coverage' : ''}>Tiếp tục</button>
          )}
          {step === 2 && mode === 'edit-coverage' && (
            <button className="btn btn-primary" onClick={() => setStep(3)} disabled={busy || !editLocId}>Tiếp tục</button>
          )}
          {step === 3 && (
            <button className="btn btn-primary" onClick={handleConfirm} disabled={busy || !reason.trim()}
              title={!reason.trim() ? 'Vui lòng nhập lý do điều chỉnh' : ''}>
              {busy ? <span className="spinner" /> : null} Xác nhận gửi yêu cầu
            </button>
          )}
        </>
      }
    >
      {renderStepIndicator()}

      {step === 1 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          <label style={{ display: 'flex', gap: '0.75rem', padding: '0.9rem', border: `2px solid ${mode === 'add-location' ? 'var(--primary, #1663d9)' : '#e5e7eb'}`, borderRadius: '10px', cursor: 'pointer' }}>
            <input type="radio" name="endorse-mode" checked={mode === 'add-location'} onChange={() => setMode('add-location')} />
            <div>
              <div style={{ fontWeight: 600 }}>Thêm địa điểm mới</div>
              <div style={{ fontSize: '0.85rem', color: '#6b7280' }}>Bổ sung địa điểm được bảo hiểm cùng các coverage của nó</div>
            </div>
          </label>
          <label style={{ display: 'flex', gap: '0.75rem', padding: '0.9rem', border: `2px solid ${mode === 'edit-coverage' ? 'var(--primary, #1663d9)' : '#e5e7eb'}`, borderRadius: '10px', cursor: hasLocations ? 'pointer' : 'not-allowed', opacity: hasLocations ? 1 : 0.5 }}>
            <input type="radio" name="endorse-mode" checked={mode === 'edit-coverage'} onChange={() => hasLocations && setMode('edit-coverage')} disabled={!hasLocations} />
            <div>
              <div style={{ fontWeight: 600 }}>Chỉnh sửa coverage của địa điểm hiện có</div>
              <div style={{ fontSize: '0.85rem', color: '#6b7280' }}>
                {hasLocations ? 'Sửa hạn mức/khấu trừ, thêm hoặc bớt coverage' : 'Hợp đồng chưa có địa điểm nào'}
              </div>
            </div>
          </label>
        </div>
      )}

      {step === 2 && mode === 'add-location' && (
        <>
          <div className="form-group">
            <label className="form-label" htmlFor="end-new-addr">Địa chỉ địa điểm mới *</label>
            <input id="end-new-addr" className="form-control" value={newAddress}
              onChange={(e) => setNewAddress(e.target.value)} placeholder="Ví dụ: Kho hàng KCN Tân Bình, Lô B2…" />
          </div>
          <div className="form-group">
            <label className="form-label">Thêm coverage cho địa điểm này</label>
            <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '0.75rem', flexWrap: 'wrap' }}>
              <select className="form-control" style={{ flex: 1, minWidth: '160px' }} value={ncCode} onChange={(e) => setNcCode(e.target.value)}>
                {COVERAGE_CATALOG.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}
              </select>
              <input type="number" min="0" className="form-control" style={{ width: '130px' }} placeholder="Hạn mức *" value={ncLimit} onChange={(e) => setNcLimit(e.target.value)} />
              <input type="number" min="0" className="form-control" style={{ width: '110px' }} placeholder="Khấu trừ" value={ncDeduct} onChange={(e) => setNcDeduct(e.target.value)} />
              <button type="button" className="btn btn-secondary" onClick={addNewCoverage} disabled={busy}>Thêm</button>
            </div>
            {newCoverages.length === 0
              ? <p className="form-hint">Chưa có coverage nào — thêm ít nhất 1 coverage để tiếp tục.</p>
              : newCoverages.map((c) => coverageRow(c, removeNewCoverage, null, null, false))}
          </div>
        </>
      )}

      {step === 2 && mode === 'edit-coverage' && (
        <>
          <div className="form-group">
            <label className="form-label" htmlFor="end-edit-loc">Chọn địa điểm</label>
            <select id="end-edit-loc" className="form-control" value={editLocId} onChange={(e) => setEditLocId(e.target.value)}>
              {locations.map((l) => <option key={l.locationId} value={l.locationId}>#{l.locationId} — {l.address}</option>)}
            </select>
          </div>
          <div className="form-group">
            <label className="form-label">Coverages tại địa điểm này</label>
            {editCoverages.length === 0
              ? <p className="form-hint">Địa điểm này chưa có coverage nào.</p>
              : editCoverages.map((c) => coverageRow(
                  c, removeEditCoverage,
                  (code, v) => updateEditCoverage(code, 'limit', v),
                  (code, v) => updateEditCoverage(code, 'deductible', v),
                  true))}
            <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.75rem', flexWrap: 'wrap' }}>
              <select className="form-control" style={{ flex: 1, minWidth: '160px' }} value={ecCode} onChange={(e) => setEcCode(e.target.value)}>
                {COVERAGE_CATALOG.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}
              </select>
              <input type="number" min="0" className="form-control" style={{ width: '130px' }} placeholder="Hạn mức *" value={ecLimit} onChange={(e) => setEcLimit(e.target.value)} />
              <input type="number" min="0" className="form-control" style={{ width: '110px' }} placeholder="Khấu trừ" value={ecDeduct} onChange={(e) => setEcDeduct(e.target.value)} />
              <button type="button" className="btn btn-secondary" onClick={addEditCoverage} disabled={busy}>Thêm coverage</button>
            </div>
          </div>
        </>
      )}

      {step === 3 && (
        <>
          <div style={{ background: '#f9fafb', border: '1px solid #e5e7eb', borderRadius: '10px', padding: '0.9rem', marginBottom: '1rem' }}>
            <div style={{ fontWeight: 600, marginBottom: '0.5rem' }}>Tóm tắt yêu cầu</div>
            {mode === 'add-location' ? (
              <>
                <div>Thêm địa điểm: <b>{newAddress.trim()}</b></div>
                <div style={{ marginTop: '0.35rem', fontSize: '0.9rem' }}>
                  {newCoverages.map((c) => {
                    const cat = COVERAGE_CATALOG.find((x) => x.code === c.coverageCode);
                    return <div key={c.coverageCode}>• {cat?.name || c.coverageCode} — hạn mức {fmtMoney(c.limit)}, khấu trừ {fmtMoney(c.deductible)}</div>;
                  })}
                </div>
              </>
            ) : (
              <>
                <div>Địa điểm: <b>#{selectedLocation?.locationId} — {selectedLocation?.address}</b></div>
                <div style={{ marginTop: '0.35rem', fontSize: '0.9rem' }}>
                  {editCoverages.length === 0
                    ? <div>Xóa toàn bộ coverage tại địa điểm này.</div>
                    : editCoverages.map((c) => {
                        const cat = COVERAGE_CATALOG.find((x) => x.code === c.coverageCode);
                        return <div key={c.coverageCode}>• {cat?.name || c.coverageName || c.coverageCode} — hạn mức {fmtMoney(c.limit)}, khấu trừ {fmtMoney(c.deductible)}</div>;
                      })}
                </div>
              </>
            )}
            <div style={{ marginTop: '0.5rem', fontSize: '0.8rem', color: '#6b7280' }}>
              Phí bảo hiểm sẽ được tính lại tự động sau khi yêu cầu được duyệt.
            </div>
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="end-reason">Lý do điều chỉnh *</label>
            <input id="end-reason" className="form-control" value={reason}
              onChange={(e) => setReason(e.target.value)} placeholder="Ví dụ: Mở rộng kho hàng mới phục vụ mùa cao điểm" />
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="end-desc">Mô tả thêm</label>
            <textarea id="end-desc" className="form-control" rows={3} value={description}
              onChange={(e) => setDescription(e.target.value)} placeholder="Thông tin bổ sung cho đội thẩm định (không bắt buộc)…" />
          </div>
        </>
      )}
    </Modal>
  );
}

// ---------------------------------------------------------------- History
// ---------------------------------------------------------------- History
const flattenCoverages = (snapshot) => {
  const map = {};
  safeList(snapshot?.locations).forEach((l, li) => {
    const lid = l.locationId ?? li;
    safeList(l.coverages).forEach((c) => {
      map[`${lid}|${c.coverageCode}`] = {
        locationId: lid,
        locationName: l.address || `Địa điểm ${lid}`,
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
      // Backend trả PolicyVersion { policySnapshot: {...} } — dữ liệu thật nằm trong policySnapshot
      const pv = body?.data ?? body ?? {};
      setSelected({ version: ver, snapshot: pv?.policySnapshot ?? pv ?? {} });
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
      <DocumentsSection policyNumber={policyNumber} />
    </div>
  );
}

/** Tài liệu hợp đồng: chứng nhận + tóm tắt PDF. */
function DocumentsSection({ policyNumber }) {
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
      const res = kind === 'cert'
        ? await documentService.downloadCertificate(policyNumber)
        : await documentService.downloadSummary(policyNumber);
      downloadBlob(res?.data ?? res, `${policyNumber}-${label}.pdf`);
      toast.success(`Đã tải ${label}.`);
    } catch (err) {
      toast.error(err.message || `Không tải được ${label}.`);
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="glass-card" style={{ marginTop: '1.5rem' }}>
      <h3 style={{ marginBottom: '1rem' }}>Tài liệu hợp đồng</h3>
      <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', marginBottom: docs.length ? '1rem' : 0 }}>
        <button className="btn btn-primary" disabled={busy} onClick={() => dl('cert', 'chung-nhan')}>
          {busy === 'cert' ? <span className="spinner" /> : <Download size={16} />} Tải chứng nhận (PDF)
        </button>
        <button className="btn btn-secondary" disabled={busy} onClick={() => dl('summary', 'tom-tat')}>
          {busy === 'summary' ? <span className="spinner" /> : <Download size={16} />} Tải tóm tắt (PDF)
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
