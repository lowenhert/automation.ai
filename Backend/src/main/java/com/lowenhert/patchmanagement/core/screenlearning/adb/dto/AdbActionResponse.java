package com.lowenhert.patchmanagement.core.screenlearning.adb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdbActionResponse {
    private boolean success;
    private String serial;
    private String message;
}
