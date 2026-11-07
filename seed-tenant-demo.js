// seed-tenant-demo.js — dữ liệu mẫu tenant-shaped để test các tính năng mới
// (luồng bồi thường UW-duyệt, role theo đại lý, thương lượng, gia hạn...).
//
// Chạy bằng mongosh trỏ vào database `insurance` trên Atlas:
//   mongosh "<SPRING_DATA_MONGODB_URI>" --file seed-tenant-demo.js
// (URI trong .env đã kết thúc bằng /insurance nên `db` chính là database đó.)
//
// Idempotent: find-or-create theo email/code/số hợp đồng — chạy lại không nhân
// bản. Tất cả mật khẩu: Password123!  |  admin@insurtech.vn cũng là ADMIN.

const NOW = new Date();
const DAY = 86400000;
const daysFromNow = (n) => new Date(NOW.getTime() + n * DAY);
// Hash BCrypt của "Password123!" (tái dùng từ seed-claim-policies.js)
const PWD = "$2b$10$T0fpFCHjw1wrfMkQgK9JHuHZ0RfDsLZbakijOqHc2TiL/XZepKMei";

print("== Seed tenant demo vào DB: " + db.getName() + " ==");

// ---------------------------------------------------------------- Agents
function upsertAgent(code, slug, displayName, contactEmail) {
  let a = db.agents.findOne({ code: code });
  if (!a) {
    a = {
      code: code, slug: slug, displayName: displayName,
      contactName: displayName, contactEmail: contactEmail,
      status: "ACTIVE", createdBy: "seed-tenant-demo",
      createdAt: NOW, updatedAt: NOW,
    };
    a._id = db.agents.insertOne(a).insertedId;
    print("  + agent " + code + " (" + slug + ")");
  }
  return db.agents.findOne({ code: code });
}
const agentAId = upsertAgent("AGT-DEMO-A", "dai-ly-a", "Đại Lý An Nhiên", "agent-a@insurtech.vn")._id.toString();
const agentBId = upsertAgent("AGT-DEMO-B", "dai-ly-b", "Đại Lý Bình Minh", "agent-b@insurtech.vn")._id.toString();
// LƯU Ý: agentId trong users/policies PHẢI là String hex của Agent._id (app
// ghi String; query findByAgentId(String) không khớp nếu lưu ObjectId).
const agentA = { _id: agentAId };
const agentB = { _id: agentBId };

// ---------------------------------------------------------------- Users
function upsertUser(email, fullName, roles, agentId) {
  const doc = {
    email: email, password: PWD, fullName: fullName,
    roles: roles, enabled: true, createdAt: NOW, updatedAt: NOW,
  };
  if (agentId) doc.agentId = agentId;
  const r = db.users.updateOne(
    { email: email }, { $setOnInsert: doc }, { upsert: true });
  if (r.upsertedCount > 0) print("  + user " + email + " [" + roles.join(",") + "]" + (agentId ? " @tenant" : ""));
  return db.users.findOne({ email: email });
}
upsertUser("admin@insurtech.vn", "Quản Trị Viên", ["ROLE_ADMIN"], null);
upsertUser("agent-a@insurtech.vn", "Đại Lý An Nhiên", ["ROLE_AGENT"], agentA._id);
upsertUser("agent-b@insurtech.vn", "Đại Lý Bình Minh", ["ROLE_AGENT"], agentB._id);
upsertUser("khach-a@insurtech.vn", "Nguyễn Văn An", ["ROLE_CUSTOMER"], null);
upsertUser("khach-b@insurtech.vn", "Trần Thị Bích", ["ROLE_CUSTOMER"], null);
upsertUser("uw@insurtech.vn", "Thẩm Định Toàn Hệ Thống", ["ROLE_UNDERWRITER"], null);
upsertUser("uw-a@insurtech.vn", "Thẩm Định An Nhiên", ["ROLE_UNDERWRITER"], agentA._id); // UW scoped theo đại lý A
upsertUser("finance@insurtech.vn", "Kế Toán Toàn Hệ Thống", ["ROLE_FINANCE"], null);
upsertUser("finance-a@insurtech.vn", "Kế Toán An Nhiên", ["ROLE_FINANCE"], agentA._id); // Kế toán scoped A

