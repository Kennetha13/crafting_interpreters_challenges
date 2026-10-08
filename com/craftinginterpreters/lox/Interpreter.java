package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.craftinginterpreters.lox.Expr.Conditional;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {

    private static class BreakException extends RuntimeException {
    }

    final Map<String, Object> globals = new HashMap<>();
    private Environment environment;
    private final Map<Expr, Integer> locals = new HashMap<>();
    private final Map<Expr, Integer> slots = new HashMap<>();

    Interpreter() {
        globals.put("clock", new LoxCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double) System.currentTimeMillis() / 1000.0;
            }

            @Override
            public String toString() {
                return "<native fn>";
            }
        });

        globals.put("Array", new LoxCallable() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                int size = (int) (double) arguments.get(0);
                return new LoxArray(size);
            }

            @Override
            public String toString() {
                return "<native fn>";
            }
        });
    }

    void resolve(Expr expr, int depth, int slot) {
        locals.put(expr, depth);
        slots.put(expr, slot);
    }

    private void define(Token name, Object value) {
        if (environment != null) {
            environment.define(value);
        } else {
            globals.put(name.lexeme, value);
        }
    }

    void interpret(List<Stmt> statements) {
        interpret(statements, false);
    }

    void interpret(List<Stmt> statements, boolean replMode) {
        try {
            for (Stmt statement : statements) {
                if (replMode && statement instanceof Stmt.Expression) {
                    Object value = evaluate(((Stmt.Expression) statement).expression);
                    System.out.println(stringify(value));
                } else {
                    execute(statement);
                }
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitThisExpr(Expr.This expr) {
        return lookUpVariable(expr.keyword, expr);
    }

    @Override
    public Object visitSuperExpr(Expr.Super expr) {
        int distance = locals.get(expr);
        // "super" and "this" are each the sole variable in their own
        // one-variable scope, so both always sit at slot 0; "this" is
        // exactly one scope closer than "super".
        LoxClass superclass = (LoxClass) environment.getAt(distance, 0);
        LoxInstance object = (LoxInstance) environment.getAt(distance - 1, 0);

        LoxFunction method = superclass.findMethod(object, expr.method.lexeme);
        if (method == null) {
            throw new RuntimeError(expr.method,
                    "Undefined property '" + expr.method.lexeme + "'.");
        }
        return method;
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG:
                return !isTruthy(right);
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double) right;
        }

        // Unreachable.
        return null;
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return lookUpVariable(expr.name, expr);
    }

    private Object lookUpVariable(Token name, Expr expr) {
        Integer distance = locals.get(expr);
        if (distance != null) {
            Object value = environment.getAt(distance, slots.get(expr));
            if (value == Environment.UNINITIALIZED) {
                throw new RuntimeError(name,
                        "Uninitialized variable '" + name.lexeme + "'.");
            }
            return value;
        } else {
            if (globals.containsKey(name.lexeme)) {
                Object value = globals.get(name.lexeme);
                if (value == Environment.UNINITIALIZED) {
                    throw new RuntimeError(name,
                            "Uninitialized variable '" + name.lexeme + "'.");
                }
                return value;
            }
            throw new RuntimeError(name,
                    "Undefined variable '" + name.lexeme + "'.");
        }
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));
        }

        if (!(callee instanceof LoxCallable)) {
            throw new RuntimeError(expr.paren,
                    "Can only call functions and classes.");
        }

        LoxCallable function = (LoxCallable) callee;
        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren, "Expected " +
                    function.arity() + " arguments but got " +
                    arguments.size() + ".");
        }

        return function.call(this, arguments);
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left))
                return left;
        } else {
            if (!isTruthy(left))
                return left;
        }

        return evaluate(expr.right);
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double)
            return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private void checkNumberOperands(Token operator,
            Object left, Object right) {
        if (left instanceof Double && right instanceof Double)
            return;

        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    private boolean isTruthy(Object object) {
        if (object == null)
            return false;
        if (object instanceof Boolean)
            return (boolean) object;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null)
            return true;
        if (a == null)
            return false;

        return a.equals(b);
    }

    private String stringify(Object object) {
        if (object == null)
            return "nil";

        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        return object.toString();
    }

    @Override
    public Object visitGetExpr(Expr.Get expr) {
        Object object = evaluate(expr.object);
        if (object instanceof LoxInstance) {
            Object result = ((LoxInstance) object).get(expr.name);
            // A getter has no parameter list -- invoke it immediately
            // instead of handing back the raw function.
            if (result instanceof LoxFunction && ((LoxFunction) result).isGetter()) {
                result = ((LoxFunction) result).call(this, new ArrayList<>());
            }
            return result;
        }

        throw new RuntimeError(expr.name,
                "Only instances have properties.");
    }

    @Override
    public Object visitSetExpr(Expr.Set expr) {
        Object object = evaluate(expr.object);

        if (!(object instanceof LoxInstance)) {
            throw new RuntimeError(expr.name, "Only instances have fields.");
        }

        Object value = evaluate(expr.value);
        ((LoxInstance) object).set(expr.name, value);
        return value;
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitFunctionExpr(Expr.Function expr) {
        return new LoxFunction(expr, environment, null, false);
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    void executeBlock(List<Stmt> statements,
            Environment environment) {
        Environment previous = this.environment;
        try {
            this.environment = environment;

            for (Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        Object superclass = null;
        if (stmt.superclass != null) {
            superclass = evaluate(stmt.superclass);
            if (!(superclass instanceof LoxClass)) {
                throw new RuntimeError(stmt.superclass.name,
                        "Superclass must be a class.");
            }
        }

        if (stmt.superclass != null) {
            environment = new Environment(environment);
            environment.define(superclass);
        }

        Map<String, LoxFunction> classMethods = new HashMap<>();
        for (Stmt.Function method : stmt.classMethods) {
            LoxFunction function = new LoxFunction(method.function, environment,
                    method.name.lexeme, false);
            classMethods.put(method.name.lexeme, function);
        }

        LoxClass metaclass = new LoxClass(null, null, stmt.name.lexeme + " metaclass",
                classMethods);

        Map<String, LoxFunction> methods = new HashMap<>();
        for (Stmt.Function method : stmt.methods) {
            boolean isInitializer = method.name.lexeme.equals("init");
            LoxFunction function = new LoxFunction(method.function, environment,
                    method.name.lexeme, isInitializer);
            methods.put(method.name.lexeme, function);
        }

        if (stmt.superclass != null) {
            environment = environment.enclosing;
        }

        LoxClass klass = new LoxClass(metaclass, (LoxClass) superclass,
                stmt.name.lexeme, methods);
        define(stmt.name, klass);
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        LoxFunction function = new LoxFunction(stmt.function, environment, stmt.name.lexeme, false);
        define(stmt.name, function);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        try {
            while (isTruthy(evaluate(stmt.condition))) {
                execute(stmt.body);
            }
        } catch (BreakException ex) {
            // Do nothing.
        }
        return null;
    }

    @Override
    public Void visitBreakStmt(Stmt.Break stmt) {
        throw new BreakException();
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null)
            value = evaluate(stmt.value);

        throw new Return(value);
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = Environment.UNINITIALIZED;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }

        define(stmt.name, value);
        return null;
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);

        Integer distance = locals.get(expr);
        if (distance != null) {
            environment.assignAt(distance, slots.get(expr), value);
        } else {
            if (globals.containsKey(expr.name.lexeme)) {
                globals.put(expr.name.lexeme, value);
            } else {
                throw new RuntimeError(expr.name,
                        "Undefined variable '" + expr.name.lexeme + "'.");
            }
        }

        return value;
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double) left > (double) right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double) left >= (double) right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double) left < (double) right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double) left <= (double) right;
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double) left - (double) right;
            case PLUS:
                if (left instanceof String || right instanceof String) {
                    return stringify(left) + stringify(right);
                }

                if (left instanceof Double && right instanceof Double) {
                    return (double) left + (double) right;
                }

                throw new RuntimeError(expr.operator,
                        "Operands must be two numbers or two strings.");
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                if ((double) right == 0) {
                    throw new RuntimeError(expr.operator, "Divide by zero.");
                }
                return (double) left / (double) right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double) left * (double) right;

            case BANG_EQUAL:
                return !isEqual(left, right);
            case EQUAL_EQUAL:
                return isEqual(left, right);
            case COMMA:
                return right;
        }

        // Unreachable.
        return null;
    }

    @Override
    public Object visitConditionalExpr(Conditional expr) {
        if (isTruthy(evaluate(expr.condition))) {
            return evaluate(expr.thenBranch);
        } else {
            return evaluate(expr.elseBranch);
        }
    }

}
