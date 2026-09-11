package fr.iamacat.spi.hit;

/**
 * Immutable double-precision 3D vector for pure geometric ray-testing.
 * Zero Minecraft/GL imports, Java 8.
 */
public final class Vec3d {
    public final double x;
    public final double y;
    public final double z;

    public Vec3d(double x, double y, double z) {
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
            throw new IllegalArgumentException("E_HIT_VEC:nan");
        }
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3d add(Vec3d o) {
        if (o == null) throw new NullPointerException("E_HIT_VEC:null");
        return new Vec3d(x + o.x, y + o.y, z + o.z);
    }

    public Vec3d sub(Vec3d o) {
        if (o == null) throw new NullPointerException("E_HIT_VEC:null");
        return new Vec3d(x - o.x, y - o.y, z - o.z);
    }

    public Vec3d scale(double s) {
        if (Double.isNaN(s)) throw new IllegalArgumentException("E_HIT_VEC:nan");
        return new Vec3d(x * s, y * s, z * s);
    }

    public double dot(Vec3d o) {
        if (o == null) throw new NullPointerException("E_HIT_VEC:null");
        return x * o.x + y * o.y + z * o.z;
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public Vec3d normalize() {
        double len = length();
        if (len == 0.0) {
            throw new IllegalArgumentException("E_HIT_DIR:zero");
        }
        return new Vec3d(x / len, y / len, z / len);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vec3d)) return false;
        Vec3d v = (Vec3d) o;
        return Double.doubleToLongBits(x) == Double.doubleToLongBits(v.x)
                && Double.doubleToLongBits(y) == Double.doubleToLongBits(v.y)
                && Double.doubleToLongBits(z) == Double.doubleToLongBits(v.z);
    }

    @Override
    public int hashCode() {
        long lx = Double.doubleToLongBits(x);
        long ly = Double.doubleToLongBits(y);
        long lz = Double.doubleToLongBits(z);
        int result = (int) (lx ^ (lx >>> 32));
        result = 31 * result + (int) (ly ^ (ly >>> 32));
        result = 31 * result + (int) (lz ^ (lz >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "Vec3d(" + x + ", " + y + ", " + z + ")";
    }
}
