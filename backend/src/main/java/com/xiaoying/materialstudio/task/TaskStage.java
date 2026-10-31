package com.xiaoying.materialstudio.task;

public enum TaskStage {
    ASSET_CHECK, GENERATE, FINALIZE;

    public int order() { return ordinal(); }

    public String displayName() {
        return switch (this) {
            case ASSET_CHECK -> "素材检查";
            case GENERATE -> "模拟生成";
            case FINALIZE -> "结果整理";
        };
    }
}
