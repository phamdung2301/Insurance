package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.CoverageDto;
import com.dungphd.insuranceass.dto.InsuredDto;
import com.dungphd.insuranceass.dto.LocationDto;
import com.dungphd.insuranceass.dto.request.CreatePolicyRequest;
import com.dungphd.insuranceass.dto.request.PolicySearchCriteria;
import com.dungphd.insuranceass.dto.request.StatusTransitionRequest;
import com.dungphd.insuranceass.dto.request.UpdatePolicyRequest;
import com.dungphd.insuranceass.dto.response.PageResponse;
import com.dungphd.insuranceass.dto.response.PolicyResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.AgentLinkInactiveException;
import com.dungphd.insuranceass.exception.DuplicateResourceException;
import com.dungphd.insuranceass.exception.ForbiddenException;
import com.dungphd.insuranceass.exception.InvalidRequestException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.*;
import com.dungphd.insuranceass.repository.AgentRepository;
import com.dungphd.insuranceass.repository.PolicyRepository;
import com.dungphd.insuranceass.repository.PolicyTransactionRepository;
import com.dungphd.insuranceass.repository.PriceNegotiationRepository;
import com.dungphd.insuranceass.service.NotificationService;
import com.dungphd.insuranceass.service.AgentSlug;
import com.dungphd.insuranceass.service.RefundService;
import com.dungphd.insuranceass.service.PermissionService;
import com.dungphd.insuranceass.service.PolicyService;
import com.dungphd.insuranceass.service.PremiumCalculationService;
import com.dungphd.insuranceass.service.TransactionLedgerService;
import com.dungphd.insuranceass.service.TransactionSideEffects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyServiceImpl implements PolicyService {

    private final PolicyRepository policyRepository;
    private final MongoTemplate mongoTemplate;
    private final PremiumCalculationService premiumCalculationService;
    private final PolicyTransactionRepository policyTransactionRepository;
    private final TransactionLedgerService transactionLedgerService;

    /**
     * Optional để test unit cũ (dùng @InjectMocks không có mock này) vẫn chạy:
     * production Spring luôn inject bean thật; khi null thì các check phạm vi/khóa
     * được bỏ qua (gọi nội bộ/test), không phá luồng cũ.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PermissionService permissionService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PriceNegotiationRepository negotiationRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private NotificationService notificationService;

    // SaaS Phase 1 (optional như các service phụ khác): chỉ dùng để (a) kiểm
    // tra agentId ADMIN chọn khi tạo hộ có tồn tại không, (b) resolve agentName
    // cho PolicyResponse. Null trong test unit cũ → bỏ qua cả hai, không fail.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AgentRepository agentRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RefundService refundService;

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;

    public static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt",
            "updatedAt",
            "policyNumber",
            "status",
            "totalPremium",
            "effectiveDate",
            "expirationDate",
            "boundDate",
            "paymentDueDate",
            "version"
    );

    @Override
    public PolicyResponse createPolicy(CreatePolicyRequest request) {
        // Đợt 1C: UNDERWRITER và FINANCE không được tạo hợp đồng (chỉ xem/xử lý
        // hợp đồng đã có). Gate đặt TRƯỚC duplicate-check để 403 luôn thắng 409
        // và không lộ policyNumber đã tồn tại. ADMIN được miễn kể cả khi mang
        // đồng thời nhiều role (vd. seed admin mang ROLE_ADMIN + ROLE_USER, hoặc
        // admin kiêm UW/FINANCE). permissionService == null (gọi nội bộ/test unit
        // cũ không set context) thì bỏ qua gate, đúng quy ước null-guard sẵn có
        // của method này. Chỉ chặn overload nhận CreatePolicyRequest này;
        // overload createPolicy(Policy) nội bộ KHÔNG chặn (xem ghi chú ở đó).
        if (permissionService != null && !permissionService.isAdmin()) {
            if (permissionService.isUnderwriter()) {
                throw new ForbiddenException("Vai trò UNDERWRITER không được tạo hợp đồng.");
            }
        }
        if (policyRepository.existsByPolicyNumber(request.getPolicyNumber())) {
            throw new DuplicateResourceException("Số hợp đồng " + request.getPolicyNumber() + " đã tồn tại. Vui lòng dùng số khác.");
        }
        Instant effectiveDate = request.getEffectiveDate() != null ? request.getEffectiveDate() : Instant.now();
        Instant expirationDate = request.getExpirationDate() != null ? request.getExpirationDate() : effectiveDate.plus(365, java.time.temporal.ChronoUnit.DAYS);

        if (effectiveDate.isAfter(expirationDate)) {
            throw new InvalidRequestException("Ngày hiệu lực phải trước ngày hết hạn.");
        }
        // Ép theo role (PLAN §1.5): CUSTOMER không tạo hộ người khác; AGENT tạo hộ
        // nhưng assignedAgentEmail luôn là mình. UW/FINANCE/ADMIN giữ nguyên request.
        Insured insured = mapToInsured(request.getInsured());
        String assignedAgentEmail = null;
        if (permissionService != null) {
            String principal = permissionService.currentPrincipal();
            // ADMIN không bị ép (tài khoản admin seed mang cả ROLE_ADMIN + ROLE_USER,
            // nếu check hasCustomerRole trước sẽ ép nhầm insured về email admin).
            // Mang đồng thời CUSTOMER + AGENT thì ép theo CUSTOMER (như applySearchScope).
            if (principal != null && !permissionService.isAdmin()) {
                if (permissionService.hasCustomerRole()) {
                    if (insured == null) {
                        insured = Insured.builder().email(principal.trim().toLowerCase()).build();
                    } else {
                        insured.setEmail(principal.trim().toLowerCase());
                    }
                } else if (permissionService.hasAgentRole()) {
                    assignedAgentEmail = principal.trim().toLowerCase();
                }
            }
        }
        // SaaS Phase 2 Track C: quy tắc mua mới BẮT BUỘC có đại lý (Q3, thay
        // đổi hành vi cũ có chủ đích — khách vãng lai không còn mua được):
        // - CUSTOMER tự mua (kể cả phía CUSTOMER của dual-role): BẮT BUỘC có
        //   agentSlug hợp lệ từ link; server tra Agent theo slug, chỉ chấp
        //   nhận ACTIVE (không tồn tại → 404, SUSPENDED/CLOSED → 410 Gone),
        //   rồi gắn agentId từ Agent đó. Mọi agentId client gửi đều bị bỏ qua.
        //   Lưu ý: KHÔNG set assignedAgentEmail từ contactEmail của Agent ở
        //   đây — sau cutover Track B, agentId là nguồn sự thật duy nhất của
        //   tenant; assignedAgentEmail chỉ còn là dấu vết dual-write của luồng
        //   AGENT tạo hộ (principal = chính agent đó).
        // - AGENT tạo hộ (thuần, không mang CUSTOMER): như Track B — tenant
        //   từ context thắng, agentSlug gửi kèm (nếu có) bị bỏ qua; agent
        //   chưa gán tenant → fail-closed 403, tenant SUSPENDED/CLOSED → 403
        //   theo guard trạng thái (checkTenantWriteForStaff).
        // - ADMIN tạo hộ: agentId tường minh (nếu có) thắng — validate tồn
        //   tại, nếu không → 400; thiếu agentId thì resolve agentSlug như
        //   CUSTOMER (wizard/storefront chỉ gửi slug qua agentRef — ADMIN
        //   đi qua wizard cũng vậy, không có field agentId để gửi). Thiếu
        //   cả hai → 400 hướng tới hành động chọn đại lý. Đây là thay đổi
        //   so với Phase 1/Track B (trước đây ADMIN được để trống → null,
        //   policy unassigned lặng); và là fix cho bug wizard ADMIN chọn
        //   đại lý rồi mà submit vẫn 400 vì nhánh ADMIN cũ bỏ qua slug.
        // - Role khác (không ADMIN/CUSTOMER/AGENT, vd FINANCE): giữ hành vi cũ
        //   (agentId null) — không phải luồng mua, không thuộc ma trận Track C.
        // Khi permissionService == null (gọi nội bộ/test unit cũ không set
        // context) thì toàn bộ khối này bị bỏ qua như quy ước null-guard sẵn
        // có của method; khi agentRepository == null (test unit cũ không
        // inject repo) thì nhánh CUSTOMER cũng bỏ qua resolve slug — production
        // luôn inject bean thật nên không đi đường này.
        String agentId = null;
        if (permissionService != null) {
            if (permissionService.isAdmin()) {
                agentId = request.getAgentId() != null && !request.getAgentId().isBlank()
                        ? request.getAgentId().trim() : null;
                if (agentId != null) {
                    // agentId tường minh (tooling/admin API): chỉ cần kiểm
                    // tra tồn tại; slug gửi kèm (nếu có) bị bỏ qua.
                    if (agentRepository != null && !agentRepository.existsById(agentId)) {
                        throw new BadRequestException(
                                "Đại lý đã chọn không còn tồn tại. Hãy chọn một đại lý khác.");
                    }
                } else {
                    // Wizard/storefront chỉ gửi agentSlug (agentRef): resolve
                    // như CUSTOMER (chỉ ACTIVE; không tồn tại → 404,
                    // SUSPENDED/CLOSED → 410; thiếu cả hai → 400 hướng tới
                    // hành động chọn đại lý, trong resolveCustomerAgentId).
                    // Id resolve từ slug đã được findBySlug validate nên
                    // không cần existsById thêm một lần nữa.
                    agentId = resolveCustomerAgentId(request.getAgentSlug());
                }
            } else if (permissionService.hasCustomerRole()) {
                agentId = resolveCustomerAgentId(request.getAgentSlug());
            } else if (permissionService.hasAgentRole()) {
                permissionService.checkTenantWriteForStaff();
                agentId = permissionService.currentTenantId();
            }
        }
        Policy policy = Policy.builder()
                .policyNumber(request.getPolicyNumber())
                .status(PolicyStatus.DRAFT)
                .agentId(agentId)
                .assignedAgentEmail(assignedAgentEmail)
                .insured(insured)
                .locations(request.getLocations() != null ?
                        request.getLocations().stream().map(this::mapToLocation).collect(Collectors.toList()) : new ArrayList<>())
                .effectiveDate(effectiveDate)
                .expirationDate(expirationDate)
                .version(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        policy.recalculateTotalPremium();
        Policy savedPolicy = policyRepository.save(policy);

        // Đợt transaction/ACID: ledger là audit ghi SAU commit — fail của nó
        // không được biến policy đã tạo thành 500 (client tưởng fail và có thể
        // tạo lại). Pattern failure-isolated đã có ở webhook/negotiation.
        // deletePolicy giữ nguyên không bọc vì ledger ở đó ghi TRƯỚC delete
        // (fail-closed đúng).
        try {
            transactionLedgerService.recordTransaction(
                    savedPolicy.getPolicyNumber(),
                    savedPolicy.getVersion(),
                    TransactionType.CREATE_POLICY,
                    "System",
                    "Created new insurance policy in DRAFT status"
            );
        } catch (Exception e) {
            log.warn("Failed to record ledger for created policy {}: {}",
                    savedPolicy.getPolicyNumber(), e.getMessage());
        }

        return mapToPolicyResponse(savedPolicy);
    }

    // Overload nội bộ (seed/benchmark): KHÔNG áp gate UW/FINANCE của đợt 1C.
    // Lý do: grep toàn codebase cho thấy KHÔNG có caller production nào gọi
    // overload này — chỉ có khai báo trong PolicyService interface và impl này;
    // controller duy nhất (PolicyController.createPolicy) gọi overload nhận
    // CreatePolicyRequest. Chặn overload nội bộ sẽ phá seed/benchmark mà không
    // bịt thêm lỗ hổng request nào, vì mọi request client đều qua overload kia.
    @Override
    public Policy createPolicy(Policy policy) {
        if (policy == null) {
            throw new BadRequestException("Dữ liệu hợp đồng không hợp lệ.");
        }

        if (policy.getPolicyNumber() == null || policy.getPolicyNumber().isBlank()) {
            policy.setPolicyNumber("POL-" + System.currentTimeMillis());
        }

        if (policyRepository.existsByPolicyNumber(policy.getPolicyNumber())) {
            throw new BadRequestException("Số hợp đồng " + policy.getPolicyNumber() + " đã tồn tại.");
        }

        if (policy.getStatus() == null) {
            policy.setStatus(PolicyStatus.DRAFT);
        }

        policy.recalculateTotalPremium();
        return policyRepository.save(policy);
    }

    @Override
    public PolicyResponse getPolicyByNumber(String policyNumber) {
        Policy policy = policyRepository.findByPolicyNumber(policyNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng: " + policyNumber));
        if (permissionService != null) {
            permissionService.checkPolicyRead(policy);
        }
        return mapToPolicyResponse(policy);
    }

    @Override
    public Policy getPolicyById(String policyIdOrNumber) {
        if (policyIdOrNumber == null || policyIdOrNumber.isBlank()) {
            throw new BadRequestException("Vui lòng cung cấp số hợp đồng.");
        }

        return policyRepository.findById(policyIdOrNumber)
                .or(() -> policyRepository.findByPolicyNumber(policyIdOrNumber))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng: " + policyIdOrNumber));
    }

    @Override
    public PageResponse<Policy> searchPolicies(PolicySearchCriteria criteria) {
        if (criteria == null) {
            criteria = new PolicySearchCriteria();
        }
        if (permissionService != null) {
            permissionService.applySearchScope(criteria);
        }
        return executeSearch(criteria);
    }

    /**
     * GET /policies/mine — Track B mục 3: "hợp đồng mà tôi là người được
     * bảo hiểm", CHỈ lọc {@code insured.email = principal}, KHÔNG đi qua
     * {@code applySearchScope} (nhánh agent của scope cũ sinh ra AND giữa
     * insuredEmail và assignedAgentEmail làm /mine rỗng với agent thuần —
     * chính là bug cần sửa). Agent thuần tạo hộ: policy tạo hộ có insured
     * là khách nên KHÔNG nằm trong /mine của agent, nhưng nằm trong
     * /policies của agent (scope tenant, xem searchPolicies + OR dual-role).
     */
    @Override
    public PageResponse<Policy> searchMyPolicies(PolicySearchCriteria criteria, String principalEmail) {
        if (criteria == null) {
            criteria = new PolicySearchCriteria();
        }
        if (principalEmail == null || principalEmail.isBlank()) {
            throw new ForbiddenException("Bạn không có quyền xem danh sách hợp đồng.");
        }
        criteria.setInsuredEmail(principalEmail.trim().toLowerCase());
        criteria.setAgentId(null);
        criteria.setAssignedAgentEmail(null);
        criteria.setDualRoleOrScope(false);
        return executeSearch(criteria);
    }

    /** Dựng query Mongo từ criteria ĐÃ scope (dùng chung cho /policies và /mine). */
    private PageResponse<Policy> executeSearch(PolicySearchCriteria criteria) {
        List<Criteria> criteriaList = new ArrayList<>();

        if (criteria.getAssignedAgentEmail() != null && !criteria.getAssignedAgentEmail().isBlank()) {
            criteriaList.add(Criteria.where("assignedAgentEmail").is(criteria.getAssignedAgentEmail().trim().toLowerCase()));
        }

        // SaaS Phase 2 Track B: dual-role AGENT+CUSTOMER dùng MỘT $or duy
        // nhất (insured == principal HOẶC agentId == tenant). AND hai điều
        // kiện riêng lẻ sẽ rỗng với policy agent tạo hộ (insured là khách,
        // agentId là tenant) và rỗng với policy khách tự mua (không có
        // agentId) — đó là lý do phải có nhánh OR riêng, không ghép AND.
        if (criteria.isDualRoleOrScope()) {
            criteriaList.add(new Criteria().orOperator(
                    Criteria.where("insured.email").is(criteria.getInsuredEmail().trim().toLowerCase()),
                    Criteria.where("agentId").is(criteria.getAgentId().trim())));
        } else if (criteria.getAgentId() != null && !criteria.getAgentId().isBlank()) {
            criteriaList.add(Criteria.where("agentId").is(criteria.getAgentId().trim()));
        }

        if (Boolean.TRUE.equals(criteria.getPaymentClaimedOnly())) {
            criteriaList.add(new Criteria().andOperator(
                    Criteria.where("paymentClaimedAt").exists(true),
                    Criteria.where("paymentClaimedAt").ne(null)));
        }

        if (criteria.getStatus() != null) {
            criteriaList.add(Criteria.where("status").is(criteria.getStatus()));
        }

        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            String sanitized = sanitizePattern(criteria.getKeyword());
            Criteria keywordCriteria = new Criteria().orOperator(
                    Criteria.where("policyNumber").regex(sanitized, "i"),
                    Criteria.where("insured.name").regex(sanitized, "i"),
                    Criteria.where("insured.email").regex(sanitized, "i"),
                    Criteria.where("insured.phone").regex(sanitized, "i"),
                    Criteria.where("locations.address").regex(sanitized, "i")
            );
            criteriaList.add(keywordCriteria);
        }

        // Dual-role: insured.email đã nằm trong $or ở trên, không AND thêm
        // (AND thêm sẽ triệt tiêu nhánh tenant của OR).
        if (!criteria.isDualRoleOrScope()
                && criteria.getInsuredEmail() != null && !criteria.getInsuredEmail().isBlank()) {
            criteriaList.add(Criteria.where("insured.email").is(criteria.getInsuredEmail().trim().toLowerCase()));
        }

        if (criteria.getInsuredName() != null && !criteria.getInsuredName().isBlank()) {
            String sanitized = sanitizePattern(criteria.getInsuredName());
            criteriaList.add(Criteria.where("insured.name").regex(sanitized, "i"));
        }

        if (criteria.getLocation() != null && !criteria.getLocation().isBlank()) {
            String sanitized = sanitizePattern(criteria.getLocation());
            criteriaList.add(Criteria.where("locations.address").regex(sanitized, "i"));
        }

        if (criteria.getPolicyNumber() != null && !criteria.getPolicyNumber().isBlank()) {
            String sanitized = sanitizePattern(criteria.getPolicyNumber());
            criteriaList.add(Criteria.where("policyNumber").regex(sanitized, "i"));
        }

        // --- $elemMatch Query on Nested Locations & Coverages (Index-Tuned) ---
        if (criteria.getCoverageCode() != null && !criteria.getCoverageCode().isBlank()) {
            String covCode = sanitizeInput(criteria.getCoverageCode());
            if (criteria.getCoverageType() != null && !criteria.getCoverageType().isBlank()) {
                String covType = sanitizePattern(criteria.getCoverageType());
                criteriaList.add(Criteria.where("locations").elemMatch(
                        Criteria.where("coverages").elemMatch(
                                Criteria.where("coverageCode").is(covCode)
                                        .and("coverageType").regex(covType, "i")
                        )
                ));
            } else {
                criteriaList.add(Criteria.where("locations").elemMatch(
                        Criteria.where("coverages").elemMatch(
                                Criteria.where("coverageCode").is(covCode)
                        )
                ));
            }
        } else if (criteria.getCoverageType() != null && !criteria.getCoverageType().isBlank()) {
            String covType = sanitizePattern(criteria.getCoverageType());
            criteriaList.add(Criteria.where("locations").elemMatch(
                    Criteria.where("coverages").elemMatch(
                            Criteria.where("coverageType").regex(covType, "i")
                    )
            ));
        }

        if (criteria.getEffectiveDateFrom() != null && criteria.getEffectiveDateTo() != null) {
            criteriaList.add(Criteria.where("effectiveDate")
                    .gte(criteria.getEffectiveDateFrom())
                    .lte(criteria.getEffectiveDateTo()));
        } else if (criteria.getEffectiveDateFrom() != null) {
            criteriaList.add(Criteria.where("effectiveDate").gte(criteria.getEffectiveDateFrom()));
        } else if (criteria.getEffectiveDateTo() != null) {
            criteriaList.add(Criteria.where("effectiveDate").lte(criteria.getEffectiveDateTo()));
        }

        if (criteria.getMinPremium() != null && criteria.getMaxPremium() != null
                && criteria.getMinPremium() > criteria.getMaxPremium()) {
            throw new BadRequestException("Khoảng phí không hợp lệ: phí tối thiểu không được lớn hơn phí tối đa.");
        }

        if (criteria.getMinPremium() != null && criteria.getMaxPremium() != null) {
            criteriaList.add(Criteria.where("totalPremium")
                    .gte(criteria.getMinPremium())
                    .lte(criteria.getMaxPremium()));
        } else if (criteria.getMinPremium() != null) {
            criteriaList.add(Criteria.where("totalPremium").gte(criteria.getMinPremium()));
        } else if (criteria.getMaxPremium() != null) {
            criteriaList.add(Criteria.where("totalPremium").lte(criteria.getMaxPremium()));
        }

        Query query = new Query();
        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        int page = Math.max(criteria.getPage(), 0);
        int rawSize = criteria.getSize() > 0 ? criteria.getSize() : DEFAULT_PAGE_SIZE;
        int size = Math.min(rawSize, MAX_PAGE_SIZE);

        String sortBy = "updatedAt";
        if (criteria.getSortBy() != null && !criteria.getSortBy().isBlank()) {
            String requestedSort = criteria.getSortBy().trim();
            if (!ALLOWED_SORT_FIELDS.contains(requestedSort)) {
                throw new BadRequestException("Invalid sortBy field: '" + requestedSort + "'. Allowed sort fields are: " + ALLOWED_SORT_FIELDS);
            }
            sortBy = requestedSort;
        }

        Sort.Direction direction = "ASC".equalsIgnoreCase(criteria.getSortDirection())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        long totalElements = mongoTemplate.count(query, Policy.class);
        query.with(pageable);

        List<Policy> policies = mongoTemplate.find(query, Policy.class);
        Page<Policy> pageResult = new PageImpl<>(policies, pageable, totalElements);

        log.debug("Found {} policies matching search criteria (page {}/{})", totalElements, page, pageResult.getTotalPages());
        return PageResponse.fromPage(pageResult);
    }

    @Override
    public PolicyResponse updatePolicy(String policyNumber, UpdatePolicyRequest request) {
        Policy policy = policyRepository.findByPolicyNumber(policyNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng: " + policyNumber));

        if (permissionService != null) {
            permissionService.checkPolicyWrite(policy);
        }
        // Khóa sau ACCEPTED (PLAN §2.3): check TRƯỚC khi recalculateTotalPremium chạy.
        checkAcceptedNegotiationLock(policy);

        if (policy.getStatus() == PolicyStatus.ACTIVE) {
            throw new InvalidRequestException("Hợp đồng đã có hiệu lực (ACTIVE) không thể sửa trực tiếp. Vui lòng dùng chức năng điều chỉnh hợp đồng (endorsement).");
        }
        if (policy.getStatus() == PolicyStatus.CANCELLED || policy.getStatus() == PolicyStatus.EXPIRED) {
            throw new InvalidRequestException("Không thể sửa hợp đồng ở trạng thái " + policy.getStatus() + ".");
        }

        if (request.getInsured() != null) {
            policy.setInsured(mapToInsured(request.getInsured()));
            // Chống chiếm quyền hợp đồng: CUSTOMER không được đổi người được bảo hiểm
            // sang email khác (đổi insured.email = chuyển tay quyền bồi thường, thanh
            // toán, thương lượng). AGENT/UW/ADMIN giữ hành vi hiện tại.
            if (permissionService != null && permissionService.hasCustomerRole() && !permissionService.isAdmin()) {
                String principal = permissionService.currentPrincipal();
                if (principal != null && policy.getInsured() != null) {
                    policy.getInsured().setEmail(principal.trim().toLowerCase());
                }
            }
        }
        if (request.getEffectiveDate() != null) {
            policy.setEffectiveDate(request.getEffectiveDate());
        }
        if (request.getExpirationDate() != null) {
            policy.setExpirationDate(request.getExpirationDate());
        }

        if (policy.getEffectiveDate() != null && policy.getExpirationDate() != null
                && policy.getEffectiveDate().isAfter(policy.getExpirationDate())) {
            throw new InvalidRequestException("Ngày hiệu lực phải trước ngày hết hạn.");
        }

        policy.setVersion(policy.getVersion() != null ? policy.getVersion() + 1 : 2);
        policy.setUpdatedAt(Instant.now());
        policy.recalculateTotalPremium();
        Policy updatedPolicy = policyRepository.save(policy);

        // Đợt transaction/ACID: như createPolicy — ledger sau commit không
        // được fail request (xem ghi chú ở createPolicy).
        try {
            transactionLedgerService.recordTransaction(
                    policyNumber,
                    policy.getVersion(),
                    TransactionType.UPDATE_POLICY,
                    "System",
                    "Updated policy terms in " + policy.getStatus() + " status"
            );
        } catch (Exception e) {
            log.warn("Failed to record ledger for updated policy {}: {}", policyNumber, e.getMessage());
        }

        return mapToPolicyResponse(updatedPolicy);
    }

    @Override
    public void deletePolicy(String policyNumber) {
        Policy policy = policyRepository.findByPolicyNumber(policyNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng: " + policyNumber));

        if (permissionService != null) {
            permissionService.checkPolicyWrite(policy);
        }

        if (policy.getStatus() != PolicyStatus.DRAFT) {
            throw new InvalidRequestException("Chỉ có thể xoá hợp đồng ở trạng thái nháp (DRAFT). Trạng thái hiện tại: " + policy.getStatus());
        }

        transactionLedgerService.recordTransaction(
                policyNumber,
                policy.getVersion(),
                TransactionType.CANCEL_POLICY,
                "System",
                "Deleted policy in DRAFT status"
        );

        policyRepository.delete(policy);
    }

    @Override
    public PolicyResponse claimPayment(String policyNumber, String actor) {
        Policy policy = policyRepository.findByPolicyNumber(policyNumber)
                .or(() -> policyRepository.findById(policyNumber))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng: " + policyNumber));
        if (permissionService != null) {
            permissionService.checkPolicyWrite(policy);
        }
        if (policy.getStatus() != PolicyStatus.BOUND) {
            throw new InvalidRequestException("Chỉ khai báo thanh toán khi hợp đồng ở trạng thái BOUND.");
        }
        // Idempotent: đã CLAIMED/CONFIRMED thì bấm lại không đổi gì, policy giữ BOUND.
        if (policy.getPaymentStatus() == PaymentStatus.CLAIMED || policy.getPaymentStatus() == PaymentStatus.CONFIRMED) {
            return mapToPolicyResponse(policy);
        }
        policy.setPaymentClaimedAt(Instant.now());
        policy.setPaymentStatus(PaymentStatus.CLAIMED);
        policy.setUpdatedAt(Instant.now());
        Policy saved = policyRepository.save(policy);
        // Đợt transaction/ACID: như createPolicy — ledger sau commit không
        // được fail request (xem ghi chú ở createPolicy).
        try {
            transactionLedgerService.recordTransaction(
                    saved.getPolicyNumber(),
                    saved.getVersion(),
                    TransactionType.UPDATE_POLICY,
                    actor,
                    "Khách hàng khai báo đã chuyển khoản, chờ xác nhận thanh toán"
            );
        } catch (Exception e) {
            log.warn("Failed to record ledger for payment claim on policy {}: {}",
                    saved.getPolicyNumber(), e.getMessage());
        }
        return mapToPolicyResponse(saved);
    }

    /**
     * Track 2 — transaction thật cho cả nhóm ghi: policy-save + vòng lặp
     * đóng/supersede negotiation + ledger (best-effort, xem dưới). Trước
     * đây vòng lặp negotiation là lỗ đa ghi chưa có transaction (§6.5):
     * lỗi ở negotiation thứ k để lại một phần đóng, một phần còn mở, trong
     * khi policy đã đổi status. Nay lỗi runtime ở bất kỳ save nào trong
     * method cũng rollback toàn bộ về status cũ. Chỉ gọi từ PolicyController
     * qua proxy nên annotation có tác dụng; không có self-invocation.
     *
     * <p>Về các hook chạy trong transaction này (không gắn
     * {@code @Transactional} riêng cho chúng — xem báo cáo Track 2):
     * <ul>
     *   <li>Refund ({@code createForCancellation}): failure-isolated +
     *       idempotent từ trước. Khi gọi trong transaction này, save thành
     *       công của nó commit CÙNG policy (nguyên tử); khi nó nuốt lỗi nội
     *       bộ thì policy vẫn commit mà không có refund — đúng hành vi
     *       "hook có thể rớt" cũ, chưa có outbox chạy bù (còn nợ). Hook
     *       hoa hồng đã bỏ hẳn ở SaaS Phase 2, không còn hook nào khác.</li>
     *   <li>Mail kích hoạt ({@code notifyPolicyActivated}): đăng ký
     *       afterCommit — trước đây mail gửi ngay sau policy-save, TRƯỚC
     *       vòng lặp negotiation; nếu vòng lặp fail thì mail "đã kích hoạt"
     *       đã bay trong khi transaction rollback. Nay mail chỉ gửi sau
     *       commit thật.</li>
     *   <li>Ledger: giữ try/catch best-effort trong transaction như đợt
     *       §6.4 (fail không làm fail request); khi thành công nó commit
     *       cùng policy.</li>
     * </ul>
     */
    @Override
    @Transactional
    public PolicyResponse transitionStatus(String policyNumber, StatusTransitionRequest request, String actor) {
        Policy policy = policyRepository.findByPolicyNumber(policyNumber)
                .or(() -> policyRepository.findById(policyNumber))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng: " + policyNumber));

        PolicyStatus currentStatus = policy.getStatus() != null ? policy.getStatus() : PolicyStatus.DRAFT;
        PolicyStatus targetStatus = request.getTargetStatus();

        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new BadRequestException("Invalid state transition from " + currentStatus + " to " + targetStatus);
        }

        // Role check sau state machine để transition vốn đã sai vẫn trả 400, không lẫn 403.
        if (permissionService != null) {
            permissionService.checkTransition(policy, currentStatus, targetStatus);
        }

        // Rule for QUOTED: must have at least 1 Location and each Location must have at least 1 Coverage
        if (targetStatus == PolicyStatus.QUOTED) {
            if (policy.getLocations() == null || policy.getLocations().isEmpty()) {
                throw new BadRequestException("Hợp đồng cần ít nhất 1 địa điểm được bảo hiểm trước khi chuyển sang trạng thái báo giá (QUOTED).");
            }
            boolean allHaveCoverages = policy.getLocations().stream()
                    .allMatch(loc -> loc.getCoverages() != null && !loc.getCoverages().isEmpty());
            if (!allHaveCoverages) {
                throw new BadRequestException("Mỗi địa điểm cần ít nhất 1 quyền lợi bảo hiểm trước khi chuyển sang trạng thái báo giá (QUOTED).");
            }
        }

        // Set boundDate and paymentDueDate when BOUND
        if (targetStatus == PolicyStatus.BOUND) {
            policy.setBoundDate(Instant.now());
            Instant dueDate = request.getPaymentDueDate() != null
                    ? request.getPaymentDueDate()
                    : Instant.now().plus(7, java.time.temporal.ChronoUnit.DAYS);
            policy.setPaymentDueDate(dueDate);
        }

        // Set effectiveDate and expirationDate when ACTIVE
        if (targetStatus == PolicyStatus.ACTIVE) {
            Instant effective = policy.getEffectiveDate() != null ? policy.getEffectiveDate() : Instant.now();
            policy.setEffectiveDate(effective);
            policy.setExpirationDate(effective.plus(365, java.time.temporal.ChronoUnit.DAYS));
        }

        // BOUND→ACTIVE (chỉ ADMIN — kích hoạt trực tiếp không qua thanh toán;
        // xác nhận tay của FINANCE đã bỏ, webhook SePay kích hoạt thẳng);
        // BOUND→DRAFT (chỉ ADMIN) = rollback, reset marker.
        if (currentStatus == PolicyStatus.BOUND && targetStatus == PolicyStatus.ACTIVE) {
            policy.setPaymentStatus(PaymentStatus.CONFIRMED);
        }
        if (currentStatus == PolicyStatus.BOUND && targetStatus == PolicyStatus.DRAFT) {
            policy.setPaymentClaimedAt(null);
            policy.setPaymentStatus(PaymentStatus.UNCLAIMED);
        }

        policy.setStatus(targetStatus);
        policy.setUpdatedAt(Instant.now());
        Policy savedPolicy = policyRepository.save(policy);

        // Hook đợt 2B: chỉ BOUND → ACTIVE. Track 2: đăng ký afterCommit thay
        // vì gửi ngay sau policy-save (xem javadoc method): mail phải đợi
        // toàn bộ transaction commit, nếu không vòng lặp negotiation phía
        // dưới fail → rollback nhưng mail đã gửi là sai sự thật.
        if (currentStatus == PolicyStatus.BOUND && targetStatus == PolicyStatus.ACTIVE
                && notificationService != null) {
            TransactionSideEffects.afterCommit(() -> notificationService.notifyPolicyActivated(savedPolicy));
        }

        // Hook hoàn phí, SAU khi policy đã save. Service này failure-isolated
        // + idempotent, không làm fail transition.
        if (currentStatus == PolicyStatus.ACTIVE && targetStatus == PolicyStatus.CANCELLED
                && refundService != null) {
            refundService.createForCancellation(savedPolicy);
        }

        // Tác dụng phụ lên negotiation (PLAN §2.3).
        if (negotiationRepository != null) {
            if (targetStatus == PolicyStatus.BOUND) {
                for (PriceNegotiation open : negotiationRepository.findByPolicyNumberAndStatus(savedPolicy.getPolicyNumber(), NegotiationStatus.OPEN)) {
                    open.setStatus(NegotiationStatus.REJECTED);
                    open.setClosedBy(actor);
                    open.setClosedAt(Instant.now());
                    negotiationRepository.save(open);
                }
                for (PriceNegotiation pending : negotiationRepository.findByPolicyNumberAndStatus(savedPolicy.getPolicyNumber(), NegotiationStatus.PENDING_CONFIRMATION)) {
                    pending.setStatus(NegotiationStatus.REJECTED);
                    pending.setClosedBy(actor);
                    pending.setClosedAt(Instant.now());
                    negotiationRepository.save(pending);
                }
            }
            if (targetStatus == PolicyStatus.DRAFT) {
                for (PriceNegotiation accepted : negotiationRepository.findByPolicyNumberAndStatus(savedPolicy.getPolicyNumber(), NegotiationStatus.ACCEPTED)) {
                    accepted.setStatus(NegotiationStatus.SUPERSEDED);
                    accepted.setClosedBy(actor);
                    accepted.setClosedAt(Instant.now());
                    negotiationRepository.save(accepted);
                }
                // Sửa đợt 2 (phát hiện khi làm hàng đợi UW): trước đây chỉ ACCEPTED
                // bị supersede khi về DRAFT, còn OPEN vẫn tồn tại — item stale trong
                // hàng đợi UW dù offer mới đã bị chặn (policy không còn QUOTED).
                // Bất biến cần giữ: OPEN chỉ tồn tại khi policy còn QUOTED.
                for (PriceNegotiation open : negotiationRepository.findByPolicyNumberAndStatus(savedPolicy.getPolicyNumber(), NegotiationStatus.OPEN)) {
                    open.setStatus(NegotiationStatus.SUPERSEDED);
                    open.setClosedBy(actor);
                    open.setClosedAt(Instant.now());
                    negotiationRepository.save(open);
                }
                for (PriceNegotiation pending : negotiationRepository.findByPolicyNumberAndStatus(savedPolicy.getPolicyNumber(), NegotiationStatus.PENDING_CONFIRMATION)) {
                    pending.setStatus(NegotiationStatus.SUPERSEDED);
                    pending.setClosedBy(actor);
                    pending.setClosedAt(Instant.now());
                    negotiationRepository.save(pending);
                }
            }
        }

        String desc = "Status transitioned from " + currentStatus + " to " + targetStatus
                + (request.getReason() != null && !request.getReason().isBlank() ? ": " + request.getReason() : "");
        // Track 2: vòng lặp đóng negotiation phía trên nay đã nằm trong cùng
        // transaction với policy-save (xem javadoc method). Ledger giữ
        // try/catch best-effort như đợt §6.4: fail không làm fail request.
        try {
            transactionLedgerService.recordTransaction(
                    savedPolicy.getPolicyNumber(),
                    savedPolicy.getVersion(),
                    TransactionType.STATUS_TRANSITION,
                    actor,
                    desc
            );
        } catch (Exception e) {
            log.warn("Failed to record ledger for transition of policy {}: {}",
                    savedPolicy.getPolicyNumber(), e.getMessage());
        }

        log.info("Transitioned policy {} from {} to {}", savedPolicy.getPolicyNumber(), currentStatus, targetStatus);
        return mapToPolicyResponse(savedPolicy);
    }

    /** Khóa giá sau ACCEPTED hoặc PENDING_CONFIRMATION còn hiệu lực (policy vẫn QUOTED): mọi update đổi phí → 400. */
    private void checkAcceptedNegotiationLock(Policy policy) {
        if (negotiationRepository != null && policy.getStatus() == PolicyStatus.QUOTED
                && (negotiationRepository.existsByPolicyNumberAndStatus(policy.getPolicyNumber(), NegotiationStatus.ACCEPTED)
                    || negotiationRepository.existsByPolicyNumberAndStatus(policy.getPolicyNumber(), NegotiationStatus.PENDING_CONFIRMATION))) {
            throw new InvalidRequestException("Giá hợp đồng đã chốt hoặc đang chờ xác nhận qua thương lượng, không được sửa đổi phí khi còn ở trạng thái QUOTED.");
        }
    }

    /**
     * Resolve tenant từ agentSlug cho CUSTOMER tự mua qua link đại lý
     * (Track C) — và cho ADMIN tạo hộ qua wizard/storefront khi request
     * không có agentId tường minh (wizard chỉ gửi slug, xem khối agentId
     * ở createPolicy).
     * Thiếu agentSlug → 400 với message rõ ràng (FE hiện toast, không lặng);
     * slug không tồn tại → 404; slug SUSPENDED/CLOSED → 410 Gone
     * (AgentLinkInactiveException, phân biệt với 404 như storefront resolve).
     * Chỉ Agent ACTIVE mới được gắn. Mọi agentId client gửi kèm đều bị bỏ qua
     * (caller không truyền nó vào đây — xem khối agentId ở createPolicy).
     * agentRepository == null (test unit cũ) → trả null theo quy ước null-guard
     * của file này; production luôn có repo thật.
     */
    private String resolveCustomerAgentId(String rawSlug) {
        String slug = AgentSlug.normalize(rawSlug);
        if (slug == null || slug.isBlank()) {
            // Ngôn ngữ app nói với khách hàng (không "trả bài" quy định):
            // không nhắc "link đã gửi", không nhắc "không thể tự mua" — chỉ
            // hướng tới hành động kế tiếp là chọn một đại lý (picker ở trang
            // mua). Server vẫn là người giữ luật Q3 (không slug = không tạo).
            throw new BadRequestException(
                    "Bạn chưa chọn đại lý đồng hành. Hãy chọn một đại lý để tiếp tục mua bảo hiểm.");
        }
        if (agentRepository == null) {
            return null;
        }
        Agent agent = agentRepository.findBySlug(slug).orElse(null);
        if (agent == null) {
            throw new ResourceNotFoundException("Không tìm thấy đại lý với link: " + slug);
        }
        if (agent.getStatus() == AgentStatus.SUSPENDED || agent.getStatus() == AgentStatus.CLOSED) {
            throw new AgentLinkInactiveException(slug, agent.getStatus());
        }
        return agent.getId();
    }

    private PolicyResponse mapToPolicyResponse(Policy policy) {
        return PolicyResponse.builder()
                .id(policy.getId())
                .policyNumber(policy.getPolicyNumber())
                .status(policy.getStatus())
                .insured(mapToInsuredDto(policy.getInsured()))
                .locations(policy.getLocations() != null ?
                        policy.getLocations().stream().map(this::mapToLocationDto).collect(Collectors.toList()) : new ArrayList<>())
                .totalPremium(policy.getTotalPremium())
                .assignedAgentEmail(policy.getAssignedAgentEmail())
                .agentId(policy.getAgentId())
                .agentName(resolveAgentName(policy.getAgentId()))
                .negotiatedPremium(policy.getNegotiatedPremium())
                .paymentClaimedAt(policy.getPaymentClaimedAt())
                .paymentStatus(policy.getPaymentStatus())
                .effectiveDate(policy.getEffectiveDate())
                .expirationDate(policy.getExpirationDate())
                .boundDate(policy.getBoundDate())
                .paymentDueDate(policy.getPaymentDueDate())
                .version(policy.getVersion())
                .createdAt(policy.getCreatedAt())
                .updatedAt(policy.getUpdatedAt())
                .build();
    }

    /**
     * Tên hiển thị của đại lý sở hữu policy — null-safe: policy chưa có agent,
     * repo chưa inject (test unit cũ), Agent đã xóa, hoặc lookup lỗi đều trả
     * null thay vì làm fail đường đọc policy (agentName chỉ để hiển thị).
     */
    private String resolveAgentName(String agentId) {
        if (agentId == null || agentId.isBlank() || agentRepository == null) {
            return null;
        }
        try {
            return agentRepository.findById(agentId)
                    .map(Agent::getDisplayName)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Không resolve được agentName cho agentId {}: {}", agentId, e.getMessage());
            return null;
        }
    }

    private Insured mapToInsured(InsuredDto dto) {
        if (dto == null) return null;
        return Insured.builder()
                .insuredId(dto.getInsuredId() != null && !dto.getInsuredId().isBlank() ? dto.getInsuredId() : "INS-" + (1000 + (int)(Math.random() * 9000)))
                .name(dto.getName())
                .type(dto.getType() != null && !dto.getType().isBlank() ? dto.getType() : "BUSINESS")
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .address(dto.getAddress())
                .build();
    }

    private InsuredDto mapToInsuredDto(Insured entity) {
        if (entity == null) return null;
        return InsuredDto.builder()
                .insuredId(entity.getInsuredId())
                .name(entity.getName())
                .type(entity.getType() != null ? entity.getType() : "BUSINESS")
                .email(entity.getEmail())
                .phone(entity.getPhone())
                .address(entity.getAddress())
                .build();
    }

    private Location mapToLocation(LocationDto dto) {
        if (dto == null) return null;
        return Location.builder()
                .locationId(dto.getLocationId())
                .address(dto.getAddress())
                .coverages(dto.getCoverages() != null ?
                        dto.getCoverages().stream().map(this::mapToCoverage).collect(Collectors.toList()) : new ArrayList<>())
                .build();
    }

    private LocationDto mapToLocationDto(Location entity) {
        if (entity == null) return null;
        return LocationDto.builder()
                .locationId(entity.getLocationId())
                .address(entity.getAddress())
                .coverages(entity.getCoverages() != null ?
                        entity.getCoverages().stream().map(this::mapToCoverageDto).collect(Collectors.toList()) : new ArrayList<>())
                .build();
    }

    private Coverage mapToCoverage(CoverageDto dto) {
        if (dto == null) return null;

        String name = (dto.getCoverageName() != null && !dto.getCoverageName().isBlank())
                ? dto.getCoverageName()
                : (dto.getCoverageCode() != null ? dto.getCoverageCode() : "Coverage");

        Coverage coverage = Coverage.builder()
                .coverageCode(dto.getCoverageCode())
                .coverageName(name)
                .coverageType(dto.getCoverageType() != null ? dto.getCoverageType() : "STANDARD")
                .limit(dto.getLimit())
                .deductible(dto.getDeductible())
                .termMonths(dto.getTermMonths() != null ? dto.getTermMonths() : 12)
                .build();

        // Giá do SERVER quyết định: luôn tính lại theo bảng giá nội bộ,
        // bỏ qua premium/baseRate do client gửi (chống giả mạo phí bảo hiểm).
        double serverRate = premiumCalculationService.resolveServerBaseRate(coverage.getCoverageCode());
        coverage.setBaseRate(serverRate);
        coverage.setPremium(premiumCalculationService.calculateServerPriced(coverage));

        return coverage;
    }

    private CoverageDto mapToCoverageDto(Coverage entity) {
        if (entity == null) return null;
        return CoverageDto.builder()
                .coverageCode(entity.getCoverageCode())
                .coverageName(entity.getCoverageName())
                .coverageType(entity.getCoverageType())
                .limit(entity.getLimit())
                .deductible(entity.getDeductible())
                .termMonths(entity.getTermMonths())
                .baseRate(entity.getBaseRate())
                .premium(entity.getPremium())
                .build();
    }

    private String sanitizePattern(String input) {
        if (input == null) return "";
        String trimmed = input.trim();
        if (trimmed.length() > 100) {
            trimmed = trimmed.substring(0, 100);
        }
        return Pattern.quote(trimmed);
    }

    private String sanitizeInput(String input) {
        if (input == null) return "";
        String trimmed = input.trim();
        if (trimmed.length() > 100) {
            trimmed = trimmed.substring(0, 100);
        }
        return trimmed;
    }
}
