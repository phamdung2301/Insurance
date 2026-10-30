import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { ScrollText, RefreshCw, ShieldCheck, Search } from 'lucide-react';
import { useToast } from '../context/ToastContext';
import { auditLogService } from '../services/auditLogApi';
import { EmptyState, Skeleton, Pagination } from '../components/ui';
import { formatDateTime } from '../utils/format';

// Số dòng mỗi trang — phân trang CLIENT-SIDE (BE chỉ có 1 endpoint GET
// /admin/audit-logs trả toàn bộ danh sách, không có tham số page/size).
const PAGE_SIZE = 20;

const shortRole = (r) => String(r || '').replace(/^ROLE_/, '');

function RoleBadges({ roles }) {
  if (!roles || roles.length === 0) return <span style={{ color: 'var(--text-muted)' }}>—</span>;
  return (
    <>
      {roles.map((r) => (
        <span key={r} className="badge badge-info" style={{ marginRight: '0.3rem' }}>{shortRole(r)}</span>
      ))}
    </>
  );
}

function friendlyError(err) {
  if (err?.status === 403) return 'Bạn không có quyền xem nhật ký quản trị. Chỉ tài khoản ADMIN mới truy cập được trang này.';
  return err?.message || 'Đã xảy ra lỗi không mong đợi.';
}

/**
 * Nhật ký quản trị (ADMIN, đợt 2C): bảng audit log đọc từ endpoint BE đã có
 * GET /admin/audit-logs (AdminAuditLogController -> AdminUserServiceImpl.
 * listAuditLogs). KHÔNG có endpoint BE mới, KHÔNG sửa App.jsx / Sidebar.jsx /
 * api.js — coordinator nối route /admin/audit-logs + menu sau.
 *
 * Chỉ dùng đúng các field AdminAuditLogResponse trả về: id, actorEmail,
 * targetEmail, action, rolesBefore, rolesAfter, createdAt (đối chiếu trực
 * tiếp với dto/response/AdminAuditLogResponse.java + map ở listAuditLogs).
 *
 * Filter + phân trang đều CLIENT-SIDE: tìm kiếm theo actor/target, dropdown
 * action lấy từ chính dữ liệu trả về (không hardcode danh sách action),
 * lọc khoảng ngày bằng 2 ô date from/to.
 */
