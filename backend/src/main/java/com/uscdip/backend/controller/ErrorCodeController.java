package com.uscdip.backend.controller;

import com.uscdip.backend.model.ApiErrorCode;
import com.uscdip.backend.model.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ErrorCodeController {

    @GetMapping("/error-codes")
    public ApiResponse<List<Map<String, String>>> listErrorCodes() {
        List<Map<String, String>> items = java.util.Arrays.stream(ApiErrorCode.values())
                .map(code -> Map.of(
                        "code", code.code(),
                        "message", code.defaultMessage()
                ))
                .toList();
        return ApiResponse.success(items);
    }
}
