/**
 * ============================================================================
 * KHÔI PHỤC & NÂNG CẤP TRACKING — TEAM 1 · INSURANCE POLICY MANAGEMENT SYSTEM
 * ============================================================================
 * Cách chạy:
 *   1. Mở file Google Sheets → Tiện ích (Extensions) → Apps Script
 *   2. Xoá toàn bộ code mẫu, dán toàn bộ file này vào
 *   3. Nhấn Lưu (Ctrl+S) → chọn hàm "restorePhase2" → nhấn Run ▶
 *   4. Google hỏi quyền → Review permissions → chọn tài khoản →
 *      "Advanced" → "Go to ... (unsafe)" → Allow (script cá nhân, chỉ sửa
 *      đúng file sheet này)
 *   5. Chạy xong sẽ hiện toast "✅ Đã khôi phục tracking Phase 2"
 *
 * Script idempotent — chạy lại nhiều lần không gây trùng lặp.
 * Nó làm 3 việc:
 *   A. Requirement Tracking: khôi phục I01–I10 (bị #ERROR!), gắn công thức
 *      đếm cho N01–N10, merge + màu băng nhóm, dropdown, format %
 *   B. Dashboard: khôi phục TOÀN BỘ công thức (đã mở rộng vùng đếm tới
 *      hàng 120/60 để phủ task Phase 2), milestone M5–M8 có công thức,
 *      dropdown + conditional màu cho Status, sửa nền đậm hàng M7
 *   C. Task Breakdown: merge + màu băng DAY 11–17, dropdown
 *      Assignee/Status, công thức WORKDAY cho ngày Plan/Actual
 * ============================================================================
 */

