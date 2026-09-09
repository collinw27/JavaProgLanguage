package plc.project.analyzer;

import jdk.jshell.spi.ExecutionControl;
import plc.project.parser.Ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Analyzer implements Ast.Visitor<Ir, AnalyzeException> {

    private Scope scope;

    public Analyzer(Scope scope) {
        this.scope = scope;
    }

    public Scope getScope() {
        return scope;
    }

    private Type parseType(String typeString, Optional<Ast> errorAst) throws AnalyzeException
    {
        if (!Environment.TYPES.containsKey(typeString))
            throw new AnalyzeException("Invalid type", errorAst);
        return Environment.TYPES.get(typeString);
    }

    @Override
    public Ir.Source visit(Ast.Source ast) throws AnalyzeException {
        var statements = new ArrayList<Ir.Stmt>();
        for (var statement : ast.statements()) {
            statements.add(visit(statement));
        }
        return new Ir.Source(statements);
    }

    private Ir.Stmt visit(Ast.Stmt ast) throws AnalyzeException {
        return (Ir.Stmt) visit((Ast) ast); //helper to cast visit(Ast.Stmt) to Ir.Stmt
    }

    @Override
    public Ir.Stmt.Let visit(Ast.Stmt.Let ast) throws AnalyzeException
    {
        // Start by making sure variable is not defined (eager erroring)

        if (scope.resolve(ast.name(), true).isPresent())
            throw new AnalyzeException("Redefinition of variable", Optional.of(ast));

        // First, get the value (if possible)

        Optional<Ir.Expr> value = ast.value().isPresent()
            ? Optional.of(visit(ast.value().get()))
            : Optional.empty();

        // Then, resolve the type
        // 1. Check if a type was specified in the definition
        // 2. Attempt to get the type of the value
        // 3. Default to DYNAMIC

        Optional<String> varTypeString = ast.type();
        Type varType = Type.DYNAMIC;
        if (varTypeString.isPresent())
            varType = parseType(varTypeString.get(), Optional.of(ast));
        else if (value.isPresent())
            varType = value.get().type();
        if (value.isPresent() && !value.get().type().isSubtypeOf(varType))
            throw new AnalyzeException("Invalid assignment", Optional.of(ast));

        // Define & return

        scope.define(ast.name(), varType);
        return new Ir.Stmt.Let(ast.name(), varType, value);
    }

    @Override
    public Ir.Stmt.Def visit(Ast.Stmt.Def ast) throws AnalyzeException
    {
        // Start by making sure variable is not defined (eager erroring)

        if (scope.resolve(ast.name(), true).isPresent())
            throw new AnalyzeException("Redefinition of function", Optional.of(ast));

        // Resolve parameter types

        List<Ir.Stmt.Def.Parameter> parameters = new ArrayList<>();
        for (int i = 0; i < ast.parameters().size(); i++)
        {
            String paramName = ast.parameters().get(i);
            Type paramType = parseType(ast.parameterTypes().get(i).orElse("Dynamic"), Optional.of(ast));
            parameters.add(new Ir.Stmt.Def.Parameter(paramName, paramType));
        }

        // Define function before body to allow recursion

        Type returnType = parseType(ast.returnType().orElse("Dynamic"), Optional.of(ast));
        scope.define(ast.name(), new Type.Function(parameters.stream().map(p -> p.type()).toList(), returnType));

        // Evaluate all statements within a new scope

        Scope prevScope = scope;
        try
        {
            scope = new Scope(prevScope);
            for (Ir.Stmt.Def.Parameter param : parameters)
            {
                if (scope.resolve(param.name(), true).isPresent())
                    throw new AnalyzeException("Duplicate parameter", Optional.of(ast));
                scope.define(param.name(), param.type());
            }
            scope.define("$RETURN", returnType);
            List<Ir.Stmt> body = new ArrayList<>();
            for (Ast.Stmt stmt : ast.body())
                body.add(visit(stmt));
            return new Ir.Stmt.Def(ast.name(), parameters, returnType, body);
        }
        finally
        {
            // Scope must always be restored

            scope = prevScope;
        }
    }

    @Override
    public Ir.Stmt.If visit(Ast.Stmt.If ast) throws AnalyzeException
    {
        Ir.Expr condition = visit(ast.condition());
        if (!condition.type().isSubtypeOf(Type.BOOLEAN))
            throw new AnalyzeException("Condition must be boolean", Optional.of(ast));

        // Evaluate all statements within a new scope

        Scope prevScope = scope;
        try
        {
            List<Ir.Stmt> thenBody = new ArrayList<>();
            List<Ir.Stmt> elseBody = new ArrayList<>();
            scope = new Scope(prevScope);
            for (Ast.Stmt stmt : ast.thenBody())
                thenBody.add(visit(stmt));
            scope = new Scope(prevScope);
            for (Ast.Stmt stmt : ast.elseBody())
                elseBody.add(visit(stmt));
            return new Ir.Stmt.If(condition, thenBody, elseBody);
        }
        finally
        {
            // Scope must always be restored

            scope = prevScope;
        }
    }

    @Override
    public Ir.Stmt.For visit(Ast.Stmt.For ast) throws AnalyzeException
    {
        Ir.Expr iterable = visit(ast.expression());
        if (!iterable.type().isSubtypeOf(Type.ITERABLE))
            throw new AnalyzeException("Invalid iterable", Optional.of(ast.expression()));

        // Define iterator in a new scope
        // Evaluate all statements within another new scope

        Scope prevScope = scope;
        try
        {
            List<Ir.Stmt> forBody = new ArrayList<>();
            scope = new Scope(prevScope);
            scope.define(ast.name(), Type.INTEGER);
            scope = new Scope(scope);
            for (Ast.Stmt stmt : ast.body())
                forBody.add(visit(stmt));
            return new Ir.Stmt.For(ast.name(), Type.INTEGER, iterable, forBody);
        }
        finally
        {
            // Scope must be restored

            scope = prevScope;
        }
    }

    @Override
    public Ir.Stmt.Return visit(Ast.Stmt.Return ast) throws AnalyzeException
    {
        // $RETURN will be defined if we are inside a function body

        if (scope.resolve("$RETURN", false).isEmpty())
            throw new AnalyzeException("Return outside of function", Optional.of(ast));
        Optional<Ir.Expr> returnExpr = ast.value().isPresent()
            ? Optional.of(visit(ast.value().get()))
            : Optional.empty();
        Type returnType = returnExpr.map(v -> v.type()).orElse(Type.NIL);
        if (!returnType.isSubtypeOf(scope.resolve("$RETURN", false).get()))
            throw new AnalyzeException("Invalid return value",
                ast.value().isPresent() ? Optional.of(ast.value().get()) : Optional.of(ast)
            );
        return new Ir.Stmt.Return(returnExpr);
    }

    @Override
    public Ir.Stmt.Expression visit(Ast.Stmt.Expression ast) throws AnalyzeException
    {
        var expression = visit(ast.expression());
        return new Ir.Stmt.Expression(expression);
    }

    @Override
    public Ir.Stmt.Assignment visit(Ast.Stmt.Assignment ast) throws AnalyzeException
    {
        if (ast.expression() instanceof Ast.Expr.Variable v)
        {
            Optional<Type> varType = scope.resolve(v.name(), false);
            if (varType.isEmpty())
                throw new AnalyzeException("Undefined variable", Optional.of(ast.expression()));
            Ir.Expr value = visit(ast.value());
            if (!value.type().isSubtypeOf(varType.get()))
                throw new AnalyzeException("Invalid assignment", Optional.of(ast.value()));
            return new Ir.Stmt.Assignment.Variable(new Ir.Expr.Variable(v.name(), varType.get()), value);
        }
        else if (ast.expression() instanceof Ast.Expr.Property p)
        {
            Ir.Expr.Property property = visit(p);
            Ir.Expr value = visit(ast.value());
            if (!value.type().isSubtypeOf(property.type()))
                throw new AnalyzeException("Invalid property", Optional.of(ast.value()));
            return new Ir.Stmt.Assignment.Property(property, value);
        }
        else
        {
            throw new AnalyzeException("Invalid assignment", Optional.of(ast));
        }
    }

    private Ir.Expr visit(Ast.Expr ast) throws AnalyzeException
    {
        return (Ir.Expr) visit((Ast) ast); //helper to cast visit(Ast.Expr) to Ir.Expr
    }

    @Override
    public Ir.Expr.Literal visit(Ast.Expr.Literal ast) throws AnalyzeException
    {
        var type = switch (ast.value()) {
            case null -> Type.NIL;
            case Boolean _ -> Type.BOOLEAN;
            case BigInteger _ -> Type.INTEGER;
            case BigDecimal _ -> Type.DECIMAL;
            case Character _ -> Type.CHARACTER;
            case String _ -> Type.STRING;
            default -> throw new AssertionError(ast.value().getClass());
        };
        return new Ir.Expr.Literal(ast.value(), type);
    }

    @Override
    public Ir.Expr.Group visit(Ast.Expr.Group ast) throws AnalyzeException
    {
        return new Ir.Expr.Group(visit(ast.expression()));
    }

    @Override
    public Ir.Expr.Binary visit(Ast.Expr.Binary ast) throws AnalyzeException
    {
        switch (ast.operator())
        {
            case "+":
            {
                Ir.Expr left = visit(ast.left());
                Ir.Expr right = visit(ast.right());

                // First, attempt to use string

                if (left.type().equals(Type.STRING) || right.type().equals(Type.STRING))
                {
                    return new Ir.Expr.Binary("+", left, right, Type.STRING);
                }

                // Otherwise, handle the case using the subsequent block
            }
            case "-":
            case "*":
            case "/":
            {
                // General case for binary arithmetic with matching types

                Ir.Expr left = visit(ast.left());
                Ir.Expr right = visit(ast.right());

                // Integer or decimal takes priority
                // If both types are dynamic, dynamic type is returned

                if (left.type().equals(Type.INTEGER) || left.type().equals(Type.DECIMAL))
                {
                    if (!right.type().isSubtypeOf(left.type()))
                        throw new AnalyzeException("Invalid type", Optional.of(ast.right()));
                    return new Ir.Expr.Binary(ast.operator(), left, right, left.type());
                }
                else if (left.type().equals(Type.DYNAMIC))
                {
                    if (!left.type().isSubtypeOf(right.type()))
                        throw new AnalyzeException("Invalid type", Optional.of(ast.left()));
                    return new Ir.Expr.Binary(ast.operator(), left, right, right.type());
                }
                else
                    throw new AnalyzeException("Invalid type", Optional.of(ast.left()));
            }
            case "==":
            case "!=":
            {
                Ir.Expr left = visit(ast.left());
                Ir.Expr right = visit(ast.right());

                if (!left.type().isSubtypeOf(right.type())
                    && !right.type().isSubtypeOf(left.type())
                )
                    throw new AnalyzeException("Disjoint types", Optional.of(ast.right()));
                return new Ir.Expr.Binary(ast.operator(), left, right, Type.BOOLEAN);
            }
            case "<":
            case "<=":
            case ">":
            case ">=":
            {
                Ir.Expr left = visit(ast.left());
                Ir.Expr right = visit(ast.right());

                if (!left.type().isSubtypeOf(Type.COMPARABLE))
                    throw new AnalyzeException("Expected comparable", Optional.of(ast.left()));
                if (!right.type().isSubtypeOf(Type.COMPARABLE))
                    throw new AnalyzeException("Expected comparable", Optional.of(ast.right()));
                if (!left.type().equals(right.type()))
                    throw new AnalyzeException("Disjoint types", Optional.of(ast.right()));
                return new Ir.Expr.Binary(ast.operator(), left, right, Type.BOOLEAN);
            }
            case "AND":
            case "OR":
            {
                Ir.Expr left = visit(ast.left());
                Ir.Expr right = visit(ast.right());

                if (!left.type().isSubtypeOf(Type.BOOLEAN))
                    throw new AnalyzeException("Expected boolean", Optional.of(ast.left()));
                if (!right.type().isSubtypeOf(Type.BOOLEAN))
                    throw new AnalyzeException("Expected boolean", Optional.of(ast.right()));
                return new Ir.Expr.Binary(ast.operator(), left, right, Type.BOOLEAN);
            }
            default:
                throw new AnalyzeException("Invalid operator " + ast.operator(), Optional.of(ast));
        }
    }

    @Override
    public Ir.Expr.Variable visit(Ast.Expr.Variable ast) throws AnalyzeException
    {
        var type = scope.resolve(ast.name(), false)
            .orElseThrow(() -> new AnalyzeException("Variable undefined", Optional.of(ast)));
        return new Ir.Expr.Variable(ast.name(), type);
    }

    @Override
    public Ir.Expr.Property visit(Ast.Expr.Property ast) throws AnalyzeException
    {
        Ir.Expr receiver = visit(ast.receiver());
        if (receiver.type().equals(Type.DYNAMIC))
        {
            return new Ir.Expr.Property(receiver, ast.name(), Type.DYNAMIC);
        }
        else if (receiver.type() instanceof Type.ObjectType o)
        {
            // Attempt property access via prototype

            Scope currScope = o.scope();
            while (true)
            {
                Optional<Type> fieldType = currScope.resolve(ast.name(), true);
                if (fieldType.isPresent())
                    return new Ir.Expr.Property(receiver, ast.name(), fieldType.get());
                Optional<Type> protType = currScope.resolve("prototype", true);
                if (protType.isPresent())
                {
                    if (protType.get().equals(Type.DYNAMIC))
                        return new Ir.Expr.Property(receiver, ast.name(), Type.DYNAMIC);
                    else if (protType.get() instanceof Type.ObjectType newO)
                        currScope = newO.scope();
                    else
                        throw new AnalyzeException("Invalid prototype", Optional.of(ast));
                }
                else
                    throw new AnalyzeException("Invalid property", Optional.of(ast));
            }
        }
        else
            throw new AnalyzeException("Property access on non-object", Optional.of(ast.receiver()));
    }

    @Override
    public Ir.Expr.Function visit(Ast.Expr.Function ast) throws AnalyzeException
    {
        Type functionType = scope.resolve(ast.name(), false)
            .orElseThrow(() -> new AnalyzeException("Function undefined", Optional.of(ast)));
        if (!(functionType instanceof Type.Function function))
            throw new AnalyzeException("Function call on non-function", Optional.of(ast));

        // Evaluate arguments, ensuring correct types

        if (ast.arguments().size() != function.parameters().size())
            throw new AnalyzeException("Wrong number of arguments", Optional.of(ast));
        List<Ir.Expr> arguments = new ArrayList<>();
        for (int i = 0; i < ast.arguments().size(); i++)
        {
            Ir.Expr arg = visit(ast.arguments().get(i));
            if (!arg.type().isSubtypeOf(function.parameters().get(i)))
                throw new AnalyzeException("Invalid argument type", Optional.of(ast.arguments().get(i)));
            arguments.add(arg);
        }
        return new Ir.Expr.Function(ast.name(), arguments, function.returns());
    }

    @Override
    public Ir.Expr.Method visit(Ast.Expr.Method ast) throws AnalyzeException
    {
        Ir.Expr.Property property = visit(new Ast.Expr.Property(ast.receiver(), ast.name()));

        if (!(property.type() instanceof Type.Function method))
        {
             if ((property.type().equals(Type.DYNAMIC)))
                 return new Ir.Expr.Method(property.receiver(), property.name(), new ArrayList<Ir.Expr>(), Type.DYNAMIC);
             else
                 throw new AnalyzeException("Method call on non-method", Optional.of(ast));
        }

        // Evaluate arguments, ensuring correct types

        if (ast.arguments().size() != method.parameters().size())
            throw new AnalyzeException("Wrong number of arguments", Optional.of(ast));
        List<Ir.Expr> arguments = new ArrayList<>();
        for (int i = 0; i < ast.arguments().size(); i++)
        {
            Ir.Expr arg = visit(ast.arguments().get(i));
            if (!arg.type().isSubtypeOf(method.parameters().get(i)))
                throw new AnalyzeException("Invalid argument type", Optional.of(ast.arguments().get(i)));
            arguments.add(arg);
        }
        return new Ir.Expr.Method(property.receiver(), property.name(), arguments, method.returns());
    }

    @Override
    public Ir.Expr.ObjectExpr visit(Ast.Expr.ObjectExpr ast) throws AnalyzeException
    {
        var objectScope = new Scope(null);

        // FIELDS
        // Inherits most behavior from Ast.Let

        List<Ir.Stmt.Let> fields = new ArrayList<>();
        for (Ast.Stmt.Let field : ast.fields())
        {
            // Start by making sure variable is not defined (eager erroring)

            if (objectScope.resolve(field.name(), true).isPresent())
                throw new AnalyzeException("Redefinition of field", Optional.of(field));

            // First, get the value (if possible)

            Optional<Ir.Expr> value = field.value().isPresent()
                ? Optional.of(visit(field.value().get()))
                : Optional.empty();

            // Then, resolve the type
            // 1. Check if a type was specified in the definition
            // 2. Attempt to get the type of the value
            // 3. Default to DYNAMIC

            Optional<String> varTypeString = field.type();
            Type varType = Type.DYNAMIC;
            if (varTypeString.isPresent())
                varType = parseType(varTypeString.get(), Optional.of(field));
            else if (value.isPresent())
                varType = value.get().type();
            if (value.isPresent() && !value.get().type().isSubtypeOf(varType))
                throw new AnalyzeException("Invalid assignment", Optional.of(ast));

            // Define

            objectScope.define(field.name(), varType);
            fields.add(new Ir.Stmt.Let(field.name(), varType, value));
        }

        // METHODS
        // Inherits most behavior from Ast.Def
        // Method are defined in 2 phases:
        // 1. Default method behavior is created
        // 2. After the object type can be resolved, all method
        // bodies are evaluated, allowing `this` to be type-checked

        List<Ir.Stmt.Def> methods = new ArrayList<>();
        List<List<Ast.Stmt>> methodBodies = new ArrayList<>();
        for (Ast.Stmt.Def method : ast.methods())
        {
            // Start by making sure variable is not defined (eager erroring)

            if (objectScope.resolve(method.name(), true).isPresent())
                throw new AnalyzeException("Redefinition of method", Optional.of(method));

            // Resolve parameter types

            List<Ir.Stmt.Def.Parameter> parameters = new ArrayList<>();
            for (int i = 0; i < method.parameters().size(); i++)
            {
                String paramName = method.parameters().get(i);
                Type paramType = parseType(method.parameterTypes().get(i).orElse("Dynamic"), Optional.of(method));
                parameters.add(new Ir.Stmt.Def.Parameter(paramName, paramType));
            }

            // Define method type in the object scope

            Type returnType = parseType(method.returnType().orElse("Dynamic"), Optional.of(method));
            objectScope.define(method.name(), new Type.Function(parameters.stream().map(p -> p.type()).toList(), returnType));
            methods.add(new Ir.Stmt.Def(method.name(), parameters, returnType, new ArrayList<Ir.Stmt>()));
            methodBodies.add(method.body());
        }
        Type objectType = new Type.ObjectType(ast.name(), objectScope);
        for (int i = 0; i < methods.size(); i++)
        {
            Ir.Stmt.Def method = methods.get(i);
            List<Ast.Stmt> methodBody = methodBodies.get(i);

            // Evaluate all statements within a new scope

            Scope prevScope = scope;
            try
            {
                scope = new Scope(scope);
                scope.define("this", objectType);
                for (Ir.Stmt.Def.Parameter param : method.parameters())
                {
                    if (scope.resolve(param.name(), true).isPresent())
                        throw new AnalyzeException("Duplicate parameter", Optional.of(ast.methods().get(i)));
                    scope.define(param.name(), param.type());
                }
                scope.define("$RETURN", method.returns());
                List<Ir.Stmt> body = new ArrayList<>();
                for (Ast.Stmt stmt : methodBody)
                    body.add(visit(stmt));
                methods.set(i, new Ir.Stmt.Def(method.name(), method.parameters(), method.returns(), body));
            }
            finally
            {
                // Scope must always be restored

                scope = prevScope;
            }
        }

        return new Ir.Expr.ObjectExpr(ast.name(), fields, methods, objectType);
    }

}
