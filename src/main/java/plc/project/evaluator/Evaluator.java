package plc.project.evaluator;

import plc.project.parser.Ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;

public final class Evaluator implements Ast.Visitor<RuntimeValue, EvaluateException> {

    // RuntimeException prevents needing to re-write the signature
    // of every visit function to check for this

    class ReturnException extends RuntimeException
    {
        RuntimeValue returnValue;
        Ast.Stmt.Return ast;

        public ReturnException(RuntimeValue returnValue, Ast.Stmt.Return ast)
        {
            this.returnValue = returnValue;
            this.ast = ast;
        }
    }

    private Scope scope;

    public Evaluator(Scope scope)
    {
        this.scope = scope;
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public RuntimeValue visit(Ast.Source ast) throws EvaluateException
    {
        try
        {
            RuntimeValue value = new RuntimeValue.Primitive(null);
            for (var stmt : ast.statements())
            {
                value = visit(stmt);
            }
            return value;
        }
        catch (ReturnException e)
        {
            throw new EvaluateException("Return used outside of function", Optional.of(ast));
        }
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Let ast) throws EvaluateException
    {
        if (scope.resolve(ast.name(), true).isPresent())
            throw new EvaluateException("Redefinition of variable " + ast.name(), Optional.of(ast));
        RuntimeValue value = new RuntimeValue.Primitive(null);
        if (ast.value().isPresent())
            value = visit(ast.value().get());
        scope.define(ast.name(), value);
        return value;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Def ast) throws EvaluateException
    {
        if (scope.resolve(ast.name(), true).isPresent())
            throw new EvaluateException("Redefinition of function " + ast.name(), Optional.of(ast));

        // Before creating function, check for duplicated parameters

        if (new HashSet<String>(ast.parameters()).size() != ast.parameters().size())
            throw new EvaluateException("Duplicate function parameter", Optional.of(ast));

        // Create function by implementing Definition interface

        var func = new RuntimeValue.Function(ast.name(), new RuntimeValue.Function.Definition ()
        {
            List<Ast.Stmt> body = ast.body();
            Scope outerScope = scope;
            public RuntimeValue invoke(List<RuntimeValue> arguments) throws EvaluateException
            {
                Scope argScope = new Scope(outerScope);
                Scope bodyScope = new Scope(argScope);
                Scope prevScope = scope;
                try
                {
                    // Evaluate arguments

                    var funcParams = ast.parameters();
                    if (funcParams.size() != arguments.size())
                        throw new EvaluateException("Incorrect number of arguments", Optional.of(ast));
                    for (int i = 0; i < funcParams.size(); i++)
                        argScope.define(funcParams.get(i), arguments.get(i));

                    // Define new scope, old one is stored so it can be restored

                    scope = bodyScope;
                    RuntimeValue returnVal = new RuntimeValue.Primitive(null);

                    // Run function body and store return value if necessary

                    try
                    {
                        for (var stmt : body)
                            visit(stmt);
                    }
                    catch (ReturnException e)
                    {
                        returnVal = e.returnValue;
                    }

                    return returnVal;
                }
                finally
                {
                    // Scope must always be restored

                    scope = prevScope;
                }
            }
        });
        scope.define(ast.name(), func);
        return func;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.If ast) throws EvaluateException
    {
        var oldScope = scope;
        try
        {
            // Evaluate condition

            var cond = visit(ast.condition());
            if (!(cond instanceof RuntimeValue.Primitive cp && cp.value() instanceof Boolean cb))
                throw new EvaluateException("Expected boolean for condition", Optional.of(ast));

            // Store & create scope

            List<Ast.Stmt> body = (cb) ? ast.thenBody() : ast.elseBody();
            var newScope = new Scope(scope);
            scope = newScope;

            // Run statements, then restore scope & return last

            RuntimeValue lastVal = new RuntimeValue.Primitive(null);
            for (var stmt : body)
                lastVal = visit(stmt);
            return lastVal;
        }
        finally
        {
            // Scope must always be restored

            scope = oldScope;
        }
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.For ast) throws EvaluateException
    {
        var oldScope = scope;
        try
        {
            // Evaluate iterable

            var name = ast.name();
            var iterable = visit(ast.expression());
            if (!(iterable instanceof RuntimeValue.Primitive ip && ip.value() instanceof Iterable it))
                throw new EvaluateException("Invalid iterable", Optional.of(ast.expression()));

            // Don't bother if iterable is empty

            if (!it.iterator().hasNext())
                return new RuntimeValue.Primitive(null);

            // Store & create scope
            // Two scopes are needed: one for variable, one for body

            var varScope = new Scope(scope);

            // Assumption: for each iteration, a new scope is used

            varScope.define(name, new RuntimeValue.Primitive(null));
            for (var element : it)
            {
                if (!(element instanceof RuntimeValue.Primitive e))
                    throw new EvaluateException("Invalid element", Optional.of(ast));
                varScope.assign(name, e);
                var bodyScope = new Scope(varScope);
                scope = bodyScope;
                for (var stmt : ast.body())
                    visit(stmt);
            }
            return new RuntimeValue.Primitive(null);
        }
        finally
        {
            // Old scope is restored (eliminating both inner scopes)

            scope = oldScope;
        }
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Return ast) throws EvaluateException
    {
        RuntimeValue value = (ast.value().isPresent())
            ? visit(ast.value().get())
            : new RuntimeValue.Primitive(null);
        throw new ReturnException(value, ast);
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Expression ast) throws EvaluateException
    {
        return visit(ast.expression());
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Assignment ast) throws EvaluateException
    {
        if (ast.expression() instanceof Ast.Expr.Variable v)
        {
            var field = scope.resolve(v.name(), false);
            if (!field.isPresent())
                throw new EvaluateException("Undefined variable", Optional.of(v));
            RuntimeValue value = visit(ast.value());
            scope.assign(v.name(), value);
            return value;
        }
        else if (ast.expression() instanceof Ast.Expr.Property p)
        {
            var receiver = visit(p.receiver());
            if (!(receiver instanceof RuntimeValue.ObjectValue obj))
                throw new EvaluateException("Property access on non-object", Optional.of(p.receiver()));
            var currScope = obj.scope();
            while (true)
            {
                var field = currScope.resolve(p.name(), true);
                if (field.isPresent())
                {
                    RuntimeValue value = visit(ast.value());
                    currScope.assign(p.name(), value);
                    return value;
                }
                var prot = currScope.resolve("prototype", true);
                if (!(prot.isPresent() && prot.get() instanceof RuntimeValue.ObjectValue protObj))
                    throw new EvaluateException("Undefined property", Optional.of(ast));
                currScope = protObj.scope();
            }
            // var field = obj.scope().resolve(p.name(), true);
            // if (!field.isPresent())
            //     throw new EvaluateException("Undefined property", Optional.of(p));
            // RuntimeValue value = visit(ast.value());
            // obj.scope().assign(p.name(), value);
            // return value;
        }
        else
            throw new EvaluateException("Invalid assignment", Optional.of(ast.expression()));
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Literal ast) throws EvaluateException
    {
        return new RuntimeValue.Primitive(ast.value());
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Group ast) throws EvaluateException
    {
        return visit(ast.expression());
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Binary ast) throws EvaluateException
    {
        switch (ast.operator())
        {
            case "+":
            {
                var left = visit(ast.left());
                var right = visit(ast.right());

                // First, attempt to use string

                if (left instanceof RuntimeValue.Primitive lp && lp.value() instanceof String ll)
                {
                    return new RuntimeValue.Primitive(left.print() + right.print());
                }
                else if (right instanceof RuntimeValue.Primitive rp && rp.value() instanceof String rr)
                {
                    return new RuntimeValue.Primitive(left.print() + right.print());
                }

                // Otherwise, do integer/decimal addition, performing type checks

                if (!(left instanceof RuntimeValue.Primitive lp))
                    throw new EvaluateException("Invalid type", Optional.of(ast.left()));
                if (lp.value() instanceof BigInteger ll)
                {
                    if (!(right instanceof RuntimeValue.Primitive rp &&
                            rp.value() instanceof BigInteger rr)
                    )
                        throw new EvaluateException("Invalid type", Optional.of(ast.right()));
                    return new RuntimeValue.Primitive(ll.add(rr));
                }
                else if (lp.value() instanceof BigDecimal ll)
                {
                    if (!(right instanceof RuntimeValue.Primitive rp &&
                            rp.value() instanceof BigDecimal rr)
                    )
                        throw new EvaluateException("Invalid type", Optional.of(ast.left()));
                    return new RuntimeValue.Primitive(ll.add(rr));
                }
                else
                    throw new EvaluateException("Invalid type", Optional.of(ast.left()));
            }
            case "-":
            {
                var left = visit(ast.left());
                if (!(left instanceof RuntimeValue.Primitive lp))
                    throw new EvaluateException("Invalid type", Optional.of(ast.left()));
                if (lp.value() instanceof BigInteger ll)
                {
                    var right = visit(ast.right());
                    if (!(right instanceof RuntimeValue.Primitive rp &&
                            rp.value() instanceof BigInteger rr)
                    )
                        throw new EvaluateException("Invalid type", Optional.of(ast.right()));
                    return new RuntimeValue.Primitive(ll.subtract(rr));
                }
                else if (lp.value() instanceof BigDecimal ll)
                {
                    var right = visit(ast.right());
                    if (!(right instanceof RuntimeValue.Primitive rp &&
                            rp.value() instanceof BigDecimal rr)
                    )
                        throw new EvaluateException("Invalid type", Optional.of(ast.left()));
                    return new RuntimeValue.Primitive(ll.subtract(rr));
                }
                else
                    throw new EvaluateException("Invalid type", Optional.of(ast.left()));
            }
            case "*":
            {
                var left = visit(ast.left());
                if (!(left instanceof RuntimeValue.Primitive lp))
                    throw new EvaluateException("Invalid type", Optional.of(ast.left()));
                if (lp.value() instanceof BigInteger ll)
                {
                    var right = visit(ast.right());
                    if (!(right instanceof RuntimeValue.Primitive rp &&
                            rp.value() instanceof BigInteger rr)
                    )
                        throw new EvaluateException("Invalid type", Optional.of(ast.right()));
                    return new RuntimeValue.Primitive(ll.multiply(rr));
                }
                else if (lp.value() instanceof BigDecimal ll)
                {
                    var right = visit(ast.right());
                    if (!(right instanceof RuntimeValue.Primitive rp &&
                            rp.value() instanceof BigDecimal rr)
                    )
                        throw new EvaluateException("Invalid type", Optional.of(ast.left()));
                    return new RuntimeValue.Primitive(ll.multiply(rr));
                }
                else
                    throw new EvaluateException("Invalid type", Optional.of(ast.left()));
            }
            case "/":
            {
                var left = visit(ast.left());
                if (!(left instanceof RuntimeValue.Primitive lp))
                    throw new EvaluateException("Invalid type", Optional.of(ast.left()));
                if (lp.value() instanceof BigInteger ll)
                {
                    var right = visit(ast.right());
                    if (!(right instanceof RuntimeValue.Primitive rp &&
                            rp.value() instanceof BigInteger rr)
                    )
                        throw new EvaluateException("Invalid type", Optional.of(ast.right()));
                    if (rr.equals(BigInteger.ZERO))
                        throw new EvaluateException("Division by zero", Optional.of(ast.right()));
                    return new RuntimeValue.Primitive(ll.divide(rr));
                }
                else if (lp.value() instanceof BigDecimal ll)
                {
                    var right = visit(ast.right());
                    if (!(right instanceof RuntimeValue.Primitive rp &&
                            rp.value() instanceof BigDecimal rr)
                    )
                        throw new EvaluateException("Invalid type", Optional.of(ast.left()));
                    if (rr.signum() == 0)
                        throw new EvaluateException("Division by zero", Optional.of(ast.right()));
                    return new RuntimeValue.Primitive(ll.divide(rr, RoundingMode.HALF_EVEN));
                }
                else
                    throw new EvaluateException("Invalid type", Optional.of(ast.left()));
            }
            case "==":
            case "!=":
            {
                var left = visit(ast.left());
                var right = visit(ast.right());
                boolean result = Objects.equals(left, right);
                return new RuntimeValue.Primitive((ast.operator().equals("==")) ? result : !result);
            }
            case "<":
            case "<=":
            case ">":
            case ">=":
            {
                var left = visit(ast.left());
                if (!(left instanceof RuntimeValue.Primitive lp && lp.value() instanceof Comparable ll))
                    throw new EvaluateException("Type must be comparable", Optional.of(ast.left()));
                var right = visit(ast.right());
                if (!(right instanceof RuntimeValue.Primitive rp && ll.getClass().isInstance(rp.value())))
                    throw new EvaluateException("Invalid type", Optional.of(ast.right()));
                var rr = ll.getClass().cast(rp.value());
                switch (ast.operator())
                {
                    case "<":
                        return new RuntimeValue.Primitive(ll.compareTo(rr) < 0);
                    case "<=":
                        return new RuntimeValue.Primitive(ll.compareTo(rr) <= 0);
                    case ">":
                        return new RuntimeValue.Primitive(ll.compareTo(rr) > 0);
                    case ">=":
                        return new RuntimeValue.Primitive(ll.compareTo(rr) >= 0);
                }
            }
            case "AND":
            case "OR":
            {
                boolean usingAnd = (ast.operator().equals("AND"));
                var left = visit(ast.left());
                if (!(left instanceof RuntimeValue.Primitive lp && lp.value() instanceof Boolean ll))
                    throw new EvaluateException("Invalid type", Optional.of(ast.left()));

                // Short circuit

                if ((usingAnd && !ll) || (!usingAnd && ll))
                    return new RuntimeValue.Primitive(ll);

                var right = visit(ast.right());
                if (!(right instanceof RuntimeValue.Primitive rp && rp.value() instanceof Boolean rr))
                    throw new EvaluateException("Invalid type", Optional.of(ast.right()));
                return new RuntimeValue.Primitive(usingAnd ? (ll && rr) : (ll || rr));
            }
        }
        throw new EvaluateException("Invalid operator " + ast.operator(), Optional.of(ast));
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Variable ast) throws EvaluateException
    {
        var variable = scope.resolve(ast.name(), false);
        if (variable.isPresent())
            return variable.get();
        else
            throw new EvaluateException("Undefined variable", Optional.of(ast));
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Property ast) throws EvaluateException
    {
        var receiver = visit(ast.receiver());
        if (!(receiver instanceof RuntimeValue.ObjectValue obj))
            throw new EvaluateException("Property access on non-object", Optional.of(ast.receiver()));

        // Attempt normal property access
        // Prototype is done using iteration

        var currScope = obj.scope();
        while (true)
        {
            var field = currScope.resolve(ast.name(), true);
            if (field.isPresent())
                return field.get();
            var prot = currScope.resolve("prototype", true);
            if (!(prot.isPresent() && prot.get() instanceof RuntimeValue.ObjectValue protObj))
                throw new EvaluateException("Invalid field", Optional.of(ast));
            currScope = protObj.scope();
        }
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Function ast) throws EvaluateException
    {
        var value = scope.resolve(ast.name(), false).orElseThrow(() -> {
            return new EvaluateException("Function " + ast.name() + " is undefined", Optional.of(ast));
        });
        var func = requireType(value, RuntimeValue.Function.class).orElseThrow(() -> {
            return new EvaluateException("Function " + ast.name() + " is incorrect type", Optional.of(ast));
        });
        List<RuntimeValue> arguments = new ArrayList<>();
        for (var arg : ast.arguments())
            arguments.add(visit(arg));
        return func.definition().invoke(arguments);
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Method ast) throws EvaluateException
    {
        var receiver = visit(ast.receiver());
        if (!(receiver instanceof RuntimeValue.ObjectValue obj))
            throw new EvaluateException("Method access on non-object", Optional.of(ast.receiver()));

        // Attempt normal property access
        // Prototype is done using iteration

        var currScope = obj.scope();
        while (true)
        {
            var field = currScope.resolve(ast.name(), true);
            if (field.isPresent())
            {
                if (!(field.get() instanceof RuntimeValue.Function func))
                    throw new EvaluateException("Expected function", Optional.of(ast));
                List<RuntimeValue> arguments = new ArrayList<>();
                for (var arg : ast.arguments())
                    arguments.add(visit(arg));
                arguments.addFirst(obj);
                return func.definition().invoke(arguments);
            }
            var prot = currScope.resolve("prototype", true);
            if (!(prot.isPresent() && prot.get() instanceof RuntimeValue.ObjectValue protObj))
                throw new EvaluateException("Invalid field", Optional.of(ast));
            currScope = protObj.scope();
        }
    }

    @Override
    public RuntimeValue visit(Ast.Expr.ObjectExpr ast) throws EvaluateException
    {
        var objScope = new Scope(null);
        var obj = new RuntimeValue.ObjectValue(ast.name(), objScope);
        for (var field : ast.fields())
        {
            if (objScope.resolve(field.name(), true).isPresent())
                throw new EvaluateException("Redefinition of field " + field.name(), Optional.of(field));
            objScope.define(field.name(), field.value().isPresent()
                ? visit(field.value().get())
                : new RuntimeValue.Primitive(null)
            );
        }
        for (var method : ast.methods())
        {
            if (objScope.resolve(method.name(), true).isPresent())
                throw new EvaluateException("Redefinition of method " + method.name(), Optional.of(method));

            // Before creating method, check for duplicated parameters
            // This includes the 'this' parameters implicitly added

            List<String> parameters = new ArrayList<>(method.parameters());
            parameters.addFirst("this");
            if (new HashSet<String>(parameters).size() != parameters.size())
                throw new EvaluateException("Duplicate method parameter", Optional.of(ast));

            // Create method

            var func = new RuntimeValue.Function(method.name(), new RuntimeValue.Function.Definition ()
            {
                List<Ast.Stmt> body = method.body();
                Scope outerScope = scope;
                public RuntimeValue invoke(List<RuntimeValue> arguments) throws EvaluateException
                {
                    Scope argScope = new Scope(outerScope);
                    Scope bodyScope = new Scope(argScope);
                    Scope prevScope = scope;
                    try
                    {
                        // Evaluate arguments

                        var funcParams = parameters;
                        if (funcParams.size() != arguments.size())
                            throw new EvaluateException("Incorrect number of arguments", Optional.of(method));
                        for (int i = 0; i < funcParams.size(); i++)
                            argScope.define(funcParams.get(i), arguments.get(i));

                        // Define new scope, storing old one so it can be restored

                        scope = bodyScope;
                        RuntimeValue returnVal = new RuntimeValue.Primitive(null);

                        // Run function body and store return value if necessary

                        try
                        {
                            for (var stmt : body)
                                visit(stmt);
                        }
                        catch (ReturnException e)
                        {
                            returnVal = e.returnValue;
                        }

                        return returnVal;
                    }
                    finally
                    {
                        // Scope must always be restored

                        scope = prevScope;
                    }
                }
            });
            objScope.define(method.name(), func);
        }

        return obj;
    }

    /**
     * Helper function for extracting RuntimeValues of specific types. If type
     * is a subclass of {@link RuntimeValue} the check applies to the value
     * itself, otherwise the value must be a {@link RuntimeValue.Primitive} and
     * the check applies to the primitive value.
     */
    private static <T> Optional<T> requireType(RuntimeValue value, Class<T> type)
    {
        //To be discussed in lecture
        Optional<Object> unwrapped = RuntimeValue.class.isAssignableFrom(type)
            ? Optional.of(value)
            : requireType(value, RuntimeValue.Primitive.class).map(RuntimeValue.Primitive::value);
        return (Optional<T>) unwrapped.filter(type::isInstance); //cast checked by isInstance
    }

}
