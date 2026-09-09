package plc.project.evaluator;

import plc.project.parser.Ast;

import java.util.Optional;

/**
 * IMPORTANT: This is an API file and should not be modified by your submission.
 */
public final class EvaluateException extends Exception {

    private final Optional<Ast> ast;

    public EvaluateException(String message, Optional<Ast> ast) {
        super(message + "\n - @ ast " + ast.map(Object::toString).orElse("???"));
        this.ast = ast;
    }

    public Optional<Ast> getAst() {
        return ast;
    }

}
