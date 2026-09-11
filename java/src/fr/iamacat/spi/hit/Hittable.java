package fr.iamacat.spi.hit;

import java.util.List;
import java.util.Map;

/**
 * Pure SPI contract for any target providing virtual hitboxes.
 * Zero Minecraft/GL imports, Java 8.
 */
public interface Hittable {
    /** List of bone bounding boxes in world space. */
    List<BoneBox> hitBoxes();

    /** Optional weakspot multipliers per bone name (e.g. "head" -> 2.0F). */
    Map<String, Float> hitWeakspots();

    /** Resolves the damage multiplier for a bone hit (default 1.0F). */
    default float weakspotMultiplier(String boneName) {
        Map<String, Float> ws = hitWeakspots();
        if (ws == null || boneName == null) {
            return 1.0F;
        }
        Float mult = ws.get(boneName);
        return mult == null ? 1.0F : mult.floatValue();
    }
}
