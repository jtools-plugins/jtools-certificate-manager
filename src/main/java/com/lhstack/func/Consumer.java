package com.lhstack.func;

import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface Consumer<T> {

    void accept(@NotNull T entity) throws Throwable;
}
