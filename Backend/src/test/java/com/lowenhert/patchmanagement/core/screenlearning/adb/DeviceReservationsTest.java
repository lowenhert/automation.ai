package com.lowenhert.patchmanagement.core.screenlearning.adb;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceReservationsTest {

    @Test
    void oneOwnerPerDevice() {
        DeviceReservations r = new DeviceReservations();
        assertThat(r.tryReserve("emulator-5554", "exploration session")).isTrue();
        assertThat(r.tryReserve("emulator-5554", "script execution 1")).isFalse();
        assertThat(r.tryReserve("emulator-5556", "script execution 1")).isTrue();
        assertThat(r.owner("emulator-5554")).contains("exploration session");

        r.release("emulator-5554", "script execution 1");   // not the owner: no effect
        assertThat(r.owner("emulator-5554")).contains("exploration session");
        r.release("emulator-5554", "exploration session");
        assertThat(r.tryReserve("emulator-5554", "script execution 1")).isTrue();
    }
}
