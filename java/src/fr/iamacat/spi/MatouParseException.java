package fr.iamacat.spi;

/** SYNTAX-V1 error: always CODE + LINE. Mirrors parser/matou_parse.py. */
public final class MatouParseException extends Exception {
    public final String code;
    public final int line;

    public MatouParseException(String code, int line, String msg) {
        super(msg == null ? "" : msg);
        this.code = code;
        this.line = line;
    }

    public MatouParseException(String code, int line) {
        this(code, line, "");
    }

    @Override
    public String toString() {
        String s = code + ":" + line;
        String m = getMessage();
        return m.isEmpty() ? s : s + ": " + m;
    }
}
