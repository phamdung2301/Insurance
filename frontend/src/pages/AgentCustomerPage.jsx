import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { Users, Plus, RefreshCw, Search } from 'lucide-react';
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
 *
 * Đợt 3A — thêm view gom nhóm theo khách hàng (KHÔNG cần endpoint BE mới,
 * gom từ chính dữ liệu searchPolicies đã scope sẵn):
 * - Gom theo insured.email (lowercase, trim). Policy thiếu email gom theo
 *   tên khách (`__no_email__:<tên>`) để không lẫn các khách không có email.
 * - Mỗi khách: tên (của hợp đồng mới nhất, fallback tên khác trong nhóm),
 *   email, số hợp đồng, tổng phí (cộng totalPremium, null = 0),
 *   trạng thái mới nhất = status của hợp đồng mới nhất.
 * - "Mới nhất" = max theo updatedAt, fallback createdAt; bằng nhau hoặc
 *   thiếu cả hai thì hợp đồng đứng sau trong danh sách trả về thắng.
 * - Khách sắp xếp theo thời gian hợp đồng mới nhất giảm dần, rồi theo email.
 * - Ô tìm kiếm lọc theo tên HOẶC email khách (không phân biệt hoa/thường,
 *   khớp chuỗi con). View hợp đồng cũ vẫn giữ nguyên qua toggle bên dưới.
 */
export function groupPoliciesByCustomer(policies) {
  const byKey = new Map();
  (policies || []).forEach((p, index) => {
    const email = (p.insured?.email || '').trim();
    const name = (p.insured?.name || '').trim();
    const key = email ? email.toLowerCase() : `__no_email__:${name || `khach-${index}`}`;
    const time = Date.parse(p.updatedAt || p.createdAt || '') || 0;
    let group = byKey.get(key);
    if (!group) {
      group = {
        key, email: email || null, name: name || null,
        policyCount: 0, totalPremium: 0,
        latestStatus: null, latestPolicyNumber: null, latestTime: -1,
        latestPolicy: null,
      };
      byKey.set(key, group);
    }
    group.policyCount += 1;
    group.totalPremium += Number(p.totalPremium) || 0;
    // >= để hợp đồng đứng sau thắng khi bằng thời gian / thiếu thời gian.
    if (time >= group.latestTime) {
      group.latestTime = time;
      group.latestStatus = p.status;
      group.latestPolicyNumber = p.policyNumber;
      group.latestPolicy = p;
    }
    if (!group.name && name) group.name = name;
  });
  return [...byKey.values()].sort((a, b) =>
    (b.latestTime - a.latestTime) || String(a.email || a.name || '').localeCompare(String(b.email || b.name || '')));
}

export function AgentCustomerPage() {
  const toast = useToast();
  const [policies, setPolicies] = useState([]);
  const [loading, setLoading] = useState(true);
  const [view, setView] = useState('customers'); // 'customers' | 'policies'
  const [query, setQuery] = useState('');

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

  const customers = useMemo(() => groupPoliciesByCustomer(policies), [policies]);

  const q = query.trim().toLowerCase();
  const filteredCustomers = useMemo(() => {
    if (!q) return customers;
    return customers.filter((c) =>
      (c.name || '').toLowerCase().includes(q) || (c.email || '').toLowerCase().includes(q));
  }, [customers, q]);
  const filteredPolicies = useMemo(() => {
    if (!q) return policies;
    return policies.filter((p) =>
      (p.insured?.name || '').toLowerCase().includes(q) || (p.insured?.email || '').toLowerCase().includes(q));
  }, [policies, q]);

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
      <p className="form-hint" style={{ marginBottom: '1rem' }}>
        Chỉ hiển thị các hợp đồng được giao cho bạn. Khi tạo hộ khách, nhập email của khách vào
        wizard mua bảo hiểm — hệ thống sẽ tự gắn bạn làm đại lý phụ trách hợp đồng đó.
      </p>

      <div style={{ display: 'flex', gap: '0.75rem', marginBottom: '1.5rem', flexWrap: 'wrap', alignItems: 'center' }}>
        <div style={{ display: 'flex', gap: '0.25rem' }} role="tablist" aria-label="Chế độ xem">
          <button
            type="button"
            className={`btn btn-sm ${view === 'customers' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setView('customers')}
          >
            Theo khách hàng ({customers.length})
          </button>
          <button
            type="button"
            className={`btn btn-sm ${view === 'policies' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setView('policies')}
          >
            Theo hợp đồng ({policies.length})
          </button>
        </div>
        <div style={{ position: 'relative', flex: '1 1 240px', maxWidth: '360px' }}>
          <Search size={15} style={{ position: 'absolute', left: '0.6rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
          <input
            className="form-control"
            style={{ paddingLeft: '2rem' }}
            placeholder="Tìm theo tên hoặc email khách…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="Tìm khách hàng theo tên hoặc email"
          />
        </div>
      </div>

      {policies.length === 0 ? (
        <EmptyState
          icon={<Users size={40} />}
          title="Chưa có khách hàng nào được giao"
          desc="Khi có hợp đồng được giao cho bạn, danh sách sẽ hiển thị tại đây."
          action={<Link className="btn btn-primary" to="/mua-bao-hiem"><Plus size={16} /> Tạo hợp đồng hộ khách</Link>}
        />
      ) : view === 'customers' ? (
        filteredCustomers.length === 0 ? (
          <EmptyState
            icon={<Search size={40} />}
            title="Không tìm thấy khách hàng"
            desc={`Không có khách hàng nào khớp với “${query.trim()}”. Thử tên hoặc email khác.`}
          />
        ) : (
          <div className="glass-card">
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Khách hàng</th>
                    <th>Email khách</th>
                    <th>Số hợp đồng</th>
                    <th>Tổng phí</th>
                    <th>Trạng thái mới nhất</th>
                    <th>Hợp đồng mới nhất</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {filteredCustomers.map((c) => (
                    <tr key={c.key}>
                      <td>{c.name || c.email || '—'}</td>
                      <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.85rem' }}>{c.email || '—'}</td>
                      <td>{c.policyCount}</td>
                      <td><strong style={{ color: '#1663d9' }}>{formatMoney(c.totalPremium)}</strong></td>
                      <td><StatusBadge status={c.latestStatus} /></td>
                      <td>
                        {c.latestPolicyNumber ? (
                          <Link className="policy-number" to={`/hop-dong/${encodeURIComponent(c.latestPolicyNumber)}`}>{c.latestPolicyNumber}</Link>
                        ) : '—'}
                      </td>
                      <td>
                        {c.latestPolicyNumber && (
                          <Link className="btn btn-secondary btn-sm" to={`/hop-dong/${encodeURIComponent(c.latestPolicyNumber)}`}>Xem</Link>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )
      ) : (
        filteredPolicies.length === 0 ? (
          <EmptyState
            icon={<Search size={40} />}
            title="Không tìm thấy hợp đồng"
            desc={`Không có hợp đồng nào khớp với “${query.trim()}”. Thử tên hoặc email khác.`}
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
                  {filteredPolicies.map((p) => (
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
        )
      )}
    </>
  );
}
