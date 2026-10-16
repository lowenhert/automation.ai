package com.lowenhert.patchmanagement.core.screenlearning.adb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdbDeviceDto {
    private String serial;
    /** adb state: device, offline, unauthorized, no permissions, ... */
    private String state;
    /** EMULATOR, NETWORK or USB */
    private String connectionType;
    private String model;
    private String product;
    private String deviceName;
    private String transportId;
    private String manufacturer;
    private String androidVersion;
    private String sdkVersion;
    private String resolution;
    private String infoError;
}
