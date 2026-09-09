package plc.project.lexer;

import com.google.common.base.Preconditions;

import java.sql.SQLXML;
import java.util.ArrayList;
import java.util.List;

/**
 * The lexer works through a combination of {@link #lex()}, which repeatedly
 * calls {@link #lexToken()} and skips over whitespace/comments, and
 * {@link #lexToken()}, which determines the type of the next token and
 * delegates to the corresponding lex method.
 *
 * <p>Additionally, {@link CharStream} manages the lexer state and contains
 * {@link CharStream#peek} and {@link CharStream#match}. These are helpful
 * utilities for working with character state and building tokens.
 */
public final class Lexer {

    private final CharStream chars;

    public Lexer(String input) {
        chars = new CharStream(input);
    }

    public List<Token> lex() throws LexException {
        var tokens = new ArrayList<Token>();
        while (chars.has(0)) {
            if (chars.peek("/", "/")) {
                lexComment();
            }
            else if (chars.peek("[ \b\n\r\t]")) {
                lexWhitespace();
            }
            else {
                tokens.add(lexToken());
            }
        }
        return tokens;
    }

    private void lexWhitespace() {
        Preconditions.checkState(chars.match("[ \b\n\r\t]"));
        while (chars.match("[ \b\n\r\t]")) {}
        chars.emit();
    }

    private void lexComment() {
        Preconditions.checkState(chars.match("/", "/"));
        while (chars.match("[^\\n\\r]")) {}
        chars.emit();
    }

    private Token lexToken() throws LexException {
        if (chars.peek("[A-Za-z_]")) {
            return lexIdentifier();
        }
        else if (chars.peek("[0-9\\-+]", "[0-9]")
                || chars.peek("[0-9]")
        ) {
            return lexNumber();
        }
        else if (chars.peek("'")) {
            return lexCharacter();
        }
        else if (chars.peek("\"")) {
            return lexString();
        }
        else {
            return lexOperator();
        }
    }

    private Token lexIdentifier() {
        Preconditions.checkState(chars.match("[A-Za-z_]"));
        while (chars.match("[A-Za-z_0-9-]")) {}
        return new Token(Token.Type.IDENTIFIER, chars.emit());
    }

    private Token lexNumber() {
        Preconditions.checkState(chars.match("[0-9\\-+]"));
        while (chars.match("[0-9]")) {}
        Token.Type tokenType = Token.Type.INTEGER;
        if (chars.match("\\.", "[0-9]")) {
            tokenType = Token.Type.DECIMAL;
            while (chars.match("[0-9]")) {}
        }
        if (chars.match("e", "[\\-+]", "[0-9]")) {
            while (chars.match("[0-9]")) {}
        }
        else if (chars.match("e", "[0-9]")) {
            while (chars.match("[0-9]")) {}
        }
        return new Token(tokenType, chars.emit());
    }

    private Token lexCharacter() throws LexException {
        Preconditions.checkState(chars.match("'"));
        if (chars.match("[^'\\n\\r\\\\]")) {}
        else if (chars.peek("\\\\")) {
            lexEscape();
        }
        else {
            throw new LexException("Invalid character", chars.index);
        }
        if (!chars.match("'")) {
            throw new LexException("Character does not terminate when expected", chars.index);
        }
        return new Token(Token.Type.CHARACTER, chars.emit());
    }

    private Token lexString() throws LexException {
        Preconditions.checkState(chars.match("\""));
        while (true) {
            if (chars.match("\"")) {
                break;
            }
            else if (chars.match("[^\"\\n\\r\\\\]")) {}
            else if (chars.peek("\\\\")) {
                lexEscape();
            }
            else {
                throw new LexException("Invalid character", chars.index);
            }
        }
        return new Token(Token.Type.STRING, chars.emit());
    }

    private void lexEscape() throws LexException {
        Preconditions.checkState(chars.match("\\\\"));
        if (!chars.match("[bnrt'\"\\\\]")) {
            throw new LexException("Invalid escape character", chars.index);
        }
    }

    public Token lexOperator() throws LexException {
        if (chars.match("[<>!=]")) {
            chars.match("=");
        }
        else if (chars.match("[^A-Za-z_0-9'\" \\n\\r\\t]")) {}
        else {
            throw new LexException("Invalid operator", chars.index);
        }
        return new Token(Token.Type.OPERATOR, chars.emit());
    }

    /**
     * A helper class for maintaining the state of the character stream (input)
     * and methods for building up token literals.
     */
    private static final class CharStream {

        private final String input;
        private int index = 0;
        private int length = 0;

        public CharStream(String input) {
            this.input = input;
        }

        public boolean has(int offset) {
            return index + offset < input.length();
        }

        /**
         * Returns true if the next character(s) match their corresponding
         * pattern(s). Each pattern is a regex matching ONE character, e.g.:
         *  - peek("/") is valid and will match the next character
         *  - peek("/", "/") is valid and will match the next two characters
         *  - peek("/+") is conceptually invalid, but will match one character
         *  - peek("//") is strictly invalid as it can never match one character
         */
        public boolean peek(String... patterns) {
            if (!has(patterns.length - 1)) {
                return false;
            }
            for (int offset = 0; offset < patterns.length; offset++) {
                var character = input.charAt(index + offset);
                if (!String.valueOf(character).matches(patterns[offset])) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Equivalent to peek, but also advances the character stream.
         */
        public boolean match(String... patterns) {
            var peek = peek(patterns);
            if (peek) {
                index += patterns.length;
                length += patterns.length;
            }
            return peek;
        }

        /**
         * Returns the literal built by all characters matched since the last
         * call to emit(); also resetting the length for subsequent tokens.
         */
        public String emit() {
            var literal = input.substring(index - length, index);
            length = 0;
            return literal;
        }

    }

}
