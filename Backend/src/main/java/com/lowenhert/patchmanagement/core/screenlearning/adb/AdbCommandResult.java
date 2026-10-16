package com.lowenhert.patchmanagement.core.screenlearning.adb;

public record AdbCommandResult(int exitCode, String output) {

    public boolean isSuccess() {
        return exitCode == 0;
    }
}
