package plc.project.evaluator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * IMPORTANT: This is an API file and should not be modified by your submission.
 */
public final class Scope {

    private final Scope parent;
    private final Map<String, RuntimeValue> variables = new LinkedHashMap<>();

    public Scope(Scope parent) {
        this.parent = parent;
    }

    public Optional<RuntimeValue> resolve(String name, boolean current) {
        if (variables.containsKey(name)) {
            return Optional.of(variables.get(name));
        } else if (parent != null && !current) {
            return parent.resolve(name, false);
        } else {
            return Optional.empty();
        }
    }

    public void define(String name, RuntimeValue object) {
        if (!variables.containsKey(name)) {
            variables.put(name, object);
        } else {
            throw new IllegalStateException("Variable is already defined.");
        }
    }

    public void assign(String name, RuntimeValue object) {
        if (variables.containsKey(name)) {
            variables.put(name, object);
        } else if (parent != null) {
            parent.assign(name, object);
        } else {
            throw new IllegalStateException("Variable is not defined.");
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

    //IMPORTANT: For use in RuntimeValue.ObjectValue, NOT the Evaluator.
    public Map<String, RuntimeValue> collect(boolean current) {
        if (current || parent == null) {
            return new LinkedHashMap<>(variables);
        } else {
            var map = parent.collect(false);
            map.putAll(variables);
            return map;
        }
    }

}