function restorePhase2() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const tb = ss.getSheetByName('Task Breakdown');
  const rt = ss.getSheetByName('Requirement Tracking');
  const db = ss.getSheetByName('Dashboard');
  if (!tb || !rt || !db) throw new Error('Không tìm thấy đủ 3 sheet!');

  const BAND = '#d9e1f2';   // màu băng nhóm DAY / group header
  const DARK = '#1f3864';   // màu chữ/chền header đậm
  // Vùng đếm MỞ RỘNG: task Phase 2 nằm ở hàng 65–106 (vượt quá $79 của bản gốc)
  const TD = "'Task Breakdown'!$D$6:$D$120";
  const TL = "'Task Breakdown'!$L$6:$L$120";
  const TB = "'Task Breakdown'!$B$6:$B$120";
  const TE = "'Task Breakdown'!$E$6:$E$120";
  const TH = "'Task Breakdown'!$H$6:$H$120";
  const RA = "'Requirement Tracking'!$A$6:$A$60";
  const RG = "'Requirement Tracking'!$G$6:$G$60";

  // ==========================================================================
  // 1. REQUIREMENT TRACKING
  // ==========================================================================

  // 1a. Khôi phục hàng 5–15: FUNCTIONAL header + I01–I10 (bị #ERROR!)
  rt.getRange('A5:H17').breakApart();
  const reqRows = [
    ['FUNCTIONAL REQUIREMENTS (I01 – I12)', '', '', '', '', '', '', ''],
    ['I01', 'Policy core CRUD', 'Create, retrieve, update, delete Policy (delete only DRAFT/CANCELLED, by Admin)', '', '', '', '', 'P01, POST/GET/PUT/DELETE /policies'],
    ['I02', 'Multi-level entities', 'Embedded Location and Coverage hierarchy with atomic $push/$pull and auto-recalculated premium', '', '', '', '', 'P02-P04, /policies/{id}/locations, /coverages'],
    ['I03', 'Dynamic search', 'Combined filters for status, date, keyword, and nested $elemMatch on coverages with paging and sorting', '', '', '', '', 'P05, GET /policies criteria & $elemMatch'],
    ['I04', 'State machine', 'Validate strict lifecycle DRAFT -> QUOTED -> BOUND -> ACTIVE -> CANCELLED/EXPIRED and role permissions', '', '', '', '', 'P06, POST /policies/{id}/status-transitions'],
    ['I05', 'Payment & activation', 'Payment due date calculation, VietQR code modal, Customer payment simulation and Admin counter activation', '', '', '', '', 'P07, PaymentDue 7 days, QR simulation'],
    ['I06', 'Endorsement engine', 'Endorse ACTIVE policy, automatic immutable snapshot creation in policy_versions and version increment', '', '', '', '', 'P08, POST /policies/{id}/endorsements'],
    ['I07', 'Transaction ledger', 'Real-time immutable audit log in policy_transactions recording actor, type, version, and timestamp', '', '', '', '', 'P09, GET /policies/{id}/history'],
    ['I08', 'Version comparison', 'Visual diff comparison between two policy version snapshots (coverages, limits, deductibles)', '', '', '', '', 'P08, GET /policies/{id}/versions/compare'],
    ['I09', 'Excel pricing matrix', 'Export pricing matrix template, upload & parse Excel rates via Apache POI, dynamic base rate updates', '', '', '', '', 'P10, /excel/template, /excel/import-rates'],
    ['I10', 'Aggregation analytics', '4 analytical aggregation pipelines (status counts, premium volume, top policies, monthly trends)', '', '', '', '', 'P11, GET /reports/* with Recharts'],
  ];
  rt.getRange('A5:H15').setValues(reqRows);

  // Công thức D6:G15 (đếm theo mã requirement, range mở rộng)
  const reqFormulas = [];
  for (let r = 6; r <= 15; r++) {
    reqFormulas.push([
      `=COUNTIF(${D},"*"&A${r}&"*")`,
      `=COUNTIFS(${D},"*"&A${r}&"*",${L},"Done")`,
      `=IF(D${r}=0,0,E${r}/D${r})`,
      `=IF(D${r}=0,"Not Started",IF(E${r}=D${r},"Done",IF(COUNTIFS(${D},"*"&A${r}&"*",${L},"Not Started")=D${r},"Not Started","In Progress")))`,
    ]);
  }
  rt.getRange('D6:G15').setFormulas(reqFormulas);

  // Format: băng nhóm, ID đậm, %, dropdown Status
  rt.getRange('A5:H5').merge().setBackground(BAND).setFontColor(DARK).setFontWeight('bold');
  rt.getRange('F6:F17').setNumberFormat('0%');
  rt.getRange('A6:A17').setFontColor(DARK).setFontWeight('bold');
  rt.getRange('G6:G17').setDataValidation(SpreadsheetApp.newDataValidation()
    .requireValueInList(['Done', 'In Progress', 'Blocked', 'Not Started'], true).build());

  // 1b. Khối Phase 2 rows 41–51: merge header + công thức cho N01–N10
  rt.getRange('A41:H51').breakApart();
  rt.getRange('A41:H41').merge().setBackground(BAND).setFontColor(DARK).setFontWeight('bold');
  rt.getRange('F42:F51').setNumberFormat('0%');
  const nFormulas = [];
  for (let r = 42; r <= 51; r++) {
    nFormulas.push([
      `=COUNTIF(${D},"*"&A${r}&"*")`,
      `=COUNTIFS(${D},"*"&A${r}&"*",${L},"Done")`,
      `=IF(D${r}=0,0,E${r}/D${r})`,
      `=IF(D${r}=0,"Not Started",IF(E${r}=D${r},"Done",IF(COUNTIFS(${D},"*"&A${r}&"*",${L},"Not Started")=D${r},"Not Started","In Progress")))`,
    ]);
  }
  rt.getRange('D42:G51').setFormulas(nFormulas);
  rt.getRange('G42:G51').setDataValidation(SpreadsheetApp.newDataValidation()
    .requireValueInList(['Done', 'In Progress', 'Blocked', 'Not Started'], true).build());
  rt.getRange('A42:A51').setFontColor(DARK).setFontWeight('bold');

  // ==========================================================================
  // 2. DASHBOARD
  // ==========================================================================

  // B5: deadline = WORKDAY(start, 9)
  db.getRange('B5').setFormula('=WORKDAY(B4,9)');

  // OVERVIEW row 9 — công thức với range mở rộng
  db.getRange('A9:F9').setFormulas([[
    `=COUNTA(${TB.replace(/\$/g, '$')})`,
    `=COUNTIF(${TL},"Done")`,
    `=COUNTIF(${TL},"In Progress")`,
    `=COUNTIF(${TL},"Blocked")`,
    `=COUNTIFS(${TB},">0",${TH},"<"&TODAY(),${TL},"<>Done")`,
    '=IF(A9=0,0,B9/A9)',
  ]]);

  // PROGRESS BY MEMBER rows 13–15
  const memberFormulas = [];
  for (let r = 13; r <= 15; r++) {
    memberFormulas.push([
      `=COUNTIF(${TE},A${r})`,
      `=COUNTIFS(${TE},A${r},${TL},"Done")`,
      `=COUNTIFS(${TE},A${r},${TL},"In Progress")`,
      `=B${r}-C${r}`,
      `=IF(B${r}=0,0,C${r}/B${r})`,
    ]);
  }
  db.getRange('B13:F15').setFormulas(memberFormulas);
  db.getRange('F13:F15').setNumberFormat('0%');

  // Milestones M1–M4 (rows 19–22) + M5–M8 (rows 23–26)
  // [row, dayFrom, dayTo, workdayOffset, bFormula (null = giữ giá trị ngày tĩnh)]
  const milestones = [
    [19, 1, 2, 1, '=WORKDAY($B$4,1)'],
    [20, 3, 6, 5, '=WORKDAY($B$4,5)'],
    [21, 7, 9, 8, '=WORKDAY($B$4,8)'],
    [22, 10, 10, 9, '=WORKDAY($B$4,9)'],
    [23, 11, 12, 10, null],
    [24, 13, 13, 12, null],
    [25, 14, 14, 13, null],
    [26, 15, 17, 16, null],
  ];
  milestones.forEach(([r, d1, d2, off, bF]) => {
    if (bF) db.getRange('B' + r).setFormula(bF);
    db.getRange('C' + r + ':F' + r).setFormulas([[
      `=COUNTIFS(${TB},">=${d1}",${TB},"<=${d2}")`,
      `=COUNTIFS(${TB},">=${d1}",${TB},"<=${d2}",${TL},"Done")`,
      `=IF(C${r}=0,0,D${r}/C${r})`,
      `=IF(D${r}=C${r},"Done",IF(B${r}<TODAY(),"Overdue","In Progress"))`,
    ]]);
  });
  db.getRange('E19:E26').setNumberFormat('0%');

  // Status M5–M8: dropdown + conditional màu (như M1–M4)
  db.getRange('F23:F26').setDataValidation(SpreadsheetApp.newDataValidation()
    .requireValueInList(['Done', 'In Progress', 'Overdue', 'Blocked', 'Not Started'], true).build());

  // Nhóm Requirement rows 30–35: công thức đếm theo pattern (range mở rộng)
  const groups = [
    ['Functional (I)', 'I??'],
    ['General technical (S)', 'S??'],
    ['Deliverables (D)', 'D??'],
    ['Management (M)', 'M??'],
    ['Team additions (A, U)', null],
    ['Upgrade (N)', 'N??'],
  ];
  groups.forEach(([label, pat], i) => {
    const r = 30 + i;
    db.getRange('A' + r).setValue(label);
    let bF, cF;
    if (pat) {
      bF = `=COUNTIF(${RA},"${pat}")`;
      cF = `=COUNTIFS(${RA},"${pat}",${RG},"Done")`;
    } else {
      bF = `=COUNTIF(${RA},"A??")+COUNTIF(${RA},"U??")`;
      cF = `=COUNTIFS(${RA},"A??",${RG},"Done")+COUNTIFS(${RA},"U??",${RG},"Done")`;
    }
    db.getRange('B' + r + ':C' + r).setFormulas([[bF, cF]]);
    db.getRange('D' + r).setFormula(`=IF(B${r}=0,0,C${r}/B${r})`);
  });
  db.getRange('D30:D35').setNumberFormat('0%');

  // Format section REQUIREMENT (row 28 tiêu đề, row 29 header đậm)
  db.getRange('A28').setFontWeight('bold');
  db.getRange('A29:F29').setBackground(DARK).setFontColor('#ffffff').setFontWeight('bold');

  // Sửa nền đậm thừa kế ở hàng M7 (row 25)
  db.getRange('A25:F25').setBackground(null);

  // Conditional màu cho Status M5–M8
  const fRange = db.getRange('F23:F26');
  const statusColors = [
    ['Done', '#d9ead3'],
    ['In Progress', '#fff2cc'],
    ['Blocked', '#f4cccc'],
    ['Overdue', '#f4cccc'],
    ['Not Started', '#efefef'],
  ];
  const cfRules = statusColors.map(([v, c]) =>
    SpreadsheetApp.newConditionalFormatRule()
      .whenTextEqualTo(v).setBackground(c).setRanges([fRange]).build());
  db.setConditionalFormatRules(db.getConditionalFormatRules().concat(cfRules));

  // ==========================================================================
  // 3. TASK BREAKDOWN
  // ==========================================================================

  // DAY header rows Phase 2: merge A:F + màu băng + H = WORKDAY(offset)
  const dayRows = { 65: 10, 70: 11, 77: 12, 82: 13, 92: 14, 96: 15, 100: 16 };
  Object.entries(dayRows).forEach(([r, off]) => {
    const row = Number(r);
    tb.getRange(`A${row}:F${row}`).breakApart().merge();
    tb.getRange(`A${row}:L${row}`).setBackground(BAND).setFontColor(DARK).setFontWeight('bold');
    tb.getRange(`H${row}`).setFormula(`=WORKDAY(Dashboard!$B$4,${off})`);
  });

  // Gán lại Requirement cho 2 task để nhóm N09 được đếm đúng
  tb.getRange('D68').setValue('N09, U01');           // T68 UX hardening
  tb.getRange('D72').setValue('N09, D02, D07');      // T72 Handbook + dashboard

  // Task rows Phase 2: G/H = WORKDAY theo cột Day (đồng bộ convention bản gốc)
  const taskRows = [];
  for (let r = 66; r <= 69; r++) taskRows.push(r);   // DAY 11
  for (let r = 71; r <= 76; r++) taskRows.push(r);   // DAY 12
  for (let r = 78; r <= 81; r++) taskRows.push(r);   // DAY 13
  for (let r = 83; r <= 91; r++) taskRows.push(r);   // DAY 14
  for (let r = 93; r <= 95; r++) taskRows.push(r);   // DAY 15
  for (let r = 97; r <= 99; r++) taskRows.push(r);   // DAY 16
  for (let r = 101; r <= 105; r++) taskRows.push(r); // DAY 17
  taskRows.forEach(r => {
    tb.getRange(`G${r}`).setFormula(`=WORKDAY(Dashboard!$B$4,B${r}-1)`);
    tb.getRange(`H${r}`).setFormula(`=WORKDAY(Dashboard!$B$4,B${r}-1)`);
  });

  // Dropdown Assignee + Status cho task Phase 2
  tb.getRange('E66:E105').setDataValidation(SpreadsheetApp.newDataValidation()
    .requireValueInList(['Huy', 'Dũng', 'Team'], true).build());
  tb.getRange('L66:L105').setDataValidation(SpreadsheetApp.newDataValidation()
    .requireValueInList(['Not Started', 'In Progress', 'Done', 'Blocked'], true).build());

  // TOTAL PHASE 2 row 106: merge A:E + màu băng + format số
  tb.getRange('A106:E106').breakApart().merge();
  tb.getRange('A106:L106').setBackground(BAND).setFontWeight('bold');
  tb.getRange('F106:I106').setNumberFormat('0.0');

  // Conditional màu cho Assignee/Status vùng Phase 2 (giống vùng cũ)
  const eRange = tb.getRange('E66:E105');
  const lRange = tb.getRange('L66:L105');
  const tbRules = [
    ['Dũng', '#d9ead3', eRange], ['Huy', '#cfe2f3', eRange], ['Team', '#f9cb9c', eRange],
    ['Done', '#d9ead3', lRange], ['In Progress', '#fff2cc', lRange],
    ['Blocked', '#f4cccc', lRange], ['Not Started', '#efefef', lRange],
  ].map(([v, c, rng]) =>
    SpreadsheetApp.newConditionalFormatRule()
      .whenTextEqualTo(v).setBackground(c).setRanges([rng]).build());
  tb.setConditionalFormatRules(tb.getConditionalFormatRules().concat(tbRules));

  SpreadsheetApp.getActive().toast(
    '✅ Đã khôi phục tracking Phase 2 — kiểm tra 3 tab Dashboard / Task Breakdown / Requirement Tracking!',
    'Hoàn tất', 10
  );
}