// ---------------------------------------------------------------- Policies
function coverage(code, name, type, limit, deductible, premium) {
  return { coverageCode: code, coverageName: name, coverageType: type,
    limit: limit, deductible: deductible, termMonths: 12, baseRate: 0.0015, premium: premium };
}

function upsertPolicy(p) {
  const existed = db.policies.findOne({ policyNumber: p.policyNumber });
  if (existed) return existed;
  db.policies.insertOne(p);
  // Baseline version v1 (tính năng versions/history/endorsement cần)
  db.policy_versions.insertOne({
    policyNumber: p.policyNumber, version: 1, policySnapshot: p,
    endorsementReason: "Initial Policy Baseline (v1)", endorsementType: "BASELINE",
    endorsementEffectiveDate: p.effectiveDate, premiumChange: 0.0,
    totalPremium: p.totalPremium, status: p.status, createdBy: "seed-tenant-demo",
    createdAt: p.createdAt,
  });
  // Ledger CREATE_POLICY (tính năng timeline + lịch sử)
  db.policy_transactions.insertOne({
    policyNumber: p.policyNumber, version: 1, transactionType: "CREATE_POLICY",
    actor: "seed-tenant-demo", description: "Tạo hợp đồng mẫu (seed)",
    timestamp: p.createdAt,
  });
  print("  + policy " + p.policyNumber + " [" + p.status + "]");
  return p;
}

function basePolicy(number, status, agentId, insured, extras) {
  return Object.assign({
    policyNumber: number, status: status, agentId: agentId,
    insured: insured,
    locations: [{
      locationId: 1, address: insured.address,
      coverages: [
        coverage("FIRE-01", "Cháy nổ & sét đánh", "STANDARD", 500000000, 10000000, 750000),
        coverage("WATER-02", "Ngập nước & thiên tai", "ENHANCED", 200000000, 5000000, 200000),
      ],
    }],
    totalPremium: 950000, paymentStatus: "UNCLAIMED",
    effectiveDate: daysFromNow(-30), expirationDate: daysFromNow(335),
    version: 1, createdAt: NOW, updatedAt: NOW,
  }, extras || {});
}

// Đại lý A — khách A
upsertPolicy(basePolicy("POL-A-001", "ACTIVE", agentA._id,
  { insuredId: "INS-A1", name: "Công ty TNHH An Phát", type: "BUSINESS",
    email: "khach-a@insurtech.vn", phone: "0901234567",
    address: "12 Nguyễn Huệ, Quận 1, TP.HCM" },
  { paymentStatus: "CONFIRMED" })); // claim + tài liệu + chứng nhận

upsertPolicy(basePolicy("POL-A-002", "ACTIVE", agentA._id,
  { insuredId: "INS-A2", name: "Kho Hàng An Nhiên", type: "BUSINESS",
    email: "khach-a@insurtech.vn", phone: "0901234567",
    address: "45 Khu Phố 3, Thủ Đức, TP.HCM" },
  { paymentStatus: "CONFIRMED", effectiveDate: daysFromNow(-345),
    expirationDate: daysFromNow(20) })); // sắp hết hạn 20 ngày → cảnh báo gia hạn

upsertPolicy(basePolicy("POL-A-003", "QUOTED", agentA._id,
  { insuredId: "INS-A3", name: "Showroom An Tâm", type: "BUSINESS",
    email: "khach-a@insurtech.vn", phone: "0901234567",
    address: "88 Trần Hưng Đạo, Quận 5, TP.HCM" },
  { totalPremium: 750000 })); // thương lượng giá + thanh toán SePay

upsertPolicy(basePolicy("POL-A-004", "DRAFT", agentA._id,
  { insuredId: "INS-A4", name: "Nhà Máy An Cư", type: "BUSINESS",
    email: "khach-a@insurtech.vn", phone: "0901234567",
    address: "9 Lê Văn Việt, Thủ Đức, TP.HCM" })); // sửa/xoá nháp trong wizard

// Đại lý B — khách B (test tách bạch tenant)
upsertPolicy(basePolicy("POL-B-001", "ACTIVE", agentB._id,
  { insuredId: "INS-B1", name: "Cửa Hàng Bình Yên", type: "INDIVIDUAL",
    email: "khach-b@insurtech.vn", phone: "0912345678",
    address: "21 Bạch Đằng, Quận 2, TP.HCM" },
  { paymentStatus: "CONFIRMED" })); // claim test tenant-isolation

