package com.example.impl.annotation;

public @interface Around {
    //标注该类为需要被织入切面 并指定切面
    String handle();
}
