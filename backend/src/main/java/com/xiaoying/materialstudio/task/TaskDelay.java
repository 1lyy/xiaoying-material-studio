package com.xiaoying.materialstudio.task;

public interface TaskDelay {
    void pause(int milliseconds) throws InterruptedException;
}
