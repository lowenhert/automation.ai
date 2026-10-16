package com.lowenhert.patchmanagement.core.screenlearning.adb;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Ensures only one controlling activity (exploration session or script execution) uses a device at a time. */
@Component
public class DeviceReservations {

    private final ConcurrentMap<String, String> owners = new ConcurrentHashMap<>();

    /** Returns true if the device was free and is now reserved for {@code owner}. */
    public boolean tryReserve(String serial, String owner) {
        return owners.putIfAbsent(serial, owner) == null || owner.equals(owners.get(serial));
    }

    public void release(String serial, String owner) {
        owners.remove(serial, owner);
    }

    public Optional<String> owner(String serial) {
        return Optional.ofNullable(owners.get(serial));
    }
}
