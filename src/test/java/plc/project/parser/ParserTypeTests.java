package plc.project.parser;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import plc.project.lexer.Lexer;
import plc.project.lexer.Token;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

final class ParserTypeTests {

    @ParameterizedTest
    @MethodSource
    void testLetStmt(String test, Object input, Object expected) {
        test("stmt", input, expected);
    }

    private static Stream<Arguments> testLetStmt() {
        return Stream.of(
            Arguments.of("Type",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "LET"),
                    new Token(Token.Type.IDENTIFIER, "name"),
                    new Token(Token.Type.OPERATOR, ":"),
                    new Token(Token.Type.IDENTIFIER, "Type"),
                    new Token(Token.Type.OPERATOR, ";")
                ),
                new Ast.Stmt.Let("name", Optional.of("Type"), Optional.empty())
            ),
            Arguments.of("Missing Type",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "LET"),
                    new Token(Token.Type.IDENTIFIER, "name"),
                    new Token(Token.Type.OPERATOR, ":"),
                    new Token(Token.Type.OPERATOR, ";")
                ),
                new ParseException("", Optional.of(new Token(Token.Type.OPERATOR, ";")))
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
            Arguments.of("Parameter Type",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "DEF"),
                    new Token(Token.Type.IDENTIFIER, "name"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.IDENTIFIER, "parameter"),
                    new Token(Token.Type.OPERATOR, ":"),
                    new Token(Token.Type.IDENTIFIER, "Type"),
                    new Token(Token.Type.OPERATOR, ")"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "END")
                ),
                new Ast.Stmt.Def("name", List.of("parameter"), List.of(Optional.of("Type")), Optional.empty(), List.of())
            ),
            Arguments.of("Return Type",
                List.of(
                    new Token(Token.Type.IDENTIFIER, "DEF"),
                    new Token(Token.Type.IDENTIFIER, "name"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.OPERATOR, ")"),
                    new Token(Token.Type.OPERATOR, ":"),
                    new Token(Token.Type.IDENTIFIER, "Type"),
                    new Token(Token.Type.IDENTIFIER, "DO"),
                    new Token(Token.Type.IDENTIFIER, "END")
                ),
                new Ast.Stmt.Def("name", List.of(), List.of(), Optional.of("Type"), List.of())
            )
        );
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
