package plc.project.parser;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * IMPORTANT: This is an API file and should not be modified by your submission.
 */
public sealed interface Ast {

    record Source(
        List<Stmt> statements
    ) implements Ast {}

    sealed interface Stmt extends Ast {

        record Let(
            String name,
            Optional<String> type,
            Optional<Expr> value
        ) implements Stmt {
            public Let(String name, Optional<Expr> value) {
                this(name, Optional.empty(), value);
            }
        }

        record Def(
            String name,
            List<String> parameters,
            List<Optional<String>> parameterTypes, //a Parameter class is better, but not compatible with existing uses.
            Optional<String> returnType,
            List<Stmt> body
        ) implements Stmt {
            public Def(String name, List<String> parameters, List<Stmt> body) {
                this(name, parameters, Stream.generate(Optional::<String>empty).limit(parameters.size()).toList(), Optional.empty(), body);
            }
        }

        record If(
            Expr condition,
            List<Stmt> thenBody,
            List<Stmt> elseBody
        ) implements Stmt {}

        record For(
            String name,
            Expr expression,
            List<Stmt> body
        ) implements Stmt {}

        record Return(
            Optional<Expr> value
        ) implements Stmt {}

        record Expression(
            Expr expression
        ) implements Stmt {}

        record Assignment(
            Expr expression,
            Expr value
        ) implements Stmt {}

    }

    sealed interface Expr extends Ast {

        record Literal(
            @Nullable Object value
        ) implements Expr {
            @Override
            public String toString() {
                var clazz = value != null ? value.getClass().getSimpleName() : "N/A";
                return "Literal[value=" + value + ", class=" + clazz + "]";
            }
        }

        record Group(
            Expr expression
        ) implements Expr {}

        record Binary(
            String operator,
            Expr left,
            Expr right
        ) implements Expr {}

        record Variable(
            String name
        ) implements Expr {}

        record Property(
            Expr receiver,
            String name
        ) implements Expr {}

        record Function(
            String name,
            List<Expr> arguments
        ) implements Expr {}

        record Method(
            Expr receiver,
            String name,
            List<Expr> arguments
        ) implements Expr {}

        //Using "ObjectExpr" to avoid confusion with Java's "Object"
        record ObjectExpr(
            Optional<String> name,
            List<Stmt.Let> fields,
            List<Stmt.Def> methods
        ) implements Expr {}

    }

    interface Visitor<T, E extends Exception> {

        default T visit(Ast ast) throws E {
            return switch (ast) {
                case Source source -> visit(source);
                case Stmt.Let stmt -> visit(stmt);
                case Stmt.Def stmt -> visit(stmt);
                case Stmt.If stmt -> visit(stmt);
                case Stmt.For stmt -> visit(stmt);
                case Stmt.Return stmt -> visit(stmt);
                case Stmt.Expression stmt -> visit(stmt);
                case Stmt.Assignment stmt -> visit(stmt);
                case Expr.Literal expr -> visit(expr);
                case Expr.Group expr -> visit(expr);
                case Expr.Binary expr -> visit(expr);
                case Expr.Variable expr -> visit(expr);
                case Expr.Property expr -> visit(expr);
                case Expr.Function expr -> visit(expr);
                case Expr.Method expr -> visit(expr);
                case Expr.ObjectExpr expr -> visit(expr);
            };
        }

        T visit(Source ast) throws E;
        T visit(Stmt.Let ast) throws E;
        T visit(Stmt.Def ast) throws E;
        T visit(Stmt.If ast) throws E;
        T visit(Stmt.For ast) throws E;
        T visit(Stmt.Return ast) throws E;
        T visit(Stmt.Expression ast) throws E;
        T visit(Stmt.Assignment ast) throws E;
        T visit(Expr.Literal ast) throws E;
        T visit(Expr.Group ast) throws E;
        T visit(Expr.Binary ast) throws E;
        T visit(Expr.Variable ast) throws E;
        T visit(Expr.Property ast) throws E;
        T visit(Expr.Function ast) throws E;
        T visit(Expr.Method ast) throws E;
        T visit(Expr.ObjectExpr ast) throws E;

    }

}
