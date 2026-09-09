package plc.project.lexer;

/**
 * IMPORTANT: This is an API file and should not be modified by your submission.
 */
public final class LexException extends Exception {

    private final int index;

    public LexException(String message, int index) {
        super(message + "\n - @ index " + index);
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

}
