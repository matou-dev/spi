package fr.iamacat.spi.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Frozen MOLANG expression subset (hub decisions/MATOU_ANIMATION.md):
 * arithmetic, comparisons, short-circuit logic, ternary, the four
 * queries, caller variables (missing reads 0.0, Bedrock parity) and the
 * frozen math functions (radians — named divergence D-ANIM-1, proof
 * assets are authored against radians). Anything else refuses with an
 * E_ANIM_MOLANG code, never a silent zero. Pure, Java 8, zero dep.
 */
public final class Molang {
    private Molang() {}

    /** Evaluation context: the four queries plus caller variables. */
    public static final class Ctx {
        public final double animTime;
        public final double lifeTime;
        public final double distMoved;
        public final double deltaTime;
        public final Map<String, Double> variables;

        public Ctx(double animTime, double lifeTime, double distMoved,
                double deltaTime, Map<String, Double> variables) {
            this.animTime = animTime;
            this.lifeTime = lifeTime;
            this.distMoved = distMoved;
            this.deltaTime = deltaTime;
            this.variables = variables;
        }

        double variable(String name) {
            if (variables == null) {
                return 0.0;
            }
            Double v = variables.get(name);
            if (v == null) {
                return 0.0;
            }
            return v.doubleValue();
        }
    }

    /** Zero context (all queries 0, no variables) for load-time checks. */
    public static Ctx zeroCtx() {
        return new Ctx(0.0, 0.0, 0.0, 0.0, null);
    }

    /** Parses and evaluates one expression. */
    public static double eval(String expr, Ctx ctx) {
        return parse(expr).eval(ctx);
    }

    /** Parses one expression, discarding the tree (load-time check). */
    public static void validate(String expr) {
        parse(expr);
    }

    /** Parsed expression tree (validations run at parse, not at eval). */
    public static final class Node {
        static final int NUM = 0;
        static final int QUERY = 1;
        static final int VAR = 2;
        static final int CALL = 3;
        static final int BIN = 4;
        static final int UN = 5;
        static final int TERNARY = 6;

        final int kind;
        final double num;
        final int query;
        final String name;
        final List<Node> args;
        final Node left;
        final Node right;
        final Node third;

        private Node(int kind, double num, int query, String name,
                List<Node> args, Node left, Node right, Node third) {
            this.kind = kind;
            this.num = num;
            this.query = query;
            this.name = name;
            this.args = args;
            this.left = left;
            this.right = right;
            this.third = third;
        }

        static Node num(double v) {
            return new Node(NUM, v, 0, null, null, null, null, null);
        }

        /** Evaluates the tree (no refusals except NaN/Inf call results). */
        public double eval(Ctx ctx) {
            if (ctx == null) {
                throw new NullPointerException("E_ANIM_MOLANG:scope "
                        + "(null eval context — want the four queries plus variables)");
            }
            switch (kind) {
                case NUM:
                    return num;
                case QUERY:
                    switch (query) {
                        case 0:
                            return ctx.animTime;
                        case 1:
                            return ctx.lifeTime;
                        case 2:
                            return ctx.distMoved;
                        default:
                            return ctx.deltaTime;
                    }
                case VAR:
                    return ctx.variable(name);
                case CALL:
                    return evalCall(ctx);
                case BIN:
                    return evalBin(ctx);
                case UN: {
                    double v = left.eval(ctx);
                    return name.equals("!") ? (v == 0.0 ? 1.0 : 0.0) : -v;
                }
                default: {
                    double c = left.eval(ctx);
                    return (c != 0.0 ? right.eval(ctx) : third.eval(ctx));
                }
            }
        }

        private double evalCall(Ctx ctx) {
            int n = args.size();
            double a = n > 0 ? args.get(0).eval(ctx) : 0.0;
            double b = n > 1 ? args.get(1).eval(ctx) : 0.0;
            double c = n > 2 ? args.get(2).eval(ctx) : 0.0;
            if (name.equals("sin")) {
                return Math.sin(a);
            }
            if (name.equals("cos")) {
                return Math.cos(a);
            }
            if (name.equals("sqrt")) {
                return Math.sqrt(a);
            }
            if (name.equals("abs")) {
                return Math.abs(a);
            }
            if (name.equals("floor")) {
                return Math.floor(a);
            }
            if (name.equals("ceil")) {
                return Math.ceil(a);
            }
            if (name.equals("round")) {
                return (double) Math.round(a);
            }
            if (name.equals("pow")) {
                return Math.pow(a, b);
            }
            if (name.equals("max")) {
                double m = a;
                for (int i = 1; i < n; i++) {
                    double v = args.get(i).eval(ctx);
                    if (v > m) {
                        m = v;
                    }
                }
                return m;
            }
            if (name.equals("min")) {
                double m = a;
                for (int i = 1; i < n; i++) {
                    double v = args.get(i).eval(ctx);
                    if (v < m) {
                        m = v;
                    }
                }
                return m;
            }
            if (name.equals("clamp")) {
                return a < b ? b : (a > c ? c : a);
            }
            return a + (b - a) * c;
        }

