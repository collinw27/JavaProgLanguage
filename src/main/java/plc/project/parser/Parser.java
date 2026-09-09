package plc.project.parser;

import com.google.common.base.Preconditions;
import plc.project.lexer.Token;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * This style of parser is called <em>recursive descent</em>. Each rule in our
 * grammar has dedicated function, and references to other rules correspond to
 * calling that function. Recursive rules are therefore supported by actual
 * recursive calls, while operator precedence is encoded via the grammar.
 *
 * <p>The parser has a similar architecture to the lexer, just with
 * {@link Token}s instead of characters. As before, {@link TokenStream#peek} and
 * {@link TokenStream#match} help with traversing the token stream. Instead of
 * emitting tokens, you will instead need to extract the literal value via
 * {@link TokenStream#get} to be added to the relevant AST.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    public Ast parse(String rule) throws ParseException {
        var ast = switch (rule) {
            case "source" -> parseSource();
            case "stmt" -> parseStmt();
            case "expr" -> parseExpr();
            default -> throw new AssertionError(rule);
        };
        if (tokens.has(0)) {
            throw new ParseException("Expected end of input.", tokens.getNext());
        }
        return ast;
    }

    private Ast.Source parseSource() throws ParseException {
        var statements = new ArrayList<Ast.Stmt>();
        while (tokens.has(0)) {
            statements.add(parseStmt());
        }
        return new Ast.Source(statements);
    }

    private Ast.Stmt parseStmt() throws ParseException {
        if (tokens.peek("LET")) {
            return parseLetStmt();
        }
        else if (tokens.peek("DEF")) {
            return parseDefStmt();
        }
        else if (tokens.peek("IF")) {
            return parseIfStmt();
        }
        else if (tokens.peek("FOR")) {
            return parseForStmt();
        }
        else if (tokens.peek("RETURN")) {
            return parseReturnStmt();
        }
        else {
            return parseExpressionOrAssignmentStmt();
        }
    }

    private Ast.Stmt.Let parseLetStmt() throws ParseException {
        Preconditions.checkState(tokens.match("LET"));
        if (!tokens.match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier", tokens.getNext());
        }
        String name = tokens.get(-1).literal();
        Optional<String> datatype = Optional.empty();
        if (tokens.match(":")) {
            if (!tokens.match(Token.Type.IDENTIFIER)) {
                throw new ParseException("Expected type identifier", tokens.getNext());
            }
            datatype = Optional.of(tokens.get(-1).literal());
        }
        Optional<Ast.Expr> expr = Optional.empty();
        if (tokens.match("=")) {
            expr = Optional.of(parseExpr());
        }
        if (!tokens.match(";")) {
            throw new ParseException("Expected semicolon", tokens.getNext());
        }
        return new Ast.Stmt.Let(name, datatype, expr);
    }

    private Ast.Stmt.Def parseDefStmt() throws ParseException {
        Preconditions.checkState(tokens.match("DEF"));
        if (!tokens.match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier", tokens.getNext());
        }
        String name = tokens.get(-1).literal();
        ArrayList<String> params = new ArrayList<>();
        ArrayList<Optional<String>> paramTypes = new ArrayList<>();
        ArrayList<Ast.Stmt> body = new ArrayList<>();
        if (!tokens.match("(")) {
            throw new ParseException("Expected opening parenthesis", tokens.getNext());
        }

        // Parse parameters

        if (!tokens.match(")")) {
            boolean first = true;
            while (tokens.has(0) && !tokens.peek(")")) {
                if (!first) {
                    if (!tokens.match(",")) {
                        throw new ParseException("Expected comma", tokens.getNext());
                    }
                }
                first = false;
                if (!tokens.match(Token.Type.IDENTIFIER)) {
                    throw new ParseException("Expected identifier", tokens.getNext());
                }
                params.add(tokens.get(-1).literal());
                Optional<String> datatype = Optional.empty();
                if (tokens.match(":")) {
                    if (!tokens.match(Token.Type.IDENTIFIER)) {
                        throw new ParseException("Expected type identifier", tokens.getNext());
                    }
                    datatype = Optional.of(tokens.get(-1).literal());
                }
                paramTypes.add(datatype);
            }
            if (!tokens.match(")")) {
                throw new ParseException("Expected closing parenthesis", tokens.getNext());
            }
        }

        // Parse return type

        Optional<String> returnType = Optional.empty();
        if (tokens.match(":")) {
            if (!tokens.match(Token.Type.IDENTIFIER)) {
                throw new ParseException("Expected type identifier", tokens.getNext());
            }
            returnType = Optional.of(tokens.get(-1).literal());
        }

        // Parse body

        if (!tokens.match("DO")) {
            throw new ParseException("Expected DO", tokens.getNext());
        }
        while (tokens.has(0) && !tokens.peek("END")) {
            body.add(parseStmt());
        }
        if (!tokens.match("END")) {
            throw new ParseException("Expected END", tokens.getNext());
        }

        return new Ast.Stmt.Def(name, params, paramTypes, returnType, body);
    }

    private Ast.Stmt parseIfStmt() throws ParseException {
        Preconditions.checkState(tokens.match("IF"));
        Ast.Expr condition = parseExpr();
        if (!tokens.match("DO")) {
            throw new ParseException("Expected DO", tokens.getNext());
        }
        ArrayList<Ast.Stmt> body = new ArrayList<>();
        ArrayList<Ast.Stmt> elseBody = new ArrayList<>();
        while (tokens.has(0) && !tokens.peek("ELSE") && !tokens.peek("END")) {
            body.add(parseStmt());
        }
        if (tokens.match("ELSE")) {
            while (tokens.has(0) && !tokens.peek("END")) {
                elseBody.add(parseStmt());
            }
        }
        if (!tokens.match("END")) {
            throw new ParseException("Expected END", tokens.getNext());
        }

        return new Ast.Stmt.If(condition, body, elseBody);
    }

    private Ast.Stmt parseForStmt() throws ParseException {
        Preconditions.checkState(tokens.match("FOR"));
        if (!tokens.match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier", tokens.getNext());
        }
        String iterator = tokens.get(-1).literal();
        if (!tokens.match("IN")) {
            throw new ParseException("Expected IN", tokens.getNext());
        }
        Ast.Expr iterable = parseExpr();
        ArrayList<Ast.Stmt> body = new ArrayList<>();
        if (!tokens.match("DO")) {
            throw new ParseException("Expected DO", tokens.getNext());
        }
        while (tokens.has(0) && !tokens.peek("END")) {
            body.add(parseStmt());
        }
        if (!tokens.match("END")) {
            throw new ParseException("Expected END", tokens.getNext());
        }

        return new Ast.Stmt.For(iterator, iterable, body);
    }

    private Ast.Stmt parseReturnStmt() throws ParseException {
        Preconditions.checkState(tokens.match("RETURN"));
        Optional<Ast.Expr> expr = Optional.empty();
        if (tokens.match(";")) {
            return new Ast.Stmt.Return(expr);
        }
        if (!tokens.peek("IF")) {
            expr = Optional.of(parseExpr());
        }
        Ast.Stmt output = new Ast.Stmt.Return(expr);
        if (tokens.match("IF")) {
            Ast.Expr condition = parseExpr();
            ArrayList<Ast.Stmt> body = new ArrayList<>();
            body.add(output);
            output = new Ast.Stmt.If(condition, body, new ArrayList<>());
        }
        if (!tokens.match(";")) {
            throw new ParseException("Expected semicolon", tokens.getNext());
        }
        return output;
    }

    private Ast.Stmt parseExpressionOrAssignmentStmt() throws ParseException {
        Ast.Stmt output;
        Ast.Expr leftSide = parseExpr();
        if (tokens.match("=")) {
            Ast.Expr rightSide = parseExpr();
            output = new Ast.Stmt.Assignment(leftSide, rightSide);
        }
        else {
            output = new Ast.Stmt.Expression(leftSide);
        }
        if (!tokens.match(";")) {
            throw new ParseException("Expected ';'", tokens.getNext());
        }
        return output;
    }

    private Ast.Expr parseExpr() throws ParseException {
        return parseLogicalExpr();
    }

    private Ast.Expr parseLogicalExpr() throws ParseException {
        Ast.Expr leftSide = parseComparisonExpr();
        while (tokens.match("AND") || tokens.match("OR")) {
            String operator = tokens.get(-1).literal();
            Ast.Expr rightSide = parseComparisonExpr();
            leftSide = new Ast.Expr.Binary(operator, leftSide, rightSide);
        }
        return leftSide;
    }

    private Ast.Expr parseComparisonExpr() throws ParseException {
        Ast.Expr leftSide = parseAdditiveExpr();
        while (tokens.match("<") || tokens.match(">")
            || tokens.match("<=") || tokens.match(">=")
            || tokens.match("==") || tokens.match("!=") )
        {
            String operator = tokens.get(-1).literal();
            Ast.Expr rightSide = parseAdditiveExpr();
            leftSide = new Ast.Expr.Binary(operator, leftSide, rightSide);
        }
        return leftSide;
    }

    private Ast.Expr parseAdditiveExpr() throws ParseException {
        Ast.Expr leftSide = parseMultiplicativeExpr();
        while (tokens.match("+") || tokens.match("-")) {
            String operator = tokens.get(-1).literal();
            Ast.Expr rightSide = parseMultiplicativeExpr();
            leftSide = new Ast.Expr.Binary(operator, leftSide, rightSide);
        }
        return leftSide;
    }

    private Ast.Expr parseMultiplicativeExpr() throws ParseException {
        Ast.Expr leftSide = parseSecondaryExpr();
        while (tokens.match("*") || tokens.match("/")) {
            String operator = tokens.get(-1).literal();
            Ast.Expr rightSide = parseSecondaryExpr();
            leftSide = new Ast.Expr.Binary(operator, leftSide, rightSide);
        }
        return leftSide;
    }

    private Ast.Expr parseSecondaryExpr() throws ParseException {
        Ast.Expr currentExpr = parsePrimaryExpr();
        while (tokens.peek(".")) {
            currentExpr = parsePropertyOrMethod(currentExpr);
        }
        return currentExpr;
    }

    private Ast.Expr parsePropertyOrMethod(Ast.Expr receiver) throws ParseException {
        Preconditions.checkState(tokens.match("."));
        if (!tokens.match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier", tokens.getNext());
        }

        // Opt to return member if no parenthesis for invocation

        String name = tokens.get(-1).literal();
        if (!tokens.match("(")) {
            return new Ast.Expr.Property(receiver, name);
        }

        // Otherwise, return method call

        ArrayList<Ast.Expr> args = new ArrayList<>();
        if (!tokens.match(")")) {
            args.add(parseExpr());
            while (tokens.has(0) && !tokens.peek(")")) {
                if (!tokens.match(",")) {
                    throw new ParseException("Expected comma", tokens.getNext());
                }
                args.add(parseExpr());
            }
            if (!tokens.match(")")) {
                throw new ParseException("Expected parenthesis", tokens.getNext());
            }
        }
        return new Ast.Expr.Method(receiver, name, args);
    }

    private Ast.Expr parsePrimaryExpr() throws ParseException {
        if (tokens.peek("NIL") || tokens.peek("TRUE")
            || tokens.peek("FALSE") || tokens.peek(Token.Type.INTEGER)
            || tokens.peek(Token.Type.DECIMAL) || tokens.peek(Token.Type.CHARACTER)
            || tokens.peek(Token.Type.STRING))
        {
            return parseLiteralExpr();
        }
        else if (tokens.peek("(")) {
            return parseGroupExpr();
        }
        else if (tokens.peek("OBJECT")) {
            return parseObjectExpr();
        }
        else if (tokens.peek(Token.Type.IDENTIFIER)) {
            return parseVariableOrFunctionExpr();
        }
        else {
            throw new ParseException("Cannot identify expression type", tokens.getNext());
        }
    }

    private Ast.Expr parseLiteralExpr() throws ParseException {
        if (tokens.match("NIL")) {
            return new Ast.Expr.Literal(null);
        }
        else if (tokens.match("TRUE")) {
            return new Ast.Expr.Literal(true);
        }
        else if (tokens.match("FALSE")) {
            return new Ast.Expr.Literal(false);
        }
        else if (tokens.match(Token.Type.INTEGER)) {

            // Parse and remove sign

            String token = tokens.get(-1).literal();
            int sign = token.startsWith("-") ? -1 : 1;
            if (token.startsWith("-") || token.startsWith("+")) {
                token = token.substring(1);
            }

            // Parse exponent, if necessary (assuming valid input)
            // Exponent cannot be negative! (Even if there are enough zeros for
            // the result to still be an integer

            if (token.contains("e")) {
                var mantissa = new BigInteger(token.split("e")[0]);
                var exponent = Integer.parseInt(token.split("e")[1]);
                if (exponent < 0) {
                    throw new ParseException("Negative exponent", Optional.of(tokens.get(-1)));
                }
                var pow_of_10 = BigInteger.valueOf(10).pow(exponent);
                var output = mantissa.multiply(pow_of_10).multiply(BigInteger.valueOf(sign));
                return new Ast.Expr.Literal(output);
            }
            else {
                var output = new BigInteger(token).multiply(BigInteger.valueOf(sign));
                return new Ast.Expr.Literal(output);
            }
        }
        else if (tokens.match(Token.Type.DECIMAL)) {

            // Parse sign and truncate

            String token = tokens.get(-1).literal();
            boolean negative = token.startsWith("-");
            if (token.startsWith("-") || token.startsWith("+")) {
                token = token.substring(1);
            }

            // Parse float with exponent

            var output = new BigDecimal(tokens.get(-1).literal());
            if (negative) {
                output = output.negate();
            }
            return new Ast.Expr.Literal(output);
        }
        else if (tokens.match(Token.Type.CHARACTER)) {
            String charr = tokens.get(-1).literal();
            charr = charr.substring(1, charr.length() - 1); // Remove quotes
            charr = charr.translateEscapes(); // Fix escapes
            return new Ast.Expr.Literal(charr.charAt(0));
        }
        else if (tokens.match(Token.Type.STRING)) {
            String string = tokens.get(-1).literal();
            string = string.substring(1, string.length() - 1); // Remove quotes
            string = string.translateEscapes(); // Fix escapes
            return new Ast.Expr.Literal(string);
        }
        else {
            throw new ParseException("Invalid token: " + tokens.get(-1), tokens.getNext());
        }
    }

    private Ast.Expr parseGroupExpr() throws ParseException {
        Preconditions.checkState(tokens.match("("));
        Ast.Expr expr = parseExpr();
        if (!tokens.match(")")) {
            throw new ParseException("Expected closing parenthesis", tokens.getNext());
        }
        return new Ast.Expr.Group(expr);
    }

    private Ast.Expr parseObjectExpr() throws ParseException {
        Preconditions.checkState(tokens.match("OBJECT"));
        String name = null;
        ArrayList<Ast.Stmt.Let> fields = new ArrayList<>();
        ArrayList<Ast.Stmt.Def> defs = new ArrayList<>();

        // Read name

        if (!tokens.peek("DO")) {
            if (!tokens.match(Token.Type.IDENTIFIER)) {
                throw new ParseException("Expected identifier", tokens.getNext());
            }
            name = tokens.get(-1).literal();
        }
        if (!tokens.match("DO")) {
            throw new ParseException("Expected DO", tokens.getNext());
        }

        // Read fields/methods

        while (tokens.peek("LET")) {
            fields.add(parseLetStmt());
        }
        while (tokens.peek("DEF")) {
            defs.add(parseDefStmt());
        }
        if (!tokens.match("END")) {
            throw new ParseException("Expected END", tokens.getNext());
        }

        return new Ast.Expr.ObjectExpr(Optional.ofNullable(name), fields, defs);
    }

    private Ast.Expr parseVariableOrFunctionExpr() throws ParseException {
        Preconditions.checkState(tokens.match(Token.Type.IDENTIFIER));

        // Opt to return variable if no parenthesis for invocation

        String name = tokens.get(-1).literal();
        if (!tokens.match("(")) {
            return new Ast.Expr.Variable(name);
        }

        // Otherwise, return function call

        ArrayList<Ast.Expr> args = new ArrayList<>();
        if (!tokens.match(")")) {
            args.add(parseExpr());
            while (tokens.has(0) && !tokens.peek(")")) {
                if (!tokens.match(",")) {
                    throw new ParseException("Expected comma", tokens.getNext());
                }
                args.add(parseExpr());
            }
            if (!tokens.match(")")) {
                throw new ParseException("Expected closing parenthesis", tokens.getNext());
            }
        }
        return new Ast.Expr.Function(name, args);
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at (index + offset).
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Returns the token at (index + offset).
         */
        public Token get(int offset) {
            Preconditions.checkState(has(offset));
            return tokens.get(index + offset);
        }

        /**
         * Returns the next token, if present.
         */
        public Optional<Token> getNext() {
            return index < tokens.size() ? Optional.of(tokens.get(index)) : Optional.empty();
        }

        /**
         * Returns true if the next characters match their corresponding
         * pattern. Each pattern is either a {@link Token.Type}, matching tokens
         * of that type, or a {@link String}, matching tokens with that literal.
         * In effect, {@code new Token(Token.Type.IDENTIFIER, "literal")} is
         * matched by both {@code peek(Token.Type.IDENTIFIER)} and
         * {@code peek("literal")}.
         */
        public boolean peek(Object... patterns) {
            if (!has(patterns.length - 1)) {
                return false;
            }
            for (int offset = 0; offset < patterns.length; offset++) {
                var token = tokens.get(index + offset);
                var pattern = patterns[offset];
                Preconditions.checkState(pattern instanceof Token.Type || pattern instanceof String, pattern);
                if (!token.type().equals(pattern) && !token.literal().equals(pattern)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Equivalent to peek, but also advances the token stream.
         */
        public boolean match(Object... patterns) {
            var peek = peek(patterns);
            if (peek) {
                index += patterns.length;
            }
            return peek;
        }

    }

}
