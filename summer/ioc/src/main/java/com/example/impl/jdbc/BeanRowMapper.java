package com.example.impl.jdbc;

import java.lang.reflect.*;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class BeanRowMapper<T> implements RowMapper<T>{
    Class<T> clazz;
    Constructor<T> constructor;
    Map<String, Field> fields = new HashMap<>();
    Map<String, Method> methods = new HashMap<>();

    //要创建一个该Class的对应关系
    public BeanRowMapper(Class<T> clazz){
        this.clazz = clazz;
        try {
            //最终应该还是要返回一个对象的
            this.constructor = clazz.getConstructor();
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        //包含父类？ 全是public
        for(Field f:clazz.getFields()){
            String name = f.getName();
            this.fields.put(name,f);
        }
        for(Method m:clazz.getMethods()){
            //获取set方法 将被set的变量设为map的key 找只有一个参数的方法
            Parameter[] parameters = m.getParameters();
            if(parameters.length == 1){
                String methodName = m.getName();
                if(methodName.length() >= 4&&methodName.startsWith("set")){
                    String key = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
                    this.methods.put(key,m);
                }
            }
        }
    }
    @Override
    public T mapRow(ResultSet rs, int rowNum) throws SQLException {
        T bean = null;
        try {
            bean = this.constructor.newInstance();
            ResultSetMetaData metaData = rs.getMetaData();
            int columns = metaData.getColumnCount();
            for (int i = 0; i <= columns; i++) {
                String label = metaData.getColumnLabel(i);
                //这个可能就是变量名
                Method method = this.methods.get(label);
                if(Objects.isNull(method)){
                    //这里可能就是没写set方法的逻辑
                    //但是这样的话 类变量名 列名的关系是什么样的呢
                    Field field = this.fields.get(label);
                    if(!Objects.isNull(field)){
                        field.set(bean,rs.getObject(label));
                    }
                }else {
                    method.invoke(bean,rs.getObject(label));
                }
            }
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return bean;
    }
    //拿到class 获得里面的具体信息

}
