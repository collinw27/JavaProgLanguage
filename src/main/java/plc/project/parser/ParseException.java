package plc.project.parser;

import plc.project.lexer.Token;

import java.util.Optional;

/**
 * IMPORTANT: This is an API file and should not be modified by your submission.
 */
public final class ParseException extends Exception {

    private final Optional<Token> token;

    public ParseException(String message, Optional<Token> token) {
        super(message + "\n - @ token " + token.map(Token::literal).orElse("<EOF>"));
        this.token = token;
    }

    public Optional<Token> getToken() {
        return token;
    }

}
