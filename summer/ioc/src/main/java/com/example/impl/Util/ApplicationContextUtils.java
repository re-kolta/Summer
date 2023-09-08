package com.example.impl.Util;

import com.example.impl.SummerAnnotationApplicationContext;

public class ApplicationContextUtils {
    private static SummerAnnotationApplicationContext context = null;

    public static SummerAnnotationApplicationContext getApplicationContext(){
        return context;
    }
    public static void setApplicationContext(SummerAnnotationApplicationContext param){
        context = param;
    }
}
