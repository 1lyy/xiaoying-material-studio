package com.xiaoying.materialstudio.asset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetRecordWriter {
    private final AssetMapper mapper;

    public AssetRecordWriter(AssetMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public void insert(AssetRow row) {
        if (mapper.insert(row.id(), row.userId(), row.templateId(), row.purpose(), row.storagePath(),
                row.mimeType(), row.sizeBytes(), row.width(), row.height(), row.createdAt()) != 1) {
            throw new IllegalStateException("Asset insert did not affect one row");
        }
    }
}
