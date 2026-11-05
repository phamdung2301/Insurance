package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.AgentCreateRequest;
import com.dungphd.insuranceass.dto.request.AgentUpdateRequest;
import com.dungphd.insuranceass.dto.response.AgentResponse;
import com.dungphd.insuranceass.dto.response.PublicAgentResponse;
import com.dungphd.insuranceass.model.AgentStatus;

import java.util.List;

public interface AgentService {

    /** Tạo đại lý mới, code do server sinh. Actor = email ADMIN tạo (audit). */
    AgentResponse createAgent(AgentCreateRequest request, String actorEmail);

    /** List đại lý, sort theo code; status null = tất cả. */
    List<AgentResponse> listAgents(AgentStatus status);

    AgentResponse getAgent(String id);

    /** Update displayName/liên hệ/slug/status; field null = giữ nguyên. */
    AgentResponse updateAgent(String id, AgentUpdateRequest request);

    /**
     * Resolve slug public cho storefront (SaaS Phase 2 Track C, endpoint
     * public, không cần JWT). Phân biệt rõ: slug không tồn tại (kể cả agent
     * chưa có slug) → ResourceNotFoundException (404); agent SUSPENDED/CLOSED
     * → AgentLinkInactiveException (410 Gone); chỉ ACTIVE mới trả 200.
     */
    PublicAgentResponse resolvePublicBySlug(String slug);

    /**
     * Danh sách đại lý đang hoạt động cho picker chọn đại lý (khách chưa có
     * link vẫn mua được qua một đại lý mình chọn — Q3 vẫn giữ: mua luôn gắn
     * với một đại lý, picker chỉ giải quyết "khách chưa có link thì tìm đại
     * lý bằng cách nào"). CHỈ ACTIVE (SUSPENDED/CLOSED là link chết, không
     * cho chọn); mỗi item vẫn là {@code PublicAgentResponse} — không id,
     * không code.
     */
    java.util.List<PublicAgentResponse> listPublicActive();
}
