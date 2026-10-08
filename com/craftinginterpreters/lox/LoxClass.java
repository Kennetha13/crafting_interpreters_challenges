package com.craftinginterpreters.lox;

import java.util.List;
import java.util.Map;

class LoxClass extends LoxInstance implements LoxCallable {
    final String name;
    final LoxClass superclass;
    private final Map<String, LoxFunction> methods;

    LoxClass(LoxClass metaclass, LoxClass superclass, String name,
            Map<String, LoxFunction> methods) {
        super(metaclass);
        this.superclass = superclass;
        this.name = name;
        this.methods = methods;
    }


    LoxFunction findMethod(LoxInstance instance, String name) {
        LoxFunction method = null;
        LoxFunction inner = null;
        LoxClass klass = this;
        while (klass != null) {
            if (klass.methods.containsKey(name)) {
                inner = method;
                method = klass.methods.get(name);
            }

            klass = klass.superclass;
        }

        if (method != null) {
            return method.bind(instance, inner);
        }

        return null;
    }

  
    private LoxFunction findTopMethod(String name) {
        LoxFunction method = null;
        LoxClass klass = this;
        while (klass != null) {
            if (klass.methods.containsKey(name)) {
                method = klass.methods.get(name);
            }

            klass = klass.superclass;
        }

        return method;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        LoxInstance instance = new LoxInstance(this);
        LoxFunction initializer = findMethod(instance, "init");
        if (initializer != null) {
            initializer.call(interpreter, arguments);
        }

        return instance;
    }

    @Override
    public int arity() {
        LoxFunction initializer = findTopMethod("init");
        if (initializer == null)
            return 0;
        return initializer.arity();
    }

    @Override
    public String toString() {
        return name;
    }
}
