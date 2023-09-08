package com.example.impl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;

public class PropertyResolver {
    Map<String, String> properties = new HashMap<>();
    Map<Class, Function<String, Object>> converts = new HashMap<>();

    //通过文件流可以获得prop
    public PropertyResolver(Properties prop) {
        //放入环境变量
        this.properties.putAll(System.getenv());
        //读入properties
        Set<String> names = prop.stringPropertyNames();
        for (String name : names) {
            this.properties.put(name, prop.getProperty(name));
        }
        //String类型
        converts.put(String.class, s -> s);
        //int Integer
        converts.put(int.class, s -> Integer.parseInt(s));
        converts.put(Integer.class, s -> Integer.valueOf(s));
        //boolean
        converts.put(boolean.class, s -> Boolean.getBoolean(s));
        converts.put(Boolean.class, s -> Boolean.valueOf(s));
        //byte
        converts.put(byte.class, s -> Byte.parseByte(s));
        converts.put(Byte.class, s -> Byte.valueOf(s));
        //short
        converts.put(short.class, s -> Short.parseShort(s));
        converts.put(Short.class, s -> Short.valueOf(s));

        converts.put(long.class, s -> Long.parseLong(s));
        converts.put(Long.class, s -> Long.valueOf(s));

        converts.put(float.class, s -> Float.parseFloat(s));
        converts.put(Float.class, s -> Float.valueOf(s));

        converts.put(double.class, s -> Double.parseDouble(s));
        converts.put(Double.class, s -> Double.valueOf(s));
        //time
        converts.put(LocalDate.class,s -> LocalDate.parse(s));
        converts.put(LocalTime.class,s -> LocalTime.parse(s));
    }
//本项目不支持 在properties文件里面再使用占位符语法
    public String getProperty(String key) {
        //这个key就是value注解里面的东西
        PropertyExpr propertyExpr = parse(key);
        //这里是value里面一定有值的情况 所以永远不可能为空
        //默认值里面什么也没有 那就用key去查就行
        if (!Objects.isNull(propertyExpr)) {
            if (Objects.isNull(propertyExpr.defaultValue())) {
                return getValueFromMap(propertyExpr.key());
            } else {
                //这就又要判断了 因为有可能这个默认值又对应着另外一个key 用户可能会嵌套 也就是这个不满足就检查另一个另一个还不满足检查另另外一个
                return getProperty(propertyExpr.defaultValue());
            }
        }
        return key;
    }

    //这就是从properties里面通过key查value
    public String getValueFromMap(String key) {
        String value = properties.get(key);
        return Objects.requireNonNull(value, "Property '" + key + "' not found.");
    }

    //这个方法就是来解析@Value(.......) 省略号的部分 形如${abc.xyz:defaultValue} 我们最终要的就是那个要注入的value
    public PropertyExpr parse(String value) {
        if (value.startsWith("${") && value.endsWith("}")) {
            //根据spring的逻辑 找到第一个冒号即为特殊冒号 后面皆是default的东西
            int index = value.indexOf(":");
            if (index != -1) {
                String key = value.substring(2, index);
                String defaultValue = value.substring(index + 1, value.length() - 1);
                return new PropertyExpr(key, defaultValue);
            } else {
                String key = value.substring(2, value.length() - 1);
                String defaultValue = null;
                return new PropertyExpr(key, null);
            }
        }
        return null;
    }

    //就像这样 @Value(...) Integer var;
    public <T> T getProperty(String key, Class<T> targetType) {
        String value = getProperty(key);
        //获得的肯定都是String类型 接下来就是考虑怎么把String转换成指定类型
        T t = (T) doConvert(targetType,value);
        return t;
    }
    public Object doConvert(Class targetType, String value){
        Function<String, Object> fn = this.converts.get(targetType);
        if (fn == null) {
            throw new IllegalArgumentException("Unsupported value type: " + targetType.getName());
        }
        return fn.apply(value);
    }
}

record PropertyExpr(String key, String defaultValue) {
}
