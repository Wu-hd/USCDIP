package com.uscdip.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
public class AuthzMatrixSpecService {

    private static final String SPEC_PATH = "a04-authz-matrix.json";

    private final ObjectMapper objectMapper;

    private JsonNode matrixSpec;

    public AuthzMatrixSpecService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() throws IOException {
        ClassPathResource resource = new ClassPathResource(SPEC_PATH);
        try (InputStream inputStream = resource.getInputStream()) {
            this.matrixSpec = objectMapper.readTree(inputStream);
        }
    }

    public JsonNode getMatrixSpec() {
        return matrixSpec;
    }
}
