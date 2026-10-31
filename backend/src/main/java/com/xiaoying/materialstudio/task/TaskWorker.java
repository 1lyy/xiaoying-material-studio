package com.xiaoying.materialstudio.task;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true", matchIfMissing = true)
public class TaskWorker implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(TaskWorker.class);
    private final TaskTransactions transactions;
    private final TaskPipeline pipeline;
    private ScheduledExecutorService executor;
    private volatile boolean running;

    public TaskWorker(TaskTransactions transactions, TaskPipeline pipeline) {
        this.transactions = transactions;
        this.pipeline = pipeline;
    }

    @Override
    public synchronized void start() {
        if (running) return;
        transactions.convergeInterrupted();
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "material-task-worker");
            thread.setDaemon(false);
            return thread;
        });
        running = true;
        executor.scheduleWithFixedDelay(this::tick, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void tick() {
        if (!running || Thread.currentThread().isInterrupted()) return;
        try {
            String id = transactions.claimHead();
            if (id != null) pipeline.run(id);
        } catch (Exception exception) {
            log.error("Task worker tick failed", exception);
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (executor != null) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Task worker did not stop within five seconds");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() { return running; }
}
