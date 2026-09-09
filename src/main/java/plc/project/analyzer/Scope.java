package plc.project.analyzer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * IMPORTANT: This is an API file and should not be modified by your submission.
 */
public final class Scope {

    private final Scope parent;
    private final Map<String, Type> variables = new LinkedHashMap<>();

    public Scope(Scope parent) {
        this.parent = parent;
    }

    public Optional<Type> resolve(String name, boolean current) {
        if (variables.containsKey(name)) {
            return Optional.of(variables.get(name));
        } else if (parent != null && !current) {
            return parent.resolve(name, false);
        } else {
            return Optional.empty();
        }
    }

    public void define(String name, Type type) {
        if (!variables.containsKey(name)) {
            variables.put(name, type);
        } else {
            throw new IllegalStateException("Variable is already defined.");
        }
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        builder.append("Scope[");
        builder.append("parent=").append(parent).append(", ");
        builder.append("variables=Map["); //format Map like record for prettify
        variables.forEach((key, value) -> {
            builder.append(key).append("=").append(value).append(", ");
        });
        builder.append("]");
        return builder.toString();
    }

    //IMPORTANT: For use in Type.ObjectType, NOT the Analyzer.
    public Map<String, Type> collect(boolean current) {
        if (current || parent == null) {
            return new LinkedHashMap<>(variables);
        } else {
            var map = parent.collect(false);
            map.putAll(variables);
            return map;
        }
    }

}
