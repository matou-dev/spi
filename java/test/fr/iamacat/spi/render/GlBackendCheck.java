package fr.iamacat.spi.render;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Gate GlBackendCheck: verifies InstanceFormat packing fidelity, NaN/overflow guards,
 * and GlBackend mock workflow. Java 8, zero MC imports.
 */
public final class GlBackendCheck {
    private GlBackendCheck() {}

    private static void check(boolean cond, String msg) {
        if (!cond) {
            System.err.println("FAIL gl-backend-check : " + msg);
            System.exit(1);
        }
    }

    private static void assertThrows(Runnable r, String expectedFragment) {
        try {
            r.run();
            System.err.println("FAIL gl-backend-check : expected exception containing <" + expectedFragment + ">");
            System.exit(1);
        } catch (Throwable t) {
            String msg = t.getMessage();
            if (msg == null || !msg.contains(expectedFragment)) {
                System.err.println("FAIL gl-backend-check : got <" + msg + "> (want fragment <" + expectedFragment + ">)");
                System.exit(1);
            }
        }
    }

    public static void main(String[] args) {
        testInstanceFormatPacking();
        testInstanceFormatRefusals();
        testMockGlBackendWorkflow();
        testMockGlBackendTexture();
        System.out.println("ok gl-backend-check : all instance formatting and backend checks passed");
    }

    private static void testInstanceFormatPacking() {
        FloatBuffer buf = FloatBuffer.allocate(InstanceFormat.FLOATS_PER_INSTANCE * 2);

        InstanceFormat.pack(buf, 10.5, 20.25, -30.75, 1.57f, 0.78f, 2.0f, 1.0f, 0.5f, 0.25f, 1.0f, 240.0f, 120.0f);
        check(buf.position() == InstanceFormat.FLOATS_PER_INSTANCE, "buffer advanced by 12 floats");

        buf.flip();
        check(Math.abs(buf.get() - 10.5f) < 1e-5, "relX packed");
        check(Math.abs(buf.get() - 20.25f) < 1e-5, "relY packed");
        check(Math.abs(buf.get() - (-30.75f)) < 1e-5, "relZ packed");
        check(Math.abs(buf.get() - 1.57f) < 1e-5, "yaw packed");
        check(Math.abs(buf.get() - 0.78f) < 1e-5, "pitch packed");
        check(Math.abs(buf.get() - 2.0f) < 1e-5, "scale packed");
        check(Math.abs(buf.get() - 1.0f) < 1e-5, "r packed");
        check(Math.abs(buf.get() - 0.5f) < 1e-5, "g packed");
        check(Math.abs(buf.get() - 0.25f) < 1e-5, "b packed");
        check(Math.abs(buf.get() - 1.0f) < 1e-5, "a packed");
        check(Math.abs(buf.get() - 240.0f) < 1e-5, "lightU packed");
        check(Math.abs(buf.get() - 120.0f) < 1e-5, "lightV packed");
    }

    private static void testInstanceFormatRefusals() {
        FloatBuffer buf = FloatBuffer.allocate(InstanceFormat.FLOATS_PER_INSTANCE);

        assertThrows(() -> InstanceFormat.pack(null, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 0, 0), "E_GL_BUFFER:null");
        assertThrows(() -> InstanceFormat.pack(buf, Double.NaN, 0, 0, 0, 0, 1, 1, 1, 1, 1, 0, 0), "E_INSTANCE_DATA:nan");
        assertThrows(() -> InstanceFormat.pack(buf, 0, 0, 0, Float.NaN, 0, 1, 1, 1, 1, 1, 0, 0), "E_INSTANCE_DATA:nan");
        assertThrows(() -> InstanceFormat.pack(buf, 0, 0, 0, 0, 0, Float.NaN, 1, 1, 1, 1, 0, 0), "E_INSTANCE_DATA:nan");
        assertThrows(() -> InstanceFormat.pack(buf, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, Float.NaN, 0), "E_INSTANCE_DATA:nan");

        // Fill buffer to capacity
        InstanceFormat.pack(buf, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 0, 0);
        // Attempting to pack again causes buffer overflow
        assertThrows(() -> InstanceFormat.pack(buf, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 0, 0), "E_GL_BUFFER:overflow");
    }

    private static void testMockGlBackendWorkflow() {
        MockGlBackend mock = new MockGlBackend();

        int vbo = mock.genBuffers();
        check(vbo == 1, "vbo id 1");

        mock.bindBuffer(GlBackend.GL_ARRAY_BUFFER, vbo);
        FloatBuffer data = FloatBuffer.allocate(InstanceFormat.FLOATS_PER_INSTANCE);
        InstanceFormat.pack(data, 1, 2, 3, 0, 0, 1, 1, 1, 1, 1, 0, 0);
        data.flip();

        mock.bufferData(GlBackend.GL_ARRAY_BUFFER, data, GlBackend.GL_DYNAMIC_DRAW);
        mock.drawArraysInstanced(GlBackend.GL_TRIANGLES, 0, 36, 1);
        mock.deleteBuffers(vbo);

        check(mock.calls.contains("bindBuffer:34962:1"), "bindBuffer recorded");
        check(mock.calls.contains("bufferData:34962:12"), "bufferData recorded with 12 floats");
        check(mock.calls.contains("drawArraysInstanced:4:0:36:1"), "drawArraysInstanced recorded");
        check(mock.calls.contains("deleteBuffers:1"), "deleteBuffers recorded");
    }

