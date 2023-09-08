package com.example.impl.Util;

import com.example.impl.annotation.SummerComponent;
import com.example.impl.annotation.SummerOrder;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Objects;

public class ClassUtil {
    /* 判断逻辑：先看表面包含不包含此注解 包含则将result设置为true 不包含则设置为false
               再循环遍历所有注解 查看是否在注解里面包含此注解
               tempResult设置的目的就是看循环里面有没有此注解 初始化为false 不能设置为true 可以根据代码书写来体会这句话
               有四种情况 target本身包含 循环不包含 最终为真
               target包含 循环亦包含 注解重复书写 最终为真
               target不包含 循环不包含 最终为假
               target不包含 循环包含 最终为真

    * */
    public static boolean hasAnnotation(Class target,Class anno){
        //获取target中所有的注解
        Object o = target.getAnnotation(anno);
        System.out.println(target + "  " + o);
        boolean result = true;
        //先判断一下 表面上有没有这个注解 没有就定义为false
        if (Objects.isNull(o)) result = false;
        //依次循环每一个注解里面是否有这个
        boolean tempResult = false;
        for (Annotation annotation:target.getAnnotations()){
            //排除官方注释 是官方注解的话什么也不做
            if(!annotation.annotationType().getPackage().equals("java.lang.annotation")){
                boolean temp = hasAnnotation(annotation.getClass(),anno);
                if(!temp) tempResult = false;
                else {
                    if (result == true) {
                        System.out.println("有重复注解");
                    }
                }
            }
        }
        if(result == true || tempResult == true){
            return true;
        }else return false;
    }
    public static String getBeanName(Class clazz){
        SummerComponent summerComponent = (SummerComponent) clazz.getAnnotation(SummerComponent.class);
        String name;
        if (summerComponent.value().length()>0){
            name = summerComponent.value();
        }
        //这里就是关键 getName是全限命名 getSimpleName是单单获取类名
        name = clazz.getSimpleName();
        name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        return name;
    }
    public static int getClassOrder(Class clazz){
        SummerOrder orderAnno = (SummerOrder) clazz.getAnnotation(SummerOrder.class);
        int order = orderAnno == null ? Integer.MAX_VALUE : orderAnno.value();
        return order;
    }
    public static int getMethodOrder(Method method){
        return 3;
    }
    public static Annotation getAnnotation(Annotation[] annotations,Class clazz){
        for (Annotation annotation : annotations) {
            if (clazz.isInstance(annotation)) {
                return annotation;
            }
        }
        return null;
    }
}
