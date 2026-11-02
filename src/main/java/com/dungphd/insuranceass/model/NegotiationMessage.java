package com.dungphd.insuranceass.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Tin nhắn trao đổi trong phiên thương lượng giá. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NegotiationMessage {

    private String messageId;

    private String senderEmail;

    private NegotiationRole senderRole;

    private String content;

    private Instant createdAt;
}

