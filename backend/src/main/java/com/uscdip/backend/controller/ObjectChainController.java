package com.uscdip.backend.controller;

import com.uscdip.backend.dto.ObjectChainResponse;
import com.uscdip.backend.dto.ObjectDictionaryItem;
import com.uscdip.backend.model.ApiErrorCode;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.service.ObjectChainService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ObjectChainController {

    private final ObjectChainService objectChainService;

    public ObjectChainController(ObjectChainService objectChainService) {
        this.objectChainService = objectChainService;
    }

    @GetMapping("/object-dictionary")
    public ApiResponse<Map<String, Object>> getObjectDictionary() {
        List<ObjectDictionaryItem> dictionary = objectChainService.getObjectDictionary();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("definitions", dictionary);
        payload.put("counts", objectChainService.getDictionaryCounts());
        return ApiResponse.success(payload);
    }

    @GetMapping("/object-chain/segment/{segmentId}")
    public ResponseEntity<ApiResponse<ObjectChainResponse>> getObjectChainBySegment(@PathVariable String segmentId) {
        return objectChainService.getBySegmentId(segmentId)
                .map(chain -> ResponseEntity.ok(ApiResponse.success(chain)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(ApiErrorCode.RESOURCE_NOT_FOUND.code(), "Segment not found: " + segmentId, null)));
    }

    @GetMapping("/object-chain/node/{nodeId}")
    public ResponseEntity<ApiResponse<ObjectChainResponse>> getObjectChainByNode(@PathVariable String nodeId) {
        return objectChainService.getByNodeId(nodeId)
                .map(chain -> ResponseEntity.ok(ApiResponse.success(chain)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.failure(ApiErrorCode.RESOURCE_NOT_FOUND.code(), "Node not found: " + nodeId, null)));
    }
}
