import React, { useState, useEffect, useCallback } from 'react';
import { Users, Plus, Lock, Unlock, RefreshCw, ShieldCheck } from 'lucide-react';
import { useToast } from '../context/ToastContext';
import { adminUserService } from '../services/api';
import { EmptyState, Skeleton } from '../components/ui';
import { formatDateTime } from '../utils/format';
import { normalizeRole } from '../context/AuthContext';

const ALL_ROLES = ['ROLE_CUSTOMER', 'ROLE_AGENT', 'ROLE_UNDERWRITER', 'ROLE_FINANCE', 'ROLE_ADMIN'];
const shortRole = (r) => normalizeRole(r).replace(/^ROLE_/, '');

function RoleBadge({ role }) {
  return <span className="badge badge-info" style={{ marginRight: '0.3rem' }}>{shortRole(role)}</span>;
}

function friendlyError(err) {
  if (err?.status === 403) return 'Bạn không có quyền quản trị người dùng. Chỉ tài khoản ADMIN mới truy cập được trang này.';
  return err?.message || 'Đã xảy ra lỗi không mong đợi.';
}

/**
 * Quản lý người dùng & phân quyền (ADMIN, PLAN §1.2): list user, tạo tài
 * khoản, gán/gỡ role, khóa/mở tài khoản + bảng audit log. FE chỉ là UX —
 * BE là chốt chặn (mọi thao tác sai quyền đều trả 403).
 */
