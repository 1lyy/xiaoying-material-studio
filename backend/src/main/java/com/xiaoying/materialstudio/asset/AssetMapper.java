package com.xiaoying.materialstudio.asset;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AssetMapper {
    int insert(@Param("id") String id, @Param("userId") String userId,
               @Param("templateId") String templateId, @Param("purpose") String purpose,
               @Param("storagePath") String storagePath, @Param("mimeType") String mimeType,
               @Param("sizeBytes") long sizeBytes, @Param("width") int width,
               @Param("height") int height, @Param("createdAt") LocalDateTime createdAt);

    AssetRow findOwned(@Param("id") String id, @Param("userId") String userId);
}
