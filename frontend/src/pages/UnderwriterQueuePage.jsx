import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { Scale, RefreshCw, CheckCircle2 } from 'lucide-react';
import { useToast } from '../context/ToastContext';
import { underwriterQueueService } from '../services/underwriterQueueApi';
import { EmptyState, Skeleton } from '../components/ui';
import { formatMoney, formatDateTime } from '../utils/format';

const ROLE_LABEL = {
  UNDERWRITER: 'Thẩm định viên',
  CUSTOMER: 'Khách hàng',
  AGENT: 'Đại lý',
};

/**
 * Hàng đợi thương lượng cho UNDERWRITER (đợt 2A).
 * GET /underwriter/negotiations — BE chỉ trả negotiation OPEN đang tới lượt UW
 * (offer cuối không phải của UW, hoặc chưa có offer), sắp xếp cũ nhất trước.
 * Trang này CHƯA được nối route/menu (coordinator nối sau) — không sửa
 * App.jsx / Sidebar.jsx trong đợt này.
 */
export function UnderwriterQueuePage() {
  const toast = useToast();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await underwriterQueueService.listPending();
      // api.js unwrap envelope thành data; giữ fallback nếu BE trả envelope thô.
      const d = res?.data ?? res;
      const list = Array.isArray(d) ? d : (d?.items ?? d?.negotiations ?? []);
      setItems(list);
    } catch (err) {
      toast.error(err.message || 'Không tải được hàng đợi thương lượng.');
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => { load(); }, [load]);

  if (loading) {
    return (<><Skeleton height={28} width="40%" style={{ marginBottom: '1rem' }} /><Skeleton height={220} /></>);
  }

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1rem', flexWrap: 'wrap' }}>
        <h2 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <Scale size={22} /> Hàng đợi thương lượng
        </h2>
        <button type="button" className="btn btn-secondary btn-sm" onClick={load} style={{ marginLeft: 'auto' }}>
          <RefreshCw size={15} /> Tải lại
        </button>
      </div>
      <p className="form-hint" style={{ marginBottom: '1.5rem' }}>
        Chỉ các thương lượng đang mở (OPEN) và đang tới lượt thẩm định viên mới xuất hiện tại đây —
        tức offer mới nhất do khách hàng hoặc đại lý đề xuất. Thương lượng đang chờ phe cầu trả lời
        không nằm trong hàng đợi này.
      </p>

      {items.length === 0 ? (
        <EmptyState
          icon={<CheckCircle2 size={40} />}
          title="Không còn thương lượng chờ thẩm định"
          desc="Hàng đợi trống — chưa có thương lượng nào đang tới lượt bạn."
        />
      ) : (
        <div className="glass-card">
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Số hợp đồng</th>
                  <th>Ngày mở</th>
                  <th>Giá niêm yết</th>
                  <th>Offer hiện tại</th>
                  <th>Bên đề xuất</th>
                  <th style={{ width: 180 }}>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {items.map((n) => {
                  const offer = n.currentOffer;
                  return (
                    <tr key={n.id || n.policyNumber}>
                      <td>
                        <Link className="policy-number" to={`/hop-dong/${encodeURIComponent(n.policyNumber)}`}>
                          {n.policyNumber}
                        </Link>
                      </td>
                      <td style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{formatDateTime(n.openedAt)}</td>
                      <td><strong>{formatMoney(n.listPremium)}</strong></td>
                      <td>
                        {offer?.proposedPremium != null
                          ? <strong style={{ color: '#1663d9' }}>{formatMoney(offer.proposedPremium)}</strong>
                          : '—'}
                        {offer?.message ? (
                          <><br /><span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{offer.message}</span></>
                        ) : null}
                      </td>
                      <td>
                        {offer ? (ROLE_LABEL[offer.proposedByRole] || offer.proposedByRole || '—') : 'Chưa có offer'}
                        {offer?.proposedByEmail ? (
                          <><br /><span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem', color: 'var(--text-secondary)' }}>{offer.proposedByEmail}</span></>
                        ) : null}
                      </td>
                      <td>
                        <Link className="btn btn-primary btn-sm" to={`/hop-dong/${encodeURIComponent(n.policyNumber)}`}>
                          Mở chi tiết hợp đồng
                        </Link>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </>
  );
}
