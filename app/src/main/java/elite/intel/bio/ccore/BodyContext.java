package elite.intel.bio.ccore;

import com.google.gson.annotations.SerializedName;

import java.util.Set;

/**
 * The physical body state C-CORE's species-evaluation rules key on, mirroring EDpjKinsaku's
 * {@code app.bio.c_core.BodyContext} dataclass field-for-field - including its JSON field names, via
 * {@link SerializedName} - so a value built here needs no translation on the far side of
 * {@link CCoreAdapter}.
 *
 * @param atmosphere  the journal's {@code AtmosphereType} (e.g. "CarbonDioxide"), not {@code Atmosphere}
 * @param gravity     Earth gravities (g) - {@code LocationDto.getGravity()}, never the journal's raw
 *                    {@code SurfaceGravity} (m/s²); see docs/ELITEINTEL_INTEGRATION_PLAN.md §9
 * @param temperature Kelvin
 * @param pressure    Pascal - {@code LocationDto.getSurfacePressure()}
 * @param bodyType    the journal's {@code PlanetClass} (e.g. "Rocky body")
 * @param volcanism   the journal's raw {@code Volcanism} string
 * @param regions     region-gated species restriction; {@code null} when not tracked (EliteIntel does
 *                    not track this yet)
 */
public record BodyContext(
        String atmosphere,
        Double gravity,
        Double temperature,
        Double pressure,
        @SerializedName("body_type") String bodyType,
        String volcanism,
        Set<String> regions
) {
}
