package com.knvbepr.panelink.dash;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

/** Background sampler. One collection at a time; the next tick is scheduled after it. */
public final class DashPoller {

    public interface Listener {
        void onData(PanelData data);
    }

    private final Context context;
    private final DashSettings settings;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());

    private HandlerThread thread;
    private Handler handler;
    private SystemCollector collector;
    private volatile boolean running;

    public DashPoller(Context context, DashSettings settings, Listener listener) {
        this.context = context.getApplicationContext();
        this.settings = settings;
        this.listener = listener;
    }

    public void start() {
        stop();
        running = true;
        thread = new HandlerThread("panel-poller");
        thread.start();
        handler = new Handler(thread.getLooper());
        collector = new SystemCollector(context, settings);
        handler.post(tick);
    }

    public void stop() {
        running = false;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        if (thread != null) {
            thread.quitSafely();
            thread = null;
        }
        handler = null;
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            final PanelData data;
            try {
                data = collector.collect();
            } catch (Throwable t) {
                // Never let one failed sample kill the panel.
                if (handler != null) {
                    handler.postDelayed(this, Math.max(100, settings.intervalMs));
                }
                return;
            }
            main.post(() -> {
                if (running) {
                    listener.onData(data);
                }
            });
            if (handler != null) {
                handler.postDelayed(this, Math.max(100, settings.intervalMs));
            }
        }
    };
}
