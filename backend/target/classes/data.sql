INSERT INTO node (node_id, node_name, node_type, status, authority_srid, display_srid, geometry_2d, z_top, z_bottom, bury_depth, elevation_ref, created_at, updated_at) VALUES
('NODE-001', '北区入口节点', 'inlet', 'ACTIVE', 'EPSG:4490', 'EPSG:3857', 'POINT(120.1533 30.2741)', 2.50, -1.20, 3.70, 'MSL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('NODE-002', '主干分流节点', 'junction', 'ACTIVE', 'EPSG:4490', 'EPSG:3857', 'POINT(120.1634 30.2842)', 2.30, -1.40, 3.70, 'MSL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('NODE-003', '东区泵站节点', 'pump_station', 'ACTIVE', 'EPSG:4490', 'EPSG:3857', 'POINT(120.1735 30.2943)', 2.10, -1.70, 3.80, 'MSL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO segment (segment_id, segment_name, start_node_id, end_node_id, segment_type, length_meter, status, created_at, updated_at) VALUES
('SEG-001', '北区主干一段', 'NODE-001', 'NODE-002', 'trunk', 520.40, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SEG-002', '东区分支一段', 'NODE-002', 'NODE-003', 'branch', 310.80, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO facility (facility_id, facility_name, segment_id, node_id, facility_type, position_meter, status, created_at, updated_at) VALUES
('FAC-001', '阀门井-01', 'SEG-001', 'NODE-001', 'valve_well', 80.00, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('FAC-002', '检测井-02', 'SEG-001', 'NODE-002', 'inspection_well', 250.00, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('FAC-003', '阀门井-03', 'SEG-002', 'NODE-003', 'valve_well', 120.00, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO device (device_id, device_name, facility_id, segment_id, node_id, protocol_type, last_heartbeat, status, created_at, updated_at) VALUES
('DEV-001', '压力传感器-01', 'FAC-001', 'SEG-001', 'NODE-001', 'MQTT', CURRENT_TIMESTAMP, 'ONLINE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('DEV-002', '流量计-02', 'FAC-002', 'SEG-001', 'NODE-002', 'MODBUS', CURRENT_TIMESTAMP, 'ONLINE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('DEV-003', '振动传感器-03', 'FAC-003', 'SEG-002', 'NODE-003', 'MQTT', CURRENT_TIMESTAMP, 'ONLINE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO incident (incident_id, segment_id, node_id, title, severity, status, created_at, updated_at) VALUES
('INC-001', 'SEG-001', 'NODE-002', '压力异常升高', 'HIGH', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('INC-002', 'SEG-002', 'NODE-003', '流量下降预警', 'MEDIUM', 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO work_order (work_order_id, incident_id, segment_id, node_id, assignee, status, created_at, updated_at) VALUES
('WO-001', 'INC-001', 'SEG-001', 'NODE-002', 'zhangsan', 'DISPATCHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('WO-002', 'INC-002', 'SEG-002', 'NODE-003', 'lisi', 'CREATED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO model_result (model_result_id, segment_id, node_id, model_code, model_version, status, created_at, updated_at) VALUES
('MR-001', 'SEG-001', 'NODE-002', 'LEAK-RISK', 'v1.0.0', 'VALID', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('MR-002', 'SEG-002', 'NODE-003', 'PRESSURE-ANOMALY', 'v1.0.0', 'VALID', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
