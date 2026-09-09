package plc.project.generator;

import plc.project.analyzer.Type;

import java.util.Map;

/**
 * IMPORTANT: This is an API file and should not be modified by your submission.
 */
public final class Environment {

    public static String imports() {
        return """
            import java.math.BigDecimal;
            import java.math.BigInteger;
            import java.math.RoundingMode;
            import java.util.List;
            import java.util.Objects;
            """.stripTrailing();
    }

    public static String definitions() {
        return """
                static Object any = "any";
                static Object equatable = "equatable";
                static Comparable comparable = "comparable";
                static Iterable<BigInteger> iterable = List.of(BigInteger.ONE, BigInteger.TEN);
                static Void debug(Object object) { System.out.println(object); return null; }
                static Void print(Object object) { System.out.println(object); return null; }
                static Iterable<BigInteger> range(BigInteger start, BigInteger end) { return List.of(start, end); /* evaluator solution */ }
                static String variable = "variable";
                static Void function() { return null; }
                static Object functionAny(Object object) { return object; }
                static String functionString(String string) { return string; }
                static class EnvironmentObject {
                    public String property = "property";
                    public Void method() { return null; }
                    public Object methodAny(Object object) { return object; }
                    public String methodString(String string) { return string; }
                }
                static EnvironmentObject object = new EnvironmentObject();
            """.stripTrailing();
    }

}
