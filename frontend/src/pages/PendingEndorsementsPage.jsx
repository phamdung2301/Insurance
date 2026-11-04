import React, { useState, useEffect, useCallback } from 'react';
import { ShieldCheck, RefreshCw, CheckCircle2, XCircle } from 'lucide-react';
import { useToast } from '../context/ToastContext';
import { useAuth, hasRoleIn } from '../context/AuthContext';
import { pendingEndorsementService } from '../services/pendingEndorsementApi';
import { EmptyState, Skeleton } from '../components/ui';
import { formatDateTime } from '../utils/format';

const STATUS_LABEL = {
  PENDING: 'Chờ duyệt',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Đã từ chối',
};

/**
 * Danh sách endorsement chờ duyệt — maker-checker (đợt 4B).
 * GET /pending-endorsements — BE đã scope theo role: UW + ADMIN thấy tất cả
 * PENDING, CUSTOMER/AGENT chỉ thấy của policy trong phạm vi mình, FINANCE 403.
 * Chỉ UW thấy nút Duyệt/Từ chối (customer/agent chỉ thấy trạng thái); quyền
 * thật vẫn do BE enforce — nút chỉ là ẩn/hiện giao diện.
 * Trang này CHƯA được nối route/menu (coordinator nối sau) — không sửa
 * App.jsx / Sidebar.jsx trong đợt này.
 */
export function PendingEndorsementsPage() {
  const toast = useToast();
  const { user } = useAuth();
  const isUnderwriter = hasRoleIn(user?.roles, 'ROLE_UNDERWRITER');
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [acting, setActing] = useState({});

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await pendingEndorsementService.listPending();
      // api.js unwrap envelope thành data; giữ fallback nếu BE trả envelope thô.
      const d = res?.data ?? res;
      const list = Array.isArray(d) ? d : (d?.items ?? []);
      setItems(list);
    } catch (err) {
      toast.error(err.message || 'Không tải được danh sách chờ duyệt.');
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => { load(); }, [load]);

  const act = async (item, action) => {
    const verb = action === 'approve' ? 'duyệt' : 'từ chối';
    if (!window.confirm(`Bạn có chắc muốn ${verb} endorsement cho hợp đồng ${item.policyNumber}?${action === 'approve' ? ' Hợp đồng sẽ được áp dụng ngay.' : ' Hợp đồng sẽ không thay đổi.'}`)) return;
    setActing((s) => ({ ...s, [item.id]: true }));
    try {
      await pendingEndorsementService[action](item.id);
      toast.success(action === 'approve'
        ? `Đã duyệt endorsement cho ${item.policyNumber}. Hợp đồng đã được áp dụng.`
        : `Đã từ chối endorsement cho ${item.policyNumber}. Hợp đồng không thay đổi.`);
      await load();
    } catch (err) {
      toast.error(err.message || `Không ${verb} được endorsement.`);
    } finally {
      setActing((s) => ({ ...s, [item.id]: false }));
    }
  };

  if (loading) {
    return (<><Skeleton height={28} width="40%" style={{ marginBottom: '1rem' }} /><Skeleton height={220} /></>);
  }

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1rem', flexWrap: 'wrap' }}>
        <h2 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <ShieldCheck size={22} /> Endorsement chờ duyệt
        </h2>
        <button type="button" className="btn btn-secondary btn-sm" onClick={load} style={{ marginLeft: 'auto' }}>
          <RefreshCw size={15} /> Tải lại
        </button>
      </div>
      <p className="form-hint" style={{ marginBottom: '1.5rem' }}>
        Endorsement làm phí thay đổi quá 20% không được áp dụng ngay mà phải chờ thẩm định viên (UNDERWRITER) duyệt.
        Chỉ UNDERWRITER mới duyệt/từ chối — quản trị viên chỉ xem, FINANCE không thấy danh sách này.
      </p>
      {items.length === 0 ? (
        <EmptyState
          icon={<CheckCircle2 size={40} />}
          title="Không có endorsement chờ duyệt"
          desc="Mọi endorsement đều đã được xử lý, hoặc bạn không có endorsement nào trong phạm vi của mình."
        />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {items.map((item) => {
            const pct = item.premiumChangePct != null ? (item.premiumChangePct * 100) : null;
            const busy = !!acting[item.id];
            return (
              <div key={item.id} className="card" style={{ padding: '1rem 1.25rem' }}>
                <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', alignItems: 'center' }}>
                  <div style={{ flex: '1 1 220px' }}>
                    <div style={{ fontWeight: 700 }}>{item.policyNumber || '—'}</div>
                    <div className="form-hint">Người yêu cầu: {item.requestedBy || '—'}</div>
                    <div className="form-hint">Ngày yêu cầu: {formatDateTime(item.requestedAt)}</div>
                    {item.request?.reason ? <div className="form-hint">Lý do: {item.request.reason}</div> : null}
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontWeight: 700, fontSize: '1.1rem' }}>
                      {pct != null ? `${pct.toFixed(1)}%` : '—'}
                    </div>
                    <div className="form-hint">đổi phí</div>
                    <div style={{ marginTop: '0.25rem' }}>{STATUS_LABEL[item.status] || item.status || '—'}</div>
                  </div>
                  {isUnderwriter && item.status === 'PENDING' ? (
                    <div style={{ display: 'flex', gap: '0.5rem' }}>
                      <button type="button" className="btn btn-primary btn-sm" disabled={busy} onClick={() => act(item, 'approve')}>
                        <CheckCircle2 size={15} /> Duyệt
                      </button>
                      <button type="button" className="btn btn-secondary btn-sm" disabled={busy} onClick={() => act(item, 'reject')}>
                        <XCircle size={15} /> Từ chối
                      </button>
                    </div>
                  ) : null}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </>
  );
}

export default PendingEndorsementsPage;
