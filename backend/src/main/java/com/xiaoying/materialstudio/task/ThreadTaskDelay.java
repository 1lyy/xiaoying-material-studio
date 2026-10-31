package com.xiaoying.materialstudio.task;

import org.springframework.stereotype.Component;

@Component
public class ThreadTaskDelay implements TaskDelay {
    @Override
    public void pause(int milliseconds) throws InterruptedException {
        Thread.sleep(milliseconds);
    }
}
