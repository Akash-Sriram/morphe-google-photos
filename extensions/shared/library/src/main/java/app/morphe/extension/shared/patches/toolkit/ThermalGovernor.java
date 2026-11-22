package app.morphe.extension.shared.patches.toolkit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;

import app.morphe.extension.shared.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adaptive hardware thermal governor for Morphe Toolkit operations.
 *
 * Continuously samples device temperatures (Linux sysfs CPU sensors, battery thermistor,
 * and Android 10+ PowerManager thermal status) and dynamically throttles or expands
 * concurrent worker threads to prevent device overheating and thermal throttling.
 */
public class ThermalGovernor {

    private final int maxWorkers;
    private final AtomicInteger activeRunningCount = new AtomicInteger(0);
    private volatile int targetConcurrency;
    private volatile float currentTempC = 0.0f;
    private volatile int currentThermalStatus = 0;
    private volatile String stateLabel = "Cool";
    private volatile int pacingDelayMs = 0;
    private volatile long lastSampleMs = 0L;

    public ThermalGovernor(int maxWorkers) {
        this.maxWorkers = Math.max(1, maxWorkers);
        this.targetConcurrency = this.maxWorkers;
    }

    /**
     * Reads battery temperature in degrees Celsius via sticky Intent.
     */
    public static float readBatteryTemperature(Context context) {
        if (context == null) return 0.0f;
        try {
            Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (intent != null) {
                int tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                if (tempRaw > 0) {
                    return tempRaw / 10.0f;
                }
            }
        } catch (Throwable ignored) {}
        return 0.0f;
    }

    /**
     * Reads the highest CPU thermal sensor from /sys/class/thermal.
     */
    public static float readCpuTemperature() {
        float maxTemp = 0.0f;
        try {
            File dir = new File("/sys/class/thermal");
            File[] zones = dir.listFiles((d, name) -> name.startsWith("thermal_zone"));
            if (zones != null) {
                for (File zone : zones) {
                    try {
                        File typeFile = new File(zone, "type");
                        File tempFile = new File(zone, "temp");
                        if (!typeFile.canRead() || !tempFile.canRead()) continue;

                        String type;
                        try (BufferedReader br = new BufferedReader(new FileReader(typeFile))) {
                            type = br.readLine();
                        }
                        if (type == null) continue;
                        type = type.toLowerCase(Locale.US);

                        // Match CPU/SoC clusters
                        boolean isCpuZone = type.contains("cpu") || type.contains("gold")
                                || type.contains("silv") || type.contains("soc")
                                || type.contains("tsens") || type.contains("cluster")
                                || type.contains("ap");
                        if (!isCpuZone) continue;

                        String tempStr;
                        try (BufferedReader br = new BufferedReader(new FileReader(tempFile))) {
                            tempStr = br.readLine();
                        }
                        if (tempStr == null) continue;

                        float temp = Float.parseFloat(tempStr.trim());
                        if (temp > 1000.0f) {
                            temp /= 1000.0f; // millidegrees to degrees C
                        }
                        if (temp >= 20.0f && temp <= 110.0f && temp > maxTemp) {
                            maxTemp = temp;
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        return maxTemp;
    }

    /**
     * Reads Android 10+ (API 29) system thermal status.
     */
    public static int readThermalStatus(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && context != null) {
            try {
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    return pm.getCurrentThermalStatus();
                }
            } catch (Throwable ignored) {}
        }
        return 0; // THERMAL_STATUS_NONE
    }

    /**
     * Updates telemetry and recalculates target concurrency.
     * Rate-limited to at most once every 1.5 seconds to avoid polling overhead.
     */
    public void update(Context context) {
        long now = System.currentTimeMillis();
        if (now - lastSampleMs < 1500L && lastSampleMs != 0L) {
            return;
        }
        lastSampleMs = now;

        float batteryTemp = readBatteryTemperature(context);
        float cpuTemp = readCpuTemperature();
        float effectiveTemp = Math.max(batteryTemp, cpuTemp);
        if (effectiveTemp <= 0.0f) {
            effectiveTemp = batteryTemp > 0 ? batteryTemp : cpuTemp;
        }

        int status = readThermalStatus(context);
        this.currentTempC = effectiveTemp;
        this.currentThermalStatus = status;

        // Dynamic concurrency decisions:
        // Android PowerManager:
        // 0 = NONE, 1 = LIGHT, 2 = MODERATE, 3 = SEVERE, 4 = CRITICAL, 5 = EMERGENCY, 6 = SHUTDOWN
        int newTarget;
        String label;
        int delay;

        if (status >= 4 /* CRITICAL */ || effectiveTemp >= 48.0f) {
            newTarget = 1;
            label = "Critical (48°C+)";
            delay = 500;
        } else if (status >= 3 /* SEVERE */ || effectiveTemp >= 45.0f) {
            newTarget = Math.min(2, maxWorkers);
            label = "Hot (45°C)";
            delay = 200;
        } else if (status >= 2 /* MODERATE */ || effectiveTemp >= 42.0f) {
            newTarget = Math.min(3, maxWorkers);
            label = "Warm (42°C)";
            delay = 0;
        } else if (status >= 1 /* LIGHT */ || effectiveTemp >= 39.0f) {
            newTarget = Math.min(4, maxWorkers);
            label = "Mild (39°C)";
            delay = 0;
        } else {
            newTarget = maxWorkers;
            label = "Cool (<39°C)";
            delay = 0;
        }

        this.pacingDelayMs = delay;
        this.stateLabel = label;

        if (newTarget != this.targetConcurrency) {
            Logger.printInfo(() -> String.format(Locale.US,
                    "ThermalGovernor: Scaling concurrency %d -> %d (temp=%.1f°C, status=%d, mode=%s)",
                    targetConcurrency, newTarget, currentTempC, currentThermalStatus, stateLabel));
            setTargetConcurrency(newTarget);
        }
    }

    /**
     * Blocks worker until a slot is available under the dynamic concurrency limit or isCancelled is set.
     */
    public synchronized boolean acquireSlot(boolean[] isCancelled) {
        while (!isCancelled[0]) {
            if (activeRunningCount.get() < targetConcurrency) {
                activeRunningCount.incrementAndGet();
                return true;
            }
            try {
                wait(500);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * Releases an active slot and wakes any waiting workers.
     */
    public synchronized void releaseSlot() {
        activeRunningCount.decrementAndGet();
        notifyAll();
    }

    public synchronized void setTargetConcurrency(int target) {
        this.targetConcurrency = Math.max(1, Math.min(maxWorkers, target));
        notifyAll();
    }

    public int getTargetConcurrency() {
        return targetConcurrency;
    }

    public int getActiveRunningCount() {
        return activeRunningCount.get();
    }

    public float getCurrentTempC() {
        return currentTempC;
    }

    public String getStateLabel() {
        return stateLabel;
    }

    public int getPacingDelayMs() {
        return pacingDelayMs;
    }
}
