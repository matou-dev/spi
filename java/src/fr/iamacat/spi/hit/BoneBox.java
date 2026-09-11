package fr.iamacat.spi.hit;

/**
 * Associates a logical bone identifier with its world-space AABBd.
 * Zero Minecraft/GL imports, Java 8.
 */
public final class BoneBox {
    public final String boneName;
    public final AABBd box;

    public BoneBox(String boneName, AABBd box) {
        if (boneName == null || boneName.trim().isEmpty()) {
            throw new IllegalArgumentException("E_HIT_BONE:empty");
        }
        if (box == null) {
            throw new NullPointerException("E_HIT_BOX:null");
        }
        this.boneName = boneName;
        this.box = box;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BoneBox)) return false;
        BoneBox boneBox = (BoneBox) o;
        return boneName.equals(boneBox.boneName) && box.equals(boneBox.box);
    }

    @Override
    public int hashCode() {
        int result = boneName.hashCode();
        result = 31 * result + box.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "BoneBox{" + boneName + "=" + box + "}";
    }
}