        private double evalBin(Ctx ctx) {
            if (name.equals("&&")) {
                double l = left.eval(ctx);
                if (l == 0.0) {
                    return 0.0;
                }
                return right.eval(ctx) != 0.0 ? 1.0 : 0.0;
            }
            if (name.equals("||")) {
                double l = left.eval(ctx);
                if (l != 0.0) {
                    return 1.0;
                }
                return right.eval(ctx) != 0.0 ? 1.0 : 0.0;
            }
            double l = left.eval(ctx);
            double r = right.eval(ctx);
            if (name.equals("+")) {
                return l + r;
            }
            if (name.equals("-")) {
                return l - r;
            }
            if (name.equals("*")) {
                return l * r;
            }
            if (name.equals("/")) {
                return l / r;
            }
            if (name.equals("%")) {
                return l % r;
            }
            if (name.equals("<")) {
                return l < r ? 1.0 : 0.0;
            }
            if (name.equals("<=")) {
                return l <= r ? 1.0 : 0.0;
            }
            if (name.equals(">")) {
                return l > r ? 1.0 : 0.0;
            }
            if (name.equals(">=")) {
                return l >= r ? 1.0 : 0.0;
            }
            if (name.equals("==")) {
                return l == r ? 1.0 : 0.0;
            }
            return l != r ? 1.0 : 0.0;
        }
    }

    static Node parse(String expr) {
        if (expr == null || expr.trim().isEmpty()) {
            throw new IllegalArgumentException("E_ANIM_MOLANG:empty (want an expression)");
        }
        Tokenizer t = new Tokenizer(expr);
        Node out = ternary(t);
        if (t.type != Tokenizer.END) {
            throw new IllegalArgumentException("E_ANIM_MOLANG:syntax <trailing <"
                    + t.text + "> in <" + expr + ">>");
        }
        return out;
    }

    private static Node ternary(Tokenizer t) {
        Node c = or(t);
        if (t.type == Tokenizer.OP && t.text.equals("?")) {
            t.next();
            Node a = ternary(t);
            if (t.type != Tokenizer.OP || !t.text.equals(":")) {
                throw new IllegalArgumentException("E_ANIM_MOLANG:syntax (want : in ternary)");
            }
            t.next();
            Node b = ternary(t);
            return new Node(Node.TERNARY, 0.0, 0, null, null, c, a, b);
        }
        return c;
    }

    private static Node or(Tokenizer t) {
        Node out = and(t);
        while (t.type == Tokenizer.OP && t.text.equals("||")) {
            t.next();
            out = new Node(Node.BIN, 0.0, 0, "||", null, out, and(t), null);
        }
        return out;
    }

    private static Node and(Tokenizer t) {
        Node out = equality(t);
        while (t.type == Tokenizer.OP && t.text.equals("&&")) {
            t.next();
            out = new Node(Node.BIN, 0.0, 0, "&&", null, out, equality(t), null);
        }
        return out;
    }

    private static Node equality(Tokenizer t) {
        Node out = comparison(t);
        while (t.type == Tokenizer.OP && (t.text.equals("==") || t.text.equals("!="))) {
            String op = t.text;
            t.next();
            out = new Node(Node.BIN, 0.0, 0, op, null, out, comparison(t), null);
        }
        return out;
    }

    private static Node comparison(Tokenizer t) {
        Node out = additive(t);
        while (t.type == Tokenizer.OP && (t.text.equals("<") || t.text.equals("<=")
                || t.text.equals(">") || t.text.equals(">="))) {
            String op = t.text;
            t.next();
            out = new Node(Node.BIN, 0.0, 0, op, null, out, additive(t), null);
        }
        return out;
    }

    private static Node additive(Tokenizer t) {
        Node out = multiplicative(t);
        while (t.type == Tokenizer.OP && (t.text.equals("+") || t.text.equals("-"))) {
            String op = t.text;
            t.next();
            out = new Node(Node.BIN, 0.0, 0, op, null, out, multiplicative(t), null);
        }
        return out;
    }

    private static Node multiplicative(Tokenizer t) {
        Node out = unary(t);
        while (t.type == Tokenizer.OP && (t.text.equals("*") || t.text.equals("/")
                || t.text.equals("%"))) {
            String op = t.text;
            t.next();
            out = new Node(Node.BIN, 0.0, 0, op, null, out, unary(t), null);
        }
        return out;
    }

