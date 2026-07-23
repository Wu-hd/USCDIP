package com.uscdip.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uscdip.backend.model.BoundarySpec;
import com.uscdip.backend.model.PlatformBoundary;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class BoundarySpecService {

    private static final String SPEC_PATH = "a01-menu-boundary.json";

    private final ObjectMapper objectMapper;

    private BoundarySpec boundarySpec;

    public BoundarySpecService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() throws IOException {
        ClassPathResource resource = new ClassPathResource(SPEC_PATH);
        try (InputStream inputStream = resource.getInputStream()) {
            this.boundarySpec = objectMapper.readValue(inputStream, BoundarySpec.class);
        }
    }

    public BoundarySpec getBoundarySpec() {
        return boundarySpec;
    }

    public List<PlatformBoundary> getPlatforms() {
        return boundarySpec.platforms();
    }

    public Optional<PlatformBoundary> findByPlatformCode(String platformCode) {
        String normalized = platformCode == null ? "" : platformCode.trim().toUpperCase(Locale.ROOT);
        return boundarySpec.platforms()
                .stream()
                .filter(platform -> platform.platformCode().equalsIgnoreCase(normalized))
                .findFirst();
    }
}