export function AdminUserPage() {
  const toast = useToast();
  const [users, setUsers] = useState([]);
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [forbidden, setForbidden] = useState(false);

  // Form tạo user
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [newRoles, setNewRoles] = useState(['ROLE_CUSTOMER']);
  const [creating, setCreating] = useState(false);

  // Role editor theo từng user: { [userId]: string[] }
  const [roleDrafts, setRoleDrafts] = useState({});
  const [saving, setSaving] = useState({});

  const load = useCallback(async () => {
    setLoading(true);
    setForbidden(false);
    try {
      const [uRes, lRes] = await Promise.allSettled([
        adminUserService.listUsers(),
        adminUserService.listAuditLogs(),
      ]);
      if (uRes.status === 'fulfilled') {
        const d = uRes.value?.data;
        const list = Array.isArray(d) ? d : d?.content ?? d?.items ?? [];
        setUsers(list);
        setRoleDrafts(Object.fromEntries(list.map((u) => [u.id, [...(u.roles || [])]])));
      } else {
        if (uRes.reason?.status === 403) setForbidden(true);
        toast.error(friendlyError(uRes.reason));
      }
      if (lRes.status === 'fulfilled') {
        const d = lRes.value?.data;
        setLogs(Array.isArray(d) ? d : d?.content ?? d?.items ?? []);
      }
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => { load(); }, [load]);

  const toggleNewRole = (role) => {
    setNewRoles((prev) => (prev.includes(role) ? prev.filter((r) => r !== role) : [...prev, role]));
  };

  const createUser = async (e) => {
    e.preventDefault();
    if (!email.trim() || !password || !fullName.trim() || newRoles.length === 0) {
      toast.error('Vui lòng nhập đủ email, mật khẩu, họ tên và chọn ít nhất 1 vai trò.');
      return;
    }
    setCreating(true);
    try {
      await adminUserService.createUser({
        email: email.trim(), password, fullName: fullName.trim(), roles: newRoles,
      });
      toast.success(`Đã tạo tài khoản ${email.trim()}.`);
      setEmail(''); setPassword(''); setFullName(''); setNewRoles(['ROLE_CUSTOMER']);
      await load();
    } catch (err) {
      toast.error(friendlyError(err));
    } finally {
      setCreating(false);
    }
  };

  const saveRoles = async (u) => {
    const roles = roleDrafts[u.id] || [];
    if (roles.length === 0) {
      toast.error('Mỗi tài khoản cần ít nhất 1 vai trò.');
      return;
    }
    setSaving((s) => ({ ...s, [u.id]: true }));
    try {
      await adminUserService.updateRoles(u.id, roles);
      toast.success(`Đã cập nhật quyền cho ${u.email}.`);
      await load();
    } catch (err) {
      toast.error(friendlyError(err));
    } finally {
      setSaving((s) => ({ ...s, [u.id]: false }));
    }
  };

  const toggleEnabled = async (u) => {
    const next = !u.enabled;
    if (!window.confirm(`${next ? 'Mở khóa' : 'Khóa'} tài khoản ${u.email}?${next ? '' : ' Tài khoản bị khóa sẽ không đăng nhập được và token cũ bị từ chối ngay.'}`)) return;
    setSaving((s) => ({ ...s, [u.id]: true }));
    try {
      await adminUserService.setEnabled(u.id, next);
      toast.success(`Đã ${next ? 'mở khóa' : 'khóa'} tài khoản ${u.email}.`);
      await load();
    } catch (err) {
      toast.error(friendlyError(err));
    } finally {
      setSaving((s) => ({ ...s, [u.id]: false }));
    }
  };

  if (loading) {
    return (<><Skeleton height={28} width="40%" style={{ marginBottom: '1rem' }} /><Skeleton height={220} /></>);
  }

  if (forbidden) {
    return (
      <EmptyState
        icon={<ShieldCheck size={40} />}
        title="Không có quyền truy cập"
        desc="Trang quản lý người dùng chỉ dành cho quản trị viên (ADMIN). Nếu bạn vừa bị gỡ quyền, hãy đăng nhập lại bằng tài khoản quản trị."
      />
    );
  }

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1.5rem', flexWrap: 'wrap' }}>
        <h2 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <Users size={22} /> Quản lý người dùng
        </h2>
        <button type="button" className="btn btn-secondary btn-sm" onClick={load} style={{ marginLeft: 'auto' }}>
          <RefreshCw size={15} /> Tải lại
        </button>
      </div>

      {/* Tạo tài khoản */}
      <form className="glass-card" onSubmit={createUser} style={{ marginBottom: '1.5rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Tạo tài khoản mới</h3>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1rem' }}>
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label" htmlFor="au-email">Email *</label>
            <input id="au-email" type="email" className="form-control" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="nhanvien@example.com" />
          </div>
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label" htmlFor="au-pass">Mật khẩu *</label>
            <input id="au-pass" type="password" className="form-control" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Mật khẩu ban đầu" />
          </div>
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label" htmlFor="au-name">Họ tên *</label>
            <input id="au-name" className="form-control" value={fullName} onChange={(e) => setFullName(e.target.value)} placeholder="Nguyễn Văn A" />
          </div>
        </div>
        <div style={{ marginTop: '1rem' }}>
          <div className="form-label">Vai trò *</div>
          <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
            {ALL_ROLES.map((r) => (
              <label key={r} style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', fontSize: '0.9rem', cursor: 'pointer' }}>
                <input type="checkbox" checked={newRoles.includes(r)} onChange={() => toggleNewRole(r)} />
                {shortRole(r)}
              </label>
            ))}
          </div>
        </div>
        <button type="submit" className="btn btn-primary" disabled={creating} style={{ marginTop: '1rem' }}>
          {creating ? <span className="spinner" /> : <Plus size={16} />} Tạo tài khoản
        </button>
      </form>

      {/* Danh sách user */}
      <div className="glass-card" style={{ marginBottom: '1.5rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Danh sách tài khoản ({users.length})</h3>
        {users.length === 0 ? (
          <p style={{ color: 'var(--text-muted)' }}>Chưa có tài khoản nào.</p>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Email</th>
                  <th>Họ tên</th>
                  <th>Vai trò</th>
                  <th>Trạng thái</th>
                  <th>Ngày tạo</th>
                  <th style={{ width: 190 }}>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u) => {
                  const draft = roleDrafts[u.id] ?? u.roles ?? [];
                  const dirty = JSON.stringify([...draft].sort()) !== JSON.stringify([...(u.roles || [])].sort());
                  return (
                    <tr key={u.id}>
                      <td><span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.85rem' }}>{u.email}</span></td>
                      <td>{u.fullName || '—'}</td>
                      <td>
                        <div style={{ display: 'flex', gap: '0.4rem', flexWrap: 'wrap', marginBottom: '0.4rem' }}>
                          {(u.roles || []).map((r) => <RoleBadge key={r} role={r} />)}
                        </div>
                        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
                          {ALL_ROLES.map((r) => (
                            <label key={r} style={{ display: 'flex', alignItems: 'center', gap: '0.25rem', fontSize: '0.8rem', cursor: 'pointer' }}>
                              <input
                                type="checkbox"
                                checked={draft.includes(r)}
                                onChange={() => setRoleDrafts((prev) => ({
                                  ...prev,
                                  [u.id]: draft.includes(r) ? draft.filter((x) => x !== r) : [...draft, r],
                                }))}
                              />
                              {shortRole(r)}
                            </label>
                          ))}
                        </div>
                        {dirty && (
                          <button type="button" className="btn btn-primary btn-sm" style={{ marginTop: '0.4rem' }} disabled={!!saving[u.id]} onClick={() => saveRoles(u)}>
                            Lưu quyền
                          </button>
                        )}
                      </td>
                      <td>
                        <span className={`badge ${u.enabled === false ? 'badge-cancelled' : 'badge-active'}`}>
                          {u.enabled === false ? 'Đã khóa' : 'Đang hoạt động'}
                        </span>
                      </td>
                      <td style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{formatDateTime(u.createdAt)}</td>
                      <td>
                        <button
                          type="button"
                          className={`btn btn-sm ${u.enabled === false ? 'btn-primary' : 'btn-danger'}`}
                          disabled={!!saving[u.id]}
                          onClick={() => toggleEnabled(u)}
                        >
                          {u.enabled === false ? <><Unlock size={14} /> Mở khóa</> : <><Lock size={14} /> Khóa</>}
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Audit log */}
      <div className="glass-card">
        <h3 style={{ marginBottom: '1rem' }}>Nhật ký phân quyền</h3>
        {logs.length === 0 ? (
          <p style={{ color: 'var(--text-muted)' }}>Chưa có thao tác phân quyền nào được ghi lại.</p>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Thời điểm</th>
                  <th>Người thực hiện</th>
                  <th>Đối tượng</th>
                  <th>Hành động</th>
                  <th>Trước</th>
                  <th>Sau</th>
                </tr>
              </thead>
              <tbody>
                {logs.map((l) => (
                  <tr key={l.id}>
                    <td style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{formatDateTime(l.createdAt)}</td>
                    <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.85rem' }}>{l.actorEmail}</td>
                    <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.85rem' }}>{l.targetEmail}</td>
                    <td>{l.action}</td>
                    <td>{(l.rolesBefore || []).map((r) => <RoleBadge key={r} role={r} />)}</td>
                    <td>{(l.rolesAfter || []).map((r) => <RoleBadge key={r} role={r} />)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  );
}
