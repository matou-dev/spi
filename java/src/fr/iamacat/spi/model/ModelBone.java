package fr.iamacat.spi.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One Bedrock bone: logical name, optional parent name (validated against
 * the model's bone set by the parser, cycles refused), bind-pose pivot
 * (px) and bind-pose Euler rotation (degrees, x-then-y-then-z per the
 * Bedrock schema — baked by MatouModel around the pivot, pre-animation),
 * default inflate (px) for cubes that carry none, and cubes in file order.
 * Zero MC/GL imports, Java 8.
 */
public final class ModelBone {
    public final String name;
    public final String parent;
    public final double pivotX;
    public final double pivotY;
    public final double pivotZ;
    public final double rotX;
    public final double rotY;
    public final double rotZ;
    public final double inflate;
    public final List<ModelCube> cubes;

    public ModelBone(String name, String parent,
            double pivotX, double pivotY, double pivotZ,
            List<ModelCube> cubes) {
        this(name, parent, pivotX, pivotY, pivotZ,
                0.0, 0.0, 0.0, 0.0, cubes);
    }

    public ModelBone(String name, String parent,
            double pivotX, double pivotY, double pivotZ,
            double rotX, double rotY, double rotZ, double inflate,
            List<ModelCube> cubes) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("E_MODEL_BONE:empty (want a non-blank bone name)");
        }
        if (Double.isNaN(pivotX) || Double.isNaN(pivotY) || Double.isNaN(pivotZ)) {
            throw new IllegalArgumentException("E_MODEL_BONE:pivot <" + name + "> (want finite numbers)");
        }
        if (Double.isNaN(rotX) || Double.isNaN(rotY) || Double.isNaN(rotZ)
                || Double.isInfinite(rotX) || Double.isInfinite(rotY) || Double.isInfinite(rotZ)) {
            throw new IllegalArgumentException("E_MODEL_BONE:rotation <" + name
                    + "> (want finite degrees [x, y, z], x-then-y-then-z)");
        }
        if (Double.isNaN(inflate) || Double.isInfinite(inflate)) {
            throw new IllegalArgumentException("E_MODEL_BONE:inflate <" + name
                    + "> (want a finite number of px)");
        }
        if (cubes == null) {
            throw new NullPointerException("E_MODEL_CUBE:null <" + name + "> (want a list, possibly empty)");
        }
        this.name = name;
        this.parent = parent;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.pivotZ = pivotZ;
        this.rotX = rotX;
        this.rotY = rotY;
        this.rotZ = rotZ;
        this.inflate = inflate;
        this.cubes = Collections.unmodifiableList(new ArrayList<ModelCube>(cubes));
    }
}
