package com.xlythe.dao;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Database {
    int version() default 1;
    boolean retainDataOnUpgrade() default false;
    String tableName() default "";
    String name() default "";
}
