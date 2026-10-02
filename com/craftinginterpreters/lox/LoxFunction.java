package com.craftinginterpreters.lox;

import java.util.List;

class LoxFunction implements LoxCallable {
    private final Expr.Function declaration;
    private final Environment closure;
    // Null for an anonymous function.
    private final String name;

    LoxFunction(Expr.Function declaration, Environment closure, String name) {
        this.closure = closure;
        this.declaration = declaration;
        this.name = name;
    }

    @Override
    public int arity() {
        return declaration.params.size();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Environment environment = new Environment(closure);
        for (int i = 0; i < declaration.params.size(); i++) {
            environment.define(declaration.params.get(i).lexeme,
                    arguments.get(i));
        }

        try {
            interpreter.executeBlock(declaration.body, environment);
        } catch (Return returnValue) {
            return returnValue.value;
        }
        return null;
    }

    @Override
    public String toString() {
        if (name == null)
            return "<fn>";
        return "<fn " + name + ">";
    }
}
