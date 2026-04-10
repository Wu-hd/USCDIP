package com.uscdip.backend.service;

import com.uscdip.backend.dto.CoordinateConvertRequest;
import com.uscdip.backend.dto.CoordinateConvertResult;
import com.uscdip.backend.dto.DepthValidationRequest;
import com.uscdip.backend.dto.DepthValidationResult;
import com.uscdip.backend.dto.GisFieldSpecItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GisCoordinateService {

    private static final Pattern POINT_PATTERN = Pattern.compile("POINT\\(([-0-9.]+)\\s+([-0-9.]+)\\)");
    private static final BigDecimal DEFAULT_TOLERANCE = new BigDecimal("0.05");

    public List<GisFieldSpecItem> getFieldSpec() {
        return List.of(
                new GisFieldSpecItem("authority_srid", "string", true, "测绘确权坐标系，原始数据唯一坐标口径", "storage/input", "EPSG:4490"),
                new GisFieldSpecItem("display_srid", "string", true, "前端展示坐标系，统一由后端转换输出", "storage/output", "EPSG:3857"),
                new GisFieldSpecItem("geometry_2d", "string", true, "二维空间几何，使用 WKT POINT/LINESTRING", "storage/output", "POINT(120.1533 30.2741)"),
                new GisFieldSpecItem("z_top", "decimal", true, "管体顶部高程", "depth", "2.50"),
                new GisFieldSpecItem("z_bottom", "decimal", true, "管体底部高程", "depth", "-1.20"),
                new GisFieldSpecItem("bury_depth", "decimal", true, "埋深，定义为 |z_top - z_bottom|", "depth", "3.70"),
                new GisFieldSpecItem("elevation_ref", "string", false, "高程基准，如黄海高程基准", "depth", "MSL")
        );
    }

    public CoordinateConvertResult convert(CoordinateConvertRequest request) {
        if (request == null || isBlank(request.getAuthoritySrid()) || isBlank(request.getDisplaySrid()) || isBlank(request.getGeometry2d())) {
            return CoordinateConvertResult.builder()
                    .sourceSrid(request == null ? null : request.getAuthoritySrid())
                    .targetSrid(request == null ? null : request.getDisplaySrid())
                    .sourceGeometry(request == null ? null : request.getGeometry2d())
                    .convertedGeometry(null)
                    .conversionMode("invalid")
                    .message("authority_srid, display_srid, geometry_2d are required")
                    .build();
        }

        String source = request.getAuthoritySrid().trim().toUpperCase();
        String target = request.getDisplaySrid().trim().toUpperCase();
        String geometry = request.getGeometry2d().trim();

        Matcher matcher = POINT_PATTERN.matcher(geometry);
        if (!matcher.matches()) {
            return CoordinateConvertResult.builder()
                    .sourceSrid(source)
                    .targetSrid(target)
                    .sourceGeometry(geometry)
                    .convertedGeometry(geometry)
                    .conversionMode("passthrough")
                    .message("Only POINT WKT is actively converted in current version")
                    .build();
        }

        double x = Double.parseDouble(matcher.group(1));
        double y = Double.parseDouble(matcher.group(2));

        if (source.equals(target)) {
            return CoordinateConvertResult.builder()
                    .sourceSrid(source)
                    .targetSrid(target)
                    .sourceGeometry(geometry)
                    .convertedGeometry(geometry)
                    .conversionMode("identity")
                    .message("No conversion needed")
                    .build();
        }

        if ("EPSG:4490".equals(source) && "EPSG:3857".equals(target)) {
            double[] converted = lonLatToWebMercator(x, y);
            return CoordinateConvertResult.builder()
                    .sourceSrid(source)
                    .targetSrid(target)
                    .sourceGeometry(geometry)
                    .convertedGeometry(formatPoint(converted[0], converted[1]))
                    .conversionMode("service")
                    .message("Converted by backend coordinate service")
                    .build();
        }

        if ("EPSG:3857".equals(source) && "EPSG:4490".equals(target)) {
            double[] converted = webMercatorToLonLat(x, y);
            return CoordinateConvertResult.builder()
                    .sourceSrid(source)
                    .targetSrid(target)
                    .sourceGeometry(geometry)
                    .convertedGeometry(formatPoint(converted[0], converted[1]))
                    .conversionMode("service")
                    .message("Converted by backend coordinate service")
                    .build();
        }

        return CoordinateConvertResult.builder()
                .sourceSrid(source)
                .targetSrid(target)
                .sourceGeometry(geometry)
                .convertedGeometry(geometry)
                .conversionMode("passthrough")
                .message("SRID pair unsupported, passthrough applied")
                .build();
    }

    public DepthValidationResult validateDepth(DepthValidationRequest request) {
        if (request == null || request.getZTop() == null || request.getZBottom() == null || request.getBuryDepth() == null) {
            return DepthValidationResult.builder()
                    .valid(false)
                    .expectedBuryDepth(null)
                    .actualBuryDepth(request == null ? null : request.getBuryDepth())
                    .delta(null)
                    .tolerance(request == null ? DEFAULT_TOLERANCE : defaultTolerance(request.getTolerance()))
                    .message("z_top, z_bottom, bury_depth are required")
                    .build();
        }

        BigDecimal expected = request.getZTop().subtract(request.getZBottom()).abs().setScale(2, RoundingMode.HALF_UP);
        BigDecimal actual = request.getBuryDepth().setScale(2, RoundingMode.HALF_UP);
        BigDecimal delta = expected.subtract(actual).abs().setScale(2, RoundingMode.HALF_UP);
        BigDecimal tolerance = defaultTolerance(request.getTolerance());
        boolean valid = delta.compareTo(tolerance) <= 0;

        String message = valid
                ? "Depth fields are consistent"
                : "Depth mismatch: expected bury_depth=|z_top-z_bottom|";

        return DepthValidationResult.builder()
                .valid(valid)
                .expectedBuryDepth(expected)
                .actualBuryDepth(actual)
                .delta(delta)
                .tolerance(tolerance)
                .message(message)
                .build();
    }

    private static String formatPoint(double x, double y) {
        return "POINT(" + round(x) + " " + round(y) + ")";
    }

    private static String round(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static double[] lonLatToWebMercator(double lon, double lat) {
        double x = lon * 20037508.34 / 180.0;
        double y = Math.log(Math.tan((90.0 + lat) * Math.PI / 360.0)) / (Math.PI / 180.0);
        y = y * 20037508.34 / 180.0;
        return new double[]{x, y};
    }

    private static double[] webMercatorToLonLat(double x, double y) {
        double lon = x / 20037508.34 * 180.0;
        double lat = y / 20037508.34 * 180.0;
        lat = 180.0 / Math.PI * (2 * Math.atan(Math.exp(lat * Math.PI / 180.0)) - Math.PI / 2.0);
        return new double[]{lon, lat};
    }

    private static BigDecimal defaultTolerance(BigDecimal tolerance) {
        return tolerance == null ? DEFAULT_TOLERANCE : tolerance.abs().setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
