package plc.project.generator;

import plc.project.analyzer.Ir;
import plc.project.analyzer.Type;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.regex.Pattern;

public final class Generator implements Ir.Visitor<StringBuilder, RuntimeException> {

    private final StringBuilder builder = new StringBuilder();
    private int indent = 0;

    private void newline(int indent) {
        builder.append("\n");
        builder.append("    ".repeat(indent));
    }

    @Override
    public StringBuilder visit(Ir.Source ir) {
        builder.append(Environment.imports()).append("\n\n");
        builder.append("public final class Main {").append("\n\n");
        builder.append(Environment.definitions()).append("\n");
        //Java doesn't allow for nested functions, but we will pretend it does.
        //To support simple programs involving functions, we will "hoist" any
        //variable/function declaration at the start of the program to allow
        //these functions to be used as valid Java.
        indent = 1;
        boolean main = false;
        for (var statement : ir.statements()) {
            newline(indent);
            if (!main) {
                if (statement instanceof Ir.Stmt.Let || statement instanceof Ir.Stmt.Def) {
                    builder.append("static ");
                } else {
                    builder.append("public static void main(String[] args) {");
                    main = true;
                    indent = 2;
                    newline(indent);
                }
            }
            visit(statement);
        }
        if (main) {
            builder.append("\n").append("    }");
        }
        indent = 0;
        builder.append("\n\n").append("}");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Let ir)
    {
        generateType(ir.type(), true);
        builder.append(ir.name());
        if (ir.value().isEmpty())
        {
            return builder.append(";");
        }
        else
        {
            builder.append(" = ");
            visit(ir.value().get());
            return builder.append(";");
        }
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Def ir)
    {
        generateType(ir.returns(), false);
//        builder.append("Void ");
        builder.append(ir.name());
        builder.append("(");
        for (int i = 0; i < ir.parameters().size(); i++)
        {
            Ir.Stmt.Def.Parameter parameter = ir.parameters().get(i);
            if (i != 0)
                builder.append(", ");
            generateType(parameter.type(), false);
            builder.append(parameter.name());
        }
        builder.append(") {");
        indent += 1;
        for (Ir.Stmt statement : ir.body())
        {
            newline(indent);
            visit(statement);
        }
        indent -= 1;
        newline(indent);
        return builder.append("}");
    }

    @Override
    public StringBuilder visit(Ir.Stmt.If ir)
    {
        builder.append("if (");
        visit(ir.condition());
        builder.append(") {");
        indent += 1;
        for (Ir.Stmt statement : ir.thenBody())
        {
            newline(indent);
            visit(statement);
        }
        if (ir.elseBody().isEmpty())
        {
            indent -= 1;
            newline(indent);
            return builder.append("}");
        }
        else
        {
            newline(indent - 1);
            builder.append("} else {");
            for (Ir.Stmt statement : ir.elseBody())
            {
                newline(indent);
                visit(statement);
            }
            indent -= 1;
            newline(indent);
            return builder.append("}");
        }
    }

    @Override
    public StringBuilder visit(Ir.Stmt.For ir)
    {
        builder.append("for (");
        generateType(ir.type(), false);
        builder.append(ir.name());
        builder.append(" : ");
        visit(ir.expression());
        builder.append(") {");
        indent += 1;
        for (Ir.Stmt statement : ir.body())
        {
            newline(indent);
            visit(statement);
        }
        indent -= 1;
        newline(indent);
        return builder.append("}");
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Return ir)
    {
        builder.append("return ");
        if (ir.value().isPresent())
            visit(ir.value().get());
        else
            builder.append("null");
        return builder.append(";");
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Expression ir)
    {
        visit(ir.expression());
        return builder.append(";");
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Assignment.Variable ir)
    {
        visit(ir.variable());
        builder.append(" = ");
        visit(ir.value());
        return builder.append(";");
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Assignment.Property ir)
    {
        visit(ir.property());
        builder.append(" = ");
        visit(ir.value());
        return builder.append(";");
    }

    @Override
    public StringBuilder visit(Ir.Expr.Literal ir) {
        var literal = switch (ir.value()) {
            case null -> "null";
            case Boolean b -> b.toString();
            case BigInteger i -> "new BigInteger(\"" + i + "\")";
            case BigDecimal d -> "new BigDecimal(\"" + d + "\")";
            case Character c -> "\'" + c + "\'"; //Limitation: escapes unsupported
            case String s -> "\"" + s + "\""; //Limitation: escapes unsupported
            default -> throw new AssertionError(ir.value());
        };
        return builder.append(literal);
    }

