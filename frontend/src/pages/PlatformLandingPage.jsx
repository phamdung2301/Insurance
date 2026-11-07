import React, { Suspense, lazy, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  ShieldCheck, Users, Link2, CreditCard, FileText, ArrowRight,
  Mail, MessageCircle, LogIn, HelpCircle, ScrollText, Lock,
} from 'lucide-react';
import {
  PLATFORM_NAME, PLATFORM_SLOGAN,
  PLATFORM_CONTACT_EMAIL, PLATFORM_CONTACT_ZALO,
} from '../config/platform';
import { ErrorBoundary } from '../components/ErrorBoundary';

// Tái dùng khiên 3D đã kiểm chứng ở LoginPage (chủ thể bảo hiểm rõ ràng,
// KHÔNG phải torus-knot chung chung). Lazy để landing vẫn hiện chữ ngay cả
// khi chunk 3D chưa tải xong; mọi hook/import khai báo ở đầu file.
const Shield3D = lazy(() => import('../components/Shield3D'));

/** Khiên tĩnh dự phòng khi WebGL mất context giữa chừng (webglcontextlost). */
function StaticShield() {
  return (
    <div className="pl-static-shield" aria-hidden>
      <svg viewBox="0 0 100 110" width="100%" height="100%">
        <defs>
          <linearGradient id="plshg" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#2b7fff" />
            <stop offset="100%" stopColor="#0b3fa0" />
          </linearGradient>
        </defs>
        <path d="M50 4 L90 18 V52 C90 78 72 96 50 106 C28 96 10 78 10 52 V18 Z"
          fill="url(#plshg)" stroke="#eab308" strokeWidth="3" />
        <path d="M35 52 L46 63 L67 40" fill="none" stroke="#f5d67b" strokeWidth="7"
          strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </div>
  );
}

const BENEFITS = [
  { icon: Users, title: 'Mỗi đại lý một gian hàng riêng', desc: 'Đại lý vận hành và bán hàng qua link riêng của mình, trên cùng một nền tảng chung.' },
  { icon: Link2, title: 'Khách hàng gắn với đúng đại lý', desc: 'Khách vào qua trang của đại lý sẽ được chính đại lý đó đồng hành suốt quá trình mua và chăm sóc hợp đồng.' },
  { icon: CreditCard, title: 'Thanh toán qua nền tảng', desc: `Thanh toán được xử lý qua ${PLATFORM_NAME}, minh bạch trên từng gian hàng đại lý.` },
];

const STEPS = [
  { n: '1', title: 'Liên hệ quản trị viên', desc: 'Gửi nhu cầu trở thành đại lý qua thông tin liên hệ bên dưới.' },
  { n: '2', title: 'Quản trị viên xét duyệt và tạo tài khoản', desc: 'Chưa có đăng ký tự động — tài khoản đại lý do quản trị viên tạo sau khi xét duyệt.' },
  { n: '3', title: 'Nhận link riêng và bắt đầu bán', desc: 'Đại lý nhận link riêng dạng /a/<tên-đại-lý> để gửi cho khách hàng của mình.' },
];

const FAQS = [
  { q: 'Tôi là khách hàng, mua bảo hiểm ở đâu?', a: 'Bạn mua qua trang của một đại lý trên nền tảng. Nếu chưa có link đại lý, bạn vẫn có thể bắt đầu tại trang Mua bảo hiểm và chọn một đại lý đồng hành ở đó.' },
  { q: 'Link đại lý báo "không còn hoạt động" thì sao?', a: 'Nghĩa là đại lý đó đã tạm ngưng hoặc ngừng hoạt động và link không còn dùng để mua tiếp được. Bạn vẫn có thể liên hệ quản trị viên qua mục Hỗ trợ để được hướng dẫn.' },
  { q: 'Tôi muốn trở thành đại lý thì đăng ký ở đâu?', a: 'Hiện chưa có form tự đăng ký. Bạn liên hệ quản trị viên qua thông tin ở mục "Trở thành đại lý", quản trị viên xét duyệt và tạo tài khoản cho bạn.' },
  { q: 'Thanh toán được xử lý như thế nào?', a: `Thanh toán được xử lý qua ${PLATFORM_NAME} và hiển thị minh bạch trên gian hàng của đại lý bạn đang mua.` },
  { q: 'Tôi quên mật khẩu hoặc không đăng nhập được?', a: 'Hãy dùng trang đăng nhập để khôi phục, hoặc liên hệ quản trị viên qua thông tin ở mục Hỗ trợ.' },
];

