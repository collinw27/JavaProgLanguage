package plc.project.analyzer;

import java.util.List;
import java.util.Optional;

/**
 * IMPORTANT: This is an API file and should not be modified by your submission.
 */
public sealed interface Type {

    Primitive ANY = new Primitive("Any", "Object");
    Primitive NIL = new Primitive("Nil", "Void");
    Primitive DYNAMIC = new Primitive("Dynamic", "Object"); //Limitation

    Primitive BOOLEAN = new Primitive("Boolean", "boolean");
    Primitive INTEGER = new Primitive("Integer", "BigInteger");
    Primitive DECIMAL = new Primitive("Decimal", "BigDecimal");
    Primitive CHARACTER = new Primitive("Character", "char");
    Primitive STRING = new Primitive("String", "String");

    Primitive EQUATABLE = new Primitive("Equatable", "Object");
    Primitive COMPARABLE = new Primitive("Comparable", "Comparable");
    Primitive ITERABLE = new Primitive("Iterable", "Iterable<BigInteger>");

    record Primitive(
        String name,
        String jvmName
    ) implements Type {}

    record Function(
        List<Type> parameters,
        Type returns
    ) implements Type {}

    //Using "ObjectType" to avoid confusion with Java's "Object"
    record ObjectType(
        Optional<String> name, //for debugging
        Scope scope
    ) implements Type {

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ObjectType object &&
                name.equals(object.name) &&
                //Note: Not strictly accurate, but sufficient for our needs.
                scope.collect(false).equals(object.scope.collect(false));
        }

        @Override
        public String toString() {
            return "Object[name=" + name + ", scope=" + scope.collect(false) + "]";
        }

    }

    default boolean isSubtypeOf(Type supertype) {
        return Environment.isSubtypeOf(this, supertype);
    }

    default String jvmName() {
        return switch (this) {
            case Primitive primitive -> primitive.jvmName;
            case Function function -> switch (function.parameters.size()) {
                case 0 -> "Supplier<" + function.returns.jvmName() + ">";
                case 1 -> "Function<" + function.parameters.getFirst() + ", " + function.returns.jvmName() + ">";
                case 2 -> "BiFunction<" + function.parameters.getFirst() + ", " + function.parameters.getLast() + ", " + function.returns.jvmName() + ">";
                default -> "Object"; //Limitation
            };
            case Object _ -> "var";
        };
    }

}
