import React, { useMemo, useState } from 'react';
import { ShieldCheck, Check, X, Info } from 'lucide-react';

/**
 * Trang so sánh gói cho customer — FE ONLY, STATIC.
 *
 * Dữ liệu viết cứng trong file này, KHÔNG gọi backend, KHÔNG tạo endpoint,
 * KHÔNG tạo hợp đồng thật và KHÔNG điều hướng sang mua bảo hiểm thật.
 * Mọi con số đều là số minh họa tròn, chỉ để so sánh giao diện.
 */

// Nhãn bắt buộc, hiển thị nổi bật ở đầu trang và lặp lại ở cuối bảng.
const SAMPLE_NOTICE =
  '3 gói dưới đây là GÓI MẪU MINH HỌA để so sánh giao diện — không phải gói thật đang bán, phí và quyền lợi chỉ để tham khảo.';

const SAMPLE_PACKAGES = [
  {
    id: 'co-ban',
    name: 'Cơ bản',
    tagline: 'Gói mẫu tối giản, chỉ gồm quyền lợi nền tảng.',
    monthlyFeeLabel: '150.000 đ/tháng (minh họa)',
    yearlyFeeLabel: '1.800.000 đ/năm (minh họa)',
    limitLabel: '500 triệu đồng (minh họa)',
    deductibleLabel: '5 triệu đồng/vụ (minh họa)',
    benefits: {
      chayNo: 'Có',
      ngapNuoc: 'Không',
      trachNhiemCongCong: 'Không',
      taiNanLaoDong: 'Không',
      tromCap: 'Có — giới hạn minh họa',
      tamTru: 'Không',
      hoTroKhanCap: 'Có',
    },
  },
  {
    id: 'tieu-chuan',
    name: 'Tiêu chuẩn',
    tagline: 'Gói mẫu cân bằng giữa phí minh họa và phạm vi quyền lợi.',
    monthlyFeeLabel: '350.000 đ/tháng (minh họa)',
    yearlyFeeLabel: '4.200.000 đ/năm (minh họa)',
    limitLabel: '1,5 tỷ đồng (minh họa)',
    deductibleLabel: '2 triệu đồng/vụ (minh họa)',
    benefits: {
      chayNo: 'Có',
      ngapNuoc: 'Có — giới hạn minh họa',
      trachNhiemCongCong: 'Có — giới hạn minh họa',
      taiNanLaoDong: 'Không',
      tromCap: 'Có',
      tamTru: 'Có — giới hạn minh họa',
      hoTroKhanCap: 'Có',
    },
  },
  {
    id: 'toan-dien',
    name: 'Toàn diện',
    tagline: 'Gói mẫu đầy đủ nhất trong 3 gói minh họa.',
    monthlyFeeLabel: '700.000 đ/tháng (minh họa)',
    yearlyFeeLabel: '8.400.000 đ/năm (minh họa)',
    limitLabel: '3 tỷ đồng (minh họa)',
    deductibleLabel: '1 triệu đồng/vụ (minh họa)',
    benefits: {
      chayNo: 'Có',
      ngapNuoc: 'Có',
      trachNhiemCongCong: 'Có',
      taiNanLaoDong: 'Có — giới hạn minh họa',
      tromCap: 'Có',
      tamTru: 'Có',
      hoTroKhanCap: 'Có',
    },
  },
];

// Các hàng tiêu chí của bảng so sánh (1 cột tiêu chí + 1 cột mỗi gói).
const CRITERIA_ROWS = [
  { key: 'monthlyFee', label: 'Phí minh họa / tháng', get: (p) => p.monthlyFeeLabel },
  { key: 'yearlyFee', label: 'Phí minh họa / năm', get: (p) => p.yearlyFeeLabel },
  { key: 'limit', label: 'Hạn mức (minh họa)', get: (p) => p.limitLabel },
  { key: 'deductible', label: 'Khấu trừ (minh họa)', get: (p) => p.deductibleLabel },
  { key: 'chayNo', label: 'Cháy nổ', get: (p) => p.benefits.chayNo },
  { key: 'ngapNuoc', label: 'Ngập nước', get: (p) => p.benefits.ngapNuoc },
  { key: 'trachNhiemCongCong', label: 'Trách nhiệm công cộng', get: (p) => p.benefits.trachNhiemCongCong },
  { key: 'taiNanLaoDong', label: 'Tai nạn lao động', get: (p) => p.benefits.taiNanLaoDong },
  { key: 'tromCap', label: 'Trộm cắp / đột nhập', get: (p) => p.benefits.tromCap },
  { key: 'tamTru', label: 'Hỗ trợ chi phí tạm trú', get: (p) => p.benefits.tamTru },
  { key: 'hoTroKhanCap', label: 'Hỗ trợ khẩn cấp 24/7', get: (p) => p.benefits.hoTroKhanCap },
];

function isCovered(value) {
  return typeof value === 'string' && value.startsWith('Có');
}

