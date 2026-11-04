package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.request.AgentCreateRequest;
import com.dungphd.insuranceass.dto.request.AgentUpdateRequest;
import com.dungphd.insuranceass.dto.response.AgentResponse;
import com.dungphd.insuranceass.exception.AgentLinkInactiveException;
import com.dungphd.insuranceass.dto.response.PublicAgentResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.Agent;
import com.dungphd.insuranceass.model.AgentStatus;
import com.dungphd.insuranceass.repository.AgentRepository;
import com.dungphd.insuranceass.service.AgentCodeGenerator;
import com.dungphd.insuranceass.service.AgentService;
import com.dungphd.insuranceass.service.AgentSlug;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * CRUD tối thiểu cho Agent (ADMIN-only qua controller; matcher /admin/** đã có
 * sẵn nên không cần matcher mới). Phase 1 KHÔNG hard-delete tenant: đóng bằng
 * status CLOSED qua update. Phase 1 cũng KHÔNG enforce gì từ status.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    /** Số lần retry sinh mã khi unique index bắt duplicate (mã tồn tại thủ công). */
    public static final int MAX_CODE_ATTEMPTS = 5;

    private final AgentRepository agentRepository;
    private final AgentCodeGenerator codeGenerator;

    @Override
    public AgentResponse createAgent(AgentCreateRequest request, String actorEmail) {
        if (request == null || request.getDisplayName() == null || request.getDisplayName().isBlank()) {
            throw new BadRequestException("Tên đại lý là bắt buộc.");
        }
        String displayName = request.getDisplayName().trim();
        // Track C: slug do ADMIN đặt, tùy chọn lúc tạo. Nếu gửi thì chuẩn hóa
        // về chữ thường + validate pattern + kiểm tra trùng TRƯỚC khi save
        // (message 400 rõ ràng); DuplicateKeyException lúc save là backstop
        // cho race hai ADMIN đặt cùng slug một lúc.
        String slug = validateSlugForWrite(request.getSlug(), null);
        // Counter nguyên tử đã chống trùng số; retry chỉ là backstop cho trường
        // hợp mã đã tồn tại thủ công (vd seed AGT-SEED va chạm dải số, hoặc DB
        // restore từ nơi khác). Hết lượt retry thì lỗi rõ ràng, không sinh mã bừa.
        for (int attempt = 1; attempt <= MAX_CODE_ATTEMPTS; attempt++) {
            String code = codeGenerator.nextCode();
            Agent agent = Agent.builder()
                    .code(code)
                    .slug(slug)
                    .displayName(displayName)
                    .contactName(trimOrNull(request.getContactName()))
                    .contactEmail(trimOrNull(request.getContactEmail()))
                    .phone(trimOrNull(request.getPhone()))
                    .address(trimOrNull(request.getAddress()))
                    .status(AgentStatus.ACTIVE)
                    .createdBy(actorEmail)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            try {
                return toResponse(agentRepository.save(agent));
            } catch (DuplicateKeyException e) {
                // Race slug: hai ADMIN đặt cùng slug một lúc, pre-check đều
                // qua rồi mới va nhau lúc save → 400 rõ ràng, không retry
                // sinh mã mới (mã không phải thứ bị trùng).
                if (slug != null && agentRepository.existsBySlug(slug)) {
                    throw new BadRequestException("Slug '" + slug + "' đã được sử dụng bởi đại lý khác.");
                }
                log.warn("Trùng mã đại lý {} (lần {}), sinh mã mới.", code, attempt);
                if (attempt == MAX_CODE_ATTEMPTS) {
                    throw new BadRequestException(
                            "Không sinh được mã đại lý không trùng sau " + MAX_CODE_ATTEMPTS + " lần thử.");
                }
            }
        }
        throw new IllegalStateException("Không tới được đây: vòng retry mã đại lý.");
    }

    @Override
    public List<AgentResponse> listAgents(AgentStatus status) {
        List<Agent> agents = status == null
                ? agentRepository.findAllByOrderByCodeAsc()
                : agentRepository.findByStatusOrderByCodeAsc(status);
        return agents.stream().map(this::toResponse).toList();
    }

    @Override
    public AgentResponse getAgent(String id) {
        return toResponse(findOrThrow(id));
    }

    @Override
    public AgentResponse updateAgent(String id, AgentUpdateRequest request) {
        Agent agent = findOrThrow(id);
        if (request == null) {
            return toResponse(agent);
        }
        if (request.getDisplayName() != null) {
            if (request.getDisplayName().isBlank()) {
                throw new BadRequestException("Tên đại lý không được rỗng.");
            }
            agent.setDisplayName(request.getDisplayName().trim());
        }
        if (request.getContactName() != null) {
            agent.setContactName(trimOrNull(request.getContactName()));
        }
        if (request.getContactEmail() != null) {
            agent.setContactEmail(trimOrNull(request.getContactEmail()));
        }
        if (request.getPhone() != null) {
            agent.setPhone(trimOrNull(request.getPhone()));
        }
        if (request.getAddress() != null) {
            agent.setAddress(trimOrNull(request.getAddress()));
        }
        if (request.getSlug() != null) {
            // Chuỗi rỗng = 400 (không xóa lặng slug đang có link trỏ tới);
            // slug hợp lệ thì chuẩn hóa + kiểm tra trùng (trừ chính agent này).
            agent.setSlug(validateSlugForWrite(request.getSlug(), agent.getId()));
        }
        if (request.getStatus() != null) {
            agent.setStatus(request.getStatus());
        }
        agent.setUpdatedAt(Instant.now());
        try {
            return toResponse(agentRepository.save(agent));
        } catch (DuplicateKeyException e) {
            if (request.getSlug() != null) {
                String normalized = AgentSlug.normalize(request.getSlug());
                if (normalized != null && agentRepository.existsBySlug(normalized)) {
                    throw new BadRequestException("Slug '" + normalized + "' đã được sử dụng bởi đại lý khác.");
                }
            }
            // Save ở update chỉ đổi được field unique là slug nên va chạm
            // unique ở đây vẫn là slug (trường hợp race đã giải quyết xong
            // giữa chừng) — trả 400 thay vì để DuplicateKeyException thành 500.
            throw new BadRequestException("Slug đã được sử dụng bởi đại lý khác.");
        }
    }

    @Override
    public PublicAgentResponse resolvePublicBySlug(String slug) {
        // Endpoint public: slug không hợp lệ pattern cũng chỉ là "không tìm
        // thấy" (404) — không lộ quy tắc validate nội bộ ra ngoài, và không
        // phân biệt được slug sai format với slug chưa từng tồn tại.
        String normalized = AgentSlug.normalize(slug);
        Agent agent = normalized == null ? null
                : agentRepository.findBySlug(normalized).orElse(null);
        if (agent == null) {
            // Bao gồm cả agent cũ chưa có slug: findBySlug không bao giờ khớp
            // null, nên link tới agent chưa đặt slug cũng về 404, đúng spec.
            throw new ResourceNotFoundException("Không tìm thấy đại lý với link: " + slug);
        }
        if (agent.getStatus() == AgentStatus.SUSPENDED || agent.getStatus() == AgentStatus.CLOSED) {
            // Link chết (Q4): 410 Gone, phân biệt với 404 — FE hiện trang
            // exception riêng, không cho mua tiếp dưới dạng vãng lai.
            throw new AgentLinkInactiveException(normalized, agent.getStatus());
        }
        // Chỉ trả field an toàn để hiển thị — KHÔNG id, code, createdBy.
        return PublicAgentResponse.builder()
                .displayName(agent.getDisplayName())
                .contactName(agent.getContactName())
                .contactEmail(agent.getContactEmail())
                .phone(agent.getPhone())
                .address(agent.getAddress())
                .status(agent.getStatus())
                .build();
    }

    /**
     * Validate slug cho ghi (tạo/cập nhật). Trả về slug đã chuẩn hóa, hoặc
     * null khi caller không gửi slug (giữ nguyên/không đặt). Trùng slug →
     * 400; chuỗi rỗng ở update → 400 (xem ghi chú ở DTO).
     *
     * @param rawSlug slug thô từ request (null = không gửi)
     * @param selfId id của agent đang cập nhật (null khi tạo) — trùng với
     *               chính mình thì cho qua (đặt lại cùng slug cũ)
     */
    private String validateSlugForWrite(String rawSlug, String selfId) {
        if (rawSlug == null) {
            return null;
        }
        String normalized = AgentSlug.normalize(rawSlug);
        if (!AgentSlug.isValid(normalized)) {
            throw new BadRequestException("Slug không hợp lệ: '" + rawSlug + "'. " + AgentSlug.ruleDescription());
        }
        agentRepository.findBySlug(normalized).ifPresent(existing -> {
            if (selfId == null || !selfId.equals(existing.getId())) {
                throw new BadRequestException("Slug '" + normalized + "' đã được sử dụng bởi đại lý khác.");
            }
        });
        return normalized;
    }

    private Agent findOrThrow(String id) {
        return agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đại lý với id: " + id));
    }

    private static String trimOrNull(String value) {
        return value == null ? null : value.trim();
    }

    private AgentResponse toResponse(Agent agent) {
        return AgentResponse.builder()
                .id(agent.getId())
                .code(agent.getCode())
                .slug(agent.getSlug())
                .displayName(agent.getDisplayName())
                .contactName(agent.getContactName())
                .contactEmail(agent.getContactEmail())
                .phone(agent.getPhone())
                .address(agent.getAddress())
                .status(agent.getStatus())
                .createdBy(agent.getCreatedBy())
                .createdAt(agent.getCreatedAt())
                .updatedAt(agent.getUpdatedAt())
                .build();
    }
}
