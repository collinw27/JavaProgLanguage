package plc.project.analyzer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import plc.project.lexer.Lexer;
import plc.project.parser.Ast;
import plc.project.parser.Parser;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Standard JUnit5 parameterized tests. See the RegexTests file from Homework 1
 * or the LexerTests file from the earlier project part for more information.
 */
final class AnalyzerTests {

    public sealed interface Input {
        record Ast(plc.project.parser.Ast ast) implements Input {}
        record Program(String program) implements Input {}
    }

    @ParameterizedTest
    @MethodSource
    void testSource(String test, Input input, Ir expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testSource() {
        return Stream.of(
            Arguments.of("Literal",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Literal("value"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Expression(new Ir.Expr.Literal("value", Type.STRING))
                ))
            ),
            Arguments.of("Function",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Function("functionAny", List.of(new Ast.Expr.Literal("value"))))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Expression(new Ir.Expr.Function("functionAny", List.of(new Ir.Expr.Literal("value", Type.STRING)), Type.ANY))
                ))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testLetStmt(String test, Input input, Ir expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testLetStmt() {
        return Stream.of(
            Arguments.of("Declaration",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.empty(), Optional.empty()),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("name"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Let("name", Type.DYNAMIC, Optional.empty()),
                    new Ir.Stmt.Expression(new Ir.Expr.Variable("name", Type.DYNAMIC))
                ))
            ),
            Arguments.of("Declaration Type",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.of("String"), Optional.empty()),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("name"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Let("name", Type.STRING, Optional.empty()),
                    new Ir.Stmt.Expression(new Ir.Expr.Variable("name", Type.STRING))
                ))
            ),
            Arguments.of("Initialization",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.empty(), Optional.of(new Ast.Expr.Literal("value"))),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("name"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Let("name", Type.STRING, Optional.of(new Ir.Expr.Literal("value", Type.STRING))),
                    new Ir.Stmt.Expression(new Ir.Expr.Variable("name", Type.STRING))
                ))
            ),
            Arguments.of("Initialization Type Subtype",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.of("Comparable"), Optional.of(new Ast.Expr.Literal("value"))),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("name"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Let("name", Type.COMPARABLE, Optional.of(new Ir.Expr.Literal("value", Type.STRING))),
                    new Ir.Stmt.Expression(new Ir.Expr.Variable("name", Type.COMPARABLE))
                ))
            ),
            Arguments.of("Initialization Type Invalid",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.of("Undefined"), Optional.of(new Ast.Expr.Literal(null)))
                ))),
                null //AnalyzeException
            ),
            Arguments.of("Redefined",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.empty()),
                    new Ast.Stmt.Let("name", Optional.empty())
                ))),
                null //AnalyzeException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testDefStmt(String test, Input input, Ir expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testDefStmt() {
        return Stream.of(
            Arguments.of("Invocation",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(), Optional.empty(), List.of()),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Def("name", List.of(), Type.DYNAMIC, List.of()),
                    new Ir.Stmt.Expression(new Ir.Expr.Function("name", List.of(), Type.DYNAMIC))
                ))
            ),
            Arguments.of("Parameter Type",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of("parameter"), List.of(Optional.of("String")), Optional.empty(), List.of(
                        new Ast.Stmt.Expression(new Ast.Expr.Variable("parameter"))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of(new Ast.Expr.Literal("argument"))))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Def("name", List.of(new Ir.Stmt.Def.Parameter("parameter", Type.STRING)), Type.DYNAMIC, List.of(
                        new Ir.Stmt.Expression(new Ir.Expr.Variable("parameter", Type.STRING))
                    )),
                    new Ir.Stmt.Expression(new Ir.Expr.Function("name", List.of(new Ir.Expr.Literal("argument", Type.STRING)), Type.DYNAMIC))
                ))
            ),
            //Duplicated in testReturnStmt, but is part of the spec for Def.
            Arguments.of("Return Type/Value",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(), Optional.of("String"), List.of(
                        new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal("value")))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Def("name", List.of(), Type.STRING, List.of(
                        new Ir.Stmt.Return(Optional.of(new Ir.Expr.Literal("value", Type.STRING)))
                    )),
                    new Ir.Stmt.Expression(new Ir.Expr.Function("name", List.of(), Type.STRING))
                ))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testIfStmt(String test, Input input, Ir expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testIfStmt() {
        return Stream.of(
            Arguments.of("If",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.If(new Ast.Expr.Literal(true), List.of(), List.of())
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.If(new Ir.Expr.Literal(true, Type.BOOLEAN), List.of(), List.of())
                ))
            ),
            Arguments.of("Condition Type Invalid",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.If(new Ast.Expr.Literal("true"), List.of(), List.of())
                ))),
                null //AnalyzeException
            ),
            Arguments.of("Scope",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.If(
                        new Ast.Expr.Literal(false),
                        List.of(new Ast.Stmt.Let("name", Optional.empty())),
                        List.of(new Ast.Stmt.Let("name", Optional.empty()))
                    )
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.If(
                        new Ir.Expr.Literal(false, Type.BOOLEAN),
                        List.of(new Ir.Stmt.Let("name", Type.DYNAMIC, Optional.empty())),
                        List.of(new Ir.Stmt.Let("name", Type.DYNAMIC, Optional.empty()))
                    )
                ))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testForStmt(String test, Input input, Ir expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testForStmt() {
        return Stream.of(
            Arguments.of("For",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.For(
                        "element",
                        new Ast.Expr.Function("range", List.of(
                            new Ast.Expr.Literal(new BigInteger("1")),
                            new Ast.Expr.Literal(new BigInteger("5"))
                        )),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable("element")))
                    )
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.For(
                        "element",
                        Type.INTEGER,
                        new Ir.Expr.Function("range", List.of(
                            new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                            new Ir.Expr.Literal(new BigInteger("5"), Type.INTEGER)
                        ), Type.ITERABLE),
                        List.of(new Ir.Stmt.Expression(new Ir.Expr.Variable("element", Type.INTEGER)))
                    )
                ))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testReturnStmt(String test, Input input, Ir expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testReturnStmt() {
        return Stream.of(
            //Part of the spec for Def, but duplicated here for clarity.
            Arguments.of("Inside Function",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(), Optional.of("String"), List.of(
                        new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal("value")))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Def("name", List.of(), Type.STRING, List.of(
                        new Ir.Stmt.Return(Optional.of(new Ir.Expr.Literal("value", Type.STRING)))
                    )),
                    new Ir.Stmt.Expression(new Ir.Expr.Function("name", List.of(), Type.STRING))
                ))
            ),
            Arguments.of("Outside Function",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Return(Optional.empty())
                ))),
                null //AnalyzeException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testExpressionStmt(String test, Input input, Ir expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testExpressionStmt() {
        return Stream.of(
            Arguments.of("Literal",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Literal("literal"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Expression(new Ir.Expr.Literal("literal", Type.STRING))
                ))
            ),
            Arguments.of("Variable",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("variable"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Expression(new Ir.Expr.Variable("variable", Type.STRING))
                ))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testAssignmentStmt(String test, Input input, Ir expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testAssignmentStmt() {
        return Stream.of(
            Arguments.of("Literal",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(new Ast.Expr.Literal("literal"), new Ast.Expr.Literal("value"))
                ))),
                null //AnalyzeException
            ),
            Arguments.of("Variable Value Subtype",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(new Ast.Expr.Variable("variable"), new Ast.Expr.Literal("value"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Assignment.Variable(new Ir.Expr.Variable("variable", Type.STRING), new Ir.Expr.Literal("value", Type.STRING))
                ))
            ),
            Arguments.of("Variable Value Invalid",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(new Ast.Expr.Variable("variable"), new Ast.Expr.Literal(null))
                ))),
                null //AnalyzeException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testLiteralExpr(String test, Input input, Ir expected) {
        test(input, expected, "expr");
    }

    private static Stream<Arguments> testLiteralExpr() {
        return Stream.of(
            Arguments.of("Boolean",
                new Input.Ast(
                    new Ast.Expr.Literal(true)
                ),
                new Ir.Expr.Literal(true, Type.BOOLEAN)
            ),
            Arguments.of("Integer",
                new Input.Ast(
                    new Ast.Expr.Literal(new BigInteger("1"))
                ),
                new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER)
            ),
            Arguments.of("String",
                new Input.Ast(
                    new Ast.Expr.Literal("string")
                ),
                new Ir.Expr.Literal("string", Type.STRING)
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testGroupExpr(String test, Input input, Ir expected) {
        test(input, expected, "expr");
    }

    private static Stream<Arguments> testGroupExpr() {
        return Stream.of(
            Arguments.of("Group",
                new Input.Ast(
                    new Ast.Expr.Group(new Ast.Expr.Literal("expr"))
                ),
                new Ir.Expr.Group(new Ir.Expr.Literal("expr", Type.STRING))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testBinaryExpr(String test, Input input, Ir expected) {
        test(input, expected, "expr");
    }

    private static Stream<Arguments> testBinaryExpr() {
        return Stream.of(
            Arguments.of("Op+ Integer",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "+",
                        new Ast.Expr.Literal(new BigInteger("1")),
                        new Ast.Expr.Literal(new BigInteger("2"))
                    )
                ),
                new Ir.Expr.Binary(
                    "+",
                    new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                    new Ir.Expr.Literal(new BigInteger("2"), Type.INTEGER),
                    Type.INTEGER
                )
            ),
            Arguments.of("Op+ Invalid Right",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "+",
                        new Ast.Expr.Literal(new BigInteger("1")),
                        new Ast.Expr.Literal(new BigDecimal("1.0"))
                    )
                ),
                null //AnalyzeException
            ),
            Arguments.of("Op+ String Right",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "+",
                        new Ast.Expr.Literal(new BigInteger("1")),
                        new Ast.Expr.Literal("right")
                    )
                ),
                new Ir.Expr.Binary(
                    "+",
                    new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                    new Ir.Expr.Literal("right", Type.STRING),
                    Type.STRING
                )
            ),
            Arguments.of("Op+ Dynamic Left",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "+",
                        new Ast.Expr.Variable("dynamic"),
                        new Ast.Expr.Literal(new BigInteger("1"))
                    )
                ),
                new Ir.Expr.Binary(
                    "+",
                    new Ir.Expr.Variable("dynamic", Type.DYNAMIC),
                    new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                    Type.INTEGER
                )
            ),
            Arguments.of("Op< Integer",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "<",
                        new Ast.Expr.Literal(new BigInteger("1")),
                        new Ast.Expr.Literal(new BigInteger("2"))
                    )
                ),
                new Ir.Expr.Binary(
                    "<",
                    new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                    new Ir.Expr.Literal(new BigInteger("2"), Type.INTEGER),
                    Type.BOOLEAN
                )
            ),
            Arguments.of("Op< Right Invalid",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "<",
                        new Ast.Expr.Literal(new BigInteger("1")),
                        new Ast.Expr.Literal(null)
                    )
                ),
                null //AnalyzeException
            ),
            Arguments.of("OpAND Boolean",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "AND",
                        new Ast.Expr.Literal(true),
                        new Ast.Expr.Literal(false)
                    )
                ),
                new Ir.Expr.Binary(
                    "AND",
                    new Ir.Expr.Literal(true, Type.BOOLEAN),
                    new Ir.Expr.Literal(false, Type.BOOLEAN),
                    Type.BOOLEAN
                )
            ),
            Arguments.of("OpOR Right Invalid",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "OR",
                        new Ast.Expr.Literal(true),
                        new Ast.Expr.Literal(null)
                    )
                ),
                null //AnalyzeException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testVariableExpr(String test, Input input, Ir expected) {
        test(input, expected, "expr");
    }

    private static Stream<Arguments> testVariableExpr() {
        return Stream.of(
            Arguments.of("Variable",
                new Input.Ast(
                    new Ast.Expr.Variable("variable")
                ),
                new Ir.Expr.Variable("variable", Type.STRING)
            ),
            Arguments.of("Undefined",
                new Input.Ast(
                    new Ast.Expr.Variable("undefined")
                ),
                null //AnalyzeException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testPropertyExpr(String test, Input input, Ir expected) {
        test(input, expected, "expr");
    }

    private static Stream<Arguments> testPropertyExpr() {
        return Stream.of(
            Arguments.of("Property",
                //Note: Ensure Environment has been updated to defined object.property (String)
                new Input.Ast(
                    new Ast.Expr.Property(
                        new Ast.Expr.Variable("object"),
                        "property"
                    )
                ),
                new Ir.Expr.Property(
                    new Ir.Expr.Variable("object", Environment.scope().resolve("object", true).get()),
                    "property",
                    Type.STRING
                )
            ),
            Arguments.of("Dynamic",
                new Input.Ast(
                    new Ast.Expr.Property(
                        new Ast.Expr.Variable("dynamic"),
                        "property"
                    )
                ),
                new Ir.Expr.Property(
                    new Ir.Expr.Variable("dynamic", Type.DYNAMIC),
                    "property",
                    Type.DYNAMIC
                )
            ),
            Arguments.of("Undefined",
                new Input.Ast(
                    new Ast.Expr.Property(
                        new Ast.Expr.Variable("object"),
                        "undefined"
                    )
                ),
                null //AnalyzeException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testFunctionExpr(String test, Input input, Ir expected) {
        test(input, expected, "expr");
    }

    private static Stream<Arguments> testFunctionExpr() {
        return Stream.of(
            Arguments.of("Function",
                new Input.Ast(
                    new Ast.Expr.Function("function", List.of())
                ),
                new Ir.Expr.Function("function", List.of(), Type.NIL)
            ),
            Arguments.of("Argument",
                new Input.Ast(
                    new Ast.Expr.Function("functionAny", List.of(new Ast.Expr.Literal("argument")))
                ),
                new Ir.Expr.Function("functionAny", List.of(new Ir.Expr.Literal("argument", Type.STRING)), Type.ANY)
            ),
            Arguments.of("Undefined",
                new Input.Ast(
                    new Ast.Expr.Function("undefined", List.of())
                ),
                null //AnalyzeException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testMethodExpr(String test, Input input, Ir expected) {
        test(input, expected, "expr");
    }

    private static Stream<Arguments> testMethodExpr() {
        return Stream.of(
            Arguments.of("Method",
                new Input.Ast(
                    new Ast.Expr.Method(
                        new Ast.Expr.Variable("object"),
                        "methodAny",
                        List.of(new Ast.Expr.Literal("argument"))
                    )
                ),
                new Ir.Expr.Method(
                    new Ir.Expr.Variable("object", Environment.scope().resolve("object", true).get()),
                    "methodAny",
                    List.of(new Ir.Expr.Literal("argument", Type.STRING)),
                    Type.ANY
                )
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testObjectExpr(String test, Input input, Ir expected) {
        test(input, expected, "expr");
    }

    private static Stream<Arguments> testObjectExpr() {
        Function<Map<String, Type>, Type.ObjectType> createObjectType = types -> {
            var type = new Type.ObjectType(Optional.empty(), new Scope(null));
            types.forEach(type.scope()::define);
            return type;
        };
        return Stream.of(
            Arguments.of("Empty",
                new Input.Ast(
                    new Ast.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(),
                        List.of()
                    )
                ),
                new Ir.Expr.ObjectExpr(
                    Optional.empty(),
                    List.of(),
                    List.of(),
                    createObjectType.apply(Map.of())
                )
            ),
            Arguments.of("Field",
                new Input.Ast(
                    new Ast.Expr.Property(
                        new Ast.Expr.ObjectExpr(
                            Optional.empty(),
                            List.of(new Ast.Stmt.Let("field", Optional.of(new Ast.Expr.Literal("value")))),
                            List.of()
                        ),
                        "field"
                    )
                ),
                new Ir.Expr.Property(
                    new Ir.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(new Ir.Stmt.Let("field", Type.STRING, Optional.of(new Ir.Expr.Literal("value", Type.STRING)))),
                        List.of(),
                        createObjectType.apply(Map.of("field", Type.STRING))
                    ),
                    "field",
                    Type.STRING
                )
            ),
            Arguments.of("Method",
                new Input.Ast(
                    new Ast.Expr.Method(
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
                    )
                ),
                new Ir.Expr.Method(
                    new Ir.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(),
                        List.of(new Ir.Stmt.Def("method", List.of(), Type.DYNAMIC, List.of())),
                        createObjectType.apply(Map.of("method", new Type.Function(List.of(), Type.DYNAMIC)))
                    ),
                    "method",
                    List.of(),
                    Type.DYNAMIC
                )
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testProgram(String test, Input input, Ir expected) {
        test(input, expected, "source");
    }

    public static Stream<Arguments> testProgram() {
        return Stream.of(
            Arguments.of("Hello World",
                //Input.Program makes tests *significantly* easier, but relies
                //on your Lexer and Parser being implemented correctly!
                new Input.Program("""
                    DEF main() DO
                        print("Hello, World!");
                    END
                    main();
                    """),
                new Ir.Source(List.of(
                    new Ir.Stmt.Def("main", List.of(), Type.DYNAMIC, List.of(
                        new Ir.Stmt.Expression(new Ir.Expr.Function("print", List.of(new Ir.Expr.Literal("Hello, World!", Type.STRING)), Type.NIL)
                    ))),
                    new Ir.Stmt.Expression(new Ir.Expr.Function("main", List.of(), Type.DYNAMIC))
                ))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testIsSubtypeOf(String test, Type type, Type other, boolean expected) {
        Assertions.assertEquals(expected, type.isSubtypeOf(other), "Expected " + type + " to " + (expected ? "" : "not ") + "be a subtype of " + other + ".");
    }

    public static Stream<Arguments> testIsSubtypeOf() {
        Scope scope1 = new Scope(null);
        scope1.define("prototype", new Type.ObjectType(Optional.empty(), new Scope(null)));
        Scope scope2 = new Scope(null);
        scope2.define("prototype", new Type.ObjectType(Optional.empty(), scope1));
        Scope scope3 = new Scope(null);
        scope3.define("prototype", new Type.ObjectType(Optional.empty(), scope2));
        Scope dscope = new Scope(null);
        dscope.define("prototype", Type.DYNAMIC);
        return Stream.of(
            Arguments.of("Equal", Type.STRING, Type.STRING, true),
            Arguments.of("Disjoint", Type.INTEGER, Type.DECIMAL, false),
            Arguments.of("Subtype", Type.STRING, Type.ANY, true),
            Arguments.of("Supertype", Type.ANY, Type.STRING, false),
            Arguments.of("Nil Equal", Type.NIL, Type.NIL, true),
            Arguments.of("Nil Subtype", Type.NIL, Type.ANY, true),
            Arguments.of("Dynamic Subtype", Type.DYNAMIC, Type.STRING, true),
            Arguments.of("Dynamic Supertype", Type.STRING, Type.DYNAMIC, true),
            Arguments.of("Equatable Subtype", Type.STRING, Type.EQUATABLE, true),
            Arguments.of("Equatable Supertype", Type.ANY, Type.EQUATABLE, false),
            Arguments.of("Comparable Subtype", Type.STRING, Type.COMPARABLE, true),
            Arguments.of("Comparable Non-Subtype", Type.NIL, Type.COMPARABLE, false),
            Arguments.of("Subtype", new Type.ObjectType(Optional.empty(), scope1),
                new Type.ObjectType(Optional.empty(), new Scope(null)), true),
            Arguments.of("Supertype", new Type.ObjectType(Optional.empty(), new Scope(null)),
                new Type.ObjectType(Optional.empty(), scope1), false),
            Arguments.of("Prototype Chain", new Type.ObjectType(Optional.empty(), scope3),
                new Type.ObjectType(Optional.empty(), new Scope(null)), true),
            Arguments.of("Dynamic Prototype Object Subtype", new Type.ObjectType(Optional.empty(), dscope),
                new Type.ObjectType(Optional.empty(), new Scope(null)), true),
            Arguments.of("Dynamic Prototype Other Subtype", new Type.ObjectType(Optional.empty(), dscope),
                Type.STRING, false)
        );
    }

    /**
     * Test function for the Evaluator. The {@link Input} behaves the same as
     * in parser tests, but will now rely on the parser behavior too. This
     * function tests both the return value of evaluation and evaluation order
     * via the use of a custom log function that tracks invocations.
     */
    private static void test(Input input, Ir expected, String rule) {
        //First, get/parse the input AST.
        var ast = switch (input) {
            case Input.Ast i -> i.ast();
            case Input.Program i -> Assertions.assertDoesNotThrow(
                () -> new Parser(new Lexer(i.program).lex()).parse(rule)
            );
        };
        //Next, initialize the analyzer and scope.
        var scope = new Scope(Environment.scope());
        Analyzer analyzer = new Analyzer(scope);
        //Then, analyze the input and check the return value.
        try {
            var ir = analyzer.visit(ast);
            if (expected == null) {
                Assertions.fail("Expected an exception to be thrown, received " + ir + ".");
            }
            Assertions.assertEquals(expected, ir);
        } catch (AnalyzeException e) {
            if (expected != null) {
                Assertions.fail("Unexpected AnalyzeException thrown (" + e.getMessage() + "), expected " + expected + ".");
            }
        }
    }

}
