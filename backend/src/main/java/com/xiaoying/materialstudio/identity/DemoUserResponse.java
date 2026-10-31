package com.xiaoying.materialstudio.identity;

public record DemoUserResponse(String id, String demoUserKey, String displayName) {
    public static DemoUserResponse from(UserRow row) {
        return new DemoUserResponse(row.id(), row.demoKey(), row.displayName());
    }
}
