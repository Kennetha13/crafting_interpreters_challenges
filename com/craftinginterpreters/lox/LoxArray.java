package com.craftinginterpreters.lox;

import java.util.List;

class LoxArray extends LoxInstance {
    private final Object[] elements;

    LoxArray(int size) {
        super(null);
        elements = new Object[size];
    }

    @Override
    Object get(Token name) {
        if (name.lexeme.equals("get")) {
            return new LoxCallable() {
                @Override
                public int arity() {
                    return 1;
                }

                @Override
                public Object call(Interpreter interpreter, List<Object> arguments) {
                    int index = (int) (double) arguments.get(0);
                    return elements[index];
                }
            };
        } else if (name.lexeme.equals("set")) {
            return new LoxCallable() {
                @Override
                public int arity() {
                    return 2;
                }

                @Override
                public Object call(Interpreter interpreter, List<Object> arguments) {
                    int index = (int) (double) arguments.get(0);
                    Object value = arguments.get(1);
                    return elements[index] = value;
                }
            };
        } else if (name.lexeme.equals("length")) {
            return (double) elements.length;
        }

        throw new RuntimeError(name,
                "Undefined property '" + name.lexeme + "'.");
    }

    @Override
    void set(Token name, Object value) {
        throw new RuntimeError(name, "Can't add properties to arrays.");
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        for (int i = 0; i < elements.length; i++) {
            if (i != 0)
                builder.append(", ");
            builder.append(elements[i] == null ? "nil" : stringify(elements[i]));
        }
        builder.append("]");
        return builder.toString();
    }

    private static String stringify(Object value) {
        if (value instanceof Double) {
            String text = value.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }
        return value.toString();
    }
}
