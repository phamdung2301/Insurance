/**
 * Cấu hình thương hiệu nền tảng — FILE DUY NHẤT cho tên nền tảng (SaaS
 * Phase 2 Track C tạo, Track D TÁI DÙNG + MỞ RỘNG file này, KHÔNG tạo
 * file config thứ hai).
 *
 * Tên/slogan/liên hệ THẬT chưa có từ user nên mọi giá trị dưới đây là
 * placeholder trung thực, hiển thị đúng như vậy trên storefront (dòng minh
 * bạch "Thanh toán được xử lý qua [Tên nền tảng]") và trên landing nền
 * tảng — KHÔNG bịa tên thương hiệu, slogan hay liên hệ giả làm thật.
 *
 * Khi user chốt branding thật, chỉ sửa đúng file này (không sửa code):
 * mọi chỗ dùng (storefront Track C, landing Track D) tự đổi theo.
 * Lưu ý: các placeholder liên hệ được hiển thị dạng TEXT, không gắn
 * mailto:/link Zalo, cho tới khi user điền giá trị thật.
 */
export const PLATFORM_NAME = '[Tên nền tảng]';

export const PLATFORM_SLOGAN = '[Slogan nền tảng]';

export const PLATFORM_CONTACT_EMAIL = '[Email liên hệ]';

export const PLATFORM_CONTACT_ZALO = '[Zalo liên hệ]';

/** Neo tới mục Hỗ trợ trên landing nền tảng (route `/`). */
export const PLATFORM_SUPPORT_LINK = '/#ho-tro';
