package com.example.impl;

import com.example.impl.Util.ClassUtil;
import com.example.impl.annotation.*;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class SummerAnnotationApplicationContext {
    private Class configClazz;
    private ArrayList<String> allClass = new ArrayList<>();
    private ConcurrentHashMap<String,BeanDefinition> beanInfo;
    private ClassLoader classLoader = SummerComponent.class.getClassLoader();
    public SummerAnnotationApplicationContext(Class configClass) throws IOException, ClassNotFoundException, NoSuchMethodException {
        this.configClazz = configClass;
        //扫描带ComponentScan的类，然后扫描对应的包下的所有类
        scan(configClazz);
        //排除所有类中没有声明要被容器管理的类
        excludeNoComponent();
        //提取希望被容器管理的类的信息 包括component和bean
        generateBeanInfo();
        System.out.println(beanInfo.get("fruit"));
    }
    //扫描类定义
    private void scan(Class configClazz) throws IOException {
        if (configClazz.isAnnotationPresent(SummerComponentScan.class)) {
            //添加了@Componentscan注解
            SummerComponentScan annotation = (SummerComponentScan) configClazz.getAnnotation(SummerComponentScan.class);
            String basePackage = annotation.value();
            String basePath = basePackage.replace(".","/");
            //获取path的长度方便后期把前面的路径截掉只剩全限命名里面的部分
            //    /Users/lmk/IdeaProjects/summer/ioc/target/classes/com/example/service/ 此时我们要把com之前的长度计算出来
            int basePathLength = basePath.length();
            //要获取classPath
            URL resource = classLoader.getResource(basePath);
            File file = new File(resource.getFile());
            //这就是前面没用的部分的长度
            int toBeCut = file.toPath().toString().length() - basePathLength;
            Files.walk(file.toPath()).filter(Files::isRegularFile).forEach(
                    files->{
                        String path = files.toString();
                        //扫描该"文件夹"下所有的后缀是class的文件
                        if(path.endsWith(".class")){
                            String unHandledName = path.substring(toBeCut,path.length() - 6);
                            String handledName = unHandledName.replace("/" , ".");
                            allClass.add(handledName);
                        }
                    }
            );
        }
    }
    public Object getBean(String beanName){
        return null;
    }
    public void excludeNoComponent() throws ClassNotFoundException {
        ArrayList<String> tempAllClass = new ArrayList<>();
        for (String s:allClass){
            Class var1 = Class.forName(s);
            if(ClassUtil.hasAnnotation(var1, SummerComponent.class)){
                tempAllClass.add(s);
            }
        }
        allClass = tempAllClass;
    }
    public void generateBeanInfo() throws NoSuchMethodException {
        beanInfo = new ConcurrentHashMap<String,BeanDefinition>();
        for (String className : allClass){
            Class clazz = null;
            try {
                clazz = Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            //把它本身放到容器里面 注意此时className是类似于com.balabala.balala 这个样子的  名字首字母小写
            String beanName = ClassUtil.getBeanName(clazz);
            //注意这一步也会把Configuration也加入，因为它包含着component 下面这个方法会循环获取
            if(ClassUtil.hasAnnotation(clazz,SummerComponent.class)){
                //component priority 因为有方法注解和类注解之分 这里是类注解
                int order = ClassUtil.getClassOrder(clazz);
                //component primary
                BeanDefinition beanDefinition = new BeanDefinition(beanName,
                        clazz,clazz.getConstructor(),order,false,null,null,null,null);
                //创建类名-类定义的映射
                beanInfo.put(beanName,beanDefinition);
            }
            if (ClassUtil.hasAnnotation(clazz, SummerConfiguration.class)){ //包含了Configuration
                scanBeanAnno(beanInfo,beanName,clazz);
            }
        }

    }
    public void scanBeanAnno(ConcurrentHashMap<String,BeanDefinition> beanInfo,String beanName,Class clazz){
        Method[] declaredMethods = clazz.getDeclaredMethods();
        for (Method method : declaredMethods){
            //遍历所有的方法 查找存在@bean注解的方法
            if(ClassUtil.hasAnnotation(clazz, SummerBean.class)){
                //如果是bean的话需要传入1.bean自己定义的容器内名字2.他所在的那个类 工厂本身的名字个人感觉可有可无
                BeanDefinition beanDefinition = new BeanDefinition(method.getName(),clazz,beanName,
                        method,ClassUtil.getMethodOrder(method),false,null,null,null,null);
                //方法名-返回类型定义映射
                beanInfo.put(method.getName(),beanDefinition); //至此component和bean信息全部都加载到beanInfo中
            }
        }
    }
    //创建bean 该项目创建容器时就把bean都创建好什么时候用什么时候拿 而不是用的时候再创建
    public void createBeanAsEarlySingleton(){
        //首先是处理构造器注入和工厂注入 这是强依赖 无法解决循环依赖问题 如果出现循环依赖就应该报错停止运行
        //例如 @bean里面返回的那个对象的创建需要另一个类的对象 或者@component里面创建该类的构造方法需要另一个类的对象
        //先创建@Configuration下面的bean
        for (BeanDefinition value : beanInfo.values()) {
            Class clazz = value.getBeanClass();
            //value对应的是一个bean的定义 里面可能有重复的 因为@Configuration那个类也会有一个defination 并且clazz和他里面的bean一样
            //当然也可以不从这里判断 因为最终肯定会拿构造方法 所以可在创建的过程中判断构造方法是否为空
            if(ClassUtil.hasAnnotation(clazz, SummerConfiguration.class) && !Objects.isNull(value.getFactoryMethod())){
                doCreateBean(value);
            }
        }
    }
    public void doCreateBean(BeanDefinition beanDefinition){
        //先检查是否有循环依赖
        //循环依赖这里有些门道 cpu给干烧了
        //先创建有@configuration的类 如果构造方法里面还有别的
    }
}
