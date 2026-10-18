package com.example;

import org.json.JSONArray;
import org.json.JSONObject;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * AirLink Device Pairing Manager
 * Handles cryptographically secure temporary PIN generation, token verification,
 * mutual device approval, and active session management without cloud servers.
 */
public class DevicePairing {

    public static class PairedDevice {
        public final String id;
        public final String name;
        public final String ipAddress;
        public final String authToken;
        public final long pairedTimestamp;

        public PairedDevice(String id, String name, String ipAddress, String authToken, long pairedTimestamp) {
            this.id = id;
            this.name = name;
            this.ipAddress = ipAddress;
            this.authToken = authToken;
            this.pairedTimestamp = pairedTimestamp;
        }

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", id);
                obj.put("name", name);
                obj.put("ip", ipAddress);
                obj.put("pairedAt", "Recently");
                return obj;
            } catch (Exception e) {
                return new JSONObject();
            }
        }
    }

    private static DevicePairing instance;
    private final List<PairedDevice> pairedDevices = Collections.synchronizedList(new ArrayList<>());
    private final SecureRandom secureRandom = new SecureRandom();

    private String currentPin = "841290";
    private long pinGeneratedTime = System.currentTimeMillis();
    private static final long PIN_VALIDITY_MS = 5 * 60 * 1000; // 5 minutes

    public static synchronized DevicePairing getInstance() {
        if (instance == null) {
            instance = new DevicePairing();
        }
        return instance;
    }

    private DevicePairing() {
        generateNewPin();
    }

    /**
     * Generates a 6-digit random pairing PIN
     */
    public synchronized String generateNewPin() {
        int code = 100000 + secureRandom.nextInt(900000);
        this.currentPin = String.valueOf(code);
        this.pinGeneratedTime = System.currentTimeMillis();
        return this.currentPin;
    }

    public synchronized String getCurrentPin() {
        if (System.currentTimeMillis() - pinGeneratedTime > PIN_VALIDITY_MS) {
            return generateNewPin();
        }
        return currentPin;
    }

    /**
     * Validates if the supplied PIN matches the current active PIN
     */
    public synchronized boolean verifyPin(String enteredPin) {
        if (enteredPin == null || enteredPin.trim().isEmpty()) return false;
        if (System.currentTimeMillis() - pinGeneratedTime > PIN_VALIDITY_MS) {
            return false;
        }
        return currentPin.equals(enteredPin.trim());
    }

    /**
     * Registers a successfully paired device and generates a session auth token
     */
    public synchronized PairedDevice registerDevice(String deviceName, String ipAddress) {
        String deviceId = "dev_" + UUID.randomUUID().toString().substring(0, 8);
        String authToken = UUID.randomUUID().toString();
        PairedDevice device = new PairedDevice(deviceId, deviceName, ipAddress, authToken, System.currentTimeMillis());
        pairedDevices.add(device);
        return device;
    }

    public synchronized boolean isTokenValid(String token) {
        if (token == null) return false;
        for (PairedDevice dev : pairedDevices) {
            if (token.equals(dev.authToken)) {
                return true;
            }
        }
        return false;
    }

    public synchronized void revokeDevice(String deviceId) {
        pairedDevices.removeIf(d -> d.id.equals(deviceId));
    }

    public synchronized void clearAllDevices() {
        pairedDevices.clear();
    }

    public synchronized List<PairedDevice> getPairedDevices() {
        return new ArrayList<>(pairedDevices);
    }

    public synchronized String getPairedDevicesJson() {
        JSONArray arr = new JSONArray();
        for (PairedDevice dev : pairedDevices) {
            arr.put(dev.toJson());
        }
        return arr.toString();
    }
}
