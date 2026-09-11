package fr.iamacat.spi.hit;

/**
 * Result of a virtual-hitbox ray intersection.
 * Zero Minecraft/GL imports, Java 8.
 */
public final class RayHit {
    public final String boneName;
    public final Vec3d hitVec;
    public final double distance;

    public RayHit(String boneName, Vec3d hitVec, double distance) {
        if (boneName == null || boneName.trim().isEmpty()) {
            throw new IllegalArgumentException("E_HIT_BONE:empty");
        }
        if (hitVec == null) {
            throw new NullPointerException("E_HIT_VEC:null");
        }
        if (Double.isNaN(distance) || distance < 0.0) {
            throw new IllegalArgumentException("E_HIT_DIST:negative_or_nan");
        }
        this.boneName = boneName;
        this.hitVec = hitVec;
        this.distance = distance;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RayHit)) return false;
        RayHit rayHit = (RayHit) o;
        return Double.doubleToLongBits(distance) == Double.doubleToLongBits(rayHit.distance)
                && boneName.equals(rayHit.boneName)
                && hitVec.equals(rayHit.hitVec);
    }

    @Override
    public int hashCode() {
        int result = boneName.hashCode();
        result = 31 * result + hitVec.hashCode();
        long d = Double.doubleToLongBits(distance);
        result = 31 * result + (int) (d ^ (d >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "RayHit{bone=" + boneName + ", at=" + hitVec + ", dist=" + distance + "}";
    }
}