/** Reveal khi cuộn tới — CHỈ dùng cho các section dưới hero. Hero (chương đầu)
 *  render tĩnh, opacity 1 ngay từ lần sơn đầu tiên, không fade-in theo scroll. */
function Reveal({ children, delay = 0 }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 18 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: '-60px' }}
      transition={{ duration: 0.5, delay, ease: [0.22, 1, 0.36, 1] }}
    >
      {children}
    </motion.div>
  );
}

/**
 * Landing nền tảng tối thiểu (SaaS Phase 2 Track D) — route public `/`.
 * Đủ 5 phần: giới thiệu, kêu gọi làm đại lý (CTA liên hệ ADMIN, KHÔNG tự
 * đăng ký), cổng đăng nhập, hỗ trợ, điều khoản (khung chờ duyệt).
 * KHÔNG bán hàng, KHÔNG danh bạ đại lý, KHÔNG bịa số liệu/khách hàng.
 * Branding lấy từ frontend/src/config/platform.js (file duy nhất).
 */
export function PlatformLandingPage() {
  const heroRef = useRef(null);
  const [glLost, setGlLost] = useState(false);

  // webglcontextlost: sự kiện không bubble nhưng capture phase từ wrapper
  // vẫn bắt được; mất context → hiện khiên tĩnh, trang không đen.
  useEffect(() => {
    const root = heroRef.current;
    if (!root) return undefined;
    const onLost = (e) => { e.preventDefault(); setGlLost(true); };
    root.addEventListener('webglcontextlost', onLost, true);
    return () => root.removeEventListener('webglcontextlost', onLost, true);
  }, []);

  return (
    <div className="pl-page">
      <style>{`
        .pl-page{max-width:1280px;margin:0 auto;padding:0 1.5rem 3rem;color:var(--text-primary)}
        .pl-header{display:flex;align-items:center;justify-content:space-between;gap:1rem;padding:1.25rem 0;flex-wrap:wrap}
        .pl-brand{display:flex;align-items:center;gap:.6rem;font-weight:800;font-size:1.15rem}
        .pl-brand .pl-logo{width:40px;height:40px;border-radius:12px;display:grid;place-items:center;color:#fff;background:var(--primary-gradient);box-shadow:var(--shadow-glow)}
        .pl-nav{display:flex;gap:1.25rem;flex-wrap:wrap;font-weight:600;font-size:.95rem}
        .pl-nav a{color:var(--text-secondary)} .pl-nav a:hover{color:var(--primary)}
        .pl-hero{display:grid;grid-template-columns:1.05fr .95fr;gap:2rem;align-items:center;padding:2.5rem 0 1rem}
        .pl-eyebrow{font-weight:700;letter-spacing:.08em;text-transform:uppercase;color:var(--primary);font-size:.8rem;margin-bottom:.75rem}
        .pl-hero h1{font-size:2.6rem;line-height:1.15;margin:0 0 .75rem}
        .pl-hero .pl-slogan{font-size:1.15rem;color:var(--text-secondary);margin:0 0 1.5rem;max-width:34rem}
        .pl-cta{display:flex;gap:.75rem;flex-wrap:wrap}
        .pl-hero-visual{position:relative;min-height:420px}
        .pl-hero-blur{position:absolute;inset:8% 4%;border-radius:50%;filter:blur(48px);pointer-events:none;
          background:radial-gradient(circle at 35% 35%, rgba(43,127,255,.35), transparent 65%), radial-gradient(circle at 70% 70%, rgba(245,184,0,.30), transparent 65%)}
        .pl-hero-canvas{position:relative}
        .pl-static-shield{width:min(320px,80%);margin:40px auto;animation:plFloat 5s ease-in-out infinite}
        @keyframes plFloat{0%,100%{transform:translateY(0) rotate(-2deg)}50%{transform:translateY(-12px) rotate(2deg)}}
        .pl-marquee{overflow:hidden;border-top:1px solid var(--border-subtle);border-bottom:1px solid var(--border-subtle);padding:.8rem 0;margin:1.5rem 0 2.5rem}
        .pl-marquee-track{display:flex;gap:2.5rem;width:max-content;animation:plMarquee 30s linear infinite;white-space:nowrap;font-weight:700;color:var(--text-secondary)}
        @keyframes plMarquee{to{transform:translateX(-50%)}}
        @media (prefers-reduced-motion: reduce){.pl-marquee-track,.pl-static-shield{animation:none}}
        .pl-section{padding:2rem 0} .pl-section h2{font-size:1.7rem;margin:0 0 .4rem}
        .pl-lead{color:var(--text-secondary);max-width:44rem;margin:0 0 1.5rem}
        .pl-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:1.1rem}
        .pl-card{background:var(--bg-card);border:1px solid var(--border-subtle);border-radius:var(--radius-lg);padding:1.4rem;box-shadow:var(--shadow-sm)}
        .pl-card .pl-icon{width:44px;height:44px;border-radius:12px;display:grid;place-items:center;color:var(--primary);background:var(--bg-glass);margin-bottom:.8rem}
        .pl-card h3{margin:0 0 .35rem;font-size:1.05rem} .pl-card p{margin:0;color:var(--text-secondary);font-size:.95rem}
        .pl-steps{display:grid;grid-template-columns:repeat(3,1fr);gap:1.1rem}
        .pl-step-num{width:40px;height:40px;border-radius:50%;display:grid;place-items:center;font-weight:800;color:#fff;background:var(--gold-gradient);margin-bottom:.8rem}
        .pl-contact{display:flex;gap:1rem;flex-wrap:wrap;margin-top:1.25rem}
        .pl-contact span{display:inline-flex;align-items:center;gap:.45rem;background:var(--bg-card);border:1px solid var(--border-subtle);border-radius:999px;padding:.55rem 1rem;font-weight:600}
        .pl-note{margin-top:1rem;color:var(--text-muted);font-size:.9rem}
        .pl-login{display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:1.25rem;padding:1.75rem 2rem;color:#fff;
          background:linear-gradient(120deg,#0b3fa0 0%,#1663d9 55%,#2b7fff 100%);border-radius:var(--radius-xl)}
        .pl-login h2{color:#fff;margin:0 0 .3rem} .pl-login p{color:rgba(255,255,255,.85);margin:0}
        .pl-faq details{background:var(--bg-card);border:1px solid var(--border-subtle);border-radius:var(--radius-md);padding:1rem 1.25rem;margin-bottom:.7rem}
        .pl-faq summary{cursor:pointer;font-weight:700} .pl-faq p{margin:.6rem 0 0;color:var(--text-secondary)}
        .pl-terms{display:grid;grid-template-columns:1fr 1fr;gap:1.1rem}
        .pl-terms ul{margin:.5rem 0 0 1.2rem;color:var(--text-secondary)} .pl-terms li{margin-bottom:.35rem}
        .pl-draft{border:1px dashed var(--accent-amber);background:rgba(217,119,6,.08);border-radius:var(--radius-md);padding:1rem 1.25rem;margin-bottom:1.25rem;font-size:.95rem}
        .pl-footer{border-top:1px solid var(--border-subtle);margin-top:2rem;padding-top:1.5rem;display:flex;justify-content:space-between;gap:1rem;flex-wrap:wrap;color:var(--text-muted);font-size:.9rem}
        @media (max-width: 820px){
          .pl-hero{grid-template-columns:1fr;padding-top:1.5rem}
          .pl-hero h1{font-size:2rem}
          .pl-hero-visual{min-height:380px}
          .pl-grid,.pl-steps,.pl-terms{grid-template-columns:1fr}
          .pl-header{padding-bottom:.5rem}
        }
      `}</style>

      <header className="pl-header">
        <div className="pl-brand">
          <span className="pl-logo"><ShieldCheck size={22} /></span>
          {PLATFORM_NAME}
        </div>
        <nav className="pl-nav" aria-label="Điều hướng nền tảng">
          <a href="#gioi-thieu">Giới thiệu</a>
          <a href="#dai-ly">Trở thành đại lý</a>
          <a href="#ho-tro">Hỗ trợ</a>
          <a href="#dieu-khoan">Điều khoản</a>
        </nav>
        <Link className="btn btn-primary" to="/login"><LogIn size={16} style={{ marginRight: '.4rem', verticalAlign: 'middle' }} />Đăng nhập</Link>
      </header>

      {/* HERO — chữ render tĩnh, hiện ngay khi load; canvas là element chứa
          trong khung (không phải nền cover), nền mờ cùng tông nằm SAU canvas,
          mobile xếp chồng chữ lên trên canvas nên không bị cắt. */}
      <section className="pl-hero">
        <div>
          <p className="pl-eyebrow">Nền tảng SaaS cho đại lý bảo hiểm</p>
          <h1>{PLATFORM_NAME}</h1>
          <p className="pl-slogan">{PLATFORM_SLOGAN} — một nền tảng chung để đại lý vận hành, bán hàng và chăm sóc khách hàng của chính mình, thay vì mỗi người tự xoay xở rời rạc.</p>
          <div className="pl-cta">
            <a className="btn btn-primary" href="#dai-ly">Trở thành đại lý <ArrowRight size={16} style={{ marginLeft: '.3rem', verticalAlign: 'middle' }} /></a>
            <Link className="btn btn-secondary" to="/login">Cổng đăng nhập</Link>
          </div>
          <p className="pl-note">Trang này chỉ giới thiệu nền tảng — không bán bảo hiểm trực tiếp. Muốn mua, bạn vào qua link riêng của một đại lý.</p>
        </div>
        <div className="pl-hero-visual" ref={heroRef}>
          <div className="pl-hero-blur" aria-hidden />
          <div className="pl-hero-canvas">
            <ErrorBoundary pageName="khối 3D trang nền tảng">
              {glLost ? (
                <StaticShield />
              ) : (
                <Suspense fallback={<StaticShield />}>
                  <Shield3D height={420} />
                </Suspense>
              )}
            </ErrorBoundary>
          </div>
        </div>
      </section>

      <div className="pl-marquee" aria-hidden>
        <div className="pl-marquee-track">
          {[0, 1].map((copy) => (
            <React.Fragment key={copy}>
              <span>Mỗi đại lý một link riêng</span><span>✦</span>
              <span>Khách hàng gắn đúng đại lý</span><span>✦</span>
              <span>Thanh toán qua nền tảng</span><span>✦</span>
              <span>Hồ sơ bồi thường tập trung</span><span>✦</span>
            </React.Fragment>
          ))}
        </div>
      </div>

      <section className="pl-section" id="gioi-thieu">
        <Reveal>
          <h2>Nền tảng làm gì?</h2>
          <p className="pl-lead">Một hệ thống chung cho các đại lý bảo hiểm: bán hàng, quản lý khách hàng và hợp đồng, xử lý bồi thường — mỗi đại lý nhìn thấy và quản lý đúng phần của mình.</p>
        </Reveal>
        <div className="pl-grid">
          {BENEFITS.map((b, i) => (
            <Reveal key={b.title} delay={i * 0.08}>
              <div className="pl-card">
                <div className="pl-icon"><b.icon size={22} /></div>
                <h3>{b.title}</h3>
                <p>{b.desc}</p>
              </div>
            </Reveal>
          ))}
        </div>
      </section>

      <section className="pl-section" id="dai-ly">
        <Reveal>
          <h2>Trở thành đại lý</h2>
          <p className="pl-lead">Bạn muốn bán bảo hiểm trên nền tảng? Quy trình gồm ba bước — và bước đầu tiên là liên hệ quản trị viên.</p>
        </Reveal>
        <div className="pl-steps">
          {STEPS.map((s, i) => (
            <Reveal key={s.title} delay={i * 0.08}>
              <div className="pl-card">
                <div className="pl-step-num">{s.n}</div>
                <h3>{s.title}</h3>
                <p>{s.desc}</p>
              </div>
            </Reveal>
          ))}
        </div>
        {(PLATFORM_CONTACT_EMAIL || PLATFORM_CONTACT_ZALO) && (
          <div className="pl-contact" id="lien-he-dai-ly">
            {PLATFORM_CONTACT_EMAIL && <span><Mail size={16} /> {PLATFORM_CONTACT_EMAIL}</span>}
            {PLATFORM_CONTACT_ZALO && <span><MessageCircle size={16} /> Zalo: {PLATFORM_CONTACT_ZALO}</span>}
          </div>
        )}
        <p className="pl-note">Chưa có luồng tự đăng ký đại lý — mọi tài khoản đại lý đều do quản trị viên tạo sau xét duyệt.</p>
      </section>

      <section className="pl-section">
        <Reveal>
          <div className="pl-login">
            <div>
              <h2>Cổng đăng nhập</h2>
              <p>Khách hàng, đại lý và quản trị viên dùng tài khoản đã có để vào hệ thống.</p>
            </div>
            <Link className="btn btn-primary" to="/login" style={{ background: 'var(--gold-gradient)' }}>Đăng nhập ngay <ArrowRight size={16} style={{ marginLeft: '.3rem', verticalAlign: 'middle' }} /></Link>
          </div>
        </Reveal>
      </section>

      <section className="pl-section" id="ho-tro">
        <Reveal>
          <h2><HelpCircle size={24} style={{ verticalAlign: 'middle', marginRight: '.4rem' }} />Hỗ trợ</h2>
          <p className="pl-lead">Câu hỏi thường gặp — và cách liên hệ quản trị viên khi cần hỗ trợ thêm. Trang link đại lý không còn hoạt động cũng dẫn về đây.</p>
        </Reveal>
        <div className="pl-faq">
          {FAQS.map((f) => (
            <details key={f.q}>
              <summary>{f.q}</summary>
              <p>{f.a}</p>
            </details>
          ))}
        </div>
        <div className="pl-contact">
          <span><Mail size={16} /> {PLATFORM_CONTACT_EMAIL}</span>
          <span><MessageCircle size={16} /> Zalo: {PLATFORM_CONTACT_ZALO}</span>
        </div>
      </section>

      <section className="pl-section" id="dieu-khoan">
        <Reveal>
          <h2>Điều khoản</h2>
          <div className="pl-draft">
            <strong>Khung điều khoản — chờ duyệt.</strong> Nội dung dưới đây chỉ là khung nguyên tắc trung thực,
            chưa phải văn bản pháp lý cuối cùng và chưa bịa chi tiết pháp lý (mức phí, thời hạn, chế tài).
            User duyệt và bổ sung trước khi công bố chính thức.
          </div>
        </Reveal>
        <div className="pl-terms">
          <Reveal>
            <div className="pl-card">
              <div className="pl-icon"><ScrollText size={22} /></div>
              <h3>Điều khoản sử dụng (khung)</h3>
              <ul>
                <li>Nền tảng cung cấp hệ thống cho đại lý vận hành và bán hàng; giao dịch mua bán diễn ra qua link đại lý.</li>
                <li>Tài khoản đại lý do quản trị viên tạo sau xét duyệt; đại lý chịu trách nhiệm về nội dung và tư vấn của mình.</li>
                <li>Thanh toán được xử lý qua nền tảng và hiển thị minh bạch trên gian hàng đại lý.</li>
                <li>Vi phạm điều khoản có thể dẫn tới tạm ngưng hoặc đóng tài khoản theo quyết định của quản trị viên.</li>
              </ul>
            </div>
          </Reveal>
          <Reveal delay={0.08}>
            <div className="pl-card">
              <div className="pl-icon"><Lock size={22} /></div>
              <h3>Bảo mật (khung)</h3>
              <ul>
                <li>Thông tin khách hàng thuộc về đúng tenant đại lý phục vụ — đại lý chỉ thấy dữ liệu của mình.</li>
                <li>Thông tin liên hệ của đại lý chỉ hiển thị trên chính gian hàng của đại lý đó.</li>
                <li>Nền tảng không công khai danh bạ đại lý và không bán bảo hiểm trực tiếp trên trang này.</li>
                <li>Chi tiết về lưu trữ, thời hạn và quyền của người dùng sẽ được bổ sung khi văn bản chính thức được duyệt.</li>
              </ul>
            </div>
          </Reveal>
        </div>
        <p className="pl-note"><FileText size={14} style={{ verticalAlign: 'middle', marginRight: '.3rem' }} />Trang này không thay thế hợp đồng bảo hiểm hay điều khoản của từng sản phẩm cụ thể.</p>
      </section>

      <footer className="pl-footer">
        <span>{PLATFORM_NAME} — {PLATFORM_SLOGAN}</span>
        <span>
          <a href="#ho-tro">Hỗ trợ</a>{' · '}
          <a href="#dieu-khoan">Điều khoản</a>{' · '}
          <Link to="/login">Đăng nhập</Link>
        </span>
        <span>Thanh toán được xử lý qua {PLATFORM_NAME}.</span>
      </footer>
    </div>
  );
}
