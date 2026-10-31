package com.xiaoying.materialstudio.task;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TaskWorkerTest {
    @Test
    void startupConvergesBeforeClaimAndStopEndsLifecycle() throws Exception {
        TaskTransactions transactions = mock(TaskTransactions.class);
        TaskPipeline pipeline = mock(TaskPipeline.class);
        CountDownLatch processed = new CountDownLatch(1);
        when(transactions.claimHead()).thenReturn("task", (String) null);
        doAnswer(invocation -> {
            processed.countDown();
            return null;
        }).when(pipeline).run("task");
        TaskWorker worker = new TaskWorker(transactions, pipeline);
        try {
            worker.start();
            assertTrue(processed.await(2, TimeUnit.SECONDS));
            var order = inOrder(transactions, pipeline);
            order.verify(transactions).convergeInterrupted();
            order.verify(transactions).claimHead();
            order.verify(pipeline).run("task");
        } finally {
            worker.stop();
        }
        assertFalse(worker.isRunning());
    }
}
