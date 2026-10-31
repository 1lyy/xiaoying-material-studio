package com.xiaoying.materialstudio.task;

import com.xiaoying.materialstudio.asset.AssetMapper;
import com.xiaoying.materialstudio.asset.AssetRow;
import com.xiaoying.materialstudio.asset.PrivateImageStorage;
import com.xiaoying.materialstudio.template.TemplateResponse;
import com.xiaoying.materialstudio.web.ApiException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class TaskAssetValidator {
    private final AssetMapper assets;
    private final PrivateImageStorage storage;

    public TaskAssetValidator(AssetMapper assets, PrivateImageStorage storage) {
        this.assets = assets;
        this.storage = storage;
    }

    public List<AssetRow> validate(String userId, TemplateResponse template, List<String> ids,
                                   boolean checkFiles) {
        List<AssetRow> rows = new ArrayList<>();
        for (String id : ids) {
            AssetRow row = assets.findOwned(id, userId);
            if (row == null || !"UPLOAD".equals(row.purpose())
                    || !template.id().equals(row.templateId())
                    || !template.acceptedMimeTypes().contains(row.mimeType())
                    || row.sizeBytes() < 1 || row.sizeBytes() > template.maxFileSizeBytes()
                    || row.sizeBytes() > PrivateImageStorage.MAX_BYTES) throw notFound();
            if (checkFiles) {
                try {
                    storage.verifyStored(row.storagePath(), row.mimeType(), row.sizeBytes(),
                            row.width(), row.height());
                } catch (ApiException exception) {
                    throw notFound();
                }
            }
            rows.add(row);
        }
        return rows;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "素材不存在或不符合模板要求");
    }
}