    @Override
    public StringBuilder visit(Ir.Expr.Group ir)
    {
        builder.append("(");
        visit(ir.expression());
        return builder.append(")");
    }

    @Override
    public StringBuilder visit(Ir.Expr.Binary ir)
    {
        switch (ir.operator())
        {
            case ("+"):
            {
                // String: left + right

                if (ir.left().type().isSubtypeOf(Type.STRING) || ir.right().type().isSubtypeOf(Type.STRING))
                {
                    visit(ir.left());
                    builder.append(" + ");
                    return visit(ir.right());
                }

                // Other: (left).add(right)

                else
                {
                    builder.append("(");
                    visit(ir.left());
                    builder.append(").add(");
                    visit(ir.right());
                    return builder.append(")");
                }
            }
            case ("-"):
            {
                builder.append("(");
                visit(ir.left());
                builder.append(").subtract(");
                visit(ir.right());
                return builder.append(")");
            }
            case ("*"):
            {
                builder.append("(");
                visit(ir.left());
                builder.append(").multiply(");
                visit(ir.right());
                return builder.append(")");
            }
            case ("/"):
            {
                // Integer

                if (ir.left().type().isSubtypeOf(Type.INTEGER))
                {
                    builder.append("(");
                    visit(ir.left());
                    builder.append(").divide(");
                    visit(ir.right());
                    return builder.append(")");
                }

                // Decimal

                else
                {
                    builder.append("(");
                    visit(ir.left());
                    builder.append(").divide(");
                    visit(ir.right());
                    return builder.append(", RoundingMode.HALF_EVEN)");
                }
            }
            case ("<"):
            case ("<="):
            case (">"):
            case (">="):
            {
                builder.append("(");
                visit(ir.left());
                builder.append(").compareTo(");
                visit(ir.right());
                builder.append(") ");
                builder.append(ir.operator());
                return builder.append(" 0");
            }
            case ("=="):
            case ("!="):
            {
                builder.append(ir.operator().equals("==") ? "" : "!");
                builder.append("Objects.equals(");
                visit(ir.left());
                builder.append(", ");
                visit(ir.right());
                return builder.append(")");
            }
            case ("AND"):
            {
                // OR has equal precedence

                if (ir.left() instanceof Ir.Expr.Binary b && b.operator().equals("OR"))
                {
                    builder.append("(");
                    visit(b.left());
                    builder.append(" || ");
                    visit(b.right());
                    builder.append(") && ");
                    return visit(ir.right());
                }

                // Otherwise, use Java's precedence

                else
                {
                    visit(ir.left());
                    builder.append(" && ");
                    return visit(ir.right());
                }
            }
            case ("OR"):
            {
                visit(ir.left());
                builder.append(" || ");
                return visit(ir.right());
            }
            default:
                throw new RuntimeException("Unhandled operator: " + ir.operator());
        }
    }

    @Override
    public StringBuilder visit(Ir.Expr.Variable ir)
    {
        return builder.append(ir.name());
    }

    @Override
    public StringBuilder visit(Ir.Expr.Property ir)
    {
        visit(ir.receiver());
        builder.append(".");
        return builder.append(ir.name());
    }

    @Override
    public StringBuilder visit(Ir.Expr.Function ir)
    {
        builder.append(ir.name());
        builder.append("(");
        for (int i = 0; i < ir.arguments().size(); i++)
        {
            Ir.Expr arg = ir.arguments().get(i);
            if (i != 0)
                builder.append(", ");
            visit(arg);
        }
        return builder.append(")");
    }

    @Override
    public StringBuilder visit(Ir.Expr.Method ir)
    {
        visit(ir.receiver());
        builder.append(".");
        builder.append(ir.name());
        builder.append("(");
        for (int i = 0; i < ir.arguments().size(); i++)
        {
            Ir.Expr arg = ir.arguments().get(i);
            if (i != 0)
                builder.append(", ");
            visit(arg);
        }
        return builder.append(")");
    }

    @Override
    public StringBuilder visit(Ir.Expr.ObjectExpr ir)
    {
        builder.append("new Object() {");
        indent += 1;
        for (Ir.Stmt.Let field : ir.fields())
        {
            newline(indent);
            visit(field);
        }
        for (Ir.Stmt.Def method : ir.methods())
        {
            newline(indent);
            visit(method);
        }
        indent -= 1;
        newline(indent);
        return builder.append("}");
    }

    // HELPER FUNCTIONS

    private void generateType(Type type, boolean isLet)
    {
        switch (type)
        {
            case Type.Primitive p:
                builder.append(p.jvmName() + " ");
                return;
            default:
                builder.append(isLet ? "var " : "Object ");
        }
    }

}
