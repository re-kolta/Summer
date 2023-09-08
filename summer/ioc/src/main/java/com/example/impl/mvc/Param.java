package com.example.impl.mvc;

import com.example.impl.annotation.SummerPathVariable;
import com.example.impl.annotation.SummerRequestBody;
import com.example.impl.annotation.SummerRequestParam;
import jakarta.servlet.ServletException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

//方法里面的参数
public class Param {
    //参数名？
    String name;
    //参数属于哪种类型？ 有容器本身的 有url里面的 有表单里面的
    ParamType paramType;
    Class<?> classType;
    String defaultValue;
    //annotation决定参数属于哪种类型
    //RequestBody，RequestParam，PathVariable 当然还有requestHeader这些
    public Param(String name, Annotation[] annotation, Parameter parameter) throws ServletException {
        this.name = name;
        this.classType = parameter.getType();
        for (int i = 0; i < annotation.length; i++) {
            int n = 0;
            if(SummerRequestParam.class.isInstance(annotation[i])){
                this.paramType = ParamType.REQUEST_PARAM;
                n++;
            };
            if(SummerRequestBody.class.isInstance(annotation[i])){
                this.paramType = ParamType.REQUEST_BODY;
                n++;
            };
            if(SummerPathVariable.class.isInstance(annotation[i])){
                this.paramType = ParamType.PATH_VARIABLE;
                n++;
            }
            if(n == 0){
                this.paramType = ParamType.SERVLET_VARIABLE;
                n++;
            }
            if(n > 1){
                throw new ServletException("controller 方法使用多个注解");
            }
        }
        defaultValue = "nospecified";
    }

}
