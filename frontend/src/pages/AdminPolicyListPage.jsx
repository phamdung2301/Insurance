import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Eye, Pencil, Trash2, ArrowUp, ArrowDown, RotateCcw, Search } from 'lucide-react';
import { policyService } from '../services/api';
import { Pagination, EmptyState, Skeleton } from '../components/ui';
import { StatusBadge, STATUS_META } from '../components/Status';
import { Modal } from '../components/Modal';
import { formatMoney, formatDate, isValidEmail } from '../utils/format';
import { normalizePhone, isValidVnPhone, isValidPersonName, isValidAddress } from '../utils/validation';
import { isBackendMissing } from '../utils/backend';
import { useToast } from '../context/ToastContext';

const EMPTY_FILTERS = {
  status: '',
  policyNumber: '',
  insuredName: '',
  location: '',
  effFrom: '',
  effTo: '',
  minPremium: '',
  maxPremium: '',
};

const SORT_OPTIONS = [
  { value: '', label: 'Sắp xếp mặc định' },
  { value: 'effectiveDate', label: 'Ngày hiệu lực' },
  { value: 'totalPremium', label: 'Phí bảo hiểm' },
  { value: 'policyNumber', label: 'Mã hợp đồng' },
];

const premiumOf = (p) => Number(p?.totalPremium ?? p?.premium ?? p?.annualPremium ?? 0) || 0;
const effDateOf = (p) => p?.effectiveDate ?? p?.startDate ?? null;
const expDateOf = (p) => p?.expirationDate ?? p?.expiryDate ?? null;
const numberOf = (p) => p?.policyNumber ?? p?.number ?? p?.id ?? '';

// Trạng thái được sửa trực tiếp qua PUT /policies/{n} — khớp đúng rule BE
// PolicyServiceImpl.updatePolicy: từ chối ACTIVE, CANCELLED, EXPIRED.
// (Ngoài ra BE còn khóa QUOTED khi có thương lượng đã chốt/đang chờ xác nhận
// — checkAcceptedNegotiationLock — nhưng danh sách này không mang trạng thái
// thương lượng nên trường hợp đó BE vẫn trả lỗi lúc lưu, hiện trong modal.)
const EDITABLE_STATUSES = ['DRAFT', 'QUOTED', 'BOUND'];
const canEditPolicy = (p) => EDITABLE_STATUSES.includes(p?.status);

// Gộp khoảng trắng thừa bên trong thành 1 khoảng trắng (format.js chưa có
// normalizeSpaces nên dùng helper cục bộ).
const normalizeSpaces = (s) => (s || '').trim().replace(/\s+/g, ' ');

const todayStr = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

