package com.uscdip.backend.dto;

import com.uscdip.backend.entity.DeviceEntity;
import com.uscdip.backend.entity.FacilityEntity;
import com.uscdip.backend.entity.IncidentEntity;
import com.uscdip.backend.entity.ModelResultEntity;
import com.uscdip.backend.entity.NodeEntity;
import com.uscdip.backend.entity.SegmentEntity;
import com.uscdip.backend.entity.WorkOrderEntity;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ObjectChainResponse {

    private String queryType;
    private String segmentId;
    private String nodeId;
    private List<NodeEntity> nodes;
    private List<SegmentEntity> segments;
    private List<FacilityEntity> facilities;
    private List<DeviceEntity> devices;
    private List<IncidentEntity> incidents;
    private List<WorkOrderEntity> workOrders;
    private List<ModelResultEntity> modelResults;
}
