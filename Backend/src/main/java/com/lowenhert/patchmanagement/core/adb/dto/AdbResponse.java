package com.lowenhert.patchmanagement.core.adb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdbResponse {
    private boolean success;
    private String output;
    private String error;
}
