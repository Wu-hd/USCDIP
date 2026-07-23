package com.uscdip.backend.service;

import com.uscdip.backend.dto.CoordinateConvertRequest;
import com.uscdip.backend.dto.CoordinateConvertResult;
import com.uscdip.backend.dto.DepthValidationRequest;
import com.uscdip.backend.dto.DepthValidationResult;
import com.uscdip.backend.dto.GisFieldSpecItem;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class GisCoordinateService {

    private static final BigDecimal DEFAULT_TOLERANCE = new BigDecimal("0.05");
    private static final String SRID_WGS84 = "EPSG:4490";
    private static final String SRID_WEB_MERCATOR = "EPSG:3857";

    private final GeometryFactory geometryFactory = new GeometryFactory();
    private final WKTReader wktReader = new WKTReader(geometryFactory);
    private final WKTWriter wktWriter = new WKTWriter();

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

        if (!supportsPair(source, target)) {
            return CoordinateConvertResult.builder()
                    .sourceSrid(source)
                    .targetSrid(target)
                    .sourceGeometry(geometry)
                    .convertedGeometry(geometry)
                    .conversionMode("passthrough")
                    .message("SRID pair unsupported, passthrough applied")
                    .build();
        }

        try {
            return CoordinateConvertResult.builder()
                    .sourceSrid(source)
                    .targetSrid(target)
                    .sourceGeometry(geometry)
                    .convertedGeometry(convertGeometryWkt(source, target, geometry))
                    .conversionMode("service")
                    .message("Converted by backend coordinate service")
                    .build();
        } catch (IllegalArgumentException ex) {
            return CoordinateConvertResult.builder()
                    .sourceSrid(source)
                    .targetSrid(target)
                    .sourceGeometry(geometry)
                    .convertedGeometry(geometry)
                    .conversionMode("passthrough")
                    .message(ex.getMessage())
                    .build();
        }
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

    public String convertGeometryWkt(String sourceSrid, String targetSrid, String geometryWkt) {
        String normalizedSource = normalizeSrid(sourceSrid);
        String normalizedTarget = normalizeSrid(targetSrid);
        if (!supportsPair(normalizedSource, normalizedTarget) || isBlank(geometryWkt)) {
            return geometryWkt;
        }
        if (normalizedSource.equals(normalizedTarget)) {
            return geometryWkt;
        }
        Geometry geometry = readGeometry(geometryWkt);
        Geometry converted = geometry.copy();
        converted.apply(new CoordinateSequenceFilter() {
            @Override
            public void filter(CoordinateSequence seq, int index) {
                double[] convertedPoint = convertPoint(seq.getX(index), seq.getY(index), normalizedSource, normalizedTarget);
                seq.setOrdinate(index, 0, convertedPoint[0]);
                seq.setOrdinate(index, 1, convertedPoint[1]);
            }

            @Override
            public boolean isDone() {
                return false;
            }

            @Override
            public boolean isGeometryChanged() {
                return true;
            }
        });
        converted.geometryChanged();
        return wktWriter.write(converted);
    }

    public BigDecimal[] convertPoint(BigDecimal x, BigDecimal y, String sourceSrid, String targetSrid) {
        double[] converted = convertPoint(
                x.doubleValue(),
                y.doubleValue(),
                normalizeSrid(sourceSrid),
                normalizeSrid(targetSrid)
        );
        return new BigDecimal[]{
                scale(converted[0]),
                scale(converted[1])
        };
    }

    public BigDecimal[] convertEnvelope(
            BigDecimal minX,
            BigDecimal minY,
            BigDecimal maxX,
            BigDecimal maxY,
            String sourceSrid,
            String targetSrid
    ) {
        String normalizedSource = normalizeSrid(sourceSrid);
        String normalizedTarget = normalizeSrid(targetSrid);
        if (normalizedSource.equals(normalizedTarget)) {
            return new BigDecimal[]{scale(minX.doubleValue()), scale(minY.doubleValue()), scale(maxX.doubleValue()), scale(maxY.doubleValue())};
        }
        Envelope envelope = new Envelope(minX.doubleValue(), maxX.doubleValue(), minY.doubleValue(), maxY.doubleValue());
        Coordinate[] corners = {
                new Coordinate(envelope.getMinX(), envelope.getMinY()),
                new Coordinate(envelope.getMinX(), envelope.getMaxY()),
                new Coordinate(envelope.getMaxX(), envelope.getMinY()),
                new Coordinate(envelope.getMaxX(), envelope.getMaxY())
        };
        Envelope converted = new Envelope();
        for (Coordinate corner : corners) {
            double[] mapped = convertPoint(corner.x, corner.y, normalizedSource, normalizedTarget);
            converted.expandToInclude(mapped[0], mapped[1]);
        }
        return new BigDecimal[]{
                scale(converted.getMinX()),
                scale(converted.getMinY()),
                scale(converted.getMaxX()),
                scale(converted.getMaxY())
        };
    }

    public Geometry readGeometry(String geometryWkt) {
        try {
            return wktReader.read(geometryWkt);
        } catch (ParseException ex) {
            throw new IllegalArgumentException("Unsupported WKT geometry: " + geometryWkt, ex);
        }
    }

    public Point geometryAnchor(String geometryWkt) {
        Geometry geometry = readGeometry(geometryWkt);
        if (geometry instanceof Point point) {
            return point;
        }
        return geometry.getCentroid();
    }

    public Envelope geometryEnvelope(String geometryWkt) {
        return readGeometry(geometryWkt).getEnvelopeInternal();
    }

    private double[] convertPoint(double x, double y, String sourceSrid, String targetSrid) {
        if (sourceSrid.equals(targetSrid) || !supportsPair(sourceSrid, targetSrid)) {
            return new double[]{x, y};
        }
        if (SRID_WGS84.equals(sourceSrid) && SRID_WEB_MERCATOR.equals(targetSrid)) {
            return lonLatToWebMercator(x, y);
        }
        if (SRID_WEB_MERCATOR.equals(sourceSrid) && SRID_WGS84.equals(targetSrid)) {
            return webMercatorToLonLat(x, y);
        }
        return new double[]{x, y};
    }

    private boolean supportsPair(String sourceSrid, String targetSrid) {
        return sourceSrid.equals(targetSrid)
                || (SRID_WGS84.equals(sourceSrid) && SRID_WEB_MERCATOR.equals(targetSrid))
                || (SRID_WEB_MERCATOR.equals(sourceSrid) && SRID_WGS84.equals(targetSrid));
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

    private static BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private static String normalizeSrid(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
