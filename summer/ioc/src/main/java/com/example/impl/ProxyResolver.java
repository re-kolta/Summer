package com.example.impl;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class ProxyResolver {
    ByteBuddy byteBuddy = new ByteBuddy();

    public <T> T createProxy(T bean, InvocationHandler invocationHandler){
        Class targetClass = bean.getClass();
        //这里会创建一个新类
        Class proxyClass = byteBuddy.subclass(targetClass, ConstructorStrategy.Default.DEFAULT_CONSTRUCTOR)
                .method(ElementMatchers.isPublic()).intercept(
                        InvocationHandlerAdapter.of(
                                new InvocationHandler() {
                                    @Override
                                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                        return invocationHandler.invoke(bean,method,args);
                                    }
                                }
                        )
                ).make().load(targetClass.getClassLoader()).getLoaded();
        Object proxy;
        try {
            proxy = proxyClass.getConstructor().newInstance();
        }catch (RuntimeException e){
            throw e;
        }catch (Exception e){
            throw new RuntimeException(e);
        }
        return (T)proxy;
    }

}
