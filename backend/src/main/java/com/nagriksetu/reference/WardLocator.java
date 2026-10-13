package com.nagriksetu.reference;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nagriksetu.config.NagrikProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Determines the ward containing a location using configured ward boundary polygons.
 * A ward is returned only when the point lies inside exactly one boundary; otherwise the
 * complaint must be verified manually by an administrator.
 */
@Service
public class WardLocator {

    private static final Logger log = LoggerFactory.getLogger(WardLocator.class);

    private final WardRepository wardRepository;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public WardLocator(WardRepository wardRepository, ObjectMapper objectMapper, NagrikProperties properties) {
        this.wardRepository = wardRepository;
        this.objectMapper = objectMapper;
        this.enabled = properties.wardMatching() == null || properties.wardMatching().enabled();
    }

    @Transactional(readOnly = true)
    public Optional<Ward> locate(double latitude, double longitude) {
        if (!enabled) {
            return Optional.empty();
        }
        List<Ward> matches = new ArrayList<>();
        for (Ward ward : wardRepository.findByBoundaryJsonIsNotNull()) {
            List<double[]> polygon = parse(ward);
            if (polygon.size() >= 3 && contains(polygon, latitude, longitude)) {
                matches.add(ward);
            }
        }
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private List<double[]> parse(Ward ward) {
        try {
            List<List<Double>> raw = objectMapper.readValue(ward.getBoundaryJson(), new TypeReference<>() { });
            List<double[]> points = new ArrayList<>(raw.size());
            for (List<Double> p : raw) {
                if (p.size() == 2) {
                    points.add(new double[] {p.get(0), p.get(1)});
                }
            }
            return points;
        } catch (Exception e) {
            log.warn("Ignoring unparseable boundary for ward {}", ward.getCode());
            return List.of();
        }
    }

    /** Ray-casting point-in-polygon test. Vertices are [lat, lng]. */
    static boolean contains(List<double[]> polygon, double lat, double lng) {
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            double yi = polygon.get(i)[0], xi = polygon.get(i)[1];
            double yj = polygon.get(j)[0], xj = polygon.get(j)[1];
            boolean crosses = (yi > lat) != (yj > lat)
                    && lng < (xj - xi) * (lat - yi) / (yj - yi) + xi;
            if (crosses) {
                inside = !inside;
            }
        }
        return inside;
    }
}
