import React, { useCallback, useEffect, useState } from 'react';
import { getUserMessage, statusLabel, PAYMENT_STATUS, POLICY_STATUS } from '../utils/messages';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { policyService } from '../services/api';
import { formatMoney } from '../utils/format';
import { useToast } from '../context/ToastContext';
import { useAuth } from '../context/AuthContext';
import { FINANCE_HOME } from '../utils/finance';

/**
 * Trang kết quả thanh toán chuyển khoản SePay — CHỈ TRA CỨU + HIỂN THỊ.
 *
 * Luồng: trang chỉ đọc `txnRef` từ query string rồi gọi
 * GET /payments/status?txnRef=...; trạng thái hiển thị lấy từ backend
 * (payment.status + policyStatus), KHÔNG tự kết luận thành công từ URL.
 * Trang này không gọi bất kỳ API nào đổi trạng thái — trạng thái chỉ đổi
 * khi SePay gửi xác nhận về backend.
 *
 * Không có txnRef → hiển thị lỗi rõ ràng, không gọi API mù.
 */
export function PaymentResultPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const toast = useToast();
  // Trang này chỉ tra cứu tiền (GET /payments/status) nên FINANCE được xem
  // nội dung; nhưng 3 nút dưới dẫn tới trang bị chặn với FINANCE
  // (/hop-dong/:n, /thanh-toan) nên ẩn hết với FINANCE, thay bằng nút về
  // trang Báo cáo (workspace tiền của kế toán).
  const { isFinance } = useAuth();
  const [payment, setPayment] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const txnRef = searchParams.get('txnRef');

  const load = useCallback(async () => {
    if (!txnRef) {
      setError('Không tìm thấy mã giao dịch (txnRef) trên đường dẫn. Vui lòng mở lại từ trang chi tiết hợp đồng hoặc trang Thanh toán.');
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const res = await policyService.getPaymentStatus(txnRef);
      setPayment(res?.data ?? res ?? null);
    } catch (err) {
      if (err.status === 404) {
        setError(`Không tìm thấy giao dịch thanh toán với mã ${txnRef}.`);
      } else {
        setError(getUserMessage(err, 'Không tra cứu được kết quả thanh toán.'));
      }
    } finally {
      setLoading(false);
    }
  }, [txnRef, toast]);

  useEffect(() => { load(); }, [load]);

  if (loading) {
    return (
      <div>
        <h2>Kết quả thanh toán</h2>
        <p style={{ color: 'var(--text-muted)' }}>Đang tra cứu kết quả từ hệ thống…</p>
      </div>
    );
  }

  if (error || !payment) {
    return (
      <div>
        <h2>Kết quả thanh toán</h2>
        <div className="card" style={{ padding: '1rem', background: 'var(--surface-warn, #fffbeb)' }}>
          {error || 'Không tra cứu được kết quả thanh toán.'}
        </div>
        <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1rem', flexWrap: 'wrap' }}>
          {isFinance ? (
            <button type="button" className="btn" onClick={() => navigate(FINANCE_HOME)}>
              Về trang Báo cáo
            </button>
          ) : (
            <button type="button" className="btn" onClick={() => navigate('/thanh-toan')}>
              Về trang Thanh toán
            </button>
          )}
        </div>
      </div>
    );
  }

  const status = payment.status;
  const policyStatus = payment.policyStatus;
  const isSuccess = status === 'PAID' || policyStatus === 'ACTIVE';
  const isPending = !isSuccess && status === 'PENDING';
  const isFailed = status === 'FAILED' || status === 'CANCELLED';

  const title = isSuccess
    ? 'Thanh toán thành công'
    : isPending
      ? 'Giao dịch đang chờ xử lý'
      : isFailed
        ? 'Thanh toán thất bại hoặc đã hủy'
        : `Trạng thái giao dịch: ${statusLabel(PAYMENT_STATUS, status)}`;

  return (
    <div>
      <h2>Kết quả thanh toán</h2>
      <div className="card" style={{ padding: '1rem', marginBottom: '1rem' }}>
        <p style={{ fontSize: '1.1rem', fontWeight: 600 }}>{title}</p>
        <dl style={{ display: 'grid', gridTemplateColumns: 'max-content 1fr', gap: '0.4rem 1rem', margin: 0 }}>
          <dt>Số hợp đồng</dt>
          <dd>{payment.policyNumber ?? '—'}</dd>
          <dt>Số tiền</dt>
          <dd>{formatMoney(payment.amount)}</dd>
          <dt>Mã giao dịch</dt>
          <dd>{payment.txnRef ?? txnRef ?? '—'}</dd>
          <dt>Nhà cung cấp</dt>
          <dd>{payment.provider ?? 'SEPAY'}</dd>
          <dt>Trạng thái thanh toán</dt>
          <dd>{statusLabel(PAYMENT_STATUS, status)}</dd>
          {policyStatus && (<><dt>Trạng thái hợp đồng</dt><dd>{policyStatus}</dd></>)}
        </dl>
        {isSuccess && (
          <p style={{ marginTop: '0.75rem' }}>
            Hợp đồng đã được kích hoạt. Kết quả trên lấy từ hệ thống sau khi
            SePay xác nhận, không dựa vào tham số trên đường dẫn.
          </p>
        )}
        {isPending && (
          <p style={{ marginTop: '0.75rem' }}>
            Đang chờ xác nhận — nếu đã chuyển khoản, hệ thống thường xác nhận
            trong vài chục giây, bấm “Kiểm tra lại” để cập nhật.
          </p>
        )}
        {isFailed && (
          <p style={{ marginTop: '0.75rem' }}>
            Giao dịch này không thành công. Bạn có thể tạo giao dịch mới từ trang
            chi tiết hợp đồng.
          </p>
        )}
      </div>
      <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
        {isPending && (
          <button type="button" className="btn btn-primary" onClick={load}>
            Kiểm tra lại
          </button>
        )}
        {!isFinance && (
          <>
            {isFailed && payment.policyNumber && (
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => navigate(`/hop-dong/${encodeURIComponent(payment.policyNumber)}`)}
              >
                Thử lại
              </button>
            )}
            {payment.policyNumber && (
              <button
                type="button"
                className="btn"
                onClick={() => navigate(`/hop-dong/${encodeURIComponent(payment.policyNumber)}`)}
              >
                Xem hợp đồng
              </button>
            )}
            <button type="button" className="btn" onClick={() => navigate('/thanh-toan')}>
              Về trang Thanh toán
            </button>
          </>
        )}
        {isFinance && (
          <button type="button" className="btn" onClick={() => navigate(FINANCE_HOME)}>
            Về trang Báo cáo
          </button>
        )}
      </div>
    </div>
  );
}
