package fr.iamacat.spi.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One Bedrock bone: logical name, optional parent name (validated against
 * the model's bone set by the parser), bind-pose pivot (px, informational
 * in V1 — the bake is axis-aligned and ignores rotation) and cubes.
 * Zero MC/GL imports, Java 8.
 */
public final class ModelBone {
    public final String name;
    public final String parent;
    public final double pivotX;
    public final double pivotY;
    public final double pivotZ;
    public final List<ModelCube> cubes;

    public ModelBone(String name, String parent,
            double pivotX, double pivotY, double pivotZ,
            List<ModelCube> cubes) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("E_MODEL_BONE:empty (want a non-blank bone name)");
        }
        if (Double.isNaN(pivotX) || Double.isNaN(pivotY) || Double.isNaN(pivotZ)) {
            throw new IllegalArgumentException("E_MODEL_BONE:pivot <" + name + "> (want finite numbers)");
        }
        if (cubes == null) {
            throw new NullPointerException("E_MODEL_CUBE:null <" + name + "> (want a list, possibly empty)");
        }
        this.name = name;
        this.parent = parent;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.pivotZ = pivotZ;
        this.cubes = Collections.unmodifiableList(new ArrayList<ModelCube>(cubes));
    }
}
