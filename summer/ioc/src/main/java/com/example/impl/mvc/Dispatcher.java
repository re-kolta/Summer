package com.example.impl.mvc;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Dispatcher {
    //我们把一个路由的处理信息抽象成一个类 包含着比如路由路径 post还是get 返回页面还是json
    boolean isRest;
    boolean isResponseBody;
    boolean isVoid;
    Pattern urlPattern;
    Object controller;
    Method handlerMethod;
    Param[] methodParams;
    public Dispatcher(boolean isRest,String path,Object controller,Method method) throws ServletException {
        this.isRest = isRest;
        this.isVoid = method.getReturnType() == void.class;
        this.controller = controller;
        this.urlPattern = PathUtil.getUrlPattern(path);
        this.handlerMethod = method;
        //生成Param
        Parameter[] parameters = method.getParameters();
        this.methodParams = new Param[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Param param = new Param(parameters[i].getName(), parameters[i].getAnnotations(),parameters[i]);
            this.methodParams[i] = param;
        }
    }
    //这里我们需要注意到 get里面有可能也是有请求体的 所以也要处理
    public DispatcherResult process(String uri, HttpServletRequest request, HttpServletResponse response) throws Exception {
        Matcher matcher = urlPattern.matcher(uri);
        if(matcher.matches()){
            //匹配url
            Object[] arguments = new Object[methodParams.length];
            for(int i = 0;i< methodParams.length;i++){
                Param param = methodParams[i];
                arguments[i] = switch (param.paramType){
                    case REQUEST_BODY ->{
                        //这种直接处理好像容易丢掉urlencoded 验证发现并不会。。
                        //将请求体的内容序列化为json？？
                        BufferedReader bufferedReader = request.getReader();
                        yield JsonUtil.readJson(bufferedReader,param.classType);
                    }
                    case PATH_VARIABLE -> {
                        //路径赋值
                        try {
                            // fdd/{balabala}  Pathvariable("balabala")
                            String s = matcher.group(param.name);
                            yield convertToType(param.classType, s);
                        } catch (IllegalArgumentException e) {
                            throw new Exception("pathVariable error");
                        }
                    }
                    case REQUEST_PARAM ->{
                        //这就要调用request的方法了
                        String s = request.getParameter(param.name);
                        yield  convertToType(param.classType,s);
                    }
                    case SERVLET_VARIABLE -> {
                        Class<?> classType = param.classType;
                        if (classType == HttpServletRequest.class) {
                            yield request;
                        } else if (classType == HttpServletResponse.class) {
                            yield response;
                        } else if (classType == HttpSession.class) {
                            yield request.getSession();
                        } else if (classType == ServletContext.class) {
                            yield request.getServletContext();
                        } else {
                            throw new Exception("SERVLET_VARIABLE error");
                        }
                    }
                };
            }
            //循环结束 也就意味着参数都以赋值完成
            Object result = null;
            result = this.handlerMethod.invoke(controller,arguments);
            return new DispatcherResult(true,result);
        }
        return new DispatcherResult(false,null);
    }
    Object convertToType(Class<?> classType, String s) throws Exception {
        if (classType == String.class) {
            return s;
        } else if (classType == boolean.class || classType == Boolean.class) {
            return Boolean.valueOf(s);
        } else if (classType == int.class || classType == Integer.class) {
            return Integer.valueOf(s);
        } else if (classType == long.class || classType == Long.class) {
            return Long.valueOf(s);
        } else if (classType == byte.class || classType == Byte.class) {
            return Byte.valueOf(s);
        } else if (classType == short.class || classType == Short.class) {
            return Short.valueOf(s);
        } else if (classType == float.class || classType == Float.class) {
            return Float.valueOf(s);
        } else if (classType == double.class || classType == Double.class) {
            return Double.valueOf(s);
        } else {
            throw new Exception("Could not determine argument type: " + classType);
        }
    }
}
