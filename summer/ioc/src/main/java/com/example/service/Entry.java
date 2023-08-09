package com.example.service;

import java.lang.reflect.Method;

public class Entry {
    public static void main(String[] args) throws NoSuchMethodException {
        Entry entry = new Entry();
        Class h = entry.getClass();
        Method refl = h.getMethod("Refl", null);
        System.out.println(refl.getReturnType());

    }
    public Fruit Refl(){
        return new Apple();
    }
}
