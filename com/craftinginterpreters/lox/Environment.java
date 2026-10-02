package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;

class Environment {
    static final Object UNINITIALIZED = new Object();

    final Environment enclosing;
    private final List<Object> values = new ArrayList<>();

    Environment() {
        enclosing = null;
    }

    Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    void define(Object value) {
        values.add(value);
    }

    private Environment ancestor(int distance) {
        Environment environment = this;
        for (int i = 0; i < distance; i++) {
            environment = environment.enclosing;
        }

        return environment;
    }

    Object getAt(int distance, int slot) {
        return ancestor(distance).values.get(slot);
    }

    void assignAt(int distance, int slot, Object value) {
        ancestor(distance).values.set(slot, value);
    }
}
