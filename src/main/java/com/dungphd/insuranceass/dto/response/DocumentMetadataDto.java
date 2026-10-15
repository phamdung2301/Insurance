package com.dungphd.insuranceass.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentMetadataDto {
    private String documentId;
    private String documentType; // CERTIFICATE | SUMMARY
    private String title;
    private String fileName;
    private String policyNumber;
    private Integer version;
    private String contentType;
    private String downloadUrl;
    private Instant generatedAt;
}
