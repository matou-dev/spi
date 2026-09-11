package fr.iamacat.spi.model;

/**
 * One Bedrock box: origin (min corner, px), size (px, strictly positive
 * after inflate), box UV anchor (px) and inflate (px, expands every side).
 * Bind pose only — pivot/rotation bake lands with the animation tranche.
 * Zero MC/GL imports, Java 8.
 */
public final class ModelCube {
    public final double originX;
    public final double originY;
    public final double originZ;
    public final double sizeX;
    public final double sizeY;
    public final double sizeZ;
    public final double uvU;
    public final double uvV;
    public final double inflate;

    public ModelCube(double originX, double originY, double originZ,
            double sizeX, double sizeY, double sizeZ,
            double uvU, double uvV, double inflate) {
        if (Double.isNaN(originX) || Double.isNaN(originY) || Double.isNaN(originZ)
                || Double.isNaN(sizeX) || Double.isNaN(sizeY) || Double.isNaN(sizeZ)
                || Double.isNaN(uvU) || Double.isNaN(uvV) || Double.isNaN(inflate)) {
            throw new IllegalArgumentException("E_MODEL_CUBE:nan (origin/size/uv/inflate must be finite)");
        }
        if (!(sizeX > 0.0) || !(sizeY > 0.0) || !(sizeZ > 0.0)) {
            throw new IllegalArgumentException("E_MODEL_CUBE:size <"
                    + sizeX + "," + sizeY + "," + sizeZ + "> (want all > 0)");
        }
        if (uvU < 0.0 || uvV < 0.0) {
            throw new IllegalArgumentException("E_MODEL_CUBE:uv <"
                    + uvU + "," + uvV + "> (want >= 0)");
        }
        if (sizeX + 2.0 * inflate <= 0.0 || sizeY + 2.0 * inflate <= 0.0
                || sizeZ + 2.0 * inflate <= 0.0) {
            throw new IllegalArgumentException("E_MODEL_CUBE:size <inflate "
                    + inflate + " inverts the box> (want size + 2*inflate > 0)");
        }
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.uvU = uvU;
        this.uvV = uvV;
        this.inflate = inflate;
    }

    public double minX() {
        return originX - inflate;
    }

    public double minY() {
        return originY - inflate;
    }

    public double minZ() {
        return originZ - inflate;
    }

    public double maxX() {
        return originX + sizeX + inflate;
    }

    public double maxY() {
        return originY + sizeY + inflate;
    }

    public double maxZ() {
        return originZ + sizeZ + inflate;
    }
}
