/**
 * Nội dung pháp lý hiển thị cho khách hàng — MỘT NGUỒN DUY NHẤT
 * (bước "Xác nhận" của wizard mua bảo hiểm bắt buộc đọc + đồng ý).
 *
 * LƯU Ý TRUNG THỰC: đây là bản điều khoản mẫu cho app demo, viết theo
 * cấu trúc hợp đồng bảo hiểm tài sản phổ biến nhưng KHÔNG thay thế văn
 * bản pháp lý chính thức. Khi có bộ điều khoản thật (do công ty phát
 * hành), chỉ cần thay nội dung ở đây và TĂNG TERMS_VERSION — hợp đồng
 * mới sẽ ghi vết phiên bản khách đã đồng ý (Policy.termsVersion).
 */
export const TERMS_VERSION = 'V2026-11';

export const TERMS_SECTIONS = [
  {
    title: '1. Điều khoản chung',
    body: [
      'Hợp đồng bảo hiểm được giao kết giữa khách hàng và công ty bảo hiểm khi yêu cầu báo giá được xác nhận và phí bảo hiểm đã được thanh toán thành công. Việc gửi yêu cầu báo giá đồng nghĩa khách hàng cam kết mọi thông tin khai báo là trung thực, chính xác và đầy đủ.',
      'Khai báo không trung thực có thể khiến hợp đồng bị vô hiệu hoặc quyền lợi bồi thường bị từ chối theo quy định pháp luật hiện hành.',
    ],
  },
  {
    title: '2. Phạm vi bảo hiểm & loại trừ',
    body: [
      'Quyền lợi bảo hiểm áp dụng cho các rủi ro được chọn tại từng địa điểm (cháy nổ, ngập nước, trộm cướp, trách nhiệm bên thứ ba…) theo hạn mức và mức khấu trừ hiển thị trên báo giá.',
      'Không bảo hiểm cho: thiệt hại có chủ đích, hao mòn tự nhiên, thiệt hại xảy ra trước ngày hiệu lực hoặc sau ngày hết hạn, và các rủi ro khác được loại trừ theo điều khoản chi tiết của từng quyền lợi.',
    ],
  },
  {
    title: '3. Quy trình bồi thường',
    body: [
      'Khi xảy ra tổn thất, khách hàng gửi yêu cầu bồi thường kèm ảnh bằng chứng trong thời gian sớm nhất. Hồ sơ sẽ được thẩm định viên tiếp nhận và đánh giá; số tiền bồi thường không vượt quá tổng số tiền bảo hiểm của hợp đồng.',
      'Một hợp đồng chỉ có một yêu cầu bồi thường đang xử lý tại một thời điểm. Kết quả duyệt/từ chối được thông báo kèm lý do; tiền bồi thường được chi trả sau khi kế toán xác nhận.',
    ],
  },
  {
    title: '4. Thanh toán & hoàn tiền',
    body: [
      'Phí bảo hiểm thanh toán qua chuyển khoản với mã QR/mã nội dung do hệ thống sinh. Hợp đồng có hiệu lực ngay khi khoản phí được xác nhận từ cổng thanh toán.',
      'Hủy hợp đồng trong thời hạn hiệu lực: phí chưa bảo hiểm có thể được xem xét hoàn lại theo quy định hoàn tiền của công ty; chi phí thẩm định (nếu có) sẽ được trừ.',
    ],
  },
  {
    title: '5. Bảo vệ dữ liệu cá nhân',
    body: [
      'Dữ liệu cá nhân và thông tin tài sản được thu thập chỉ nhằm mục đích thẩm định, cấp hợp đồng, bồi thường và chăm sóc khách hàng. Nền tảng không bán/chuyển nhượng dữ liệu cho bên thứ ba ngoài phạm vi phục vụ hợp đồng.',
      'Khách hàng có quyền xem, chỉnh sửa hồ sơ cá nhân tại mục "Hồ sơ" và yêu cầu hỗ trợ về dữ liệu qua đại lý phục vụ hoặc quản trị viên nền tảng.',
    ],
  },
];
