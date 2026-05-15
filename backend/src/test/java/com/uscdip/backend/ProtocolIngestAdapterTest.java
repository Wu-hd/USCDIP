package com.uscdip.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uscdip.backend.dto.ProtocolAdaptRequest;
import com.uscdip.backend.dto.UnifiedIngestMetricDto;
import com.uscdip.backend.service.ModbusProtocolIngestAdapter;
import com.uscdip.backend.service.MqttProtocolIngestAdapter;
import com.uscdip.backend.service.NbIotProtocolIngestAdapter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

class ProtocolIngestAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MqttProtocolIngestAdapter mqttAdapter = new MqttProtocolIngestAdapter(objectMapper);
    private final ModbusProtocolIngestAdapter modbusAdapter = new ModbusProtocolIngestAdapter(objectMapper);
    private final NbIotProtocolIngestAdapter nbIotAdapter = new NbIotProtocolIngestAdapter(objectMapper);

    @Test
    void mqttAdapterMapsTimesAndAttributes() throws Exception {
        ProtocolAdaptRequest request = new ProtocolAdaptRequest(
                "EDGE_GATEWAY",
                "EDGE-HZ-GW-01",
                "TRACE-UNIT-001",
                false,
                objectMapper.readTree("""
                        {
                          "deviceId":"DEV-001",
                          "metricCode":"PRESSURE",
                          "value":0.86,
                          "eventTime":"2026-04-23T08:00:00",
                          "recvTime":"2026-04-23T08:00:03",
                          "deviceTime":"2026-04-23T08:00:00",
                          "topic":"region/hz/dev-001/pressure",
                          "qos":1
                        }
                        """)
        );

        List<UnifiedIngestMetricDto> metrics = mqttAdapter.adapt(request);

        Assertions.assertEquals(1, metrics.size());
        Assertions.assertEquals("MQTT", metrics.get(0).protocolType());
        Assertions.assertEquals("2026-04-23T08:00:03", metrics.get(0).recvTime().toString());
        Assertions.assertEquals("region/hz/dev-001/pressure", metrics.get(0).attributes().get("topic").asText());
    }

    @Test
    void modbusAdapterMapsGatewayAndControllerTimesSeparately() throws Exception {
        ProtocolAdaptRequest request = new ProtocolAdaptRequest(
                "PLC_GATEWAY",
                "PLC-HZ-01",
                "TRACE-UNIT-002",
                false,
                objectMapper.readTree("""
                        {
                          "deviceId":"DEV-002",
                          "registerAddress":"40001",
                          "registerValue":42.5,
                          "sampledAt":"2026-04-23T08:10:00",
                          "gatewayReceivedAt":"2026-04-23T08:10:02",
                          "controllerTime":"2026-04-23T08:09:59",
                          "slaveId":"7"
                        }
                        """)
        );

        List<UnifiedIngestMetricDto> metrics = modbusAdapter.adapt(request);

        Assertions.assertEquals(1, metrics.size());
        Assertions.assertEquals("40001", metrics.get(0).metricCode());
        Assertions.assertEquals(LocalDateTime.parse("2026-04-23T08:10:00"), metrics.get(0).eventTime());
        Assertions.assertEquals(LocalDateTime.parse("2026-04-23T08:10:02"), metrics.get(0).recvTime());
        Assertions.assertEquals(LocalDateTime.parse("2026-04-23T08:09:59"), metrics.get(0).deviceTime());
    }

    @Test
    void nbIotAdapterMapsTerminalAndCloudTimesSeparately() throws Exception {
        ProtocolAdaptRequest request = new ProtocolAdaptRequest(
                "OPERATOR_PUSH",
                "NB-HZ-01",
                "TRACE-UNIT-003",
                true,
                objectMapper.readTree("""
                        {
                          "terminalId":"DEV-003",
                          "metric":"VIBRATION",
                          "reading":0.12,
                          "eventTime":"2026-04-22T21:20:00",
                          "cloudReceiveTime":"2026-04-23T08:20:10",
                          "deviceReportedAt":"2026-04-22T21:19:58",
                          "imei":"867530900000001"
                        }
                        """)
        );

        List<UnifiedIngestMetricDto> metrics = nbIotAdapter.adapt(request);

        Assertions.assertEquals(1, metrics.size());
        Assertions.assertEquals("NB_IOT", metrics.get(0).protocolType());
        Assertions.assertTrue(metrics.get(0).isBackfill());
        Assertions.assertEquals("2026-04-23T08:20:10", metrics.get(0).recvTime().toString());
        Assertions.assertEquals("867530900000001", metrics.get(0).attributes().get("imei").asText());
    }
}
