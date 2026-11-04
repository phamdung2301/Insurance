import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { policyService } from '../services/api';
import { formatMoney } from '../utils/format';
import { useToast } from '../context/ToastContext';

/**
 * Trang kết quả thanh toán qua cổng VNPay — CHỈ TRA CỨU + HIỂN THỊ.
 *
 * Luồng: VNPay redirect về đây (xem VNPAY_RETURN_URL — backend mặc định để
 * trống, xem báo cáo FE) kèm các tham số vnp_* trên query string. Trang đọc
 * vnp_TxnRef và gửi TOÀN BỘ tham số vnp_* về GET /payments/vnpay-return để
 * backend verify chữ ký; trạng thái hiển thị lấy từ backend (payment.status
 * + policyStatus), KHÔNG tự kết luận thành công từ vnp_ResponseCode trên URL.
 * Trang này không gọi bất kỳ API nào đổi trạng thái — trạng thái chỉ đổi khi
 * IPN từ VNPay về backend.
 *
 * Không có vnp_TxnRef → hiển thị lỗi rõ ràng, không gọi API mù.
 */
export function PaymentResultPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const toast = useToast();
  const [payment, setPayment] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const txnRef = searchParams.get('vnp_TxnRef');

  const load = useCallback(async () => {
    if (!txnRef) {
      setError('Không tìm thấy mã giao dịch (vnp_TxnRef) trên đường dẫn quay về từ cổng thanh toán.');
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      // Gửi toàn bộ tham số vnp_* (kèm chữ ký nếu có) để backend verify.
      const params = Object.fromEntries(searchParams.entries());
      const res = await policyService.lookupGatewayReturn(params);
      setPayment(res?.data ?? res ?? null);
    } catch (err) {
      if (err.status === 404) {
        setError(`Không tìm thấy giao dịch thanh toán với mã ${txnRef}.`);
      } else if (err.status === 403) {
        setError('Chữ ký xác thực không hợp lệ hoặc bạn không có quyền xem giao dịch này.');
      } else {
        setError(err.message || 'Không tra cứu được kết quả thanh toán.');
      }
    } finally {
      setLoading(false);
    }
  }, [txnRef, searchParams, toast]);

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
          <button type="button" className="btn" onClick={() => navigate('/thanh-toan')}>
            Về trang Thanh toán
          </button>
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
        : `Trạng thái giao dịch: ${status ?? '—'}`;

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
          <dt>Trạng thái thanh toán</dt>
          <dd>{status ?? '—'}</dd>
          {policyStatus && (<><dt>Trạng thái hợp đồng</dt><dd>{policyStatus}</dd></>)}
        </dl>
        {isSuccess && (
          <p style={{ marginTop: '0.75rem' }}>
            Hợp đồng đã được kích hoạt. Kết quả trên lấy từ hệ thống sau khi cổng
            thanh toán xác nhận, không dựa vào mã trên đường dẫn.
          </p>
        )}
        {isPending && (
          <p style={{ marginTop: '0.75rem' }}>
            Hệ thống chưa nhận được xác nhận cuối cùng từ cổng thanh toán. Vui lòng
            chờ ít phút rồi bấm “Kiểm tra lại” — trạng thái chỉ đổi khi cổng gửi
            xác nhận về hệ thống.
          </p>
        )}
        {isFailed && (
          <p style={{ marginTop: '0.75rem' }}>
            Giao dịch này không thành công. Bạn có thể tạo giao dịch mới từ trang
            chi tiết hợp đồng.
          </p>
        )}
        {payment.signatureValid === false && (
          <p style={{ marginTop: '0.75rem', color: 'var(--text-muted)', fontSize: '0.9rem' }}>
            Lưu ý: đường dẫn quay về không kèm chữ ký xác thực — trạng thái trên
            vẫn lấy từ hệ thống, không tin tham số trên đường dẫn.
          </p>
        )}
      </div>
      <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
        {isPending && (
          <button type="button" className="btn btn-primary" onClick={load}>
            Kiểm tra lại
          </button>
        )}
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
      </div>
    </div>
  );
}
