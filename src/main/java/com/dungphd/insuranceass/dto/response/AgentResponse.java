package com.dungphd.insuranceass.dto.response;

import com.dungphd.insuranceass.model.AgentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentResponse {
    private String id;
    private String code;
    /** Slug public trong link /a/<slug> (Track C); null = agent chưa có link. */
    private String slug;
    private String displayName;
    private String contactName;
    private String contactEmail;
    private String phone;
    private String address;
    private AgentStatus status;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}
