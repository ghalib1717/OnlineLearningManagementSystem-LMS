package lms;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/** Background thread that persists system activity events without slowing down web requests. */
public final class ActivityLog implements Runnable {
    private static final BlockingQueue<String> QUEUE = new LinkedBlockingQueue<>();

    private ActivityLog() { }

    public static void start() {
        Thread t = new Thread(new ActivityLog(), "activity-logger");
        t.setDaemon(true);
        t.start();
    }

    public static void log(String message) { QUEUE.offer(message); }

    @Override
    public void run() {
        while (true) {
            try {
                Db.exec("INSERT INTO activity(msg) VALUES(?)", QUEUE.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (LmsException e) {
                System.err.println("Activity log failed: " + e.getMessage());
            }
        }
    }
}