    /**
     * Texture upload workflow (V2 tranche): gen, bind, image, params —
     * the exact call shape the bridge renderer replays against LWJGL.
     */
    private static void testMockGlBackendTexture() {
        MockGlBackend mock = new MockGlBackend();

        int tex = mock.genTextures();
        check(tex == 1, "texture id 1");
        mock.bindTexture(GlBackend.GL_TEXTURE_2D, tex);
        java.nio.ByteBuffer pixels = java.nio.ByteBuffer.allocate(4 * 4 * 4);
        mock.texImage2D(GlBackend.GL_TEXTURE_2D, 0, GlBackend.GL_RGBA,
                4, 4, 0, GlBackend.GL_RGBA, GlBackend.GL_UNSIGNED_BYTE, pixels);
        mock.texParameteri(GlBackend.GL_TEXTURE_2D,
                GlBackend.GL_TEXTURE_MIN_FILTER, GlBackend.GL_NEAREST);
        mock.texParameteri(GlBackend.GL_TEXTURE_2D,
                GlBackend.GL_TEXTURE_MAG_FILTER, GlBackend.GL_NEAREST);
        mock.deleteTextures(tex);

        check(mock.calls.contains("bindTexture:3553:1"), "bindTexture recorded");
        check(mock.calls.contains("texImage2D:3553:4:4:64"), "texImage2D recorded with 64 bytes");
        check(mock.calls.contains("texParameteri:3553:10241:9728"), "min filter recorded");
        check(mock.calls.contains("texParameteri:3553:10240:9728"), "mag filter recorded");
        check(mock.calls.contains("deleteTextures:1"), "deleteTextures recorded");
    }

    private static final class MockGlBackend implements GlBackend {
        final List<String> calls = new ArrayList<>();
        private int nextId = 1;

        @Override
        public int genBuffers() {
            return nextId++;
        }

        @Override
        public void bindBuffer(int target, int buffer) {
            calls.add("bindBuffer:" + target + ":" + buffer);
        }

        @Override
        public void bufferData(int target, FloatBuffer data, int usage) {
            calls.add("bufferData:" + target + ":" + data.remaining());
        }

        @Override
        public void deleteBuffers(int buffer) {
            calls.add("deleteBuffers:" + buffer);
        }

        @Override
        public int genVertexArrays() { return nextId++; }
        @Override
        public void bindVertexArray(int array) { calls.add("bindVAO:" + array); }
        @Override
        public void deleteVertexArrays(int array) { calls.add("deleteVAO:" + array); }
        @Override
        public void enableVertexAttribArray(int index) { calls.add("enableAttrib:" + index); }
        @Override
        public void disableVertexAttribArray(int index) { calls.add("disableAttrib:" + index); }
        @Override
        public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long offset) {
            calls.add("attribPointer:" + index + ":" + size);
        }
        @Override
        public void vertexAttribDivisor(int index, int divisor) {
            calls.add("attribDivisor:" + index + ":" + divisor);
        }
        @Override
        public int createShader(int type) { return nextId++; }
        @Override
        public void shaderSource(int shader, String source) {}
        @Override
        public void compileShader(int shader) {}
        @Override
        public boolean getShaderCompileStatus(int shader) { return true; }
        @Override
        public String getShaderInfoLog(int shader) { return ""; }
        @Override
        public void deleteShader(int shader) {}
        @Override
        public int createProgram() { return nextId++; }
        @Override
        public void attachShader(int program, int shader) {}
        @Override
        public void linkProgram(int program) {}
        @Override
        public boolean getProgramLinkStatus(int program) { return true; }
        @Override
        public String getProgramInfoLog(int program) { return ""; }
        @Override
        public void useProgram(int program) { calls.add("useProgram:" + program); }
        @Override
        public void deleteProgram(int program) {}
        @Override
        public int getUniformLocation(int program, String name) { return 0; }
        @Override
        public void uniformMatrix4fv(int location, boolean transpose, FloatBuffer matrices) {}
        @Override
        public void uniform1i(int location, int value) {}
        @Override
        public void uniform1f(int location, float value) {}
        @Override
        public void uniform4f(int location, float x, float y, float z, float w) {}
        @Override
        public void drawArraysInstanced(int mode, int first, int count, int instanceCount) {
            calls.add("drawArraysInstanced:" + mode + ":" + first + ":" + count + ":" + instanceCount);
        }
        @Override
        public int genTextures() { return nextId++; }
        @Override
        public void bindTexture(int target, int texture) {
            calls.add("bindTexture:" + target + ":" + texture);
        }
        @Override
        public void texImage2D(int target, int level, int internalFormat,
                int width, int height, int border, int format, int type,
                java.nio.ByteBuffer pixels) {
            calls.add("texImage2D:" + target + ":" + width + ":" + height + ":" + pixels.remaining());
        }
        @Override
        public void texParameteri(int target, int pname, int param) {
            calls.add("texParameteri:" + target + ":" + pname + ":" + param);
        }
        @Override
        public void deleteTextures(int texture) {
            calls.add("deleteTextures:" + texture);
        }
    }
}
