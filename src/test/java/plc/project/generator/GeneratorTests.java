package plc.project.generator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import plc.project.analyzer.Analyzer;
import plc.project.analyzer.Ir;
import plc.project.analyzer.Scope;
import plc.project.analyzer.Type;
import plc.project.lexer.Lexer;
import plc.project.parser.Ast;
import plc.project.parser.ParseException;
import plc.project.parser.Parser;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Standard JUnit5 parameterized tests. See the RegexTests file from Homework 1
 * or the LexerTests file from the earlier project part for more information.
 */
final class GeneratorTests {

    public sealed interface Input {
        record Ir(plc.project.analyzer.Ir ir) implements Input {}
        record Program(String program) implements Input {}
    }

    @ParameterizedTest
    @MethodSource
    void testSource(String test, Input input, String expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testSource() {
        return Stream.of(
            Arguments.of("Hello World",
                new Input.Ir(new Ir.Source(List.of(
                    new Ir.Stmt.Expression(new Ir.Expr.Function("print", List.of(new Ir.Expr.Literal("Hello, World!", Type.STRING)), Type.NIL))
                ))),
                String.join("\n",
                    Environment.imports(),
                    "\npublic final class Main {\n",
                    Environment.definitions(),
                    "",
                    "    public static void main(String[] args) {",
                    "        print(\"Hello, World!\");",
                    "    }",
                    "",
                    "}"
                )
            ),
            Arguments.of("Hoisted Variable Declaration",
                new Input.Ir(new Ir.Source(List.of(
                    new Ir.Stmt.Let("message", Type.STRING, Optional.of(new Ir.Expr.Literal("Hello, World!", Type.STRING))),
                    new Ir.Stmt.Expression(new Ir.Expr.Function("print", List.of(new Ir.Expr.Variable("message", Type.STRING)), Type.NIL))
                ))),
                String.join("\n",
                    Environment.imports(),
                    "\npublic final class Main {\n",
                    Environment.definitions(),
                    "",
                    "    static String message = \"Hello, World!\";",
                    "    public static void main(String[] args) {",
                    "        print(message);",
                    "    }",
                    "",
                    "}"
                )
            ),
            Arguments.of("Hoisted Function Declaration",
                new Input.Ir(new Ir.Source(List.of(
                    new Ir.Stmt.Def("function", List.of(), Type.NIL, List.of(new Ir.Stmt.Return(Optional.empty()))),
                    new Ir.Stmt.Expression(new Ir.Expr.Function("print", List.of(new Ir.Expr.Literal("Hello, World!", Type.STRING)), Type.NIL))
                ))),
                String.join("\n",
                    Environment.imports(),
                    "\npublic final class Main {\n",
                    Environment.definitions(),
                    "",
                    "    static Void function() {",
                    "        return null;",
                    "    }",
                    "    public static void main(String[] args) {",
                    "        print(\"Hello, World!\");",
                    "    }",
                    "",
                    "}"
                )
            ),
            Arguments.of("Unhoisted Function Declaration (Invalid Java)",
                new Input.Ir(new Ir.Source(List.of(
                    new Ir.Stmt.Expression(new Ir.Expr.Function("print", List.of(new Ir.Expr.Literal("Hello, World!", Type.STRING)), Type.NIL)),
                    new Ir.Stmt.Def("function", List.of(), Type.NIL, List.of(new Ir.Stmt.Return(Optional.empty())))
                ))),
                String.join("\n",
                    Environment.imports(),
                    "\npublic final class Main {\n",
                    Environment.definitions(),
                    "",
                    "    public static void main(String[] args) {",
                    "        print(\"Hello, World!\");",
                    "        Void function() {",
                    "            return null;",
                    "        }",
                    "    }",
                    "",
                    "}"
                )
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testLetStmt(String test, Input input, String expected) {
        test(input, expected, "stmt");
    }

    private static Stream<Arguments> testLetStmt() {
        return Stream.of(
            Arguments.of("Declaration",
                new Input.Ir(
                    new Ir.Stmt.Let("name", Type.STRING, Optional.empty())
                ),
                "String name;"
            ),
            Arguments.of("Initialization",
                new Input.Ir(
                    new Ir.Stmt.Let("name", Type.STRING, Optional.of(new Ir.Expr.Literal("value", Type.STRING)))
                ),
                "String name = \"value\";"
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testDefStmt(String test, Input input, String expected) {
        test(input, expected, "stmt");
    }

    private static Stream<Arguments> testDefStmt() {
        return Stream.of(
            Arguments.of("Def",
                new Input.Ir(
                    new Ir.Stmt.Def("name", List.of(), Type.INTEGER, List.of())
                ),
                """
                    BigInteger name() {
                    }
                    """.stripTrailing()
            ),
            Arguments.of("Single Parameter",
                new Input.Ir(
                    new Ir.Stmt.Def("name", List.of(new Ir.Stmt.Def.Parameter("parameter", Type.ANY)), Type.NIL, List.of())
                ),
                """
                    Void name(Object parameter) {
                    }
                    """.stripTrailing()
            ),
            Arguments.of("Multiple Parameters",
                new Input.Ir(
                    new Ir.Stmt.Def("name", List.of(
                        new Ir.Stmt.Def.Parameter("first", Type.ANY),
                        new Ir.Stmt.Def.Parameter("second", Type.INTEGER),
                        new Ir.Stmt.Def.Parameter("third", Type.ITERABLE)
                    ), Type.NIL, List.of())
                ),
                """
                    Void name(Object first, BigInteger second, Iterable<BigInteger> third) {
                    }
                    """.stripTrailing()
            ),
            Arguments.of("Body Statements",
                new Input.Ir(
                    new Ir.Stmt.Def("name", List.of(), Type.NIL, List.of(
                        new Ir.Stmt.Let("message", Type.STRING, Optional.of(new Ir.Expr.Literal("Hello, World!", Type.STRING))),
                        new Ir.Stmt.Expression(new Ir.Expr.Function("print", List.of(new Ir.Expr.Variable("message", Type.STRING)), Type.NIL))
                    ))
                ),
                """
                    Void name() {
                        String message = "Hello, World!";
                        print(message);
                    }
                    """.stripTrailing()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testIfStmt(String test, Input input, String expected) {
        test(input, expected, "stmt");
    }

    private static Stream<Arguments> testIfStmt() {
        return Stream.of(
            Arguments.of("Then",
                new Input.Ir(
                    new Ir.Stmt.If(
                        new Ir.Expr.Literal(true, Type.BOOLEAN),
                        List.of(new Ir.Stmt.Expression(new Ir.Expr.Literal("then", Type.STRING))),
                        List.of()
                    )
                ),
                """
                    if (true) {
                        "then";
                    }
                    """.stripTrailing()
            ),
            Arguments.of("Else",
                new Input.Ir(
                    new Ir.Stmt.If(
                        new Ir.Expr.Literal(false, Type.BOOLEAN),
                        List.of(new Ir.Stmt.Expression(new Ir.Expr.Literal("then", Type.STRING))),
                        List.of(new Ir.Stmt.Expression(new Ir.Expr.Literal("else", Type.STRING)))
                    )
                ),
                """
                    if (false) {
                        "then";
                    } else {
                        "else";
                    }
                    """.stripTrailing()
            ),
            Arguments.of("Empty",
                new Input.Ir(
                    new Ir.Stmt.If(
                        new Ir.Expr.Literal(false, Type.BOOLEAN),
                        List.of(),
                        List.of()
                    )
                ),
                """
                    if (false) {
                    }
                    """.stripTrailing()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testForStmt(String test, Input input, String expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testForStmt() {
        return Stream.of(
            Arguments.of("For",
                new Input.Ir(
                    new Ir.Stmt.For(
                        "element",
                        Type.INTEGER,
                        new Ir.Expr.Function("list", List.of(), Type.ITERABLE),
                        List.of(new Ir.Stmt.Expression(new Ir.Expr.Function("print", List.of(new Ir.Expr.Variable("element", Type.INTEGER)), Type.ANY)))
                    )
                ),
                """
                    for (BigInteger element : list()) {
                        print(element);
                    }
                    """.stripTrailing()
            ),
            Arguments.of("Multiple Body Statements",
                new Input.Ir(
                    new Ir.Stmt.For(
                        "element",
                        Type.INTEGER,
                        new Ir.Expr.Function("list", List.of(), Type.ITERABLE),
                        List.of(
                            new Ir.Stmt.Expression(new Ir.Expr.Literal("first", Type.STRING)),
                            new Ir.Stmt.Expression(new Ir.Expr.Literal("second", Type.STRING)),
                            new Ir.Stmt.Expression(new Ir.Expr.Literal("third", Type.STRING))
                        )
                    )
                ),
                """
                    for (BigInteger element : list()) {
                        "first";
                        "second";
                        "third";
                    }
                    """.stripTrailing()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testReturnStmt(String test, Input input, String expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testReturnStmt() {
        return Stream.of(
            Arguments.of("Return",
                new Input.Ir(
                    new Ir.Stmt.Return(Optional.empty())
                ),
                "return null;"
            ),
            Arguments.of("Value",
                new Input.Ir(
                    new Ir.Stmt.Return(Optional.of(new Ir.Expr.Literal("value", Type.STRING)))
                ),
                "return \"value\";"
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testExpressionStmt(String test, Input input, String expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testExpressionStmt() {
        return Stream.of(
            Arguments.of("Variable",
                new Input.Ir(
                    new Ir.Stmt.Expression(new Ir.Expr.Variable("variable", Type.STRING))
                ),
                "variable;"
            ),
            Arguments.of("Function",
                new Input.Ir(
                    new Ir.Stmt.Expression(new Ir.Expr.Function("functionAny", List.of(new Ir.Expr.Literal("argument", Type.STRING)), Type.ANY))
                ),
                "functionAny(\"argument\");"
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testAssignmentStmt(String test, Input input, String expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testAssignmentStmt() {
        return Stream.of(
            Arguments.of("Variable",
                new Input.Ir(
                    new Ir.Stmt.Assignment.Variable(
                        new Ir.Expr.Variable("variable", Type.STRING),
                        new Ir.Expr.Literal("value", Type.STRING)
                    )
                ),
                "variable = \"value\";"
            ),
            Arguments.of("Property",
                new Input.Ir(
                    new Ir.Stmt.Assignment.Property(
                        new Ir.Expr.Property(new Ir.Expr.Variable("object", Type.ANY /*unused*/), "property", Type.ANY /*unused*/),
                        new Ir.Expr.Literal("value", Type.STRING)
                    )
                ),
                "object.property = \"value\";"
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testLiteralExpr(String test, Input input, String expected) {
        test(input, expected, "expr");
    }

    private static Stream<Arguments> testLiteralExpr() {
        return Stream.of(
            Arguments.of("Boolean",
                new Input.Ir(
                    new Ir.Expr.Literal(true, Type.BOOLEAN)
                ),
                "true"
            ),
            Arguments.of("Integer",
                new Input.Ir(
                    new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER)
                ),
                "new BigInteger(\"1\")"
            ),
            Arguments.of("String",
                new Input.Ir(
                    new Ir.Expr.Literal("string", Type.STRING)
                ),
                "\"string\""
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testGroupExpr(String test, Input input, String expected) {
        test(input, expected, "expr");
    }

    private static Stream<Arguments> testGroupExpr() {
        return Stream.of(
            Arguments.of("Group",
                new Input.Ir(
                    new Ir.Expr.Group(new Ir.Expr.Literal("expr", Type.STRING))
                ),
                "(\"expr\")"
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testBinaryExpr(String test, Input input, String expected) {
        test(input, expected, "expr");
    }

    private static Stream<Arguments> testBinaryExpr() {
        return Stream.of(
            Arguments.of("Op+ Integer Addition",
                new Input.Ir(
                    new Ir.Expr.Binary(
                        "+",
                        new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                        new Ir.Expr.Literal(new BigInteger("2"), Type.INTEGER),
                        Type.INTEGER
                    )
                ),
                "(new BigInteger(\"1\")).add(new BigInteger(\"2\"))"
            ),
            Arguments.of("Op+ Decimal Addition",
                new Input.Ir(
                    new Ir.Expr.Binary(
                        "+",
                        new Ir.Expr.Literal(new BigDecimal("1.0"), Type.DECIMAL),
                        new Ir.Expr.Literal(new BigDecimal("2.0"), Type.DECIMAL),
                        Type.DECIMAL
                    )
                ),
                "(new BigDecimal(\"1.0\")).add(new BigDecimal(\"2.0\"))"
            ),
            Arguments.of("Op+ String Concatenation",
                new Input.Ir(
                    new Ir.Expr.Binary(
                        "+",
                        new Ir.Expr.Literal("left", Type.STRING),
                        new Ir.Expr.Literal("right", Type.STRING),
                        Type.STRING
                    )
                ),
                "\"left\" + \"right\""
            ),
            Arguments.of("Op/ Decimal Rounding Down",
                new Input.Ir(
                    new Ir.Expr.Binary(
                        "/",
                        new Ir.Expr.Literal(new BigDecimal("5"), Type.DECIMAL),
                        new Ir.Expr.Literal(new BigDecimal("2"), Type.DECIMAL),
                        Type.DECIMAL
                    )
                ),
                "(new BigDecimal(\"5\")).divide(new BigDecimal(\"2\"), RoundingMode.HALF_EVEN)"
            ),
            Arguments.of("Op< Integer True",
                new Input.Ir(
                    new Ir.Expr.Binary(
                        "<",
                        new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                        new Ir.Expr.Literal(new BigInteger("2"), Type.INTEGER),
                        Type.BOOLEAN
                    )
                ),
                "(new BigInteger(\"1\")).compareTo(new BigInteger(\"2\")) < 0"
            ),
            Arguments.of("Op== Decimal False",
                new Input.Ir(
                    new Ir.Expr.Binary(
                        "==",
                        new Ir.Expr.Literal(new BigDecimal("1.0"), Type.DECIMAL),
                        new Ir.Expr.Literal(new BigDecimal("2.0"), Type.DECIMAL),
                        Type.DECIMAL
                    )
                ),
                "Objects.equals(new BigDecimal(\"1.0\"), new BigDecimal(\"2.0\"))"
            ),
            Arguments.of("OpAND",
                new Input.Ir(
                    new Ir.Expr.Binary(
                        "AND",
                        new Ir.Expr.Literal(true, Type.BOOLEAN),
                        new Ir.Expr.Literal(false, Type.BOOLEAN),
                        Type.BOOLEAN
                    )
                ),
                "true && false"
            ),
            Arguments.of("OpAND Left OR Precedence",
                new Input.Ir(
                    new Ir.Expr.Binary(
                        "AND",
                        new Ir.Expr.Binary(
                            "OR",
                            new Ir.Expr.Literal(true, Type.BOOLEAN),
                            new Ir.Expr.Literal(false, Type.BOOLEAN),
                            Type.BOOLEAN
                        ),
                        new Ir.Expr.Literal(false, Type.BOOLEAN),
                        Type.BOOLEAN
                    )
                ),
                "(true || false) && false"
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testVariableExpr(String test, Input input, String expected) {
        test(input, expected, "expr");
    }

    private static Stream<Arguments> testVariableExpr() {
        return Stream.of(
            Arguments.of("Variable",
                new Input.Ir(
                    new Ir.Expr.Variable("variable", Type.STRING)
                ),
                "variable"
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testPropertyExpr(String test, Input input, String expected) {
        test(input, expected, "expr");
    }

    private static Stream<Arguments> testPropertyExpr() {
        return Stream.of(
            Arguments.of("Property",
                new Input.Ir(
                    new Ir.Expr.Property(
                        new Ir.Expr.Variable("object", Type.ANY /*unused*/),
                        "property",
                        Type.STRING
                    )
                ),
                "object.property"
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testFunctionExpr(String test, Input input, String expected) {
        test(input, expected, "expr");
    }

    private static Stream<Arguments> testFunctionExpr() {
        return Stream.of(
            Arguments.of("Function",
                new Input.Ir(
                    new Ir.Expr.Function("function", List.of(), Type.NIL)
                ),
                "function()"
            ),
            Arguments.of("Single Argument",
                new Input.Ir(
                    new Ir.Expr.Function("functionAny", List.of(
                        new Ir.Expr.Literal("argument", Type.STRING)
                    ), Type.ANY)
                ),
                "functionAny(\"argument\")"
            ),
            Arguments.of("Multiple Arguments",
                new Input.Ir(
                    new Ir.Expr.Function("range", List.of(
                        new Ir.Expr.Literal(BigInteger.ONE, Type.INTEGER),
                        new Ir.Expr.Literal(BigInteger.TEN, Type.INTEGER)
                    ), Type.ANY)
                ),
                "range(new BigInteger(\"1\"), new BigInteger(\"10\"))"
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testMethodExpr(String test, Input input, String expected) {
        test(input, expected, "expr");
    }

    private static Stream<Arguments> testMethodExpr() {
        return Stream.of(
            Arguments.of("Method",
                new Input.Ir(
                    new Ir.Expr.Method(
                        new Ir.Expr.Variable("object", Type.ANY /*unused*/),
                        "method",
                        List.of(),
                        Type.NIL
                    )
                ),
                "object.method()"
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testObjectExpr(String test, Input input, String expected) {
        test(input, expected, "expr");
    }

    private static Stream<Arguments> testObjectExpr() {
        return Stream.of(
            Arguments.of("Empty",
                new Input.Ir(
                    new Ir.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(),
                        List.of(),
                        Type.ANY /*unused*/
                    )
                ),
                """
                    new Object() {
                    }
                    """.stripTrailing()
            ),
            Arguments.of("Field",
                new Input.Ir(
                    new Ir.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(new Ir.Stmt.Let("field", Type.STRING, Optional.of(new Ir.Expr.Literal("value", Type.STRING)))),
                        List.of(),
                        Type.ANY /*unused*/
                    )
                ),
                """
                    new Object() {
                        String field = "value";
                    }
                    """.stripTrailing()
            ),
            Arguments.of("Method",
                new Input.Ir(
                    new Ir.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(),
                        List.of(new Ir.Stmt.Def("method", List.of(), Type.NIL, List.of())),
                        Type.ANY /*unused*/
                    )
                ),
                """
                    new Object() {
                        Void method() {
                        }
                    }
                    """.stripTrailing()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testProgram(String test, Input input, String expected) {
        test(input, expected, "source");
    }

    public static Stream<Arguments> testProgram() {
        return Stream.of(
            Arguments.of("Factorial",
                //Input.Program makes tests *significantly* easier, but relies
                //on your Lexer and Parser being implemented correctly!
                new Input.Program("""
                    DEF factorial(n: Integer): Integer DO
                        IF n <= 0 DO
                            RETURN 1;
                        ELSE
                            RETURN n * factorial(n - 1);
                        END
                    END
                    print(factorial(10));
                    """),
                String.join("\n",
                    Environment.imports(),
                    "\npublic final class Main {\n",
                    Environment.definitions(),
                    "",
                    "    static BigInteger factorial(BigInteger n) {",
                    "        if ((n).compareTo(new BigInteger(\"0\")) <= 0) {",
                    "            return new BigInteger(\"1\");",
                    "        } else {",
                    "            return (n).multiply(factorial((n).subtract(new BigInteger(\"1\"))));",
                    "        }",
                    "    }",
                    "    public static void main(String[] args) {",
                    "        print(factorial(new BigInteger(\"10\")));",
                    "    }",
                    "",
                    "}"
                )
            )
        );
    }

    /**
     * Test function for the Evaluator. The {@link Input} behaves the same as
     * in parser tests, but will now rely on the parser behavior too. This
     * function tests both the return value of evaluation and evaluation order
     * via the use of a custom log function that tracks invocations.
     */
    private static void test(Input input, String expected, String rule) {
        //First, get/parse the input IR.
        var ir = switch (input) {
            case Input.Ir i -> i.ir();
            case Input.Program i -> Assertions.assertDoesNotThrow(
                () -> new Analyzer(new Scope(plc.project.analyzer.Environment.scope())).visit(new Parser(new Lexer(i.program).lex()).parse(rule))
            );
        };
        //Then, evaluate the input and check the return value.
        try {
            var compiled = new Generator().visit(ir).toString();
            Assertions.assertNotNull(expected, "Expected an exception to be thrown, received " + compiled + ".");
            Assertions.assertEquals(expected, compiled);
        } catch (Exception e) {
            Assertions.assertNull(expected, "Unexpected Exception thrown (" + e.getMessage() +"), expected " + expected + ".");
        }
    }

}
