package plc.project.evaluator;

import javax.swing.text.html.Option;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Environment {

    public static Scope scope() {
        var scope = new Scope(null);
        //"Native" functions for printing and creating lists.
        scope.define("debug", new RuntimeValue.Function("debug", Environment::debug));
        scope.define("print", new RuntimeValue.Function("print", Environment::print));
        scope.define("log", new RuntimeValue.Function("log", Environment::log));
        scope.define("list", new RuntimeValue.Function("list", Environment::list));
        scope.define("range", new RuntimeValue.Function("range", Environment::range));
        //Helper functions for testing variables, functions, and objects.
        scope.define("variable", new RuntimeValue.Primitive("variable"));
        scope.define("function", new RuntimeValue.Function("function", Environment::function));
        var prototype = new RuntimeValue.ObjectValue(Optional.of("Prototype"), new Scope(null));
        prototype.scope().define("inherited_property", new RuntimeValue.Primitive("inherited_property"));
        prototype.scope().define("inherited_method", new RuntimeValue.Function("inherited_method", Environment::method));
        var object = new RuntimeValue.ObjectValue(Optional.of("Object"), new Scope(null));
        scope.define("object", object);
        object.scope().define("prototype", prototype);
        object.scope().define("property", new RuntimeValue.Primitive("property"));
        object.scope().define("method", new RuntimeValue.Function("method", Environment::method));
        var object2 = new RuntimeValue.ObjectValue(Optional.of("Object"), new Scope(null));
        scope.define("object2", object2);
        object2.scope().define("prototype", object);

        return scope;
    }

    /**
     * Prints the raw RuntimeValue.toString() result.
     */
    private static RuntimeValue debug(List<RuntimeValue> arguments) throws EvaluateException {
        if (arguments.size() != 1) {
            throw new EvaluateException("Expected debug to be called with 1 argument.", Optional.empty());
        }
        System.out.println(arguments.getFirst());
        return new RuntimeValue.Primitive(null);
    }

    /**
     * Prints a formatted RuntimeValue.
     */
    public static RuntimeValue print(List<RuntimeValue> arguments) throws EvaluateException {
        if (arguments.size() != 1) {
            throw new EvaluateException("Expected print to be called with 1 argument.", Optional.empty());
        }
        System.out.println(arguments.getFirst().print());
        return new RuntimeValue.Primitive(null);
    }

    /**
     * Logs a formatted RuntimeValue and returns it.
     */
    static RuntimeValue log(List<RuntimeValue> arguments) throws EvaluateException {
        if (arguments.size() != 1) {
            throw new EvaluateException("Expected log to be called with 1 argument.", Optional.empty());
        }
        System.out.println("log: " + arguments.getFirst().print());
        return arguments.getFirst();
    }

    /**
     * Returns a List value containing all arguments.
     */
    private static RuntimeValue list(List<RuntimeValue> arguments) {
        return new RuntimeValue.Primitive(arguments);
    }

    /**
     * Takes two integer arguments (start, end) and returns a List containing
     * all integers in that range (inclusive, exclusive).
     */
    private static RuntimeValue range(List<RuntimeValue> arguments) throws EvaluateException
    {
        if (arguments.size() != 2)
        {
            throw new EvaluateException("Expected range to be called with 2 arguments.", Optional.empty());
        }
        if (!(arguments.get(0) instanceof RuntimeValue.Primitive p1 && p1.value() instanceof BigInteger start))
            throw new EvaluateException("Expected integer for range", Optional.empty());
        if (!(arguments.get(1) instanceof RuntimeValue.Primitive p2 && p2.value() instanceof BigInteger end))
            throw new EvaluateException("Expected integer for range", Optional.empty());
        if (start.compareTo(end) > 0)
            throw new EvaluateException("Invalid range", Optional.empty());
        List<RuntimeValue.Primitive> output = new ArrayList<>();
        for (BigInteger i = start; i.compareTo(end) < 0; i = i.add(BigInteger.ONE))
            output.add(new RuntimeValue.Primitive(i));
        return new RuntimeValue.Primitive(output);
    }

    /**
     * Returns a list of all function arguments.
     */
    private static RuntimeValue function(List<RuntimeValue> arguments) {
        return new RuntimeValue.Primitive(arguments);
    }

    /**
     * Returns a list of all method arguments. Question: why the difference?
     */
    private static RuntimeValue method(List<RuntimeValue> arguments) {
        return new RuntimeValue.Primitive(arguments.subList(1, arguments.size()));
    }

}
