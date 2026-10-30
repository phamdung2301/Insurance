import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { Users, Plus, RefreshCw } from 'lucide-react';
import { useToast } from '../context/ToastContext';
import { policyService } from '../services/api';
import { StatusBadge } from '../components/Status';
import { EmptyState, Skeleton } from '../components/ui';
import { formatMoney } from '../utils/format';

/**
 * Khách hàng của tôi (AGENT, PLAN §1.2): list policy có assignedAgentEmail =
 * mình qua GET /policies đã scope ở BE. KHÔNG dùng /policies/mine — /mine ép
 * insuredEmail = principal nên agent gọi sẽ ra rỗng.
 * Nút tạo policy hộ khách dẫn tới wizard mua: wizard thu thập insured.email
 * của khách, BE tự set assignedAgentEmail = agent (FE không tự gán).
 */
export function AgentCustomerPage() {
  const toast = useToast();
  const [policies, setPolicies] = useState([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await policyService.searchPolicies({ page: 0, size: 100 });
      const d = res?.data;
      const list = d?.content ?? d?.items ?? (Array.isArray(d) ? d : []);
      setPolicies(list);
    } catch (err) {
      toast.error(err.message || 'Không tải được danh sách khách hàng.');
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
          <Users size={22} /> Khách hàng của tôi
        </h2>
        <div style={{ marginLeft: 'auto', display: 'flex', gap: '0.5rem' }}>
          <button type="button" className="btn btn-secondary btn-sm" onClick={load}>
            <RefreshCw size={15} /> Tải lại
          </button>
          <Link className="btn btn-primary btn-sm" to="/mua-bao-hiem">
            <Plus size={15} /> Tạo hợp đồng hộ khách
          </Link>
        </div>
      </div>
      <p className="form-hint" style={{ marginBottom: '1.5rem' }}>
        Chỉ hiển thị các hợp đồng được giao cho bạn. Khi tạo hộ khách, nhập email của khách vào
        wizard mua bảo hiểm — hệ thống sẽ tự gắn bạn làm đại lý phụ trách hợp đồng đó.
      </p>

      {policies.length === 0 ? (
        <EmptyState
          icon={<Users size={40} />}
          title="Chưa có khách hàng nào được giao"
          desc="Khi có hợp đồng được giao cho bạn, danh sách sẽ hiển thị tại đây."
          action={<Link className="btn btn-primary" to="/mua-bao-hiem"><Plus size={16} /> Tạo hợp đồng hộ khách</Link>}
        />
      ) : (
        <div className="glass-card">
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Số hợp đồng</th>
                  <th>Khách hàng</th>
                  <th>Email khách</th>
                  <th>Trạng thái</th>
                  <th>Tổng phí</th>
                  <th>Đại lý phụ trách</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {policies.map((p) => (
                  <tr key={p.policyNumber}>
                    <td><Link className="policy-number" to={`/hop-dong/${encodeURIComponent(p.policyNumber)}`}>{p.policyNumber}</Link></td>
                    <td>{p.insured?.name || '—'}</td>
                    <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.85rem' }}>{p.insured?.email || '—'}</td>
                    <td><StatusBadge status={p.status} /></td>
                    <td><strong style={{ color: '#1663d9' }}>{formatMoney(p.totalPremium)}</strong></td>
                    <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.85rem' }}>{p.assignedAgentEmail || '—'}</td>
                    <td>
                      <Link className="btn btn-secondary btn-sm" to={`/hop-dong/${encodeURIComponent(p.policyNumber)}`}>Xem</Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </>
  );
}
