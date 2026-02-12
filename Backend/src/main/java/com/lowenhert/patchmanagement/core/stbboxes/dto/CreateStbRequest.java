package com.lowenhert.patchmanagement.core.stbboxes.dto;
import lombok.Data;

@Data
public class CreateStbRequest {
    private String vendor;
    private String model;
    private Resolution resolution;
    private Integer stbNo;
}
