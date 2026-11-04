package com.dungphd.insuranceass.service.impl;

import com.dungphd.insuranceass.dto.request.AgentCreateRequest;
import com.dungphd.insuranceass.dto.request.AgentUpdateRequest;
import com.dungphd.insuranceass.dto.response.AgentResponse;
import com.dungphd.insuranceass.exception.BadRequestException;
import com.dungphd.insuranceass.exception.ResourceNotFoundException;
import com.dungphd.insuranceass.model.Agent;
import com.dungphd.insuranceass.model.AgentStatus;
import com.dungphd.insuranceass.repository.AgentRepository;
import com.dungphd.insuranceass.service.AgentCodeGenerator;
import com.dungphd.insuranceass.service.AgentService;
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
        // Counter nguyên tử đã chống trùng số; retry chỉ là backstop cho trường
        // hợp mã đã tồn tại thủ công (vd seed AGT-SEED va chạm dải số, hoặc DB
        // restore từ nơi khác). Hết lượt retry thì lỗi rõ ràng, không sinh mã bừa.
        for (int attempt = 1; attempt <= MAX_CODE_ATTEMPTS; attempt++) {
            String code = codeGenerator.nextCode();
            Agent agent = Agent.builder()
                    .code(code)
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
        if (request.getStatus() != null) {
            agent.setStatus(request.getStatus());
        }
        agent.setUpdatedAt(Instant.now());
        return toResponse(agentRepository.save(agent));
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