    private static Node unary(Tokenizer t) {
        if (t.type == Tokenizer.OP && (t.text.equals("-") || t.text.equals("!"))) {
            String op = t.text;
            t.next();
            return new Node(Node.UN, 0.0, 0, op, null, unary(t), null, null);
        }
        return primary(t);
    }

    private static Node primary(Tokenizer t) {
        if (t.type == Tokenizer.NUM) {
            double v = t.num;
            t.next();
            return Node.num(v);
        }
        if (t.type == Tokenizer.LPAREN) {
            t.next();
            Node out = ternary(t);
            if (t.type != Tokenizer.RPAREN) {
                throw new IllegalArgumentException("E_ANIM_MOLANG:syntax (want ) )");
            }
            t.next();
            return out;
        }
        if (t.type != Tokenizer.IDENT) {
            throw new IllegalArgumentException("E_ANIM_MOLANG:syntax <unexpected <"
                    + t.text + ">>");
        }
        String name = t.text;
        t.next();
        if (name.equals("true")) {
            return Node.num(1.0);
        }
        if (name.equals("false")) {
            return Node.num(0.0);
        }
        if (name.equals("this") || name.startsWith("this.")) {
            throw new IllegalArgumentException("E_ANIM_MOLANG:this <" + name
                    + "> (entity-field binding is a named follow-up, never silent)");
        }
        if (name.startsWith("Math.")) {
            name = "math." + name.substring(5);
        }
        boolean call = t.type == Tokenizer.LPAREN;
        if (name.startsWith("query.")) {
            if (call) {
                throw new IllegalArgumentException("E_ANIM_MOLANG:syntax <" + name
                        + "> (a query is a value, never a call)");
            }
            return queryNode(name);
        }
        if (name.startsWith("variable.")) {
            if (call) {
                throw new IllegalArgumentException("E_ANIM_MOLANG:syntax <" + name
                        + "> (a variable is a value, never a call)");
            }
            String var = name.substring(9);
            if (var.isEmpty()) {
                throw new IllegalArgumentException("E_ANIM_MOLANG:scope <" + name
                        + "> (want variable.<name>)");
            }
            return new Node(Node.VAR, 0.0, 0, var, null, null, null, null);
        }
        if (name.equals("math.pi")) {
            if (call) {
                throw new IllegalArgumentException("E_ANIM_MOLANG:syntax (math.pi is a value)");
            }
            return Node.num(Math.PI);
        }
        if (name.startsWith("math.")) {
            if (!call) {
                throw new IllegalArgumentException("E_ANIM_MOLANG:syntax <" + name
                        + "> (a math function needs its argument list)");
            }
            return callNode(name.substring(5), t);
        }
        if (call) {
            throw new IllegalArgumentException("E_ANIM_MOLANG:fn <" + name
                    + "> (want one of sin/cos/sqrt/abs/floor/ceil/round/clamp/lerp/max/min/pow)");
        }
        if (name.contains("=") || name.equals(";")) {
            throw new IllegalArgumentException("E_ANIM_MOLANG:assign (assignments never evaluate)");
        }
        throw new IllegalArgumentException("E_ANIM_MOLANG:scope <" + name
                + "> (want query.* / variable.* / math.* / true / false)");
    }

    private static Node queryNode(String name) {
        if (name.equals("query.anim_time")) {
            return new Node(Node.QUERY, 0.0, 0, null, null, null, null, null);
        }
        if (name.equals("query.life_time")) {
            return new Node(Node.QUERY, 0.0, 1, null, null, null, null, null);
        }
        if (name.equals("query.modified_distance_moved")) {
            return new Node(Node.QUERY, 0.0, 2, null, null, null, null, null);
        }
        if (name.equals("query.delta_time")) {
            return new Node(Node.QUERY, 0.0, 3, null, null, null, null, null);
        }
        throw new IllegalArgumentException("E_ANIM_MOLANG:query <" + name
                + "> (frozen subset: anim_time/life_time/modified_distance_moved/delta_time)");
    }

