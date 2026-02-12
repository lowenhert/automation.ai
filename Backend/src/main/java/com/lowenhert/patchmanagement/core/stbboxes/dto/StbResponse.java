package com.lowenhert.patchmanagement.core.stbboxes.dto;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class StbResponse {
    private UUID uuid;
    private String vendor;
    private String model;
    private Resolution resolution;
    private Integer stbNo;
}
