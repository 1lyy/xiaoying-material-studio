package com.xiaoying.materialstudio.task;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskMapper {
    int insert(Map<String, Object> row);
    int insertAsset(@Param("taskId") String taskId, @Param("assetId") String assetId,
                    @Param("position") int position);
    int insertStep(@Param("taskId") String taskId, @Param("stage") String stage,
                   @Param("order") int order);
    TaskRow findOwned(@Param("id") String id, @Param("userId") String userId);
    List<TaskRow> history(@Param("userId") String userId);
    TaskRow findById(@Param("id") String id);
    List<TaskStepRow> steps(@Param("taskId") String taskId);
    List<String> assetIds(@Param("taskId") String taskId);
    String queueHead();
    int claim(@Param("id") String id, @Param("now") LocalDateTime now);
    int startTaskStage(@Param("id") String id, @Param("previous") String previous,
                       @Param("stage") String stage);
    int startStep(@Param("id") String id, @Param("stage") String stage,
                  @Param("now") LocalDateTime now, @Param("order") int order);
    int completeStep(@Param("id") String id, @Param("stage") String stage,
                     @Param("now") LocalDateTime now);
    int bumpAfterStep(@Param("id") String id, @Param("stage") String stage);
    int succeed(@Param("id") String id, @Param("resultAssetId") String resultAssetId,
                @Param("now") LocalDateTime now);
    int failStep(@Param("id") String id, @Param("stage") String stage,
                 @Param("message") String message, @Param("now") LocalDateTime now);
    int skipLater(@Param("id") String id, @Param("order") int order);
    int failTask(@Param("id") String id, @Param("stage") String stage,
                 @Param("code") String code, @Param("message") String message,
                 @Param("now") LocalDateTime now);
    List<TaskRow> running();
    int interruptStep(@Param("id") String id, @Param("stage") String stage,
                      @Param("message") String message, @Param("now") LocalDateTime now);
    int interruptTask(@Param("id") String id, @Param("stage") String stage,
                      @Param("message") String message, @Param("now") LocalDateTime now);
}
