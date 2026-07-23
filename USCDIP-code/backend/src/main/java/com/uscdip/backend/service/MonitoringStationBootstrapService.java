package com.uscdip.backend.service;

import com.uscdip.backend.entity.MonitoringStationEntity;
import com.uscdip.backend.entity.ObjectScopeBindingEntity;
import com.uscdip.backend.repository.MonitoringStationRepository;
import com.uscdip.backend.repository.ObjectScopeBindingRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class MonitoringStationBootstrapService {

    public static final String STATION_ID = "ST-HUT-ZZ-001";
    public static final String REGION_ID = "REGION-HN-ZZ";

    private final MonitoringStationRepository monitoringStationRepository;
    private final ObjectScopeBindingRepository objectScopeBindingRepository;
    private final ObjectGeoIndexService objectGeoIndexService;

    public MonitoringStationBootstrapService(
            MonitoringStationRepository monitoringStationRepository,
            ObjectScopeBindingRepository objectScopeBindingRepository,
            ObjectGeoIndexService objectGeoIndexService
    ) {
        this.monitoringStationRepository = monitoringStationRepository;
        this.objectScopeBindingRepository = objectScopeBindingRepository;
        this.objectGeoIndexService = objectGeoIndexService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeHunanStation() {
        LocalDateTime now = LocalDateTime.now();
        if (!monitoringStationRepository.existsById(STATION_ID)) {
            monitoringStationRepository.saveAndFlush(new MonitoringStationEntity(
                    STATION_ID,
                    "湖南工业大学管网监测点",
                    "ACTIVE",
                    "430000",
                    "430200",
                    new BigDecimal("113.107385"),
                    new BigDecimal("27.818289"),
                    "EPSG:4490",
                    "EPSG:4490",
                    "{\"campus\":\"河西主校区\",\"city\":\"株洲市\"}",
                    "{\"type\":\"FeatureCollection\",\"features\":[]}",
                    now,
                    now,
                    null
            ));
        }

        if (objectScopeBindingRepository.findByObjectTypeAndObjectId(ObjectScopeService.OBJECT_STATION, STATION_ID).isEmpty()) {
            objectScopeBindingRepository.save(new ObjectScopeBindingEntity(
                    "OSB-ST-HUT-ZZ-001",
                    ObjectScopeService.OBJECT_STATION,
                    STATION_ID,
                    REGION_ID,
                    null,
                    null,
                    ObjectScopeService.LEVEL_DETAIL,
                    now,
                    now
            ));
        }
        objectGeoIndexService.refreshObject(ObjectScopeService.OBJECT_STATION, STATION_ID);
    }
}
