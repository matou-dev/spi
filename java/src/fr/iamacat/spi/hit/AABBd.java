package fr.iamacat.spi.hit;

/**
 * Immutable double-precision Axis-Aligned Bounding Box and slab ray-intersection.
 * Zero Minecraft/GL imports, Java 8.
 */
public final class AABBd {
    public final double minX;
    public final double minY;
    public final double minZ;
    public final double maxX;
    public final double maxY;
    public final double maxZ;

    private static final double EPS = 1e-9;

    public AABBd(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (Double.isNaN(minX) || Double.isNaN(minY) || Double.isNaN(minZ)
                || Double.isNaN(maxX) || Double.isNaN(maxY) || Double.isNaN(maxZ)) {
            throw new IllegalArgumentException("E_HIT_BOX:nan");
        }
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("E_HIT_BOX:degenerate");
        }
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    /**
     * Slab ray-vs-AABB test along ray = origin + t*dir.
     * Returns the entry parameter t >= 0.0 along the ray (0.0 when origin is inside the box),
     * or Double.NaN when the ray does not cross the box within [0.0, maxDist].
     */
    public double intersect(double ox, double oy, double oz, double dx, double dy, double dz, double maxDist) {
        if (Double.isNaN(ox) || Double.isNaN(oy) || Double.isNaN(oz)) {
            throw new IllegalArgumentException("E_HIT_ORIGIN:nan");
        }
        if (Double.isNaN(dx) || Double.isNaN(dy) || Double.isNaN(dz)) {
            throw new IllegalArgumentException("E_HIT_DIR:nan");
        }
        if (Double.isNaN(maxDist)) {
            throw new IllegalArgumentException("E_HIT_DIST:nan");
        }
        if (maxDist < 0.0) {
            throw new IllegalArgumentException("E_HIT_DIST:negative");
        }

        double tmin = 0.0;
        double tmax = maxDist;

        // X slab
        if (Math.abs(dx) < EPS) {
            if (ox < minX || ox > maxX) return Double.NaN;
        } else {
            double inv = 1.0 / dx;
            double t1 = (minX - ox) * inv;
            double t2 = (maxX - ox) * inv;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > tmin) tmin = t1;
            if (t2 < tmax) tmax = t2;
            if (tmin > tmax) return Double.NaN;
        }

        // Y slab
        if (Math.abs(dy) < EPS) {
            if (oy < minY || oy > maxY) return Double.NaN;
        } else {
            double inv = 1.0 / dy;
            double t1 = (minY - oy) * inv;
            double t2 = (maxY - oy) * inv;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > tmin) tmin = t1;
            if (t2 < tmax) tmax = t2;
            if (tmin > tmax) return Double.NaN;
        }

        // Z slab
        if (Math.abs(dz) < EPS) {
            if (oz < minZ || oz > maxZ) return Double.NaN;
        } else {
            double inv = 1.0 / dz;
            double t1 = (minZ - oz) * inv;
            double t2 = (maxZ - oz) * inv;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > tmin) tmin = t1;
            if (t2 < tmax) tmax = t2;
            if (tmin > tmax) return Double.NaN;
        }

        return tmin;
    }

    public boolean contains(double x, double y, double z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AABBd)) return false;
        AABBd b = (AABBd) o;
        return Double.doubleToLongBits(minX) == Double.doubleToLongBits(b.minX)
                && Double.doubleToLongBits(minY) == Double.doubleToLongBits(b.minY)
                && Double.doubleToLongBits(minZ) == Double.doubleToLongBits(b.minZ)
                && Double.doubleToLongBits(maxX) == Double.doubleToLongBits(b.maxX)
                && Double.doubleToLongBits(maxY) == Double.doubleToLongBits(b.maxY)
                && Double.doubleToLongBits(maxZ) == Double.doubleToLongBits(b.maxZ);
    }

    @Override
    public int hashCode() {
        long x1 = Double.doubleToLongBits(minX);
        long y1 = Double.doubleToLongBits(minY);
        long z1 = Double.doubleToLongBits(minZ);
        long x2 = Double.doubleToLongBits(maxX);
        long y2 = Double.doubleToLongBits(maxY);
        long z2 = Double.doubleToLongBits(maxZ);
        int result = (int) (x1 ^ (x1 >>> 32));
        result = 31 * result + (int) (y1 ^ (y1 >>> 32));
        result = 31 * result + (int) (z1 ^ (z1 >>> 32));
        result = 31 * result + (int) (x2 ^ (x2 >>> 32));
        result = 31 * result + (int) (y2 ^ (y2 >>> 32));
        result = 31 * result + (int) (z2 ^ (z2 >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "AABBd[" + minX + "," + minY + "," + minZ + " -> " + maxX + "," + maxY + "," + maxZ + "]";
    }
}
