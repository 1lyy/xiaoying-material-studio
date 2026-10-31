package com.xiaoying.materialstudio.identity;

public record UserResponse(String id, String displayName, String demoUserKey) {
    public static UserResponse from(UserRow row) {
        return new UserResponse(row.id(), row.displayName(), row.demoKey());
    }
}
