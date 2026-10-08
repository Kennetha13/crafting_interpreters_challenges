package com.craftinginterpreters.lox;

import java.util.List;

class LoxFunction implements LoxCallable {
    private final Expr.Function declaration;
    private final Environment closure;
    // Null for an anonymous function.
    private final String name;
    private final boolean isInitializer;

    LoxFunction(Expr.Function declaration, Environment closure, String name,
            boolean isInitializer) {
        this.isInitializer = isInitializer;
        this.closure = closure;
        this.declaration = declaration;
        this.name = name;
    }

    @Override
    public int arity() {
        if (declaration.params == null)
            return 0;
        return declaration.params.size();
    }

    boolean isGetter() {
        return declaration.params == null;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Environment environment = new Environment(closure);
        if (declaration.params != null) {
            for (int i = 0; i < declaration.params.size(); i++) {
                environment.define(arguments.get(i));
            }
        }

        try {
            interpreter.executeBlock(declaration.body, environment);
        } catch (Return returnValue) {
            if (isInitializer)
                return closure.getAt(0, 0);
            return returnValue.value;
        }

        if (isInitializer)
            return closure.getAt(0, 0);
        return null;
    }

    LoxFunction bind(LoxInstance instance) {
        Environment environment = new Environment(closure);
        environment.define(instance);
        return new LoxFunction(declaration, environment, name, isInitializer);
    }

    @Override
    public String toString() {
        if (name == null)
            return "<fn>";
        return "<fn " + name + ">";
    }
}
