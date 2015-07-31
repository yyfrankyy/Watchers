package com.yyfrankyy.watchers;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

class Context {
    final AtomicBoolean consumed = new AtomicBoolean(false);
    final Method method;
    final Object[] args;

    Context(Method method, Object[] args) {
        this.method = method;
        this.args = args;
    }
}