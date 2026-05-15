package com.uscdip.backend.dto;

import java.util.List;
import java.util.Map;

public record MasterDataRecordResponse(
        String objectType,
        String objectId,
        String objectName,
        String status,
        String regionId,
        Map<String, List<String>> relatedObjectIds,
        Map<String, Object> attributes
) {
}
