package com.example.service;

import com.example.impl.SummerAnnotationApplicationContext;
import com.example.impl.annotation.SummerComponentScan;

import java.io.IOException;

@SummerComponentScan("com.example.service")
public class AppConfig {
    public static void main(String[] args) throws IOException, ClassNotFoundException, NoSuchMethodException {
        SummerAnnotationApplicationContext summerAnnotationApplicationContext = new SummerAnnotationApplicationContext(AppConfig.class);
    }}
