package com.dungphd.insuranceass.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Ảnh bằng chứng đính kèm hồ sơ bồi thường (tối đa 10 ảnh / claim).
 * File được lưu trên đĩa, chỉ lưu metadata và đường dẫn trong DB.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimPhoto {

    private String id;
    private String claimId;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String contentType;
    private Instant uploadedAt;
}