upsertPolicy(basePolicy("POL-B-002", "BOUND", agentB._id,
  { insuredId: "INS-B2", name: "Xưởng Bình Tân", type: "BUSINESS",
    email: "khach-b@insurtech.vn", phone: "0912345678",
    address: "7 Tên Lửa, Bình Tân, TP.HCM" },
  { boundDate: NOW, paymentDueDate: daysFromNow(7), totalPremium: 950000 })); // luồng khai báo chuyển khoản

// ---------------------------------------------------------------- Claims
function history(from, to, by, note) {
  return { id: UUID().toString(), fromStatus: from, toStatus: to,
    changedBy: by, changedAt: NOW, note: note };
}
function upsertClaim(id, policyNumber, claimant, amount, status, extra) {
  if (db.claims.findOne({ _id: id })) return;
  const hist = [history(null, "SUBMITTED", claimant, "Khởi tạo yêu cầu bồi thường (seed)")];
  if (status !== "SUBMITTED") {
    hist.push(history("SUBMITTED", "UNDER_REVIEW", "uw@insurtech.vn", "Tiếp nhận thẩm định (seed)"));
  }
  const doc = Object.assign({
    _id: id, policyNumber: policyNumber, claimantEmail: claimant,
    damageDescription: "Tai nạn xảy ra lúc 14h: ngập nước sau mưa lớn làm hư hỏng hàng hoá và thiết bị tại địa điểm được bảo hiểm.",
    claimedAmount: amount, status: status,
    photos: [], statusHistory: hist,
    createdAt: NOW, updatedAt: NOW,
  }, extra || {});
  db.claims.insertOne(doc);
  print("  + claim " + id + " [" + status + "] " + policyNumber);
}
// Đại lý A: test intake (SUBMITTED) và UW duyệt/từ chối (UNDER_REVIEW)
upsertClaim("claim-a-1", "POL-A-001", "khach-a@insurtech.vn", 20000000, "SUBMITTED");
upsertClaim("claim-a-2", "POL-A-001", "khach-a@insurtech.vn", 35000000, "UNDER_REVIEW");
// Đại lý B: test tách bạch tenant (uw-a chỉ được xử lý claim của đại lý A)
upsertClaim("claim-b-1", "POL-B-001", "khach-b@insurtech.vn", 12000000, "UNDER_REVIEW");
// Đã duyệt chờ kế toán chi trả
upsertClaim("claim-b-2", "POL-B-001", "khach-b@insurtech.vn", 15000000, "APPROVED",
  { approvedAmount: 12000000,
    handlerNote: "Duyệt 12 triệu trong hạn mức quyền lợi (seed)" });

// ---------------------------------------------------------------- Negotiation
if (!db.price_negotiations.findOne({ policyNumber: "POL-A-003" })) {
  const offer = {
    round: 1, proposedPremium: 600000,
    proposedByEmail: "khach-a@insurtech.vn", proposedByRole: "CUSTOMER",
    message: "Bên em mua nhiều hợp đồng rồi, giảm còn 600k được không ạ?",
    decision: "PROPOSED", createdAt: NOW,
  };
  db.price_negotiations.insertOne({
    _id: "neg-a-003", policyNumber: "POL-A-003",
    status: "OPEN", listPremium: 750000, floorPremium: null,
    currentOffer: offer, offers: [offer], messages: [],
    openedBy: "khach-a@insurtech.vn", openedAt: NOW,
    version: 0, createdAt: NOW,
  });
  print("  + negotiation OPEN trên POL-A-003 (đề xuất 600k / list 750k)");
}

print("== Seed xong ==");
print("Tài khoản (mật khẩu tất cả: Password123!):");
print("  admin@insurtech.vn   ADMIN toàn hệ thống");
print("  agent-a@insurtech.vn AGENT đại lý A   | agent-b@insurtech.vn AGENT đại lý B");
print("  khach-a@insurtech.vn CUSTOMER (A)     | khach-b@insurtech.vn CUSTOMER (B)");
print("  uw@insurtech.vn      UNDERWRITER global | uw-a@insurtech.vn UW scoped đại lý A");
print("  finance@insurtech.vn FINANCE global   | finance-a@insurtech.vn FINANCE scoped A");
