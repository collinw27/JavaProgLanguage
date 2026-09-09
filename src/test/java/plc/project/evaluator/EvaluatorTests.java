package plc.project.evaluator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import plc.project.lexer.Lexer;
import plc.project.parser.Ast;
import plc.project.parser.Parser;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Standard JUnit5 parameterized tests. See the RegexTests file from Homework 1
 * or the LexerTests file from the earlier project part for more information.
 */
final class EvaluatorTests {

    public sealed interface Input {
        record Ast(plc.project.parser.Ast ast) implements Input {}
        record Program(String program) implements Input {}
    }

    public sealed interface Expected {
        record Success(RuntimeValue value) implements Expected {}
        record Failure(Optional<Class<? extends Ast>> astClass) implements Expected {}
    }

    @ParameterizedTest
    @MethodSource
    void testSource(String test, Input input, Expected expected, List<RuntimeValue> log) {
        test("source", input, expected, log);
    }

    private static Stream<Arguments> testSource() {
        return Stream.of(
            Arguments.of("Single",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("value"))))
                ))),
                new Expected.Success(new RuntimeValue.Primitive("value")),
                List.of(new RuntimeValue.Primitive("value"))
            ),
            Arguments.of("Multiple",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(new BigInteger("1"))))),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(new BigInteger("2"))))),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(new BigInteger("3")))))
                ))),
                new Expected.Success(new RuntimeValue.Primitive(new BigInteger("3"))),
                List.of(
                    new RuntimeValue.Primitive(new BigInteger("1")),
                    new RuntimeValue.Primitive(new BigInteger("2")),
                    new RuntimeValue.Primitive(new BigInteger("3"))
                )
            ),
            //Duplicated in testReturnStmt, but is part of the spec for Source.
            Arguments.of("Unhandled Return",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Return(Optional.empty())
                ))),
                new Expected.Failure(Optional.of(Ast.Source.class)), //EvaluateException
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testLetStmt(String test, Input input, Expected expected, List<RuntimeValue> log) {
        test("source", input, expected, log);
    }

    private static Stream<Arguments> testLetStmt() {
        return Stream.of(
            Arguments.of("Declaration",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.empty()),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("name"))))
                ))),
                new Expected.Success(new RuntimeValue.Primitive(null)),
                List.of(new RuntimeValue.Primitive(null))
            ),
            Arguments.of("Initialization",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.of(new Ast.Expr.Literal("value"))),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("name"))))
                ))),
                new Expected.Success(new RuntimeValue.Primitive("value")),
                List.of(new RuntimeValue.Primitive("value"))
            ),
            Arguments.of("Redefined",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.empty()),
                    new Ast.Stmt.Let("name", Optional.empty())
                ))),
                new Expected.Failure(Optional.of(Ast.Stmt.Let.class)), //EvaluateException
                List.of()
            ),
            Arguments.of("Shadowed",
                new Input.Ast(new Ast.Source(List.of(
                    //"variable" is defined to "variable" in Environment.scope()
                    new Ast.Stmt.Let("variable", Optional.empty())
                ))),
                new Expected.Success(new RuntimeValue.Primitive(null)),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testDefStmt(String test, Input input, Expected expected, List<RuntimeValue> log) {
        test("source", input, expected, log);
    }

    private static Stream<Arguments> testDefStmt() {
        return Stream.of(
            Arguments.of("Invocation",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(
                        new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("invoked"))))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                ))),
                new Expected.Success(new RuntimeValue.Primitive(null)),
                List.of(new RuntimeValue.Primitive("invoked"))
            ),
            Arguments.of("Parameter",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of("parameter"), List.of(
                        new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("parameter"))))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of(new Ast.Expr.Literal("argument"))))
                ))),
                new Expected.Success(new RuntimeValue.Primitive(null)),
                List.of(new RuntimeValue.Primitive("argument"))
            ),
            Arguments.of("Duplicate Parameter",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of("p1", "p1"), List.of(
                        new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("parameter"))))
                    ))
                ))),
                new Expected.Failure(Optional.of(Ast.Stmt.Def.class)),
                List.of()
            ),
            //Duplicated in testReturnStmt, but is part of the spec for Def.
            Arguments.of("Return Value",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(
                        new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal("value")))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                ))),
                new Expected.Success(new RuntimeValue.Primitive("value")),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testIfStmt(String test, Input input, Expected expected, List<RuntimeValue> log) {
        test("source", input, expected, log);
    }

    private static Stream<Arguments> testIfStmt() {
        return Stream.of(
            Arguments.of("Then",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.If(
                        new Ast.Expr.Literal(true),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("then"))))),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("else")))))
                    )
                ))),
                new Expected.Success(new RuntimeValue.Primitive("then")),
                List.of(new RuntimeValue.Primitive("then"))
            ),
            Arguments.of("Else",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.If(
                        new Ast.Expr.Literal(false),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("then"))))),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("else")))))
                    )
                ))),
                new Expected.Success(new RuntimeValue.Primitive("else")),
                List.of(new RuntimeValue.Primitive("else"))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testForStmt(String test, Input input, Expected expected, List<RuntimeValue> log) {
        test("source", input, expected, log);
    }

    private static Stream<Arguments> testForStmt() {
        return Stream.of(
            Arguments.of("For",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.For(
                        "element",
                        new Ast.Expr.Function("list", List.of(
                            new Ast.Expr.Literal(new BigInteger("1")),
                            new Ast.Expr.Literal(new BigInteger("2")),
                            new Ast.Expr.Literal(new BigInteger("3"))
                        )),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("element")))))
                    )
                ))),
                new Expected.Success(new RuntimeValue.Primitive(null)),
                List.of(
                    new RuntimeValue.Primitive(new BigInteger("1")),
                    new RuntimeValue.Primitive(new BigInteger("2")),
                    new RuntimeValue.Primitive(new BigInteger("3"))
                )
            ),
            Arguments.of("Range",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.For(
                        "element",
                        new Ast.Expr.Function("range", List.of(
                            new Ast.Expr.Literal(new BigInteger("1")),
                            new Ast.Expr.Literal(new BigInteger("5"))
                        )),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("element")))))
                    )
                ))),
                new Expected.Success(new RuntimeValue.Primitive(null)),
                List.of(
                    new RuntimeValue.Primitive(new BigInteger("1")),
                    new RuntimeValue.Primitive(new BigInteger("2")),
                    new RuntimeValue.Primitive(new BigInteger("3")),
                    new RuntimeValue.Primitive(new BigInteger("4"))
                )
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testReturnStmt(String test, Input input, Expected expected, List<RuntimeValue> log) {
        test("source", input, expected, log);
    }

    private static Stream<Arguments> testReturnStmt() {
        return Stream.of(
            //Part of the spec for Def, but duplicated here for clarity.
            Arguments.of("Inside Function",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(
                        new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal("value")))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                ))),
                new Expected.Success(new RuntimeValue.Primitive("value")),
                List.of()
            ),
            //Part of the spec for Source, but duplicated here for clarity.
            Arguments.of("Outside Function",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Return(Optional.empty())
                ))),
                new Expected.Failure(Optional.of(Ast.Source.class)), //EvaluateException
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testExpressionStmt(String test, Input input, Expected expected, List<RuntimeValue> log) {
        test("source", input, expected, log);
    }

    private static Stream<Arguments> testExpressionStmt() {
        return Stream.of(
            Arguments.of("Variable",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("variable"))
                ))),
                new Expected.Success(new RuntimeValue.Primitive("variable")),
                List.of()
            ),
            Arguments.of("Function",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Function("function", List.of(new Ast.Expr.Literal("argument"))))
                ))),
                new Expected.Success(new RuntimeValue.Primitive(List.of(new RuntimeValue.Primitive("argument")))),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testAssignmentStmt(String test, Input input, Expected expected, List<RuntimeValue> log) {
        test("source", input, expected, log);
    }

    private static Stream<Arguments> testAssignmentStmt() {
        return Stream.of(
            Arguments.of("Variable",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(
                        new Ast.Expr.Variable("variable"),
                        new Ast.Expr.Literal("value")
                    ),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("variable"))))
                ))),
                new Expected.Success(new RuntimeValue.Primitive("value")),
                List.of(new RuntimeValue.Primitive("value"))
            ),
            Arguments.of("Property",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(
                        new Ast.Expr.Property(new Ast.Expr.Variable("object"), "property"),
                        new Ast.Expr.Literal("value")
                    ),
                    new Ast.Stmt.Expression(
                        new Ast.Expr.Property(new Ast.Expr.Variable("object"), "property")
                    )
                ))),
                new Expected.Success(new RuntimeValue.Primitive("value")),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testLiteralExpr(String test, Input input, Expected expected, List<RuntimeValue> log) {
        test("expr", input, expected, log);
    }

    private static Stream<Arguments> testLiteralExpr() {
        return Stream.of(
            Arguments.of("Boolean",
                new Input.Ast(new Ast.Expr.Literal(true)),
                new Expected.Success(new RuntimeValue.Primitive(true)),
                List.of()
            ),
            Arguments.of("Integer",
                new Input.Ast(new Ast.Expr.Literal(new BigInteger("1"))),
                new Expected.Success(new RuntimeValue.Primitive(new BigInteger("1"))),
                List.of()
            ),
            Arguments.of("String",
                new Input.Ast(new Ast.Expr.Literal("string")),
                new Expected.Success(new RuntimeValue.Primitive("string")),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testGroupExpr(String test, Input input, Expected expected, List<RuntimeValue> log) {
        test("expr", input, expected, log);
    }

    private static Stream<Arguments> testGroupExpr() {
        return Stream.of(
            Arguments.of("Group",
                new Input.Ast(new Ast.Expr.Group(new Ast.Expr.Literal("expr"))),
                new Expected.Success(new RuntimeValue.Primitive("expr")),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testBinaryExpr(String test, Input input, Expected expected, List<RuntimeValue> log) {
        test("expr", input, expected, log);
    }

    private static Stream<Arguments> testBinaryExpr() {
        return Stream.of(
            Arguments.of("Op+ Integer Addition",
                new Input.Ast(new Ast.Expr.Binary(
                    "+",
                    new Ast.Expr.Literal(new BigInteger("1")),
                    new Ast.Expr.Literal(new BigInteger("2"))
                )),
                new Expected.Success(new RuntimeValue.Primitive(new BigInteger("3"))),
                List.of()
            ),
            Arguments.of("Op+ Decimal Addition",
                new Input.Ast(new Ast.Expr.Binary(
                    "+",
                    new Ast.Expr.Literal(new BigDecimal("1.0")),
                    new Ast.Expr.Literal(new BigDecimal("2.0"))
                )),
                new Expected.Success(new RuntimeValue.Primitive(new BigDecimal("3.0"))),
                List.of()
            ),
                Arguments.of("Op+ String Concatenation",
                        new Input.Ast(new Ast.Expr.Binary(
                                "+",
                                new Ast.Expr.Literal("left"),
                                new Ast.Expr.Literal("right")
                        )),
                        new Expected.Success(new RuntimeValue.Primitive("leftright")),
                        List.of()
                ),
            Arguments.of("Op+ Evaluation Order Left Validation Error",
                new Input.Ast(new Ast.Expr.Binary(
                    "+",
                    new Ast.Expr.Literal(false),
                    new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(true)))
                )),
                new Expected.Failure(Optional.of(Ast.Expr.Literal.class)), //EvaluateException
                List.of(new RuntimeValue.Primitive(true))
            ),
            Arguments.of("Op- Evaluation Order Left Validation Error",
                new Input.Ast(new Ast.Expr.Binary(
                    "-",
                    new Ast.Expr.Literal("invalid"),
                    new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("unevaluated")))
                )),
                new Expected.Failure(Optional.of(Ast.Expr.Literal.class)), //EvaluateException
                List.of()
            ),
            Arguments.of("Op* Evaluation Order Left Execution Error",
                new Input.Ast(new Ast.Expr.Binary(
                    "*",
                    new Ast.Expr.Variable("undefined"),
                    new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("unevaluated")))
                )),
                new Expected.Failure(Optional.of(Ast.Expr.Variable.class)), //EvaluateException
                List.of()
            ),
            Arguments.of("Op/ Decimal Rounding Down",
                new Input.Ast(new Ast.Expr.Binary(
                    "/",
                    new Ast.Expr.Literal(new BigDecimal("5")),
                    new Ast.Expr.Literal(new BigDecimal("2"))
                )),
                new Expected.Success(new RuntimeValue.Primitive(new BigDecimal("2"))),
                List.of()
            ),
            Arguments.of("Op< Integer True",
                new Input.Ast(new Ast.Expr.Binary(
                    "<",
                    new Ast.Expr.Literal(new BigInteger("1")),
                    new Ast.Expr.Literal(new BigInteger("2"))
                )),
                new Expected.Success(new RuntimeValue.Primitive(true)),
                List.of()
            ),
            Arguments.of("Op== Decimal False",
                new Input.Ast(new Ast.Expr.Binary(
                    "==",
                    new Ast.Expr.Literal(new BigDecimal("1.0")),
                    new Ast.Expr.Literal(new BigDecimal("2.0"))
                )),
                new Expected.Success(new RuntimeValue.Primitive(false)),
                List.of()
            ),
            Arguments.of("OpAND False",
                new Input.Ast(new Ast.Expr.Binary(
                    "AND",
                    new Ast.Expr.Literal(true),
                    new Ast.Expr.Literal(false)
                )),
                new Expected.Success(new RuntimeValue.Primitive(false)),
                List.of()
            ),
            Arguments.of("OpOR True Short-Circuit",
                new Input.Ast(new Ast.Expr.Binary(
                    "OR",
                    new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(true))),
                    new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(false)))
                )),
                new Expected.Success(new RuntimeValue.Primitive(true)),
                List.of(new RuntimeValue.Primitive(true))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testVariableExpr(String test, Input input, Expected expected, List<RuntimeValue> log) {
        test("expr", input, expected, log);
    }

    private static Stream<Arguments> testVariableExpr() {
        return Stream.of(
            Arguments.of("Variable",
                new Input.Ast(new Ast.Expr.Variable("variable")),
                new Expected.Success(new RuntimeValue.Primitive("variable")),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testPropertyExpr(String test, Input input, Expected expected, List<RuntimeValue> log) {
        test("expr", input, expected, log);
    }

    private static Stream<Arguments> testPropertyExpr() {
        return Stream.of(
            Arguments.of("Property",
                new Input.Ast(new Ast.Expr.Property(
                    new Ast.Expr.Variable("object"),
                    "property"
                )),
                new Expected.Success(new RuntimeValue.Primitive("property")),
                List.of()
            ),
            Arguments.of("Prototypal Inheritance",
                new Input.Ast(new Ast.Expr.Property(
                    new Ast.Expr.Variable("object"),
                    "inherited_property"
                )),
                new Expected.Success(new RuntimeValue.Primitive("inherited_property")),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testFunctionExpr(String test, Input input, Expected expected, List<RuntimeValue> log) {
        test("expr", input, expected, log);
    }

    private static Stream<Arguments> testFunctionExpr() {
        return Stream.of(
            Arguments.of("Function",
                new Input.Ast(new Ast.Expr.Function("function", List.of())),
                new Expected.Success(new RuntimeValue.Primitive(List.of())),
                List.of()
            ),
            Arguments.of("Argument",
                new Input.Ast(new Ast.Expr.Function("function", List.of(
                    new Ast.Expr.Literal("argument")
                ))),
                new Expected.Success(new RuntimeValue.Primitive(List.of(
                    new RuntimeValue.Primitive("argument"))
                )),
                List.of()
            ),
            Arguments.of("Range",
                new Input.Ast(new Ast.Expr.Function("range", List.of(
                    new Ast.Expr.Literal(new BigInteger("1")),
                    new Ast.Expr.Literal(new BigInteger("5"))
                ))),
                new Expected.Success(new RuntimeValue.Primitive(List.of(
                    new RuntimeValue.Primitive(new BigInteger("1")),
                    new RuntimeValue.Primitive(new BigInteger("2")),
                    new RuntimeValue.Primitive(new BigInteger("3")),
                    new RuntimeValue.Primitive(new BigInteger("4"))
                ))),
                List.of()
            ),
            Arguments.of("Undefined",
                new Input.Ast(new Ast.Expr.Function("undefined", List.of(
                    new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("argument")))
                ))),
                new Expected.Failure(Optional.of(Ast.Expr.Function.class)), //EvaluateException
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testMethodExpr(String test, Input input, Expected expected, List<RuntimeValue> log) {
        test("expr", input, expected, log);
    }

    private static Stream<Arguments> testMethodExpr() {
        return Stream.of(
            Arguments.of("Method",
                new Input.Ast(new Ast.Expr.Method(
                    new Ast.Expr.Variable("object"),
                    "method",
                    List.of(new Ast.Expr.Literal("argument"))
                )),
                new Expected.Success(new RuntimeValue.Primitive(List.of(
                    new RuntimeValue.Primitive("argument")
                ))),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testObjectExpr(String test, Input input, Expected expected, List<RuntimeValue> log) {
        test("expr", input, expected, log);
    }

    private static Stream<Arguments> testObjectExpr() {
        return Stream.of(
            Arguments.of("Field",
                new Input.Ast(new Ast.Expr.Property(
                    new Ast.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(new Ast.Stmt.Let("field", Optional.of(new Ast.Expr.Literal("value")))),
                        List.of()
                    ),
                    "field"
                )),
                new Expected.Success(new RuntimeValue.Primitive("value")),
                List.of()
            ),
            Arguments.of("Method",
                new Input.Ast(new Ast.Expr.Method(
                    new Ast.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(),
                        List.of(new Ast.Stmt.Def(
                            "method",
                            List.of(),
                            List.of()
                        ))
                    ),
                    "method",
                    List.of()
                )),
                new Expected.Success(new RuntimeValue.Primitive(null)),
                List.of()
            ),
            Arguments.of("Method Parameter",
                new Input.Ast(new Ast.Expr.Method(
                    new Ast.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(),
                        List.of(new Ast.Stmt.Def(
                            "method",
                            List.of("parameter"),
                            List.of(new Ast.Stmt.Return(Optional.of(new Ast.Expr.Variable("parameter"))))
                        ))
                    ),
                    "method",
                    List.of(new Ast.Expr.Literal("argument"))
                )),
                new Expected.Success(new RuntimeValue.Primitive("argument")),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testProgram(String test, Input input, Expected expected, List<RuntimeValue> log) {
        test("source", input, expected, log);
    }

    public static Stream<Arguments> testProgram() {
        return Stream.of(
            Arguments.of("Hello World",
                new Input.Program("""
                    DEF main() DO
                        log("Hello, World!");
                    END
                    main();
                    """),
                new Expected.Success(new RuntimeValue.Primitive(null)),
                List.of(new RuntimeValue.Primitive("Hello, World!"))
            )
        );
    }

    /**
     * Test function for the Evaluator. The {@link Input} behaves the same as
     * in parser tests, but will now rely on the parser behavior too. This
     * function tests both the return value of evaluation and evaluation order
     * via the use of a custom log function that tracks invocations.
     */
    private static void test(String rule, Input input, Expected expected, List<RuntimeValue> log) {
        //First, get/parse the input AST.
        var ast = switch (input) {
            case Input.Ast i -> i.ast();
            case Input.Program i -> Assertions.assertDoesNotThrow(
                () -> new Parser(new Lexer(i.program).lex()).parse(rule)
            );
        };
        //Next, initialize the Evaluator with a scope containing a test version
        //of log that saves the logged arguments.
        var scope = new Scope(Environment.scope());
        var logged = new ArrayList<RuntimeValue>();
        scope.define("log", new RuntimeValue.Function("log", arguments -> {
            if (arguments.size() != 1) {
                throw new EvaluateException("Expected log to be called with 1 argument.", Optional.empty());
            }
            logged.add(arguments.getFirst());
            return arguments.getFirst();
        }));
        Evaluator evaluator = new Evaluator(scope);
        //Then, evaluate the input and check the return value.
        try {
            var value = evaluator.visit(ast);
            Assertions.assertInstanceOf(Expected.Success.class, expected, "Expected an exception to be thrown, received " + value + ".");
            Assertions.assertEquals(((Expected.Success) expected).value, value);
        } catch (EvaluateException e) {
            Assertions.assertInstanceOf(Expected.Failure.class, expected,"Unexpected EvaluateException thrown (" + e.getMessage() +"), expected " + expected + ".");
            ((Expected.Failure) expected).astClass.ifPresent(clazz -> {
                Assertions.assertInstanceOf(clazz, e.getAst().orElse(null), "Expected an EvaluateException ast of type " + clazz + ", received " + e.getAst() + ".");
            });
        }
        //Finally, check the log results for evaluation order.
        Assertions.assertEquals(log, logged);
    }

}
