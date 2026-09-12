package fr.iamacat.spi.render;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Pure Java 8 interface abstracting standard OpenGL 3.1+ instancing primitives.
 * Zero LWJGL/Minecraft dependencies. Implemented per bridge by Lwjgl2Backend or Lwjgl3Backend.
 */
public interface GlBackend {
    int GL_ARRAY_BUFFER = 0x8892;
    int GL_STATIC_DRAW = 0x88E4;
    int GL_DYNAMIC_DRAW = 0x88E8;
    int GL_STREAM_DRAW = 0x88E0;
    int GL_FLOAT = 0x1406;
    int GL_TRIANGLES = 0x0004;
    int GL_VERTEX_SHADER = 0x8B31;
    int GL_FRAGMENT_SHADER = 0x8B30;
    int GL_TEXTURE_2D = 0x0DE1;
    int GL_RGBA = 0x1908;
    int GL_UNSIGNED_BYTE = 0x1401;
    int GL_TEXTURE_MIN_FILTER = 0x2801;
    int GL_TEXTURE_MAG_FILTER = 0x2800;
    int GL_TEXTURE_WRAP_S = 0x2802;
    int GL_TEXTURE_WRAP_T = 0x2803;
    int GL_NEAREST = 0x2600;
    int GL_CLAMP_TO_EDGE = 0x812F;

    int genBuffers();
    void bindBuffer(int target, int buffer);
    void bufferData(int target, FloatBuffer data, int usage);
    void deleteBuffers(int buffer);

    int genVertexArrays();
    void bindVertexArray(int array);
    void deleteVertexArrays(int array);

    void enableVertexAttribArray(int index);
    void disableVertexAttribArray(int index);
    void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long offset);
    void vertexAttribDivisor(int index, int divisor);

    int createShader(int type);
    void shaderSource(int shader, String source);
    void compileShader(int shader);
    boolean getShaderCompileStatus(int shader);
    String getShaderInfoLog(int shader);
    void deleteShader(int shader);

    int createProgram();
    void attachShader(int program, int shader);
    void linkProgram(int program);
    boolean getProgramLinkStatus(int program);
    String getProgramInfoLog(int program);
    void useProgram(int program);
    void deleteProgram(int program);

    int getUniformLocation(int program, String name);
    void uniformMatrix4fv(int location, boolean transpose, FloatBuffer matrices);
    void uniform1i(int location, int value);
    void uniform1f(int location, float value);
    void uniform4f(int location, float x, float y, float z, float w);

    int genTextures();
    void bindTexture(int target, int texture);
    void texImage2D(int target, int level, int internalFormat,
            int width, int height, int border, int format, int type,
            ByteBuffer pixels);
    void texParameteri(int target, int pname, int param);
    void deleteTextures(int texture);

    void drawArraysInstanced(int mode, int first, int count, int instanceCount);
}
