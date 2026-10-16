package com.lowenhert.patchmanagement.core.screenlearning.adb.dto;

import lombok.Data;

@Data
public class AdbConnectRequest {
    private String host;
    private Integer port;
}
