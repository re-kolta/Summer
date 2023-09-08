package com.example.service;

import com.example.impl.annotation.SummerBean;
import com.example.impl.annotation.SummerConfiguration;

@SummerConfiguration
public class AppleConfig {
    @SummerBean
    public Apple getApple(){
        return new Apple();
    }
}
