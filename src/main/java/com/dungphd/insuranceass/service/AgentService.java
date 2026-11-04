package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.request.AgentCreateRequest;
import com.dungphd.insuranceass.dto.request.AgentUpdateRequest;
import com.dungphd.insuranceass.dto.response.AgentResponse;
import com.dungphd.insuranceass.model.AgentStatus;

import java.util.List;

public interface AgentService {

    /** Tạo đại lý mới, code do server sinh. Actor = email ADMIN tạo (audit). */
    AgentResponse createAgent(AgentCreateRequest request, String actorEmail);

    /** List đại lý, sort theo code; status null = tất cả. */
    List<AgentResponse> listAgents(AgentStatus status);

    AgentResponse getAgent(String id);

    /** Update displayName/liên hệ/status; field null = giữ nguyên. */
    AgentResponse updateAgent(String id, AgentUpdateRequest request);
}
