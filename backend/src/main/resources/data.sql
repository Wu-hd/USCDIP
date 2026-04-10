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

INSERT INTO user_account (user_id, username, display_name, primary_region_id, status, created_at, updated_at) VALUES
('U-ADMIN-001', 'admin', '平台管理员', 'GLOBAL', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('U-DISPATCH-001', 'hz_dispatcher', '杭州区域调度员', 'REGION-HZ', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('U-INSPECT-001', 'zhangsan', '巡检人员张三', 'REGION-HZ', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('U-ALGO-001', 'algo_user', '算法工程师', 'REGION-HZ', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('U-LEADER-001', 'leader_readonly', '领导只读', 'CITY-HZ', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO rbac_role (role_code, role_name, description, read_only) VALUES
('PLATFORM_ADMIN', '平台管理员', '全平台管理权限', FALSE),
('REGIONAL_DISPATCHER', '区域调度员', '仅限辖区调度与订阅', FALSE),
('INSPECTOR', '巡检人员', '仅可查看和处理本人任务', FALSE),
('ALGORITHM_ENGINEER', '算法工程师', '默认仅可访问脱敏特征视图', FALSE),
('LEADER_READONLY', '领导只读', '只读查看聚合态势与报表', TRUE);

INSERT INTO rbac_permission (permission_code, permission_type, resource_code, description) VALUES
('ENTRY:PORTAL', 'ENTRY', 'PORTAL', '门户入口'),
('ENTRY:MGMT', 'ENTRY', 'MGMT', '综合管理平台入口'),
('ENTRY:EMGC', 'ENTRY', 'EMGC', '应急指挥平台入口'),
('ENTRY:DIAG', 'ENTRY', 'DIAG', '智能诊断中枢入口'),
('ENTRY:SUPPORT', 'ENTRY', 'SUPPORT', '平台支撑层入口'),
('MENU:ASSET:READ', 'MENU', 'ASSET', '资产查看'),
('MENU:ASSET:WRITE', 'MENU', 'ASSET', '资产编辑'),
('MENU:WORKORDER:READ', 'MENU', 'WORKORDER', '工单查看'),
('MENU:WORKORDER:DISPATCH', 'MENU', 'WORKORDER', '工单派发'),
('MENU:MODEL:READ', 'MENU', 'MODEL', '模型查看'),
('MENU:MODEL:WRITE', 'MENU', 'MODEL', '模型发布与回退'),
('MENU:DASHBOARD:READ', 'MENU', 'DASHBOARD', '态势看板只读');

INSERT INTO rbac_role_permission (role_code, permission_code) VALUES
('PLATFORM_ADMIN', 'ENTRY:PORTAL'),
('PLATFORM_ADMIN', 'ENTRY:MGMT'),
('PLATFORM_ADMIN', 'ENTRY:EMGC'),
('PLATFORM_ADMIN', 'ENTRY:DIAG'),
('PLATFORM_ADMIN', 'ENTRY:SUPPORT'),
('PLATFORM_ADMIN', 'MENU:ASSET:READ'),
('PLATFORM_ADMIN', 'MENU:ASSET:WRITE'),
('PLATFORM_ADMIN', 'MENU:WORKORDER:READ'),
('PLATFORM_ADMIN', 'MENU:WORKORDER:DISPATCH'),
('PLATFORM_ADMIN', 'MENU:MODEL:READ'),
('PLATFORM_ADMIN', 'MENU:MODEL:WRITE'),
('PLATFORM_ADMIN', 'MENU:DASHBOARD:READ'),
('REGIONAL_DISPATCHER', 'ENTRY:MGMT'),
('REGIONAL_DISPATCHER', 'ENTRY:EMGC'),
('REGIONAL_DISPATCHER', 'MENU:ASSET:READ'),
('REGIONAL_DISPATCHER', 'MENU:WORKORDER:READ'),
('REGIONAL_DISPATCHER', 'MENU:WORKORDER:DISPATCH'),
('INSPECTOR', 'ENTRY:EMGC'),
('INSPECTOR', 'MENU:WORKORDER:READ'),
('ALGORITHM_ENGINEER', 'ENTRY:DIAG'),
('ALGORITHM_ENGINEER', 'MENU:MODEL:READ'),
('LEADER_READONLY', 'ENTRY:EMGC'),
('LEADER_READONLY', 'MENU:DASHBOARD:READ'),
('LEADER_READONLY', 'MENU:WORKORDER:READ');

INSERT INTO rbac_user_role (user_id, role_code) VALUES
('U-ADMIN-001', 'PLATFORM_ADMIN'),
('U-DISPATCH-001', 'REGIONAL_DISPATCHER'),
('U-INSPECT-001', 'INSPECTOR'),
('U-ALGO-001', 'ALGORITHM_ENGINEER'),
('U-LEADER-001', 'LEADER_READONLY');

INSERT INTO user_data_scope (user_id, scope_type, scope_value) VALUES
('U-DISPATCH-001', 'REGION', 'REGION-HZ'),
('U-DISPATCH-001', 'REGION', 'REGION-BINJIANG'),
('U-INSPECT-001', 'REGION', 'REGION-HZ'),
('U-INSPECT-001', 'ASSIGNEE', 'zhangsan'),
('U-ALGO-001', 'DATA_VIEW', 'MASKED_FEATURE'),
('U-LEADER-001', 'REGION_AGGREGATE', 'CITY-HZ');

INSERT INTO topic_scope_rule (role_code, topic_pattern, description) VALUES
('PLATFORM_ADMIN', '#', '全量订阅'),
('REGIONAL_DISPATCHER', 'region.{regionId}.alerts.#', '辖区告警订阅'),
('REGIONAL_DISPATCHER', 'region.{regionId}.workorder.#', '辖区工单订阅'),
('INSPECTOR', 'user.{userId}.workorder.#', '本人工单订阅'),
('ALGORITHM_ENGINEER', 'diag.model.#', '模型特征与结果订阅'),
('LEADER_READONLY', 'city.aggregate.#', '聚合态势只读订阅');
