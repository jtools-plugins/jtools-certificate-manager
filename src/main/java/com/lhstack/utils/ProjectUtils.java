package com.lhstack.utils;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;

import java.util.function.Supplier;

public class ProjectUtils {

    public static <T> T getOrCreate(Project project, Key<T> key, Supplier<T> supplier) {
        T userData = project.getUserData(key);
        if(userData == null) {
            userData = supplier.get();
            project.putUserData(key,userData);
        }
        return userData;
    }
}
