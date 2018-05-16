package no.entur.kakka.geocoder.routes.pelias.mapper.netex;

import net.opengis.gml._3.AbstractRingType;
import net.opengis.gml._3.LinearRingType;
import org.apache.commons.collections4.CollectionUtils;
import org.geojson.LngLatAlt;
import org.geojson.Polygon;
import org.rutebanken.netex.model.GroupOfEntities_VersionStructure;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.ValidBetween;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class NetexPeliasMapperUtil {

    public static List<MultilingualString> filterUnique(List<MultilingualString> strings) {
        return strings.stream().filter(distinctByKey(name -> name.getValue())).collect(Collectors.toList());
    }

    public static boolean isValid(GroupOfEntities_VersionStructure object) {
        if (!CollectionUtils.isEmpty(object.getValidBetween()) && object.getValidBetween().stream().noneMatch(vb -> isValidNow(vb))) {
            return false;
        }
        return true;
    }

    // Should compare instant with validbetween from/to in timezone defined in PublicationDelivery, but makes little difference in practice
    private static boolean isValidNow(ValidBetween validBetween) {
        LocalDateTime now = LocalDateTime.now();
        if (validBetween != null) {
            if (validBetween.getFromDate() != null && validBetween.getFromDate().isAfter(now)) {
                return false;
            }

            if (validBetween.getToDate() != null && validBetween.getToDate().isBefore(now)) {
                return false;
            }
        }
        return true;
    }

    public static Polygon toPolygon(AbstractRingType ring) {

        if (ring instanceof LinearRingType) {
            LinearRingType linearRing = (LinearRingType) ring;

            List<LngLatAlt> coordinates = new ArrayList<>();
            LngLatAlt coordinate = null;
            LngLatAlt prevCoordinate = null;
            for (Double val : linearRing.getPosList().getValue()) {
                if (coordinate == null) {
                    coordinate = new LngLatAlt();
                    coordinate.setLatitude(val);
                } else {
                    coordinate.setLongitude(val);
                    if (prevCoordinate == null || !equals(coordinate, prevCoordinate)) {
                        coordinates.add(coordinate);
                    }
                    prevCoordinate = coordinate;
                    coordinate = null;
                }
            }
            return new Polygon(coordinates);

        }
        return null;
    }

    private static boolean equals(LngLatAlt coordinate, LngLatAlt other) {
        return other.getLatitude() == coordinate.getLatitude() && other.getLongitude() == coordinate.getLongitude();
    }

    protected static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }
}
