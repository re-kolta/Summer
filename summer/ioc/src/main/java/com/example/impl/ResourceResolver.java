package com.example.impl;

import com.example.impl.Resource;

import java.util.List;
import java.util.function.Function;

public class ResourceResolver {
    String basePackage;
    public ResourceResolver(String basePackage){
        this.basePackage = basePackage;
    }
    public <E> List<E> scan(Function<Resource,E> mapper){
        return null;
    }
}
