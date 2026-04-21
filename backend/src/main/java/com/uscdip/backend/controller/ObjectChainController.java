package com.uscdip.backend.controller;

import com.uscdip.backend.annotation.AuthzGuard;
import com.uscdip.backend.dto.ObjectChainResponse;
import com.uscdip.backend.dto.ObjectDictionaryItem;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.AuthorizationContext;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.service.CurrentUserResolver;
import com.uscdip.backend.service.ObjectChainService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Validated
@Tag(name = "ObjectChain", description = "A-02 对象字典与对象链查询")
public class ObjectChainController {

    private final ObjectChainService objectChainService;
    private final CurrentUserResolver currentUserResolver;

    public ObjectChainController(ObjectChainService objectChainService, CurrentUserResolver currentUserResolver) {
        this.objectChainService = objectChainService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/object-dictionary")
    @AuthzGuard(entryPermission = "ENTRY:EMGC")
    @Operation(summary = "查询对象字典（全量）")
    public ApiResponse<Map<String, Object>> getObjectDictionary(Authentication authentication) {
        AuthorizationContext authorizationContext = currentUserResolver.requireContext(authentication);
        List<ObjectDictionaryItem> dictionary = objectChainService.getObjectDictionary(authorizationContext);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("definitions", dictionary);
        payload.put("counts", objectChainService.getDictionaryCounts(authorizationContext));
        return ApiResponse.success(payload);
    }

    @GetMapping("/object-dictionary/page")
    @AuthzGuard(entryPermission = "ENTRY:EMGC")
    @Operation(summary = "查询对象字典（分页示例）")
    public ApiResponse<PageResponse<ObjectDictionaryItem>> getObjectDictionaryPage(
            Authentication authentication,
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页条数，最大 200") @RequestParam(defaultValue = "20") @Min(1) @Max(200) int pageSize
    ) {
        return ApiResponse.success(objectChainService.getObjectDictionaryPage(currentUserResolver.requireContext(authentication), page, pageSize));
    }

    @GetMapping("/object-chain/segment/{segmentId}")
    @AuthzGuard(entryPermission = "ENTRY:EMGC")
    @Operation(summary = "按 segment_id 查询完整对象链")
    public ResponseEntity<ApiResponse<ObjectChainResponse>> getObjectChainBySegment(@PathVariable String segmentId, Authentication authentication) {
        return objectChainService.getBySegmentId(segmentId, currentUserResolver.requireContext(authentication))
                .map(chain -> ResponseEntity.ok(ApiResponse.success(chain)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.failure(ErrorCode.SEGMENT_NOT_FOUND, "Segment not found: " + segmentId)));
    }

    @GetMapping("/object-chain/node/{nodeId}")
    @AuthzGuard(entryPermission = "ENTRY:EMGC")
    @Operation(summary = "按 node_id 查询完整对象链")
    public ResponseEntity<ApiResponse<ObjectChainResponse>> getObjectChainByNode(@PathVariable String nodeId, Authentication authentication) {
        return objectChainService.getByNodeId(nodeId, currentUserResolver.requireContext(authentication))
                .map(chain -> ResponseEntity.ok(ApiResponse.success(chain)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.failure(ErrorCode.NODE_NOT_FOUND, "Node not found: " + nodeId)));
    }
}
