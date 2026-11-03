import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import {
  MapPin, Plus, Pencil, Trash2, ChevronDown, History,
  FileText, ArrowLeft, CreditCard, Zap, Send, SlidersHorizontal, Download, Check, ShieldAlert,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import {
  policyService, policyLocationService, policyCoverageService,
  documentService, downloadBlob, negotiationService,
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

// Danh mục coverage dùng chung (đồng bộ với BuyWizardPage)
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
  const { user, hasRole, hasAnyRole } = useAuth();

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
    // PLAN §1.6: "Tôi đã chuyển khoản" chỉ ghi nhận đã báo trả (marker
    // paymentClaimedAt / paymentStatus=CLAIMED), policy GIỮ BOUND và hiển thị
    // "Đang chờ đối soát". Kích hoạt ACTIVE là bước riêng của FINANCE ở trang
    // đối soát (ADMIN giữ nút override "Kích hoạt trực tiếp" bên dưới).
    setBusy(true);
    try {
      await policyService.claimPayment(policyNumber);
      toast.success('Đã ghi nhận khai báo chuyển khoản. Hợp đồng đang chờ kế toán đối soát trước khi kích hoạt.');
      setPayOpen(false);
      await load();
    } catch (err) {
      failToast(err, 'ghi nhận chuyển khoản');
    } finally {
      setBusy(false);
    }
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
  // PLAN §1.6: marker "đã báo trả" — BOUND + marker = đang chờ đối soát.
  const isPaymentClaimed = policy.paymentStatus === 'CLAIMED' || policy.paymentClaimedAt != null;
  // Ma trận PLAN §1.3 (FE chỉ ẩn/hiện cho UX, BE mới là chốt chặn):
  // chuyển trạng thái / thanh toán / endorsement thuộc CUSTOMER (chủ), AGENT
  // (được giao) và ADMIN; UNDERWRITER/FINANCE xem chi tiết ở chế độ đọc.
  const canManage = hasAnyRole(['ROLE_CUSTOMER', 'ROLE_AGENT', 'ROLE_ADMIN']);
  const isInsured = !policy.insured?.email || (user?.email && policy.insured.email.toLowerCase() === user.email.toLowerCase());
  const canClaim = (hasRole('ROLE_CUSTOMER') || hasRole('ROLE_USER')) && isInsured;
  const isAdmin = hasRole('ROLE_ADMIN');

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

      {/* Trạng thái chờ đối soát (PLAN §1.6): BOUND + đã báo trả, chưa ACTIVE */}
      {status === 'BOUND' && isPaymentClaimed && (
        <div style={{ background: '#fffbeb', border: '1px solid #fde68a', borderRadius: '10px', padding: '0.8rem 1rem', marginBottom: '1.5rem', fontSize: '0.9rem', color: '#92400e' }}>
          <strong>Đang chờ đối soát:</strong> bạn đã khai báo chuyển khoản
          {policy.paymentClaimedAt ? ` lúc ${formatDateTime(policy.paymentClaimedAt)}` : ''}. Hợp đồng sẽ
          được kích hoạt sau khi kế toán xác nhận đã nhận tiền.
        </div>
      )}

      {/* Action bar theo trạng thái + vai trò */}
      <ActionBar
        status={status}
        isAdmin={isAdmin}
        canManage={canManage}
        isPaymentClaimed={isPaymentClaimed}
        canClaim={canClaim}
        onClaim={() => navigate(`/claims/new?policyNumber=${policy.policyNumber}`)}
        busy={busy}
        onRequestQuote={requestQuote}
        onAcceptQuote={acceptQuote}
        onPayNow={() => setPayOpen(true)}
        onAdminActivate={adminActivate}
        onEndorse={() => setEndorseOpen(true)}
        onDelete={() => setDelPolicy(true)}
      />

      {/* Panel thương lượng giá — CHỈ hiện khi QUOTED (PLAN §2.5) */}
      {status === 'QUOTED' && (
        <NegotiationPanel policy={policy} user={user} onPolicyReload={load} failToast={failToast} />
      )}

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
          readOnly={!['DRAFT', 'QUOTED', 'BOUND'].includes(status)}
          onAddLocation={() => setLocModal({ mode: 'add' })}
          onEditLocation={(loc) => setLocModal({ mode: 'edit', loc })}
          onDeleteLocation={(loc) => setDelLoc(loc.locationId)}
          onAddCoverage={(loc) => setCovModal({ mode: 'add', locId: loc.locationId })}
          onEditCoverage={(loc, cov) => setCovModal({ mode: 'edit', locId: loc.locationId, cov })}
          onDeleteCoverage={(loc, cov) => setDelCov({ locId: loc.locationId, cov })}
        />
      )}

      {tab === 'history' && <HistoryTab policyNumber={policyNumber} currentPolicy={policy} />}

      {tab === 'negotiation' && (
        <NegotiationPanel policy={policy} user={user} onPolicyReload={load} failToast={failToast} />
      )}

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
// Nút bấm filter theo role VÀ status (PLAN §1.4/§1.3): FE chỉ ẩn/hiện cho UX,
// BE vẫn check lại mọi transition. Nút xác nhận thu tiền của FINANCE KHÔNG ở
// đây mà ở trang đối soát /admin/doi-soat.
function ActionBar({ status, isAdmin, canManage, isPaymentClaimed, canClaim, onClaim, busy, onRequestQuote, onAcceptQuote, onPayNow, onAdminActivate, onEndorse, onDelete }) {
  const btn = (props, children) => (
    <button type="button" className="btn" disabled={busy} {...props}>{children}</button>
  );
  return (
    <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', marginBottom: '1.5rem' }}>
      {status === 'DRAFT' && canManage && (
        <>
          {btn({ className: 'btn btn-primary', onClick: onRequestQuote }, <><Send size={16} /> Yêu cầu báo giá</>)}
          {btn({ className: 'btn btn-danger', onClick: onDelete }, <><Trash2 size={16} /> Xóa hợp đồng</>)}
        </>
      )}
      {status === 'QUOTED' && canManage && btn({ className: 'btn btn-primary', onClick: onAcceptQuote }, <><Check size={16} /> Chấp nhận & thanh toán</>)}
      {status === 'BOUND' && (
        <>
          {canManage && !isPaymentClaimed && btn({ className: 'btn btn-primary', onClick: onPayNow }, <><CreditCard size={16} /> Thanh toán ngay</>)}
          {isAdmin && btn({ className: 'btn btn-secondary', onClick: onAdminActivate }, <><Zap size={16} /> Kích hoạt trực tiếp</>)}
        </>
      )}
      {status === 'ACTIVE' && (
        <>
          {canManage && btn({ className: 'btn btn-secondary', onClick: onEndorse }, <><SlidersHorizontal size={16} /> Yêu cầu điều chỉnh</>)}
          {canClaim && btn({ className: 'btn btn-warning', style: { backgroundColor: '#ea580c', borderColor: '#ea580c', color: '#fff' }, onClick: onClaim }, <><ShieldAlert size={16} /> Yêu cầu bồi thường</>)}
        </>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- Negotiation
// Panel "Thương lượng giá" 2 cột theo mẫu demo:
// Cột trái: Tóm tắt giá, Tiến trình 0/2-1/2-2/2, Đặt giá sàn, Lịch sử đề xuất, Form gửi giá.
// Cột phải: Khung chat trao đổi trực tiếp (append-only, lưu vết toàn bộ).
const NEG_STATUS_LABEL = {
  OPEN: 'Đang thương lượng',
  PENDING_CONFIRMATION: 'Chờ xác nhận chốt giá (1/2)',
  ACCEPTED: 'Đã chốt giá & khóa (2/2)',
  REJECTED: 'Đã từ chối',
  SUPERSEDED: 'Đã thay thế',
};

const ROLE_LABEL = {
  UNDERWRITER: 'Thẩm định viên',
  CUSTOMER: 'Khách hàng',
  AGENT: 'Đại lý',
  ADMIN: 'Quản trị viên',
};

function NegotiationPanel({ policy, user, onPolicyReload, failToast }) {
  const toast = useToast();
  const { hasRole } = useAuth();
  const [negotiations, setNegotiations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [unavailable, setUnavailable] = useState(false);
  const [premium, setPremium] = useState('');
  const [message, setMessage] = useState('');
  const [floorInput, setFloorInput] = useState('');
  const [chatInput, setChatInput] = useState('');
  const [busy, setBusy] = useState(false);

  const loadNeg = useCallback(async () => {
    setLoading(true);
    try {
      const res = await negotiationService.list(policy.policyNumber);
      const d = res?.data;
      const list = Array.isArray(d) ? d : d?.items ?? d?.negotiations ?? [];
      setNegotiations(list);
      setUnavailable(false);
    } catch (err) {
      if (isBackendMissing(err)) setUnavailable(true);
      else toast.error(err.message || 'Không tải được lịch sử thương lượng.');
    } finally {
      setLoading(false);
    }
  }, [policy.policyNumber, toast]);

  useEffect(() => { loadNeg(); }, [loadNeg]);

  const me = String(user?.email || '').toLowerCase();
  const isCustomer = me && me === String(policy.insured?.email || '').toLowerCase();
  const isAgent = me && policy.assignedAgentEmail && me === String(policy.assignedAgentEmail).toLowerCase();
  const isDemand = isCustomer || isAgent;
  const isSupply = hasRole('ROLE_UNDERWRITER');
  const isAdminView = hasRole('ROLE_ADMIN');

  // Lấy phiên thương lượng hiện hành (ưu tiên phiên OPEN hoặc PENDING_CONFIRMATION, fallback phiên mới nhất)
  const activeNeg = negotiations.find((n) => n.status === 'OPEN' || n.status === 'PENDING_CONFIRMATION');
  const latest = negotiations[0] || null;
  const shown = activeNeg || latest;

  const offers = safeList(shown?.offers);
  const sortedOffers = [...offers].sort((a, b) => (a.round ?? 0) - (b.round ?? 0));
  const lastOffer = sortedOffers[sortedOffers.length - 1];
  const currentPrice = shown?.currentOffer?.proposedPremium ?? lastOffer?.proposedPremium ?? null;
  const listPremium = shown?.listPremium ?? policy.totalPremium;
  const floorPremium = shown?.floorPremium ?? null;

  // Lượt đi: tính theo phe (Demand vs Supply)
  const demandTurn = !lastOffer || lastOffer.proposedByRole === 'UNDERWRITER';
  const myTurn = shown?.status === 'OPEN' && !isAdminView && (
    (isDemand && demandTurn) || (isSupply && !demandTurn)
  );

  const canOpen = !shown && policy.status === 'QUOTED' && isDemand && !isAdminView;
  const canSetFloor = isSupply && !isAdminView && shown && shown.status === 'OPEN' && floorPremium == null
    && !sortedOffers.some((o) => o.proposedByRole === 'UNDERWRITER');

  // Kiểm tra xác nhận 2 lớp
  const isPendingConfirmation = shown?.status === 'PENDING_CONFIRMATION';
  const proposerEmail = shown?.proposerEmail || lastOffer?.proposedByEmail || '';
  const offerWasSupply = lastOffer?.proposedByRole === 'UNDERWRITER';
  // LUẬT MỚI: AGENT không bao giờ canConfirm. Offer phe cầu đưa thì chỉ CUSTOMER canConfirm.
  const canConfirm = isPendingConfirmation && !isAdminView && !isAgent && (
    shown?.canConfirm != null ? shown.canConfirm : (
      offerWasSupply ? (isSupply && lastOffer?.proposedByEmail?.toLowerCase() === me) : isCustomer
    )
  );

  // Tiến trình xác nhận: 0/2, 1/2, 2/2
  const progressStep = shown?.status === 'ACCEPTED' ? 2 : isPendingConfirmation ? 1 : 0;

  // Gửi đề xuất / phản hồi / chấp nhận / từ chối
  const submitAction = async (kind) => {
    setBusy(true);
    try {
      if (kind === 'open') {
        const val = Number(premium);
        if (!Number.isFinite(val) || val <= 0) {
          toast.error('Giá đề xuất phải lớn hơn 0.');
          return;
        }
        if (listPremium != null && val > Number(listPremium)) {
          toast.error(`Giá đề xuất không được vượt giá hệ thống (${formatMoney(listPremium)}).`);
          return;
        }
        await negotiationService.open(policy.policyNumber, { proposedPremium: val, message: message.trim() || undefined });
        toast.success('Đã gửi đề xuất giá mở đầu.');
      } else if (kind === 'counter') {
        const val = Number(premium);
        if (!Number.isFinite(val) || val <= 0) {
          toast.error('Giá đề xuất phải lớn hơn 0.');
          return;
        }
        if (listPremium != null && val > Number(listPremium)) {
          toast.error(`Giá đề xuất không được vượt giá hệ thống (${formatMoney(listPremium)}).`);
          return;
        }
        if (isSupply && floorPremium != null && val < floorPremium) {
          toast.error(`Thẩm định viên không được đề xuất giá dưới giá sàn (${formatMoney(floorPremium)}).`);
          return;
        }
        await negotiationService.addOffer(policy.policyNumber, shown.id, { proposedPremium: val, message: message.trim() || undefined });
        toast.success('Đã gửi phản hồi giá mới.');
      } else if (kind === 'accept') {
        if (isSupply && floorPremium != null && currentPrice < floorPremium) {
          toast.error(`Không thể chấp nhận mức giá dưới giá sàn (${formatMoney(floorPremium)}).`);
          return;
        }
        await negotiationService.accept(policy.policyNumber, shown.id);
        toast.success('Đã chấp thuận mức giá (1/2). Đang chờ người đề xuất xác nhận lần cuối.');
        await onPolicyReload();
      } else if (kind === 'reject') {
        await negotiationService.reject(policy.policyNumber, shown.id);
        toast.success('Đã từ chối mức giá này. Thương lượng vẫn tiếp tục để hai bên trả giá.');
        await onPolicyReload();
      } else if (kind === 'confirm') {
        await negotiationService.confirm(policy.policyNumber, shown.id);
        toast.success('Đã hoàn tất xác nhận chốt giá (2/2)! Hợp đồng đã được cập nhật giá thương lượng.');
        await onPolicyReload();
      } else if (kind === 'decline') {
        await negotiationService.declineConfirmation(policy.policyNumber, shown.id);
        toast.success('Đã từ chối xác nhận chốt giá. Phiên thương lượng đã được mở lại.');
        await onPolicyReload();
      }
      setPremium('');
      setMessage('');
      await loadNeg();
    } catch (err) {
      failToast(err, 'thao tác thương lượng');
    } finally {
      setBusy(false);
    }
  };

  // Đặt giá sàn
  const handleSetFloor = async () => {
    const f = Number(floorInput);
    if (!Number.isFinite(f) || f <= 0) {
      toast.error('Giá sàn phải lớn hơn 0.');
      return;
    }
    if (listPremium != null && f > Number(listPremium)) {
      toast.error(`Giá sàn không được lớn hơn giá niêm yết (${formatMoney(listPremium)}).`);
      return;
    }
    setBusy(true);
    try {
      await negotiationService.setFloor(policy.policyNumber, shown.id, f);
      toast.success('Đã thiết lập giá sàn bảo mật thành công.');
      setFloorInput('');
      await loadNeg();
    } catch (err) {
      failToast(err, 'đặt giá sàn');
    } finally {
      setBusy(false);
    }
  };

  // Gửi tin nhắn chat
  const handleSendChat = async () => {
    const text = chatInput.trim();
    if (!text) return;
    setBusy(true);
    try {
      await negotiationService.sendMessage(policy.policyNumber, shown.id, text);
      setChatInput('');
      await loadNeg();
    } catch (err) {
      failToast(err, 'gửi tin nhắn');
    } finally {
      setBusy(false);
    }
  };

  if (loading) return (<div className="glass-card" style={{ marginBottom: '1.5rem' }}><Skeleton height={140} /></div>);
  if (unavailable) return null;



  const isLocked = shown?.status === 'ACCEPTED';
  const isQuoted = policy.status === 'QUOTED';

  return (
    <div style={{ marginBottom: '1.5rem' }}>
      {/* Tiêu đề & Tiến trình chốt giá 2 lớp */}
      <div className="glass-card" style={{ marginBottom: '1.25rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.75rem', marginBottom: '1rem' }}>
          <div>
            <h3 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              Thương lượng giá hợp đồng {policy.policyNumber}
            </h3>
            <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
              Phiên thương lượng append-only · Minh bạch · Không sửa xóa lịch sử
            </span>
          </div>
          {shown && (
            <span className={`badge badge-${shown.status === 'ACCEPTED' ? 'success' : shown.status === 'PENDING_CONFIRMATION' ? 'warning' : shown.status === 'REJECTED' ? 'danger' : 'info'}`}>
              {NEG_STATUS_LABEL[shown.status] || shown.status}
            </span>
          )}
        </div>

        {/* Thanh tiến trình 0/2 - 1/2 - 2/2 */}
        <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', background: 'var(--bg-subtle, #f8fafc)', padding: '0.75rem 1rem', borderRadius: '10px' }}>
          <div style={{ flex: 1, textAlign: 'center', opacity: progressStep >= 0 ? 1 : 0.4 }}>
            <div style={{ fontWeight: progressStep === 0 ? 700 : 500, color: progressStep === 0 ? '#2563eb' : 'inherit' }}>
              0/2: Đang thương lượng
            </div>
            <small style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Đề xuất & phản hồi giá</small>
          </div>
          <div style={{ color: 'var(--border-subtle, #cbd5e1)' }}>→</div>
          <div style={{ flex: 1, textAlign: 'center', opacity: progressStep >= 1 ? 1 : 0.4 }}>
            <div style={{ fontWeight: progressStep === 1 ? 700 : 500, color: progressStep === 1 ? '#d97706' : 'inherit' }}>
              1/2: Chờ xác nhận
            </div>
            <small style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Một bên đã chấp thuận</small>
          </div>
          <div style={{ color: 'var(--border-subtle, #cbd5e1)' }}>→</div>
          <div style={{ flex: 1, textAlign: 'center', opacity: progressStep >= 2 ? 1 : 0.4 }}>
            <div style={{ fontWeight: progressStep === 2 ? 700 : 500, color: progressStep === 2 ? '#059669' : 'inherit' }}>
              2/2: Đã chốt & Khóa
            </div>
            <small style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Ghi nhận vào hợp đồng</small>
          </div>
        </div>
      </div>

      {/* Bố cục 2 cột chính */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: '1.25rem', alignItems: 'start' }}>
        {/* CỘT TRÁI: Tóm tắt giá, Tiến trình, Form gửi giá / đặt sàn, Lịch sử */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          {isAgent && (
            <div className="glass-card" style={{ padding: '0.75rem 1rem', background: '#eff6ff', border: '1px solid #bfdbfe', borderRadius: '10px' }}>
              <span style={{ color: '#1e40af', fontSize: '0.875rem' }}>
                ℹ️ <strong>Vai trò Đại lý:</strong> Bạn chỉ hỗ trợ khách hàng trả giá và trao đổi trực tiếp; quyền quyết định chấp nhận mức giá và xác nhận chốt hợp đồng hoàn toàn thuộc về khách hàng.
              </span>
            </div>
          )}
          {/* Card Tóm tắt giá */}
          <div className="glass-card">
            <h4 style={{ marginTop: 0, marginBottom: '0.75rem' }}>Thông tin mức giá</h4>
            <dl className="kv" style={{ margin: 0 }}>
              <dt>Giá niêm yết</dt>
              <dd><strong>{formatMoney(listPremium)}</strong></dd>

              {floorPremium != null && (
                <>
                  <dt>Giá sàn (bảo mật)</dt>
                  <dd>
                    <strong style={{ color: '#dc2626' }}>{formatMoney(floorPremium)}</strong>
                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginLeft: '0.4rem' }}>
                      (Chỉ Thẩm định & Admin thấy)
                    </span>
                  </dd>
                </>
              )}

              <dt>Giá đề xuất hiện tại</dt>
              <dd>
                <strong style={{ color: '#2563eb', fontSize: '1.1rem' }}>
                  {currentPrice != null ? formatMoney(currentPrice) : 'Chưa có'}
                </strong>
              </dd>

              {shown?.acceptedPremium != null && (
                <>
                  <dt>Giá đã chốt (2/2)</dt>
                  <dd><strong style={{ color: '#059669', fontSize: '1.1rem' }}>{formatMoney(shown.acceptedPremium)}</strong></dd>
                </>
              )}

              {shown && (
                <>
                  <dt>Mở bởi</dt>
                  <dd>{shown.openedBy || '—'} · {formatDateTime(shown.openedAt)}</dd>
                </>
              )}
            </dl>
          </div>

          {/* Banner hành động khi PENDING_CONFIRMATION (1/2) */}
          {isPendingConfirmation && (
            <div className="glass-card" style={{ border: '2px solid #f59e0b', background: '#fffbeb' }}>
              <h4 style={{ color: '#b45309', marginTop: 0, marginBottom: '0.5rem' }}>
                Xác nhận chốt giá (1/2 → 2/2)
              </h4>
              <p style={{ margin: '0 0 0.75rem 0', fontSize: '0.9rem', color: '#92400e' }}>
                Mức giá <strong>{formatMoney(currentPrice)}</strong> đã được đối phương chấp thuận.
                {canConfirm
                  ? ' Bạn là người đề xuất mức giá này, vui lòng xác nhận lần cuối để chính thức chốt giá và ghi vào hợp đồng, hoặc từ chối để mở lại thương lượng.'
                  : ` Đang chờ bên đề xuất (${proposerEmail || 'đối phương'}) xác nhận để chính thức hoàn tất chốt giá.`}
              </p>
              {canConfirm && (
                <div style={{ display: 'flex', gap: '0.6rem', flexWrap: 'wrap' }}>
                  <button type="button" className="btn btn-primary" disabled={busy} onClick={() => submitAction('confirm')}>
                    {busy ? <span className="spinner" /> : null} Xác nhận chốt giá (2/2)
                  </button>
                  <button type="button" className="btn btn-secondary" disabled={busy} onClick={() => submitAction('decline')}>
                    Từ chối, mở lại thương lượng
                  </button>
                </div>
              )}
            </div>
          )}

          {/* Form Đặt giá sàn (Underwriter) */}
          {canSetFloor && (
            <div className="glass-card" style={{ border: '1px solid #fed7aa', background: '#fff7ed' }}>
              <h4 style={{ color: '#c2410c', marginTop: 0, marginBottom: '0.5rem' }}>Thiết lập giá sàn bảo hiểm</h4>
              <p style={{ fontSize: '0.85rem', color: '#9a3412', margin: '0 0 0.75rem 0' }}>
                Giá sàn chỉ được đặt 1 lần duy nhất trước phản hồi đầu tiên của bạn. Khách hàng và đại lý sẽ không thấy giá sàn.
              </p>
              <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                <input
                  type="number" min="1" max={listPremium ?? undefined} className="form-control"
                  style={{ maxWidth: 220 }} placeholder="Nhập giá sàn (VND)"
                  value={floorInput} onChange={(e) => setFloorInput(e.target.value)}
                  disabled={busy}
                />
                <button type="button" className="btn btn-primary btn-sm" disabled={busy || !floorInput} onClick={handleSetFloor}>
                  {busy ? <span className="spinner" /> : null} Đặt giá sàn
                </button>
              </div>
            </div>
          )}

          {/* Form Gửi giá mở đầu hoặc Trả giá tiếp theo */}
          {(canOpen || (myTurn && !isPendingConfirmation)) && (
            <div className="glass-card">
              <h4 style={{ marginTop: 0, marginBottom: '0.75rem' }}>
                {canOpen ? 'Gửi đề xuất giá mở đầu' : 'Gửi giá phản hồi (trả giá)'}
              </h4>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '0.75rem' }}>
                <div className="form-group" style={{ marginBottom: 0 }}>
                  <label className="form-label" htmlFor="neg-input-premium">Mức giá đề xuất (VND) *</label>
                  <input
                    id="neg-input-premium" type="number" min="1" max={listPremium ?? undefined}
                    className="form-control" value={premium} onChange={(e) => setPremium(e.target.value)}
                    placeholder={`Tối đa ${formatMoney(listPremium)}`} disabled={busy}
                  />
                </div>
                <div className="form-group" style={{ marginBottom: 0 }}>
                  <label className="form-label" htmlFor="neg-input-msg">Ghi chú kèm theo</label>
                  <input
                    id="neg-input-msg" className="form-control" value={message}
                    onChange={(e) => setMessage(e.target.value)} placeholder="Không bắt buộc" disabled={busy}
                  />
                </div>
              </div>

              {isSupply && floorPremium != null && (
                <small style={{ color: '#b91c1c', display: 'block', marginTop: '0.4rem' }}>
                  * Lưu ý: Giá bạn đề xuất không được thấp hơn giá sàn ({formatMoney(floorPremium)}).
                </small>
              )}

              <div style={{ display: 'flex', gap: '0.6rem', marginTop: '1rem', flexWrap: 'wrap' }}>
                {canOpen && (
                  <button type="button" className="btn btn-primary" disabled={busy || !premium} onClick={() => submitAction('open')}>
                    {busy ? <span className="spinner" /> : null} Gửi đề xuất mở đầu
                  </button>
                )}
                {myTurn && (
                  <>
                    <button type="button" className="btn btn-primary" disabled={busy || !premium} onClick={() => submitAction('counter')}>
                      {busy ? <span className="spinner" /> : null} Gửi phản hồi giá
                    </button>
                    {lastOffer && (
                      <>
                        {(isSupply || isCustomer) && (
                          <button type="button" className="btn btn-secondary" disabled={busy} onClick={() => submitAction('accept')}>
                            Chấp nhận mức giá hiện tại (1/2)
                          </button>
                        )}
                        <button type="button" className="btn btn-danger" disabled={busy} onClick={() => submitAction('reject')}>
                          Từ chối mức giá này
                        </button>
                      </>
                    )}
                  </>
                )}
              </div>
            </div>
          )}

          {/* Thông báo trạng thái lượt đi */}
          {shown?.status === 'OPEN' && !myTurn && !isAdminView && (
            <div className="glass-card" style={{ padding: '0.85rem 1rem', background: 'var(--bg-subtle, #f8fafc)' }}>
              <span style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>
                ⏳ {demandTurn ? 'Đang chờ khách hàng / đại lý phản hồi mức giá.' : 'Đang chờ chuyên viên thẩm định phản hồi mức giá.'}
              </span>
            </div>
          )}

          {isAdminView && (
            <div className="glass-card" style={{ padding: '0.85rem 1rem', background: '#f1f5f9' }}>
              <span style={{ color: '#475569', fontSize: '0.875rem' }}>
                👁️ Bạn đang xem với vai trò Quản trị viên (ADMIN) — chỉ có quyền xem lịch sử và tiến trình thương lượng.
              </span>
            </div>
          )}

          {/* Lịch sử các vòng đề xuất */}
          <div className="glass-card">
            <h4 style={{ marginTop: 0, marginBottom: '0.75rem' }}>Lịch sử đề xuất giá (Append-Only)</h4>
            {sortedOffers.length === 0 ? (
              <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Chưa có đề xuất giá nào được ghi nhận.</p>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
                {sortedOffers.map((o, i) => (
                  <div
                    key={o.round ?? i}
                    style={{
                      display: 'flex', gap: '0.75rem', padding: '0.75rem 0.9rem',
                      border: '1px solid var(--border-subtle, #e2e8f0)', borderRadius: '10px',
                      background: o.decision === 'ACCEPTED' ? '#f0fdf4' : o.decision === 'REJECTED' ? '#fef2f2' : 'transparent',
                    }}
                  >
                    <span className="badge badge-info" style={{ height: 'fit-content' }}>
                      Vòng {o.round ?? i + 1}
                    </span>
                    <div style={{ flex: 1 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <div>
                          <strong style={{ color: '#2563eb', fontSize: '1rem' }}>{formatMoney(o.proposedPremium)}</strong>
                          <span style={{ color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
                            {' '}— {ROLE_LABEL[o.proposedByRole] || o.proposedByRole} ({o.proposedByEmail})
                          </span>
                        </div>
                        <span style={{ fontSize: '0.8rem', fontWeight: 600, color: o.decision === 'ACCEPTED' ? '#16a34a' : o.decision === 'REJECTED' ? '#dc2626' : 'var(--text-secondary)' }}>
                          {o.decision === 'ACCEPTED' ? 'Đã chấp nhận' : o.decision === 'REJECTED' ? 'Đã từ chối' : 'Đề xuất'}
                        </span>
                      </div>
                      <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginTop: '0.2rem' }}>
                        {formatDateTime(o.createdAt)}
                        {o.message ? ` · “${o.message}”` : ''}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* CỘT PHẢI: Khung chat trực tiếp append-only */}
        <div className="glass-card" style={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 520 }}>
          <div style={{ borderBottom: '1px solid var(--border-subtle, #e2e8f0)', paddingBottom: '0.75rem', marginBottom: '0.75rem' }}>
            <h4 style={{ margin: '0 0 0.25rem 0' }}>Trao đổi trực tiếp</h4>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              Tin nhắn được lưu vết đầy đủ và chỉ đọc khi chốt giá. Mọi trao đổi trong chat mang tính tham khảo và không thay thế việc gửi đề xuất giá chính thức.
            </div>
          </div>

          {/* Vùng cuộn tin nhắn */}
          <div
            style={{
              flex: 1, minHeight: 340, maxHeight: 460, overflowY: 'auto',
              display: 'flex', flexDirection: 'column', gap: '0.6rem', padding: '0.5rem',
              background: 'var(--bg-subtle, #f8fafc)', borderRadius: '8px', marginBottom: '0.75rem',
            }}
          >
            {safeList(shown?.messages).length === 0 ? (
              <div style={{ textAlign: 'center', color: 'var(--text-muted)', margin: 'auto', fontSize: '0.9rem' }}>
                Chưa có tin nhắn nào. Bạn có thể trao đổi thêm chi tiết về mức phí tại đây.
              </div>
            ) : (
              safeList(shown?.messages).map((m, idx) => {
                const mine = m.senderEmail && m.senderEmail.toLowerCase() === me;
                return (
                  <div
                    key={m.messageId || idx}
                    style={{
                      alignSelf: mine ? 'flex-end' : 'flex-start',
                      maxWidth: '82%',
                      background: mine ? '#dbeafe' : '#ffffff',
                      border: '1px solid',
                      borderColor: mine ? '#bfdbfe' : '#e2e8f0',
                      borderRadius: '12px',
                      padding: '0.6rem 0.85rem',
                      boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
                    }}
                  >
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', marginBottom: '0.2rem', display: 'flex', justifyContent: 'space-between', gap: '0.5rem' }}>
                      <span><strong>{mine ? 'Bạn' : m.senderEmail}</strong> ({ROLE_LABEL[m.senderRole] || m.senderRole})</span>
                      <span>{formatDateTime(m.createdAt)}</span>
                    </div>
                    <div style={{ fontSize: '0.9rem', wordBreak: 'break-word', color: '#1e293b' }}>
                      {m.content}
                    </div>
                  </div>
                );
              })
            )}
          </div>

          {/* Khung nhập tin nhắn */}
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <input
              type="text" className="form-control"
              placeholder={isLocked ? 'Phiên thương lượng đã khóa (chỉ đọc)' : !isQuoted ? 'Hợp đồng không còn ở trạng thái báo giá' : isAdminView ? 'Quản trị viên chỉ có quyền đọc' : 'Nhập tin nhắn trao đổi...'}
              value={chatInput} onChange={(e) => setChatInput(e.target.value)}
              disabled={busy || isLocked || !isQuoted || isAdminView || !shown}
              onKeyDown={(e) => { if (e.key === 'Enter') handleSendChat(); }}
            />
            <button
              type="button" className="btn btn-primary"
              disabled={busy || isLocked || !isQuoted || isAdminView || !chatInput.trim() || !shown}
              onClick={handleSendChat}
            >
              {busy ? <span className="spinner" /> : null} Gửi
            </button>
          </div>
        </div>
      </div>
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
          {policy.assignedAgentEmail && (<><dt>Đại lý phụ trách</dt><dd>{policy.assignedAgentEmail}</dd></>)}
          {policy.paymentStatus && (<><dt>Thanh toán</dt><dd>{policy.paymentStatus === 'CLAIMED' ? 'Đã khai báo — chờ đối soát' : policy.paymentStatus === 'CONFIRMED' ? 'Đã xác nhận thu tiền' : 'Chưa khai báo'}</dd></>)}
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
          {policy.negotiatedPremium != null && (<><dt>Giá đã thương lượng</dt><dd><strong style={{ color: '#047857' }}>{formatMoney(policy.negotiatedPremium)}</strong></dd></>)}
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
function LocationsTab({ locations, onAddLocation, onEditLocation, onDeleteLocation, onAddCoverage, onEditCoverage, onDeleteCoverage, readOnly }) {
  const [openId, setOpenId] = useState(locations[0]?.locationId ?? 0);

  useEffect(() => {
    if (!locations.some((l) => (l.locationId ?? 0) === openId)) {
      setOpenId(locations[0]?.locationId ?? 0);
    }
  }, [locations, openId]);

  return (
    <div>
      {readOnly ? (
        <div style={{ background: '#fffbeb', border: '1px solid #fde68a', borderRadius: '10px', padding: '0.8rem 1rem', marginBottom: '1rem', fontSize: '0.9rem', color: '#92400e' }}>
          Hợp đồng đã có hiệu lực — mọi thay đổi địa điểm/gói bảo hiểm vui lòng thực hiện qua <strong>Yêu cầu điều chỉnh</strong>.
        </div>
      ) : (
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '1rem' }}>
          <button type="button" className="btn btn-secondary btn-sm" onClick={onAddLocation}>
            <Plus size={15} /> Thêm địa điểm
          </button>
        </div>
      )}
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
                {!readOnly && (
                  <div style={{ display: 'flex', gap: '0.6rem', marginBottom: '1rem', flexWrap: 'wrap' }}>
                    <button type="button" className="btn btn-ghost btn-sm" onClick={() => onEditLocation(l)} aria-label={`Sửa địa điểm ${l.name || i + 1}`}>
                      <Pencil size={14} /> Sửa địa điểm
                    </button>
                    <button type="button" className="btn btn-ghost btn-sm" onClick={() => onDeleteLocation(l)} aria-label={`Xóa địa điểm ${l.name || i + 1}`}>
                      <Trash2 size={14} /> Xóa địa điểm
                    </button>
                  </div>
                )}
                <div className="table-wrap">
                  <table className="table">
                    <thead>
                      <tr>
                        <th>Mã gói</th>
                        <th>Tên gói</th>
                        <th>Hạn mức</th>
                        <th>Khấu trừ</th>
                        <th>Phí</th>
                        {!readOnly && <th style={{ width: 110 }}>Thao tác</th>}
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
                          {!readOnly && (
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
                          )}
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
                {!readOnly && (
                  <button type="button" className="btn btn-secondary btn-sm" style={{ marginTop: '1rem' }} onClick={() => onAddCoverage(l)}>
                    <Plus size={15} /> Thêm gói bảo hiểm
                  </button>
                )}
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
  const catSelected = COVERAGE_CATALOG.find((x) => x.code === code) || {};
  const estPremium = valid ? Math.round(limitNum * (catSelected.baseRate || 0)) : 0;
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
      baseRate: cat.baseRate || existing?.baseRate || 0,
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
      <div style={{ background: '#f0f7ff', border: '1px solid #bfdbfe', borderRadius: '10px', padding: '0.8rem 1rem', marginBottom: '1rem' }}>
        <div style={{ fontSize: '0.85rem', color: '#6b7280' }}>Phí dự kiến (tạm tính = hạn mức × tỉ lệ)</div>
        <div style={{ fontSize: '1.25rem', fontWeight: 700, color: '#1663d9' }}>
          {valid ? formatMoney(estPremium) : '—'}
        </div>
        <div style={{ fontSize: '0.75rem', color: '#9ca3af' }}>Phí chính thức do hệ thống tính khi lưu.</div>
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
