package com.xiaoying.materialstudio.task;

public class TaskStateConflict extends IllegalStateException {
    public TaskStateConflict(String message) { super(message); }
}