    private static Node callNode(String fn, Tokenizer t) {
        t.next();
        List<Node> args = new ArrayList<Node>();
        if (t.type != Tokenizer.RPAREN) {
            while (true) {
                args.add(ternary(t));
                if (t.type == Tokenizer.COMMA) {
                    t.next();
                    continue;
                }
                break;
            }
        }
        if (t.type != Tokenizer.RPAREN) {
            throw new IllegalArgumentException("E_ANIM_MOLANG:syntax (want ) after " + fn + ")");
        }
        t.next();
        int want = -1;
        if (fn.equals("sin") || fn.equals("cos") || fn.equals("sqrt")
                || fn.equals("abs") || fn.equals("floor") || fn.equals("ceil")
                || fn.equals("round")) {
            want = 1;
        } else if (fn.equals("pow")) {
            want = 2;
        } else if (fn.equals("clamp") || fn.equals("lerp")) {
            want = 3;
        } else if (!fn.equals("max") && !fn.equals("min")) {
            throw new IllegalArgumentException("E_ANIM_MOLANG:fn <math." + fn
                    + "> (frozen subset: sin/cos/sqrt/abs/floor/ceil/round/clamp/lerp/max/min/pow)");
        }
        if (want >= 0 && args.size() != want) {
            throw new IllegalArgumentException("E_ANIM_MOLANG:syntax <math." + fn
                    + " wants " + want + " args, got " + args.size() + ">");
        }
        if (want < 0 && args.size() < 2) {
            throw new IllegalArgumentException("E_ANIM_MOLANG:syntax <math." + fn
                    + " wants 2+ args, got " + args.size() + ">");
        }
        return new Node(Node.CALL, 0.0, 0, fn,
                Collections.unmodifiableList(args), null, null, null);
    }

    /** Minimal tokenizer: numbers, dotted idents, two-char operators. */
    static final class Tokenizer {
        static final int NUM = 0;
        static final int IDENT = 1;
        static final int OP = 2;
        static final int LPAREN = 3;
        static final int RPAREN = 4;
        static final int COMMA = 5;
        static final int END = 6;

        private final String src;
        private int pos;
        int type;
        String text;
        double num;

        Tokenizer(String src) {
            this.src = src;
            next();
        }

        void next() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
            if (pos >= src.length()) {
                type = END;
                text = "";
                return;
            }
            char c = src.charAt(pos);
            if (c == '(') {
                pos++;
                type = LPAREN;
                text = "(";
                return;
            }
            if (c == ')') {
                pos++;
                type = RPAREN;
                text = ")";
                return;
            }
            if (c == ',') {
                pos++;
                type = COMMA;
                text = ",";
                return;
            }
            if (c == '?' || c == ':') {
                pos++;
                type = OP;
                text = String.valueOf(c);
                return;
            }
            if (c == '=' && peek(1) == '=') {
                pos += 2;
                type = OP;
                text = "==";
                return;
            }
            if (c == '!' && peek(1) == '=') {
                pos += 2;
                type = OP;
                text = "!=";
                return;
            }
            if (c == '<' && peek(1) == '=') {
                pos += 2;
                type = OP;
                text = "<=";
                return;
            }
            if (c == '>' && peek(1) == '=') {
                pos += 2;
                type = OP;
                text = ">=";
                return;
            }
            if (c == '&' && peek(1) == '&') {
                pos += 2;
                type = OP;
                text = "&&";
                return;
            }
            if (c == '|' && peek(1) == '|') {
                pos += 2;
                type = OP;
                text = "||";
                return;
            }
            if (c == '+' || c == '-' || c == '*' || c == '/' || c == '%'
                    || c == '<' || c == '>' || c == '!' || c == '=') {
                pos++;
                type = OP;
                text = String.valueOf(c);
                if (c == '=') {
                    throw new IllegalArgumentException("E_ANIM_MOLANG:assign "
                            + "(assignments never evaluate)");
                }
                return;
            }
            if (isDigit(c) || (c == '.' && isDigit(peek(1)))) {
                int start = pos;
                while (pos < src.length()
                        && (isDigit(src.charAt(pos)) || src.charAt(pos) == '.'
                        || src.charAt(pos) == 'e' || src.charAt(pos) == 'E'
                        || ((src.charAt(pos) == '+' || src.charAt(pos) == '-')
                        && pos > start && (src.charAt(pos - 1) == 'e'
                        || src.charAt(pos - 1) == 'E')))) {
                    pos++;
                }
                text = src.substring(start, pos);
                try {
                    num = Double.parseDouble(text);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("E_ANIM_MOLANG:syntax <bad number <"
                            + text + ">>");
                }
                type = NUM;
                return;
            }
            if (isIdentStart(c)) {
                int start = pos;
                while (pos < src.length() && isIdentPart(src.charAt(pos))) {
                    pos++;
                }
                text = src.substring(start, pos);
                type = IDENT;
                return;
            }
            if (c == ';') {
                throw new IllegalArgumentException("E_ANIM_MOLANG:assign "
                        + "(statements never evaluate)");
            }
            throw new IllegalArgumentException("E_ANIM_MOLANG:syntax <bad character <"
                    + c + "> at " + pos + ">");
        }

        private char peek(int ahead) {
            int i = pos + ahead;
            return i < src.length() ? src.charAt(i) : 0;
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private static boolean isIdentStart(char c) {
            return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_';
        }

        private static boolean isIdentPart(char c) {
            return isIdentStart(c) || isDigit(c) || c == '.';
        }
    }
}
