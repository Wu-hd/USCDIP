package com.uscdip.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
public class RealtimeLinkSpecService {

    private static final String SPEC_PATH = "a05-realtime-link-spec.json";

    private final ObjectMapper objectMapper;

    private JsonNode spec;

    public RealtimeLinkSpecService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() throws IOException {
        ClassPathResource resource = new ClassPathResource(SPEC_PATH);
        try (InputStream inputStream = resource.getInputStream()) {
            this.spec = objectMapper.readTree(inputStream);
        }
    }

    public JsonNode getSpec() {
        return spec;
    }
}
