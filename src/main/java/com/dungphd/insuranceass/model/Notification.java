package com.dungphd.insuranceass.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Thông báo in-app (đợt 2B). Một document cho một người nhận: scope theo
 * recipientEmail, không bao giờ liệt kê chéo giữa các user.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "notifications")
public class Notification {

    public static final String TYPE_NEW_OFFER = "NEW_OFFER";
    public static final String TYPE_POLICY_ACTIVATED = "POLICY_ACTIVATED";
    public static final String TYPE_ENDORSEMENT_APPLIED = "ENDORSEMENT_APPLIED";

    @Id
    private String id;

    private String recipientEmail;

    /** NEW_OFFER | POLICY_ACTIVATED | ENDORSEMENT_APPLIED (chuỗi, không enum riêng). */
    private String type;

    private String title;

    private String message;

    /** Dạng /hop-dong/{policyNumber}. */
    private String link;

    @Builder.Default
    private boolean read = false;

    private Instant createdAt;
}
