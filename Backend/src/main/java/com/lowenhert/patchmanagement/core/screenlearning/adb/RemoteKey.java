package com.lowenhert.patchmanagement.core.screenlearning.adb;

/** The only key events Screen Learning may send. No text input, no shell commands. */
public enum RemoteKey {
    DPAD_UP("KEYCODE_DPAD_UP"),
    DPAD_DOWN("KEYCODE_DPAD_DOWN"),
    DPAD_LEFT("KEYCODE_DPAD_LEFT"),
    DPAD_RIGHT("KEYCODE_DPAD_RIGHT"),
    SELECT("KEYCODE_DPAD_CENTER"),
    BACK("KEYCODE_BACK"),
    HOME("KEYCODE_HOME");

    private final String keycode;

    RemoteKey(String keycode) {
        this.keycode = keycode;
    }

    public String keycode() {
        return keycode;
    }

    public boolean isDpad() {
        return this == DPAD_UP || this == DPAD_DOWN || this == DPAD_LEFT || this == DPAD_RIGHT;
    }
}
