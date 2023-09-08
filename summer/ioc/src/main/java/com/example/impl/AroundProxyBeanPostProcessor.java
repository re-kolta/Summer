package com.example.impl;


import com.example.impl.Util.ApplicationContextUtils;
import com.example.impl.annotation.Around;
import net.bytebuddy.implementation.InvocationHandlerAdapter;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

//记录一下逻辑 首先任何要让容器操作的类 一定要把它加入到容器
//1. 要被增强的类 肯定要标注Component 加上Around指定代理类 并且在方法上标注enhance 让代理类知道要增强哪一个
//2. 代理类 也就是InvocationHandler 也要被注入到容器中 因为统一要在BeanPostProcessor里面执行相关动态代理逻辑
//3. 当然这个aop的BeanPostProcessor也要放到容器里面 在创建bean的时候是要循环执行这个Processor里面的方法逻辑的 他会把类定义都传入 可以以此判断是否有Around决定是否增强
public class AroundProxyBeanPostProcessor implements BeanPostProcessor{
    //原对象还是要能找到的 但是这个处理好像并不会换对象
    Map<String, Object> originBeans = new HashMap<>();

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {

        //检测是否有Around注解
        Class clazz = bean.getClass();
        Around around = (Around) clazz.getAnnotation(Around.class);
        if(!Objects.isNull(around)){
            //要把Around里面的handler的名字也就是对应的beanName拿出来
            String handlerName = around.handle();
            Object proxy = createProxy(bean,handlerName);
            originBeans.put(beanName,bean);
            return proxy;
        }else return bean;
        //如果没有这个around注解那就啥也不干
    }
    //这里就是调用ProxyResolver的逻辑 生成proxy的class 然后产生对象
    public Object createProxy(Object bean,String handlerName){
        //先拿到这个增强类 这时候就用到我们最近定义的功能（获取容器） 但是如果此时这个handler还未被创建咋办？？？
        SummerAnnotationApplicationContext context = ApplicationContextUtils.getApplicationContext();
        BeanDefinition handlerBeanDefinition = context.findBeanDefinition(handlerName);
        if(!Objects.isNull(handlerBeanDefinition) && Objects.isNull(handlerBeanDefinition.getInstance())){
            //证明容器启动了 但是还未创建这个handlerBean那我们就要手动创建
            try {
                handlerBeanDefinition = context.doCreateNormalBean((byte) 0,handlerBeanDefinition);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        ProxyResolver proxyResolver = new ProxyResolver();
        if(handlerBeanDefinition.getInstance() instanceof InvocationHandler){
            return proxyResolver.createProxy(bean, (InvocationHandler) handlerBeanDefinition.getInstance());
        }
        return null;
    }
    @Override
    public Object postProcessOnSetProperty(Object bean, String beanName) {
        Object origin = this.originBeans.get(beanName);
        return origin != null ? origin : bean;
    }
}