export function ComparePackagesPage() {
  // Mặc định chọn cả 3 gói; người dùng có thể bỏ chọn để thu hẹp bảng.
  const [selectedIds, setSelectedIds] = useState(() => SAMPLE_PACKAGES.map((p) => p.id));
  const [notice, setNotice] = useState('');

  const selectedPackages = useMemo(
    () => SAMPLE_PACKAGES.filter((p) => selectedIds.includes(p.id)),
    [selectedIds],
  );

  const togglePackage = (id) => {
    setSelectedIds((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
  };

  const handleChoose = (pkg) => {
    // Chỉ hiện thông báo — KHÔNG tạo hợp đồng, KHÔNG điều hướng mua thật.
    setNotice(`Đây là gói mẫu, chưa mở bán (gói “${pkg.name}” chỉ để so sánh giao diện).`);
  };

  return (
    <div style={{ padding: '2rem', maxWidth: '1200px', margin: '0 auto' }}>
      {/* Tiêu đề + nhãn gói mẫu minh họa (không thể nhầm với gói thật) */}
      <div className="glass-card hero-card" style={{ marginBottom: '1.5rem' }}>
        <div>
          <h1>So sánh gói bảo hiểm</h1>
          <p style={{ color: 'var(--text-secondary)' }}>
            Chọn các gói bên dưới để xem bảng so sánh theo từng tiêu chí.
          </p>
        </div>
        <ShieldCheck size={40} color="#818cf8" />
      </div>

      <div
        role="alert"
        className="glass-card"
        style={{
          padding: '1rem 1.25rem',
          marginBottom: '1.5rem',
          border: '1px solid #f59e0b',
          display: 'flex',
          gap: '0.75rem',
          alignItems: 'flex-start',
        }}
      >
        <Info size={20} color="#f59e0b" style={{ flexShrink: 0, marginTop: '0.1rem' }} />
        <strong>{SAMPLE_NOTICE}</strong>
      </div>

      {/* Chọn / bỏ chọn gói để so sánh */}
      <div className="chip-row" style={{ marginBottom: '1.25rem' }} role="group" aria-label="Chọn gói để so sánh">
        {SAMPLE_PACKAGES.map((p) => {
          const selected = selectedIds.includes(p.id);
          return (
            <button
              key={p.id}
              type="button"
              className={`chip${selected ? ' active' : ''}`}
              onClick={() => togglePackage(p.id)}
              aria-pressed={selected}
            >
              {selected ? <Check size={14} /> : <X size={14} />}
              {p.name}
            </button>
          );
        })}
      </div>

      {notice && (
        <div
          role="status"
          className="glass-card"
          style={{ padding: '0.9rem 1.25rem', marginBottom: '1.25rem', border: '1px solid var(--border-subtle)' }}
        >
          {notice}
        </div>
      )}

      {selectedPackages.length === 0 ? (
        <div className="glass-card" style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-secondary)' }}>
          Bạn đã bỏ chọn tất cả các gói. Hãy chọn ít nhất một gói mẫu ở trên để xem bảng so sánh.
        </div>
      ) : (
        <div className="glass-card" style={{ padding: '1.5rem', overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: '640px' }}>
            <thead>
              <tr>
                <th style={{ textAlign: 'left', padding: '0.75rem', color: 'var(--text-muted)', fontSize: '0.8rem', textTransform: 'uppercase' }}>
                  Tiêu chí
                </th>
                {selectedPackages.map((p) => (
                  <th
                    key={p.id}
                    style={{
                      textAlign: 'left',
                      padding: '0.75rem',
                      background: 'rgba(129, 140, 248, 0.12)',
                      borderRadius: 'var(--radius-md)',
                    }}
                  >
                    <div style={{ fontSize: '1.05rem', fontWeight: 700 }}>{p.name}</div>
                    <div style={{ fontSize: '0.8rem', fontWeight: 400, color: 'var(--text-secondary)' }}>{p.tagline}</div>
                    <span className="badge badge-primary" style={{ marginTop: '0.4rem', display: 'inline-block' }}>
                      Gói mẫu minh họa
                    </span>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {CRITERIA_ROWS.map((row) => (
                <tr key={row.key} style={{ borderTop: '1px solid var(--border-subtle)' }}>
                  <td style={{ padding: '0.75rem', fontWeight: 600, fontSize: '0.9rem' }}>{row.label}</td>
                  {selectedPackages.map((p) => {
                    const value = row.get(p);
                    const covered = row.key !== 'monthlyFee' && row.key !== 'yearlyFee' && row.key !== 'limit' && row.key !== 'deductible'
                      ? isCovered(value)
                      : null;
                    return (
                      <td key={p.id} style={{ padding: '0.75rem', fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
                        {covered === null ? (
                          value
                        ) : covered ? (
                          <span style={{ color: '#10b981', fontWeight: 600 }}>
                            <Check size={15} style={{ verticalAlign: 'middle' }} /> {value}
                          </span>
                        ) : (
                          <span style={{ color: 'var(--text-muted)' }}>
                            <X size={15} style={{ verticalAlign: 'middle' }} /> {value}
                          </span>
                        )}
                      </td>
                    );
                  })}
                </tr>
              ))}
              <tr style={{ borderTop: '1px solid var(--border-subtle)' }}>
                <td style={{ padding: '0.75rem' }} />
                {selectedPackages.map((p) => (
                  <td key={p.id} style={{ padding: '0.75rem' }}>
                    <button type="button" className="btn btn-primary btn-sm" onClick={() => handleChoose(p)}>
                      Chọn gói này
                    </button>
                  </td>
                ))}
              </tr>
            </tbody>
          </table>
          <p style={{ marginTop: '1rem', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
            {SAMPLE_NOTICE} Nút “Chọn gói này” chỉ hiện thông báo, không tạo hợp đồng và không mở trang mua bảo hiểm thật.
          </p>
        </div>
      )}
    </div>
  );
}

export default ComparePackagesPage;