export function AdminAuditLogPage() {
  const toast = useToast();
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [forbidden, setForbidden] = useState(false);

  // Filter client-side
  const [query, setQuery] = useState('');
  const [actionFilter, setActionFilter] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  // Phân trang client-side (0-based, theo Pagination của components/ui)
  const [page, setPage] = useState(0);

  const load = useCallback(async () => {
    setLoading(true);
    setForbidden(false);
    try {
      const res = await auditLogService.listAuditLogs();
      // api.js unwrap envelope -> res là { success, message, data }; phòng
      // hờ BE trả mảng trần hoặc dạng page { content / items }.
      const d = res?.data ?? res;
      const list = Array.isArray(d) ? d : d?.content ?? d?.items ?? [];
      setLogs(list);
    } catch (err) {
      if (err?.status === 403) setForbidden(true);
      toast.error(friendlyError(err));
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => { load(); }, [load]);

  // Danh sách action lấy từ dữ liệu thật (BE hiện ghi CREATE_USER,
  // UPDATE_ROLES, LOCK_ACCOUNT, UNLOCK_ACCOUNT, BLOCKED_* — nhưng trang này
  // không hardcode để action mới tự xuất hiện trong dropdown).
  const actions = useMemo(() => {
    const set = new Set();
    logs.forEach((l) => { if (l.action) set.add(l.action); });
    return [...set].sort();
  }, [logs]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const fromMs = from ? new Date(`${from}T00:00:00`).getTime() : NaN;
    const toMs = to ? new Date(`${to}T23:59:59.999`).getTime() : NaN;
    return logs.filter((l) => {
      if (q) {
        const hay = `${l.actorEmail || ''} ${l.targetEmail || ''}`.toLowerCase();
        if (!hay.includes(q)) return false;
      }
      if (actionFilter && l.action !== actionFilter) return false;
      if (!Number.isNaN(fromMs) || !Number.isNaN(toMs)) {
        const t = l.createdAt ? new Date(l.createdAt).getTime() : NaN;
        if (Number.isNaN(t)) return false;
        if (!Number.isNaN(fromMs) && t < fromMs) return false;
        if (!Number.isNaN(toMs) && t > toMs) return false;
      }
      return true;
    });
  }, [logs, query, actionFilter, from, to]);

  // Đổi filter -> về trang đầu (tránh kẹt ở trang vượt quá tổng số trang mới).
  useEffect(() => { setPage(0); }, [query, actionFilter, from, to]);

  const totalPages = Math.ceil(filtered.length / PAGE_SIZE);
  const safePage = totalPages === 0 ? 0 : Math.min(page, totalPages - 1);
  const pageRows = filtered.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE);

  const resetFilters = () => {
    setQuery('');
    setActionFilter('');
    setFrom('');
    setTo('');
  };
  const hasActiveFilter = query.trim() !== '' || actionFilter !== '' || from !== '' || to !== '';

  if (loading) {
    return (<><Skeleton height={28} width="40%" style={{ marginBottom: '1rem' }} /><Skeleton height={220} /></>);
  }

  if (forbidden) {
    return (
      <EmptyState
        icon={<ShieldCheck size={40} />}
        title="Không có quyền truy cập"
        desc="Trang nhật ký quản trị chỉ dành cho quản trị viên (ADMIN). Nếu bạn vừa bị gỡ quyền, hãy đăng nhập lại bằng tài khoản quản trị."
      />
    );
  }

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1rem', flexWrap: 'wrap' }}>
        <h2 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <ScrollText size={22} /> Nhật ký quản trị
        </h2>
        <button type="button" className="btn btn-secondary btn-sm" onClick={load} style={{ marginLeft: 'auto' }}>
          <RefreshCw size={15} /> Tải lại
        </button>
      </div>
      <p className="form-hint" style={{ marginBottom: '1.5rem' }}>
        Ghi lại các thao tác phân quyền của quản trị viên: tạo tài khoản, đổi role,
        khóa/mở tài khoản (kể cả các thao tác bị chặn). Lọc và phân trang thực hiện
        ở phía client — backend chỉ có một endpoint trả toàn bộ danh sách.
      </p>

      {/* Bộ lọc client-side */}
      <div className="glass-card" style={{ marginBottom: '1.5rem' }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem' }}>
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label" htmlFor="al-query">Tìm kiếm</label>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Search size={16} style={{ flexShrink: 0, color: 'var(--text-muted)' }} />
              <input
                id="al-query"
                className="form-control"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Người thực hiện hoặc đối tượng…"
              />
            </div>
          </div>
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label" htmlFor="al-action">Hành động</label>
            <select id="al-action" className="form-control" value={actionFilter} onChange={(e) => setActionFilter(e.target.value)}>
              <option value="">Tất cả hành động</option>
              {actions.map((a) => (<option key={a} value={a}>{a}</option>))}
            </select>
          </div>
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label" htmlFor="al-from">Từ ngày</label>
            <input id="al-from" type="date" className="form-control" value={from} onChange={(e) => setFrom(e.target.value)} />
          </div>
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label" htmlFor="al-to">Đến ngày</label>
            <input id="al-to" type="date" className="form-control" value={to} onChange={(e) => setTo(e.target.value)} />
          </div>
        </div>
        {hasActiveFilter && (
          <div style={{ marginTop: '1rem' }}>
            <button type="button" className="btn btn-secondary btn-sm" onClick={resetFilters}>Xóa bộ lọc</button>
          </div>
        )}
      </div>

      {logs.length === 0 ? (
        <EmptyState
          icon={<ScrollText size={40} />}
          title="Chưa có nhật ký quản trị"
          desc="Chưa có thao tác phân quyền nào được ghi lại."
        />
      ) : filtered.length === 0 ? (
        <EmptyState
          icon={<Search size={40} />}
          title="Không tìm thấy bản ghi phù hợp"
          desc="Thử nới rộng từ khóa, hành động hoặc khoảng ngày."
          action={<button type="button" className="btn btn-secondary btn-sm" onClick={resetFilters}>Xóa bộ lọc</button>}
        />
      ) : (
        <>
          <p className="form-hint" style={{ marginBottom: '1rem' }}>
            Hiển thị {safePage * PAGE_SIZE + 1}–{safePage * PAGE_SIZE + pageRows.length} / {filtered.length} bản ghi
            {hasActiveFilter ? ` (đã lọc từ ${logs.length} bản ghi)` : ` (tổng ${logs.length} bản ghi)`}.
          </p>
          <div className="glass-card">
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Thời gian</th>
                    <th>Người thực hiện</th>
                    <th>Đối tượng</th>
                    <th>Hành động</th>
                    <th>Role trước</th>
                    <th>Role sau</th>
                  </tr>
                </thead>
                <tbody>
                  {pageRows.map((l, i) => (
                    <tr key={l.id || `${l.actorEmail || ''}-${l.createdAt || ''}-${i}`}>
                      <td style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', whiteSpace: 'nowrap' }}>{formatDateTime(l.createdAt)}</td>
                      <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.85rem' }}>{l.actorEmail || '—'}</td>
                      <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.85rem' }}>{l.targetEmail || '—'}</td>
                      <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.85rem' }}>{l.action || '—'}</td>
                      <td><RoleBadges roles={l.rolesBefore} /></td>
                      <td><RoleBadges roles={l.rolesAfter} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          <Pagination page={safePage} totalPages={totalPages} onChange={setPage} />
        </>
      )}
    </>
  );
}
