package com.lowenhert.patchmanagement.core.adb.dto;

import lombok.Data;

@Data
public class AdbRequest {
    private String ip;
    private String keycode;
    private String text;
    private String command;
}
