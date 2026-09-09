package plc.project.parser;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import plc.project.lexer.Lexer;
import plc.project.lexer.Token;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

final class ParserTests {

    @ParameterizedTest
    @MethodSource
    void testSource(String test, Object input, Object expected) {
        test("source", input, expected);
    }

    private static Stream<Arguments> testSource() {
        return Stream.of(
            Arguments.of("Single",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "stmt"),
                    new Token(Token.Type.OPERATOR, ";")
                ),
                new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("stmt"))
                ))
            ),
            Arguments.of("Multiple",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "first"),
                    new Token(Token.Type.OPERATOR, ";"),
                    new Token(Token.Type.IDENTIFIER, "second"),
                    new Token(Token.Type.OPERATOR, ";"),
                    new Token(Token.Type.IDENTIFIER, "third"),
                    new Token(Token.Type.OPERATOR, ";")
                ),
                new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("first")),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("second")),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("third"))
                ))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testLetStmt(String test, Object input, Object expected) {
        test("stmt", input, expected);
    }

    private static Stream<Arguments> testLetStmt() {
        return Stream.of(
            Arguments.of("Declaration",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "LET"),
                    new Token(Token.Type.IDENTIFIER, "name"),
                    new Token(Token.Type.OPERATOR, ";")
                ),
                new Ast.Stmt.Let("name", Optional.empty())
            ),
            Arguments.of("Initialization",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "LET"),
                    new Token(Token.Type.IDENTIFIER, "name"),
                    new Token(Token.Type.OPERATOR, "="),
                    new Token(Token.Type.IDENTIFIER, "expr"),
                    new Token(Token.Type.OPERATOR, ";")
                ),
                new Ast.Stmt.Let("name", Optional.of(new Ast.Expr.Variable("expr")))
            ),
            Arguments.of("Missing Semicolon",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "LET"),
                    new Token(Token.Type.IDENTIFIER, "name"),
                    new Token(Token.Type.OPERATOR, "="),
                    new Token(Token.Type.IDENTIFIER, "expr")
                ),
                new ParseException("", Optional.empty())
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testDefStmt(String test, Object input, Object expected) {
        test("stmt", input, expected);
    }

    private static Stream<Arguments> testDefStmt() {
        return Stream.of(
            Arguments.of("Def",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "DEF"),
                    new Token(Token.Type.IDENTIFIER, "name"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.OPERATOR, ")"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "END")
                ),
                new Ast.Stmt.Def("name", List.of(), List.of())
            ),
            Arguments.of("Parameter",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "DEF"),
                    new Token(Token.Type.IDENTIFIER, "name"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.IDENTIFIER, "parameter"),
                    new Token(Token.Type.OPERATOR, ")"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "END")
                ),
                new Ast.Stmt.Def("name", List.of("parameter"), List.of())
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testIfStmt(String test, Object input, Object expected) {
        test("stmt", input, expected);
    }

    private static Stream<Arguments> testIfStmt() {
        return Stream.of(
            Arguments.of("If",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "IF"),
                    new Token(Token.Type.IDENTIFIER, "cond"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "then"),
                    new Token(Token.Type.OPERATOR, ";"),
                    new Token(Token.Type.IDENTIFIER, "END")
                ),
                new Ast.Stmt.If(
                    new Ast.Expr.Variable("cond"),
                    List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable("then"))),
                    List.of()
                )
            ),
            Arguments.of("Else",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "IF"),
                    new Token(Token.Type.IDENTIFIER, "cond"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "then"),
                    new Token(Token.Type.OPERATOR, ";"),
                    new Token(Token.Type.IDENTIFIER, "ELSE"),
                    new Token(Token.Type.IDENTIFIER, "else"),
                    new Token(Token.Type.OPERATOR, ";"),
                    new Token(Token.Type.IDENTIFIER, "END")
                ),
                new Ast.Stmt.If(
                    new Ast.Expr.Variable("cond"),
                    List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable("then"))),
                    List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable("else")))
                )
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testForStmt(String test, Object input, Object expected) {
        test("stmt", input, expected);
    }

    private static Stream<Arguments> testForStmt() {
        return Stream.of(
            Arguments.of("For",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "FOR"),
                    new Token(Token.Type.IDENTIFIER, "name"),
                    new Token(Token.Type.IDENTIFIER, "IN"),
                    new Token(Token.Type.IDENTIFIER, "expr"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "stmt"),
                    new Token(Token.Type.OPERATOR, ";"),
                    new Token(Token.Type.IDENTIFIER, "END")
                ),
                new Ast.Stmt.For(
                    "name",
                    new Ast.Expr.Variable("expr"),
                    List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable("stmt")))
                )
            ),
            Arguments.of("Missing In",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "FOR"),
                    new Token(Token.Type.IDENTIFIER, "name"),
                    new Token(Token.Type.IDENTIFIER, "expr"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "stmt"),
                    new Token(Token.Type.OPERATOR, ";"),
                    new Token(Token.Type.IDENTIFIER, "END")
                ),
                new ParseException("", Optional.of(new Token(Token.Type.IDENTIFIER, "expr")))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testReturnStmt(String test, Object input, Object expected) {
        test("stmt", input, expected);
    }

    private static Stream<Arguments> testReturnStmt() {
        return Stream.of(
            Arguments.of("Return Expr",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "RETURN"),
                    new Token(Token.Type.IDENTIFIER, "expr"),
                    new Token(Token.Type.OPERATOR, ";")
                ),
                new Ast.Stmt.Return(Optional.of(new Ast.Expr.Variable("expr")))
            ),
            Arguments.of("Return If",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "RETURN"),
                    new Token(Token.Type.IDENTIFIER, "IF"),
                    new Token(Token.Type.IDENTIFIER, "cond"),
                    new Token(Token.Type.OPERATOR, ";")
                ),
                new Ast.Stmt.If(
                    new Ast.Expr.Variable("cond"),
                    List.of(new Ast.Stmt.Return(Optional.empty())),
                    List.of()
                )
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testExpressionStmt(String test, Object input, Object expected) {
        test("stmt", input, expected);
    }

    private static Stream<Arguments> testExpressionStmt() {
        return Stream.of(
            Arguments.of("Variable",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "variable"),
                    new Token(Token.Type.OPERATOR, ";")
                ),
                new Ast.Stmt.Expression(new Ast.Expr.Variable("variable"))
            ),
            Arguments.of("Function",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "function"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.OPERATOR, ")"),
                    new Token(Token.Type.OPERATOR, ";")
                ),
                new Ast.Stmt.Expression(new Ast.Expr.Function("function", List.of()))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testAssignmentStmt(String test, Object input, Object expected) {
        test("stmt", input, expected);
    }

    private static Stream<Arguments> testAssignmentStmt() {
        return Stream.of(
            Arguments.of("Variable",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "variable"),
                    new Token(Token.Type.OPERATOR, "="),
                    new Token(Token.Type.IDENTIFIER, "value"),
                    new Token(Token.Type.OPERATOR, ";")
                ),
                new Ast.Stmt.Assignment(
                    new Ast.Expr.Variable("variable"),
                    new Ast.Expr.Variable("value")
                )
            ),
            Arguments.of("Missing Value",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "object"),
                    new Token(Token.Type.OPERATOR, "."),
                    new Token(Token.Type.IDENTIFIER, "property"),
                    new Token(Token.Type.OPERATOR, "="),
                    new Token(Token.Type.OPERATOR, ";")
                ),
                new ParseException("", Optional.of(new Token(Token.Type.OPERATOR, ";")))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testLiteralExpr(String test, Object input, Object expected) {
        test("expr", input, expected);
    }

    private static Stream<Arguments> testLiteralExpr() {
        return Stream.of(
            Arguments.of("Nil",
                List.of(new Token(Token.Type.IDENTIFIER, "NIL")),
                new Ast.Expr.Literal(null)
            ),
            Arguments.of("Boolean",
                List.of(new Token(Token.Type.IDENTIFIER, "TRUE")),
                new Ast.Expr.Literal(true)
            ),
            Arguments.of("Integer",
                List.of(new Token(Token.Type.INTEGER, "1")),
                new Ast.Expr.Literal(new BigInteger("1"))
            ),
            Arguments.of("Decimal",
                List.of(new Token(Token.Type.DECIMAL, "1.0")),
                new Ast.Expr.Literal(new BigDecimal("1.0"))
            ),
            Arguments.of("Character",
                List.of(new Token(Token.Type.CHARACTER, "\'c\'")),
                new Ast.Expr.Literal('c')
            ),
            Arguments.of("String",
                List.of(new Token(Token.Type.STRING, "\"string\"")),
                new Ast.Expr.Literal("string")
            ),
            Arguments.of("String Newline Escape",
                List.of(new Token(Token.Type.STRING, "\"Hello,\\nWorld!\"")),
                new Ast.Expr.Literal("Hello,\nWorld!")
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testGroupExpr(String test, Object input, Object expected) {
        test("expr", input, expected);
    }

    private static Stream<Arguments> testGroupExpr() {
        return Stream.of(
            Arguments.of("Group",
                List.of(
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.IDENTIFIER, "expr"),
                    new Token(Token.Type.OPERATOR, ")")
                ),
                new Ast.Expr.Group(new Ast.Expr.Variable("expr"))
            ),
            Arguments.of("Missing Expression",
                List.of(
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.OPERATOR, ")")
                ),
                new ParseException("", Optional.of(new Token(Token.Type.OPERATOR, ")")))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testBinaryExpr(String test, Object input, Object expected) {
        test("expr", input, expected);
    }

    private static Stream<Arguments> testBinaryExpr() {
        return Stream.of(
            Arguments.of("Addition",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "left"),
                    new Token(Token.Type.OPERATOR, "+"),
                    new Token(Token.Type.IDENTIFIER, "right")
                ),
                new Ast.Expr.Binary(
                    "+",
                    new Ast.Expr.Variable("left"),
                    new Ast.Expr.Variable("right")
                )
            ),
            Arguments.of("Multiplication",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "left"),
                    new Token(Token.Type.OPERATOR, "*"),
                    new Token(Token.Type.IDENTIFIER, "right")
                ),
                new Ast.Expr.Binary(
                    "*",
                    new Ast.Expr.Variable("left"),
                    new Ast.Expr.Variable("right")
                )
            ),
            Arguments.of("Equal Precedence",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "first"),
                    new Token(Token.Type.OPERATOR, "+"),
                    new Token(Token.Type.IDENTIFIER, "second"),
                    new Token(Token.Type.OPERATOR, "+"),
                    new Token(Token.Type.IDENTIFIER, "third")
                ),
                new Ast.Expr.Binary(
                    "+",
                    new Ast.Expr.Binary(
                        "+",
                        new Ast.Expr.Variable("first"),
                        new Ast.Expr.Variable("second")
                    ),
                    new Ast.Expr.Variable("third")
                )
            ),
            Arguments.of("Lower Precedence",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "first"),
                    new Token(Token.Type.OPERATOR, "+"),
                    new Token(Token.Type.IDENTIFIER, "second"),
                    new Token(Token.Type.OPERATOR, "*"),
                    new Token(Token.Type.IDENTIFIER, "third")
                ),
                new Ast.Expr.Binary(
                    "+",
                    new Ast.Expr.Variable("first"),
                    new Ast.Expr.Binary(
                        "*",
                        new Ast.Expr.Variable("second"),
                        new Ast.Expr.Variable("third")
                    )
                )
            ),
            Arguments.of("Higher Precedence",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "first"),
                    new Token(Token.Type.OPERATOR, "*"),
                    new Token(Token.Type.IDENTIFIER, "second"),
                    new Token(Token.Type.OPERATOR, "+"),
                    new Token(Token.Type.IDENTIFIER, "third")
                ),
                new Ast.Expr.Binary(
                    "+",
                    new Ast.Expr.Binary(
                        "*",
                        new Ast.Expr.Variable("first"),
                        new Ast.Expr.Variable("second")
                    ),
                    new Ast.Expr.Variable("third")
                )
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testVariableExpr(String test, Object input, Object expected) {
        test("expr", input, expected);
    }

    private static Stream<Arguments> testVariableExpr() {
        return Stream.of(
            Arguments.of("Variable",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "variable")
                ),
                new Ast.Expr.Variable("variable")
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testPropertyExpr(String test, Object input, Object expected) {
        test("expr", input, expected);
    }

    private static Stream<Arguments> testPropertyExpr() {
        return Stream.of(
            Arguments.of("Property",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "receiver"),
                    new Token(Token.Type.OPERATOR, "."),
                    new Token(Token.Type.IDENTIFIER, "property")
                ),
                new Ast.Expr.Property(
                    new Ast.Expr.Variable("receiver"),
                    "property"
                )
            ),
            Arguments.of("Missing Name",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "receiver"),
                    new Token(Token.Type.OPERATOR, ".")
                ),
                new ParseException("", Optional.empty())
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testFunctionExpr(String test, Object input, Object expected) {
        test("expr", input, expected);
    }

    private static Stream<Arguments> testFunctionExpr() {
        return Stream.of(
            Arguments.of("Function",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "function"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.OPERATOR, ")")
                ),
                new Ast.Expr.Function("function", List.of())
            ),
            Arguments.of("Argument",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "function"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.IDENTIFIER, "argument"),
                    new Token(Token.Type.OPERATOR, ")")
                ),
                new Ast.Expr.Function("function", List.of(
                    new Ast.Expr.Variable("argument")
                ))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testMethodExpr(String test, Object input, Ast.Expr.Method expected) {
        test("expr", input, expected);
    }

    private static Stream<Arguments> testMethodExpr() {
        return Stream.of(
            Arguments.of("Method",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "receiver"),
                    new Token(Token.Type.OPERATOR, "."),
                    new Token(Token.Type.IDENTIFIER, "method"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.OPERATOR, ")")
                ),
                new Ast.Expr.Method(
                    new Ast.Expr.Variable("receiver"),
                    "method",
                    List.of()
                )
            ),
            Arguments.of("Arguments",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "receiver"),
                    new Token(Token.Type.OPERATOR, "."),
                    new Token(Token.Type.IDENTIFIER, "method"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.IDENTIFIER, "first"),
                    new Token(Token.Type.OPERATOR, ","),
                    new Token(Token.Type.IDENTIFIER, "second"),
                    new Token(Token.Type.OPERATOR, ","),
                    new Token(Token.Type.IDENTIFIER, "third"),
                    new Token(Token.Type.OPERATOR, ")")
                ),
                new Ast.Expr.Method(
                    new Ast.Expr.Variable("receiver"),
                    "method",
                    List.of(
                        new Ast.Expr.Variable("first"),
                        new Ast.Expr.Variable("second"),
                        new Ast.Expr.Variable("third")
                    )
                )
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testObjectExpr(String test, Object input, Ast.Expr.ObjectExpr expected) {
        test("expr", input, expected);
    }

    private static Stream<Arguments> testObjectExpr() {
        return Stream.of(
            Arguments.of("Field",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "OBJECT"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "LET"),
                    new Token(Token.Type.IDENTIFIER, "field"),
                    new Token(Token.Type.OPERATOR, ";"),
                    new Token(Token.Type.IDENTIFIER, "END")
                ),
                new Ast.Expr.ObjectExpr(
                    Optional.empty(),
                    List.of(new Ast.Stmt.Let("field", Optional.empty())),
                    List.of()
                )
            ),
            Arguments.of("Method",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "OBJECT"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "DEF"),
                    new Token(Token.Type.IDENTIFIER, "method"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.OPERATOR, ")"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "END"),
                    new Token(Token.Type.IDENTIFIER, "END")
                ),
                new Ast.Expr.ObjectExpr(
                    Optional.empty(),
                    List.of(),
                    List.of(new Ast.Stmt.Def("method", List.of(), List.of()))
                )
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testProgram(String test, Object input, Object expected) {
        test("source", input, expected);
    }

    public static Stream<Arguments> testProgram() {
        return Stream.of(
            Arguments.of("Hello World",
                """
                DEF main() DO
                    print("Hello, World!");
                END
                """,
                new Ast.Source(List.of(
                    new Ast.Stmt.Def("main", List.of(), List.of(
                        new Ast.Stmt.Expression(new Ast.Expr.Function(
                            "print",
                            List.of(new Ast.Expr.Literal("Hello, World!"))
                        ))
                    ))
                ))
            )
        );
    }

    interface ParserMethod<T extends Ast> {
        T invoke(Parser parser) throws ParseException;
    }

    private static void test(String rule, Object input, Object expected) {
        var tokens = switch (input) {
            case List<?> list -> (List<Token>) list;
            case String program -> Assertions.assertDoesNotThrow(() -> new Lexer(program).lex());
            default -> throw new AssertionError(input);
        };
        Parser parser = new Parser(tokens);
        switch (expected) {
            case Ast ast -> {
                var received = Assertions.assertDoesNotThrow(() -> parser.parse(rule));
                Assertions.assertEquals(ast, received);
            }
            case ParseException e -> {
                var received = Assertions.assertThrows(ParseException.class, () -> parser.parse(rule));
                Assertions.assertEquals(e.getToken(), received.getToken());
            }
            default -> throw new AssertionError(input);
        }
    }

}
