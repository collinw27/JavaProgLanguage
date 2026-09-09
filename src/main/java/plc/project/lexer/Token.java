package plc.project.lexer;

/**
 * IMPORTANT: This is an API file and should not be modified by your submission.
 */
public record Token(
    Type type,
    String literal
) {

    public enum Type {
        IDENTIFIER,
        INTEGER,
        DECIMAL,
        CHARACTER,
        STRING,
        OPERATOR
    }

}
