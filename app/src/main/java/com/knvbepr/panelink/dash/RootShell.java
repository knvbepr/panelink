package com.knvbepr.panelink.dash;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Minimal root helper. Used only as a fallback; most metrics are readable directly. */
public final class RootShell {

    private RootShell() {
    }

    private static Boolean cached;

    public static synchronized boolean available() {
        if (cached == null) {
            String out = exec("id", 2000);
            cached = out.contains("uid=0");
        }
        return cached;
    }

    public static String exec(String command, int timeoutMs) {
        Process process = null;
        final StringBuilder out = new StringBuilder();
        try {
            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            final Process running = process;
            final CountDownLatch done = new CountDownLatch(1);
            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(running.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        out.append(line).append('\n');
                    }
                } catch (Exception ignored) {
                    // command ended
                } finally {
                    done.countDown();
                }
            }, "root-reader");
            reader.setDaemon(true);
            reader.start();
            done.await(timeoutMs, TimeUnit.MILLISECONDS);
            return out.toString();
        } catch (Exception e) {
            return "";
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
