package com.uscdip.backend.dto;

import java.util.List;
import java.util.Map;

public record GisObjectRecordResponse(
        String objectType,
        String objectId,
        String objectName,
        String regionId,
        String authoritySrid,
        String displaySrid,
        String geometry2d,
        GisPointResponse anchorPoint,
        GisBboxResponse bbox,
        Map<String, List<String>> relatedObjectIds,
        Map<String, Object> attributes
) {
}