export function AdminPolicyListPage() {
  const toast = useToast();
  const navigate = useNavigate();

  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [debounced, setDebounced] = useState(EMPTY_FILTERS);
  const [sortBy, setSortBy] = useState('');
  const [sortDir, setSortDir] = useState('desc');
  const [page, setPage] = useState(0);
  const [policies, setPolicies] = useState([]);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [backendMissing, setBackendMissing] = useState(false);

  const [editing, setEditing] = useState(null);
  const [editForm, setEditForm] = useState({ name: '', email: '', phone: '', address: '', effectiveDate: '' });
  const [editSaving, setEditSaving] = useState(false);
  const [editError, setEditError] = useState('');
  const [editFieldErrors, setEditFieldErrors] = useState({});

  const [deleting, setDeleting] = useState(null);
  const [deleteBusy, setDeleteBusy] = useState(false);

  const timer = useRef(null);

  // Debounce 400ms cho mọi ô nhập tìm kiếm
  useEffect(() => {
    timer.current = setTimeout(() => setDebounced(filters), 400);
    return () => clearTimeout(timer.current);
  }, [filters]);

  const setField = (key) => (e) => setFilters((f) => ({ ...f, [key]: e.target.value }));

  const fetchPolicies = async (signal) => {
    setLoading(true);
    setBackendMissing(false);
    try {
      const params = { page, size: 10 };
      if (debounced.status) params.status = debounced.status;
      if (debounced.policyNumber.trim()) params.policyNumber = debounced.policyNumber.trim();
      if (debounced.insuredName.trim()) params.insuredName = debounced.insuredName.trim();
      if (debounced.location.trim()) params.location = debounced.location.trim();
      if (debounced.effFrom) params.effectiveDateFrom = debounced.effFrom;
      if (debounced.effTo) params.effectiveDateTo = debounced.effTo;
      if (debounced.minPremium !== '') params.minPremium = Number(debounced.minPremium);
      if (debounced.maxPremium !== '') params.maxPremium = Number(debounced.maxPremium);
      if (sortBy) {
        params.sortBy = sortBy;
        params.sortDir = sortDir;
      }
      const res = await policyService.searchPolicies(params);
      if (signal?.cancelled) return;
      const d = res?.data;
      const list = d?.content ?? d?.items ?? (Array.isArray(d) ? d : []);
      setPolicies(list);
      setTotalPages(d?.totalPages ?? 1);
      setTotalElements(d?.totalElements ?? d?.total ?? list.length);
    } catch (err) {
      if (signal?.cancelled) return;
      if (isBackendMissing(err)) {
        setBackendMissing(true);
        setPolicies([]);
      } else {
        toast.error(err.message);
      }
    } finally {
      if (!signal?.cancelled) setLoading(false);
    }
  };

  useEffect(() => {
    const signal = { cancelled: false };
    fetchPolicies(signal);
    return () => { signal.cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debounced, sortBy, sortDir, page]);

  // Về trang đầu mỗi khi điều kiện tìm kiếm đổi
  useEffect(() => {
    setPage(0);
  }, [debounced, sortBy, sortDir]);

  const resetFilters = () => {
    setFilters(EMPTY_FILTERS);
    setSortBy('');
    setSortDir('desc');
    setPage(0);
  };

  const openEdit = (p) => {
    if (!canEditPolicy(p)) return;
    setEditing(p);
    setEditError('');
    setEditFieldErrors({});
    setEditForm({
      name: p?.insured?.name ?? '',
      email: p?.insured?.email ?? '',
      phone: p?.insured?.phone ?? '',
      address: p?.insured?.address ?? '',
      effectiveDate: effDateOf(p) ? String(effDateOf(p)).slice(0, 10) : '',
    });
  };

  // Đổi 1 field trong form sửa + xóa lỗi của field đó (báo lại khi bấm Lưu).
  const setEditField = (key) => (e) => {
    const value = e.target.value;
    setEditForm((f) => ({ ...f, [key]: value }));
    setEditFieldErrors((errs) => {
      if (!errs[key]) return errs;
      const next = { ...errs };
      delete next[key];
      return next;
    });
  };

  const editErr = (k) => (editFieldErrors[k] ? <div className="form-error">{editFieldErrors[k]}</div> : null);

  const validateEditForm = () => {
    const errors = {};
    const name = normalizeSpaces(editForm.name);
    if (!name) {
      errors.name = 'Vui lòng nhập tên khách hàng.';
    } else if (!isValidPersonName(name)) {
      errors.name = 'Tên phải từ 2 đến 100 ký tự, chỉ gồm chữ cái và khoảng trắng.';
    }
    // SĐT không bắt buộc ở form này (BE InsuredDto.phone không có @NotBlank)
    // nhưng nếu đã nhập thì phải đúng chuẩn di động Việt Nam.
    const phone = (editForm.phone || '').trim();
    if (phone && !isValidVnPhone(normalizePhone(phone))) {
      errors.phone = 'Số điện thoại phải là số di động Việt Nam 10 chữ số, bắt đầu bằng 03, 05, 07, 08 hoặc 09.';
    }
    // B-10: BE bắt buộc insured.email (@NotBlank + @Email) nên email là
    // bắt buộc ở form này; thiếu email là payload 400 VALIDATION_ERROR.
    const email = (editForm.email || '').trim();
    if (!email) {
      errors.email = 'Vui lòng nhập email người được bảo hiểm.';
    } else if (email.length > 100) {
      errors.email = 'Email tối đa 100 ký tự.';
    } else if (!isValidEmail(email)) {
      errors.email = 'Địa chỉ email không hợp lệ.';
    }
    const address = normalizeSpaces(editForm.address);
    if (address && !isValidAddress(address)) {
      errors.address = 'Địa chỉ cần ghi rõ số nhà, đường, phường/xã, quận/huyện, tỉnh/thành (tối thiểu 10 ký tự).';
    }
    // N-12: ngày hiệu lực không được sau ngày hết hạn đang lưu. Ngày quá khứ
    // chỉ cảnh báo (không chặn) — xem effPastWarning ở dưới.
    if (editForm.effectiveDate) {
      const exp = expDateOf(editing);
      const expStr = exp ? String(exp).slice(0, 10) : '';
      if (expStr && editForm.effectiveDate > expStr) {
        errors.effectiveDate = `Ngày hiệu lực phải trước ngày hết hạn (${formatDate(expStr)}).`;
      }
    }
    return errors;
  };

  const handleEditSave = async (e) => {
    if (e) e.preventDefault();
    if (editing && !canEditPolicy(editing)) {
      setEditError('Không thể sửa hợp đồng ở trạng thái này.');
      return;
    }
    const errors = validateEditForm();
    if (Object.keys(errors).length > 0) {
      setEditFieldErrors(errors);
      setEditError('Vui lòng kiểm tra lại các thông tin chưa hợp lệ.');
      return;
    }
    setEditFieldErrors({});
    setEditSaving(true);
    setEditError('');
    try {
      const phone = (editForm.phone || '').trim();
      await policyService.updatePolicy(numberOf(editing), {
        insured: {
          name: normalizeSpaces(editForm.name),
          email: (editForm.email || '').trim().toLowerCase(),
          phone: phone ? normalizePhone(phone) : '',
          address: normalizeSpaces(editForm.address),
        },
        effectiveDate: editForm.effectiveDate || undefined,
      });
      toast.success('Đã cập nhật hợp đồng.');
      setEditing(null);
      fetchPolicies({ cancelled: false });
    } catch (err) {
      setEditError(err.message);
    } finally {
      setEditSaving(false);
    }
  };

  // N-12: cảnh báo (không chặn lưu) khi ngày hiệu lực là ngày quá khứ.
  const effPastWarning =
    editing && editForm.effectiveDate && editForm.effectiveDate < todayStr()
      ? 'Ngày hiệu lực nằm trong quá khứ. Vui lòng kiểm tra lại trước khi lưu.'
      : '';

  const handleDelete = async () => {
    if (!deleting) return;
    setDeleteBusy(true);
    try {
      await policyService.deletePolicy(numberOf(deleting));
      toast.success('Đã xóa hợp đồng.');
      setDeleting(null);
      fetchPolicies({ cancelled: false });
    } catch (err) {
      toast.error(err.message);
    } finally {
      setDeleteBusy(false);
    }
  };

  return (
    <div>
      <div className="page-header">
        <h1>Quản lý hợp đồng</h1>
        <p>Tìm kiếm, xem, sửa và xóa hợp đồng bảo hiểm trong hệ thống.</p>
      </div>

      <div className="glass-card" style={{ marginBottom: '1.25rem' }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: '0.9rem' }}>
          <div className="form-group" style={{ margin: 0 }}>
            <label className="form-label">Trạng thái</label>
            <select className="form-control" value={filters.status} onChange={setField('status')}>
              <option value="">Tất cả trạng thái</option>
              {Object.entries(STATUS_META).map(([s, m]) => (
                <option key={s} value={s}>{m.label}</option>
              ))}
            </select>
          </div>
          <div className="form-group" style={{ margin: 0 }}>
            <label className="form-label">Mã hợp đồng</label>
            <input className="form-control" placeholder="VD: HD-0001" value={filters.policyNumber} onChange={setField('policyNumber')} />
          </div>
          <div className="form-group" style={{ margin: 0 }}>
            <label className="form-label">Tên khách hàng</label>
            <input className="form-control" placeholder="Tên người được bảo hiểm" value={filters.insuredName} onChange={setField('insuredName')} />
          </div>
          <div className="form-group" style={{ margin: 0 }}>
            <label className="form-label">Địa điểm</label>
            <input className="form-control" placeholder="Tên hoặc địa chỉ" value={filters.location} onChange={setField('location')} />
          </div>
          <div className="form-group" style={{ margin: 0 }}>
            <label className="form-label">Hiệu lực từ</label>
            <input type="date" className="form-control" value={filters.effFrom} onChange={setField('effFrom')} />
          </div>
          <div className="form-group" style={{ margin: 0 }}>
            <label className="form-label">Hiệu lực đến</label>
            <input type="date" className="form-control" value={filters.effTo} onChange={setField('effTo')} />
          </div>
          <div className="form-group" style={{ margin: 0 }}>
            <label className="form-label">Phí từ</label>
            <input type="number" min="0" className="form-control" placeholder="0" value={filters.minPremium} onChange={setField('minPremium')} />
          </div>
          <div className="form-group" style={{ margin: 0 }}>
            <label className="form-label">Phí đến</label>
            <input type="number" min="0" className="form-control" placeholder="Không giới hạn" value={filters.maxPremium} onChange={setField('maxPremium')} />
          </div>
        </div>
        <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1rem', flexWrap: 'wrap', alignItems: 'center' }}>
          <select
            className="form-control"
            style={{ width: 'auto' }}
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value)}
            aria-label="Sắp xếp theo"
          >
            {SORT_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
          <button
            className="btn btn-ghost btn-sm"
            onClick={() => setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'))}
            aria-label={sortDir === 'asc' ? 'Đang tăng dần, nhấn để đổi giảm dần' : 'Đang giảm dần, nhấn để đổi tăng dần'}
            title="Đổi chiều sắp xếp"
          >
            {sortDir === 'asc' ? <ArrowUp size={16} /> : <ArrowDown size={16} />}
            {sortDir === 'asc' ? ' Tăng dần' : ' Giảm dần'}
          </button>
          <button className="btn btn-ghost btn-sm" onClick={resetFilters}>
            <RotateCcw size={16} /> Đặt lại
          </button>
        </div>
      </div>

      <div className="glass-card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h3 style={{ margin: 0 }}>Danh sách hợp đồng</h3>
          {!loading && <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Tìm thấy {totalElements} hợp đồng</span>}
        </div>

        {loading ? (
          <div>
            {[0, 1, 2, 3, 4].map((i) => <Skeleton key={i} height={44} style={{ marginBottom: 8 }} />)}
          </div>
        ) : backendMissing ? (
          <EmptyState
            icon={<Search size={28} />}
            title="Chưa tải được danh sách hợp đồng"
            desc="Hệ thống chưa trả được dữ liệu tìm kiếm. Vui lòng thử lại sau."
          />
        ) : policies.length === 0 ? (
          <EmptyState
            icon={<Search size={28} />}
            title="Không tìm thấy hợp đồng"
            desc="Thử nới lỏng điều kiện tìm kiếm hoặc đặt lại bộ lọc."
            action={<button className="btn btn-secondary" onClick={resetFilters}>Đặt lại bộ lọc</button>}
          />
        ) : (
          <>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Mã hợp đồng</th>
                    <th>Khách hàng</th>
                    <th>Trạng thái</th>
                    <th>Ngày hiệu lực</th>
                    <th className="num">Phí bảo hiểm</th>
                    <th style={{ textAlign: 'right' }}>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {policies.map((p, i) => {
                    const n = numberOf(p);
                    const canEdit = canEditPolicy(p);
                    const canDelete = p?.status === 'DRAFT';
                    return (
                      <tr key={n + i}>
                        <td>
                          <button
                            className="btn btn-ghost btn-sm"
                            style={{ fontFamily: 'monospace', fontWeight: 700, padding: '0.25rem 0.4rem' }}
                            onClick={() => navigate(`/hop-dong/${n}`)}
                          >
                            {n}
                          </button>
                        </td>
                        <td>
                          <div style={{ fontWeight: 600 }}>{p?.insured?.name ?? '—'}</div>
                          <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>{p?.insured?.email ?? ''}</div>
                        </td>
                        <td><StatusBadge status={p?.status} /></td>
                        <td>{formatDate(effDateOf(p))}</td>
                        <td className="num">{formatMoney(premiumOf(p))}</td>
                        <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                          <button className="btn btn-ghost btn-sm" title="Xem chi tiết" onClick={() => navigate(`/hop-dong/${n}`)}>
                            <Eye size={16} />
                          </button>
                          <button
                            className="btn btn-ghost btn-sm"
                            title={canEdit ? 'Sửa hợp đồng' : `Không sửa được hợp đồng ở trạng thái ${STATUS_META[p?.status]?.label ?? p?.status}`}
                            disabled={!canEdit}
                            onClick={() => openEdit(p)}
                            style={!canEdit ? { opacity: 0.35, cursor: 'not-allowed' } : undefined}
                          >
                            <Pencil size={16} />
                          </button>
                          <button
                            className="btn btn-ghost btn-sm"
                            title={canDelete ? 'Xóa hợp đồng' : 'Chỉ xóa được hợp đồng nháp'}
                            disabled={!canDelete}
                            onClick={() => setDeleting(p)}
                            style={!canDelete ? { opacity: 0.35, cursor: 'not-allowed' } : undefined}
                          >
                            <Trash2 size={16} />
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <div style={{ marginTop: '1rem', display: 'flex', justifyContent: 'center' }}>
              <Pagination page={page} totalPages={totalPages} onChange={setPage} />
            </div>
          </>
        )}
      </div>

      <Modal
        open={!!editing}
        onClose={() => setEditing(null)}
        title={`Sửa hợp đồng ${editing ? numberOf(editing) : ''}`}
        footer={
          <>
            <button type="button" className="btn btn-ghost" onClick={() => setEditing(null)} disabled={editSaving}>Hủy</button>
            <button type="submit" form="edit-policy-form" className="btn btn-primary" disabled={editSaving}>
              {editSaving ? 'Đang lưu…' : 'Lưu thay đổi'}
            </button>
          </>
        }
      >
        {editError && <div className="alert alert-error">{editError}</div>}
        {/* N-12: bọc modal trong <form> để nhấn Enter submit được. Nút Lưu ở
            footer của Modal nằm ngoài <form> nên gắn qua form="edit-policy-form". */}
        <form id="edit-policy-form" onSubmit={handleEditSave} noValidate>
          <div className="form-group">
            <label className="form-label">Tên khách hàng *</label>
            <input
              className="form-control"
              maxLength={100}
              value={editForm.name}
              onChange={setEditField('name')}
            />
            {editErr('name')}
          </div>
          <div className="form-group">
            <label className="form-label">Email *</label>
            <input
              type="email"
              className="form-control"
              maxLength={100}
              placeholder="VD: khachhang@example.com"
              value={editForm.email}
              onChange={setEditField('email')}
            />
            {editErr('email')}
          </div>
          <div className="form-group">
            <label className="form-label">Số điện thoại</label>
            <input
              type="tel"
              inputMode="tel"
              maxLength={13}
              className="form-control"
              placeholder="VD: 0901234567"
              value={editForm.phone}
              onChange={setEditField('phone')}
            />
            {editErr('phone')}
          </div>
          <div className="form-group">
            <label className="form-label">Địa chỉ</label>
            <input
              className="form-control"
              maxLength={255}
              placeholder="Số nhà, đường, phường/xã, quận/huyện, tỉnh/thành"
              value={editForm.address}
              onChange={setEditField('address')}
            />
            {editErr('address')}
          </div>
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label">Ngày hiệu lực</label>
            <input
              type="date"
              className="form-control"
              value={editForm.effectiveDate}
              onChange={setEditField('effectiveDate')}
            />
            {editErr('effectiveDate')}
            {effPastWarning && !editFieldErrors.effectiveDate && (
              <div className="form-hint" style={{ color: '#f59e0b' }}>{effPastWarning}</div>
            )}
          </div>
        </form>
      </Modal>

      <Modal
        open={!!deleting}
        onClose={() => setDeleting(null)}
        title="Xóa hợp đồng"
        footer={
          <>
            <button className="btn btn-ghost" onClick={() => setDeleting(null)} disabled={deleteBusy}>Hủy</button>
            <button className="btn btn-danger" onClick={handleDelete} disabled={deleteBusy}>
              {deleteBusy ? 'Đang xóa…' : 'Xóa hợp đồng'}
            </button>
          </>
        }
      >
        <p>
          Bạn có chắc muốn xóa hợp đồng <strong style={{ fontFamily: 'monospace' }}>{deleting ? numberOf(deleting) : ''}</strong>?
          Hành động này không thể hoàn tác.
        </p>
      </Modal>
    </div>
  );
}
