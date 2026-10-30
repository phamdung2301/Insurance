import React from 'react';
import { ShieldCheck, ShieldAlert } from 'lucide-react';
import { MATRIX_ROLES, PERMISSION_MATRIX, MATRIX_DIFFERENCES } from '../data/permissionMatrix';

// Ma trận phân quyền READ-ONLY cho ADMIN (đợt 4C).
// Trang này CHỈ RENDER dữ liệu tĩnh từ ../data/permissionMatrix.js: không gọi
// API, không có nút sửa/lưu, không có form nào. Coordinator nối route
// /admin/phan-quyen cho ROLE_ADMIN sau — KHÔNG sửa App.jsx / Sidebar.jsx /
// api.js trong đợt này.
//
// Cách render mỗi ô: 'allowed' -> ✅, 'denied' -> ❌, chuỗi ghi chú -> hiển
// thị nguyên văn (ghi chú luôn mang ✅/❌ ở đầu để nhìn là biết phe nào).
function renderCell(value) {
  if (value === 'allowed') return <span title="Được phép">✅</span>;
  if (value === 'denied') return <span title="Không được phép">❌</span>;
  return <span>{value}</span>;
}

export function PermissionMatrixPage() {
  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1rem', flexWrap: 'wrap' }}>
        <h2 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <ShieldCheck size={22} /> Ma trận phân quyền
        </h2>
      </div>

      {/* Cảnh báo read-only — phải đọc được ngay ở đầu trang, không thể nhầm. */}
      <div
        className="glass-card"
        role="alert"
        style={{
          marginBottom: '1.5rem',
          borderLeft: '4px solid var(--danger, #dc2626)',
          padding: '1rem 1.25rem',
        }}
      >
        <p style={{ margin: 0, display: 'flex', alignItems: 'flex-start', gap: '0.5rem', fontWeight: 600 }}>
          <ShieldAlert size={20} style={{ flexShrink: 0, marginTop: '0.1rem' }} />
          <span>
            Trang này CHỈ ĐỂ XEM. Quyền thực thi vẫn nằm trong code backend
            (SecurityConfig + PermissionService) — sửa bảng ở đây KHÔNG đổi quyền thật.
            Muốn đổi quyền thật cần sửa code + deploy (editor phân quyền là việc tương lai,
            cần policy engine riêng).
          </span>
        </p>
        <p className="form-hint" style={{ margin: '0.75rem 0 0 1.75rem' }}>
          Bảng dưới mirror bảng §1.6 trong báo cáo Phân tích Use Case + Filter Chain RBAC,
          đã đối chiếu lại với code hiện tại sau đợt 1–3. Những điểm bảng cũ không còn đúng
          được sửa theo code và liệt kê đầy đủ ở mục “Khác biệt so với bảng §1.6 cũ” cuối trang.
        </p>
      </div>

      <div className="glass-card">
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th style={{ minWidth: '220px' }}>Quyền</th>
                {MATRIX_ROLES.map((r) => (
                  <th key={r.key} style={{ textAlign: 'center', whiteSpace: 'nowrap' }}>{r.label}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {PERMISSION_MATRIX.map((row) => (
                <tr key={row.id}>
                  <td>
                    <div style={{ fontWeight: 600 }}>{row.name}</div>
                    <div className="form-hint" style={{ marginTop: '0.25rem' }}>{row.description}</div>
                    <div style={{ marginTop: '0.25rem', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                      Nguồn: {row.source}
                    </div>
                  </td>
                  {MATRIX_ROLES.map((r) => (
                    <td key={r.key} style={{ textAlign: 'center', verticalAlign: 'top', fontSize: '0.9rem' }}>
                      {renderCell(row.roles[r.key])}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <p className="form-hint" style={{ margin: '1rem 0' }}>
        Chú thích: ✅ = được phép, ❌ = không được phép. Ô ghi chú là trường hợp đặc biệt
        (có phạm vi hoặc bị cấm có chủ đích) — ký hiệu ✅/❌ ở đầu ghi chú cho biết phe đó.
        “Trong phạm vi” với CUSTOMER nghĩa là hợp đồng của chính mình, với AGENT nghĩa là
        hợp đồng được giao cho mình; UNDERWRITER / FINANCE / ADMIN đọc mọi hợp đồng.
      </p>

      <h3 style={{ marginTop: '2rem', marginBottom: '1rem' }}>Khác biệt so với bảng §1.6 cũ (mirror theo code hiện tại)</h3>
      <div className="glass-card" style={{ padding: '1.25rem' }}>
        {MATRIX_DIFFERENCES.map((d) => (
          <div key={d.id} style={{ marginBottom: '1.25rem' }}>
            <div style={{ fontWeight: 600 }}>{d.title}</div>
            <p className="form-hint" style={{ margin: '0.25rem 0 0 0' }}>{d.detail}</p>
          </div>
        ))}
        <p className="form-hint" style={{ margin: 0 }}>
          Ngoài 5 điểm trên, nhóm “Bổ sung sau §1.6” trong bảng chính là các quyền mới có
          từ đợt 2–3 (hàng đợi thương lượng, hoa hồng agent, đối soát FINANCE, ghi chú nội bộ)
          và 2 quyền §1.1–§1.2 mà §1.6 không liệt kê thành hàng riêng (endorsement, hồ sơ của
          chính mình) — mỗi hàng đều ghi rõ căn cứ code, không hàng nào tự bịa.
        </p>
      </div>
    </>
  );
}
