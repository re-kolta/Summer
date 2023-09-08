package com.example.impl;

import com.example.impl.Util.ApplicationContextUtils;
import com.example.impl.Util.ClassUtil;
import com.example.impl.annotation.*;
import com.sun.jdi.Value;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

//8.21 我们要实现BeanPostProcessor 故而要修改创建bean的代码
//创建流程 configuration基类 -> BeanPostProcessor -> 基础bean 因为BPP是@bean 得在configuration创建之后才能创建
//所以必须在@Con..后面创建 所以也就意味着不能替换Configuration 而为什么在基础bean之前创建呢 因为基础bean创建之后会遍历IOC容器找
//是否使用者提供了BPP 所以这个时候BPP一定是在容器里面的！
public class SummerAnnotationApplicationContext {
    private Class configClazz;
    private ArrayList<String> allClass = new ArrayList<>();
    private ConcurrentHashMap<String, BeanDefinition> beanInfo;
    private ClassLoader classLoader = SummerComponent.class.getClassLoader();
    private PropertyResolver resolver;
    private Set<String> checkCircle = new HashSet<>();
    private List<BeanPostProcessor> beanPostProcessors = new ArrayList<>();

    public SummerAnnotationApplicationContext(Class configClass, PropertyResolver resolver) throws Exception {
        ApplicationContextUtils.setApplicationContext(this);
        this.resolver = resolver;
        this.configClazz = configClass;
        //扫描带ComponentScan的类，然后扫描对应的包下的所有类
        scan(configClazz);
        //排除所有类中没有声明要被容器管理的类
        excludeNoComponent();
        //提取希望被容器管理的类的信息 包括component和bean
        generateBeanInfo();
        //注入强依赖
        createBeanAsEarlySingleton();
        //注入弱依赖
        inject();
        //该项目没有设置 init-method 和 destory-method
        //初始化完毕
    }

    //扫描类定义
    private void scan(Class configClazz) throws IOException {
        if (configClazz.isAnnotationPresent(SummerComponentScan.class)) {
            //添加了@Componentscan注解
            SummerComponentScan annotation = (SummerComponentScan) configClazz.getAnnotation(SummerComponentScan.class);
            String basePackage = annotation.value();
            String basePath = basePackage.replace(".", "/");
            //获取path的长度方便后期把前面的路径截掉只剩全限命名里面的部分
            //    /Users/lmk/IdeaProjects/summer/ioc/target/classes/com/example/service/ 此时我们要把com之前的长度计算出来
            int basePathLength = basePath.length();
            //要获取classPath
            URL resource = classLoader.getResource(basePath);
            File file = new File(resource.getFile());
            //这就是前面没用的部分的长度
            int toBeCut = file.toPath().toString().length() - basePathLength;
            Files.walk(file.toPath()).filter(Files::isRegularFile).forEach(
                    files -> {
                        String path = files.toString();
                        //扫描该"文件夹"下所有的后缀是class的文件
                        if (path.endsWith(".class")) {
                            String unHandledName = path.substring(toBeCut, path.length() - 6);
                            String handledName = unHandledName.replace("/", ".");
                            allClass.add(handledName);
                        }
                    }
            );
        }
    }

    public Object getBean(String beanName) {
        return null;
    }

    public void excludeNoComponent() throws ClassNotFoundException {
        ArrayList<String> tempAllClass = new ArrayList<>();
        for (String s : allClass) {
            Class var1 = Class.forName(s);
            if (ClassUtil.hasAnnotation(var1, SummerComponent.class)) {
                tempAllClass.add(s);
            }
        }
        allClass = tempAllClass;
    }

    public void generateBeanInfo() throws NoSuchMethodException {
        beanInfo = new ConcurrentHashMap<String, BeanDefinition>();
        for (String className : allClass) {
            Class clazz = null;
            try {
                clazz = Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            //把它本身放到容器里面 注意此时className是类似于com.balabala.balala 这个样子的  名字首字母小写
            String beanName = ClassUtil.getBeanName(clazz);
            //注意这一步也会把Configuration也加入，因为它包含着component 下面这个方法会循环获取
            if (ClassUtil.hasAnnotation(clazz, SummerComponent.class)) {
                //component priority 因为有方法注解和类注解之分 这里是类注解
                int order = ClassUtil.getClassOrder(clazz);
                //component primary
                BeanDefinition beanDefinition = new BeanDefinition(beanName,
                        clazz, clazz.getConstructor(), order, false, null, null, null, null);
                //创建类名-类定义的映射
                beanInfo.put(beanName, beanDefinition);
            }
            if (ClassUtil.hasAnnotation(clazz, SummerConfiguration.class)) { //包含了Configuration
                scanBeanAnno(beanInfo, beanName, clazz);
            }
        }

    }

    public void scanBeanAnno(ConcurrentHashMap<String, BeanDefinition> beanInfo, String beanName, Class clazz) {
        Method[] declaredMethods = clazz.getDeclaredMethods();
        for (Method method : declaredMethods) {
            //遍历所有的方法 查找存在@bean注解的方法
            if (ClassUtil.hasAnnotation(clazz, SummerBean.class)) {
                //如果是bean的话需要传入1.bean自己定义的容器内名字2.(他所在的那个类 工厂本身的名字个人感觉可有可无)错误 应该是这个bean的返回类型
                BeanDefinition beanDefinition = new BeanDefinition(method.getName(), method.getReturnType(), beanName,
                        method, ClassUtil.getMethodOrder(method), false, null, null, null, null);
                //方法名-返回类型定义映射
                beanInfo.put(method.getName(), beanDefinition); //至此component和bean信息全部都加载到beanInfo中
            }
        }
    }

    //创建bean 该项目创建容器时就把bean都创建好什么时候用什么时候拿 而不是用的时候再创建
    public void createBeanAsEarlySingleton() throws Exception {
        //首先是处理构造器注入和工厂注入 这是强依赖 无法解决循环依赖问题 如果出现循环依赖就应该报错停止运行
        //例如 @bean里面返回的那个对象的创建需要另一个类的对象 或者@component里面创建该类的构造方法需要另一个类的对象
        //先创建@Configuration下面的bean
        int i = 0;
        System.out.println(beanInfo.values());
        for (BeanDefinition value : beanInfo.values()) {
            Class clazz = value.getBeanClass();
            if (ClassUtil.hasAnnotation(clazz, SummerConfiguration.class)) {
                System.out.println("我被执行了" + i);
                doCreateBaseBean(value);
                i++;
            }
        }
//      注入BeanPostProcessor
        for (BeanDefinition value : beanInfo.values()) {
            if (subOfBeanPostProcessor(value)) {
                doCreateBeanPostProcessor(value);
            }
        }
        for (BeanDefinition value : beanInfo.values()) {
            if (Objects.isNull(value.getInstance())) {
                doCreateNormalBean((byte) 1,value);
            }
        }
    }

    public void doCreateBaseBean(BeanDefinition beanDefinition) throws Exception {
        //8.21 还要在这里创建的bean里面捡出来BeanPostProcessor。。。。。。。。
        //先检查是否有循环依赖
        //循环依赖这里有些门道 cpu给干烧了
        //先创建有@configuration的类 规定他的构造方法里面不能有AutoWired 本项目中规定不能有Autowired
        //为什么不能有autowired呢 因为创建类要用构造方法创建如果有autowired会把创建难度提高
        //先获取对象创建方法
        Constructor constructor = beanDefinition.getConstructor();
        //可能一个参数里面有多个注解
        Parameter[] parameters = constructor.getParameters();
        Annotation[][] parameterAnnotations = constructor.getParameterAnnotations();
        Object[] args = new Object[parameters.length];
        //把参数都获取出来 一会儿创建对象要用
        for (int i = 0; i < parameters.length; i++) {
            //这个参数 以及这个参数上的注解们
            Parameter parameter = parameters[i];
            Annotation[] thisParamAnnotations = parameterAnnotations[i];
            //注入方式 Value Autowired 有这俩咱们就得处理
            SummerValue value = (SummerValue) ClassUtil.getAnnotation(thisParamAnnotations, SummerValue.class);
            SummerAutowired autowired = (SummerAutowired) ClassUtil.getAnnotation(thisParamAnnotations, SummerAutowired.class);
            if (!Objects.isNull(autowired)) {
                throw new Exception("本ioc容器不允许在@Configuration注解的类的构造方法中注入别的bean");
            }
            if (!Objects.isNull(value) && !Objects.isNull(autowired)) {
                throw new Exception("value 和 autowired 两个注解不能同时用");
            }
            if (Objects.isNull(value) && Objects.isNull(autowired)) {
                throw new Exception("要让ioc容器管理 并且声明了要注入某个类 请声明该注入的来源");
            }
            //此时看用不用注入Value
            //获取这个Value的类型
            Class<?> type = parameter.getType();
            if (!Objects.isNull(value)) {
                //获取该参数
                args[i] = resolver.getProperty(value.value(), type);
            }
        }
        //创建对象
        Object instance = null;
        try {
            instance = beanDefinition.getConstructor().newInstance(args);
        } catch (Exception e) {
            System.out.println("something wrong when creating base bean");
        }
        if(BeanPostProcessor.class.isAssignableFrom(instance.getClass())){
            beanPostProcessors.add((BeanPostProcessor) instance);
        }
        beanDefinition.setInstance(instance);
    }

    public void doCreateBeanPostProcessor(BeanDefinition beanDefinition) throws Exception {
        //这个里面我们也不允许有autowired
        //经过前面的逻辑 被@Configuration修饰的BeanPostProcessor会被创建 下面我们只需要管@bean修饰的
        //  大概逻辑和下面的创建normalbean类似 就不修改代码了 因为一会儿还有判断autowired的东西 相当于base bean和normal bean的杂交方法Executable executable = null;
        Method method = beanDefinition.getFactoryMethod();
        Parameter[] parameters = method.getParameters();
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            //这个参数 以及这个参数上的注解们
            Parameter parameter = parameters[i];
            Annotation[] thisParamAnnotations = parameterAnnotations[i];
            //注入方式 Value Autowired 有这俩咱们就得处理
            SummerValue value = (SummerValue) ClassUtil.getAnnotation(thisParamAnnotations, SummerValue.class);
            SummerAutowired autowired = (SummerAutowired) ClassUtil.getAnnotation(thisParamAnnotations, SummerAutowired.class);
            if (!Objects.isNull(autowired)) {
                throw new Exception("本ioc容器不允许在@Configuration注解的类的构造方法中注入别的bean");
            }
            if (!Objects.isNull(value) && !Objects.isNull(autowired)) {
                throw new Exception("value 和 autowired 两个注解不能同时用");
            }
            if (Objects.isNull(value) && Objects.isNull(autowired)) {
                throw new Exception("要让ioc容器管理 并且声明了要注入某个类 请声明该注入的来源");
            }
            //此时看用不用注入Value
            //获取这个Value的类型
            Class<?> type = parameter.getType();
            if (!Objects.isNull(value)) {
                //获取该参数
                args[i] = resolver.getProperty(value.value(), type);
            }
        }
        //创建对象
        Object instance = null;
        // 用@Bean方法创建:
        Object configInstance = getBean(beanDefinition.getFactoryName());
        try {
            instance = beanDefinition.getFactoryMethod().invoke(configInstance, args);
        } catch (Exception e) {
            throw new Exception("创建BeanPostProcessor bean 出错");
        }
        //上面的 用configuration标注的 和 bean 标注的 都要加到这个大的List里面
        beanPostProcessors.add((BeanPostProcessor) instance);
        beanDefinition.setInstance(instance);

    }

    /**  关于这个funSwitch 是为了适应AOP那里的功能 因为在那里需要在beanPostprocessor里面拿到context
      *  并且要拿到handler的beanDefinition 那么可能会出现这个bean还没初始化的可能
     *  所以就设置了一个函数开关 为0的时候就证明aop里面在调用 就不让下面的beanPostprocessor逻辑
     *  再执行了 如果继续执行可能会出错 创建完之后直接检测funSwitch符合0就直接返回
     * */
    public BeanDefinition doCreateNormalBean(byte funSwitch,BeanDefinition beanDefinition) throws Exception {
        //检查循环依赖主要依靠的就是这个set 一直检查下去没有被创建过的就创建
        if (!checkCircle.add(beanDefinition.getName())) {
            //没加入成功 则证明已经有了
            throw new Exception("circular dependence detected");
        }
        // 创建方式：构造方法或工厂方法:
        Executable executable = null;
        if (beanDefinition.getFactoryName() == null) {
            // 构造器
            executable = beanDefinition.getConstructor();
        } else {
            // 纯方法
            executable = beanDefinition.getFactoryMethod();
        }
        //可能一个参数里面有多个注解
        Parameter[] parameters = executable.getParameters();
        Annotation[][] parameterAnnotations = executable.getParameterAnnotations();
        Object[] args = new Object[parameters.length];
        //获取参数相关逻辑
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Annotation[] thisParamAnnotations = parameterAnnotations[i];
            SummerValue value = (SummerValue) ClassUtil.getAnnotation(thisParamAnnotations, SummerValue.class);
            SummerAutowired autowired = (SummerAutowired) ClassUtil.getAnnotation(thisParamAnnotations, SummerAutowired.class);
            if (!Objects.isNull(value) && !Objects.isNull(autowired)) {
                throw new Exception("value 和 autowired 两个注解不能同时用");
            }
            if (Objects.isNull(value) && Objects.isNull(autowired)) {
                throw new Exception("要让ioc容器管理 并且声明了要注入某个类 请声明该注入的来源");
            }
            Class<?> type = param.getType();
            if (!Objects.isNull(value)) {
                //获取该参数
                args[i] = resolver.getProperty(value.value(), type);
            } else {
                //表明需要被注入对象 这时候就开始检查循环依赖了
                String name = autowired.name();
                //根据name找这个对应的bean
                BeanDefinition dependOnBeanDefinition = name.isEmpty() ? findBeanDefinitionByType(type) : findBeanDefinitionByName(name);
                if (Objects.isNull(dependOnBeanDefinition.getInstance())) {
                    doCreateNormalBean((byte) 1,dependOnBeanDefinition);
                } else args[i] = dependOnBeanDefinition.getInstance();
            }
        }
        //  此时创建bean
        Object instance = null;
        if (Objects.isNull(beanDefinition.getFactoryName())) {
            //构造器
            try {
                instance = beanDefinition.getConstructor().newInstance(args);
            } catch (Exception e) {
                throw e;
            }
        } else {
            //工厂方法
            try {
                //要获取这个方法所在的类的对象
                //因为我们先把Configuration对象全部创建完了 所以不可能发生要创建某个bean的时候其在的类还没被创建
                String beanName = beanDefinition.getFactoryName();
                BeanDefinition beanDefinition1 = beanInfo.get(beanName);
                instance = beanDefinition.getFactoryMethod().invoke(beanDefinition1.getInstance(), args);
            } catch (Exception e) {
                throw e;
            }
        }
        beanDefinition.setInstance(instance);
        if(funSwitch == 0){
            return beanDefinition;
        }
        //创建好bean之后执行BeanPostProcessor
        //这里的逻辑就是拿到每一个instance 然后在很多个beanPostProcessor里面匹配 全都调用一遍 看能满足谁
        //但是这里有个问题就是 如果对同一个类有多个自定义逻辑 那么先后处理逻辑是什么
        for (BeanPostProcessor beanPostProcessor : this.beanPostProcessors) {
            Object result = beanPostProcessor.postProcessBeforeInitialization(instance,beanDefinition.getName());
            if(Objects.isNull(result)){
                throw new Exception("执行postProcessBeforeInitialization返回了空对象");
            }else {
                beanDefinition.setInstance(result);
            }
        }
        return beanDefinition;
        //此时设置完 原对象都跑哪儿了？原对象还要往里面注入东西呢
        //目前发现好像是这要让用户来管理
        //用户管理也很简单 因为在这个接口的方法参数里面有一个beanName beanName唯一的 所以只要用户建立一个map就行 通过名字传回去原对象
        //好像没有选择在beandefinition里面设置proxy字段是为了多重代理？？ 多重代理可以用@Order来规定注入顺序
    }

    //获得所有是该类型子类或实现的类 接下来就是看有无primary或匹配名字
    public List<BeanDefinition> findBeanDefinitionsByType(Class<?> type) {
        Collection<BeanDefinition> values = beanInfo.values();
        List<BeanDefinition> beanDefinitions = new ArrayList<>();
        for (BeanDefinition beanDefinition : values) {
            //这个type一般是接口 然后defination里面是其子类或实现类
            if (type.isAssignableFrom(beanDefinition.getBeanClass())) {
                beanDefinitions.add(beanDefinition);
            }
        }
        return beanDefinitions;
    }

    public BeanDefinition findBeanDefinitionByType(Class<?> type) throws Exception {
        List<BeanDefinition> beanCandis = findBeanDefinitionsByType(type);
        if (beanCandis.size() == 0) {
            //证明容器里面没有 就要抛出错误
            throw new Exception("未找到相匹配的bean");
        }
        if (beanCandis.size() == 1) {
            //就这一个definition
            return beanCandis.get(0);
        }
        //筛选出来
        List<BeanDefinition> primaryBeanCandi = beanCandis.stream().filter(beanCandi -> beanCandi.isPrimary()).collect(Collectors.toList());
        if (primaryBeanCandi.size() == 1) {
            return primaryBeanCandi.get(0);
        } else throw new Exception("multi primary anno or no primary anno");
    }

    public BeanDefinition findBeanDefinitionByName(String beanName) throws Exception {
        BeanDefinition beanDefinition = beanInfo.get(beanName);
        if (Objects.isNull(beanDefinition)) {
            throw new Exception("没有命名的这个bean");
        }
        return beanDefinition;
    }

    public void inject() throws Exception {
        for (BeanDefinition value : this.beanInfo.values()) {
            injectBean(value.getBeanClass(), value);
        }
    }

    public void injectBean(Class clazz, BeanDefinition beanDefinition) throws Exception {
        //这里能决定到底set方法注入优先还是字段注入优先
        //字段 获取除继承的字段以外的所有字段
        for (Field field : clazz.getDeclaredFields()) {
            doInjectBeanByField(beanDefinition, field);
        }
        for (Method method : clazz.getDeclaredMethods()) {
            doInjectBeanByMethod(beanDefinition, method);
        }
        //获取父类 有可能父类里面写了autowired 相应的子类也要能访问到 尽管子类并没有显式声明 这就是下面代码的作用
        //为什么要找父类呢 因为代码书写简单 一个子类只有一个父类
        Class superclass = clazz.getSuperclass();
        if (!Objects.isNull(superclass)) {
            injectBean(superclass, beanDefinition);
        }

    }

    //修改对象 需要用到原对象 field.set()
    public void doInjectBeanByField(BeanDefinition beanDefinition, Field field) throws Exception {
        //先检查autowired和value
        SummerAutowired autowired = field.getAnnotation(SummerAutowired.class);
        SummerValue value = field.getAnnotation(SummerValue.class);
        //这个字段不用被spring管理
        if (Objects.isNull(autowired) && Objects.isNull(value)) {
            return;
        }
        if (!Objects.isNull(autowired) && !Objects.isNull(value)) {
            throw new Exception("字段不能同时使用value与autowired");
        }
        //字段设置为access 注入private
        field.setAccessible(true);
        String fieldName = field.getName();
        Class fieldType = field.getType();
        if (Objects.isNull(autowired)) {
            //value注入
            String s = value.value();
            Object param = resolver.getProperty(value.value(), fieldType);
            field.set(getProxiedInstance(beanDefinition), param);
        } else {
            //和上面注入强依赖逻辑相同
            String name = autowired.name();
            BeanDefinition dependOnBeanDefinition = name.isEmpty() ? findBeanDefinitionByType(fieldType) : findBeanDefinitionByName(name);
            ;
            field.set(getProxiedInstance(beanDefinition), dependOnBeanDefinition.getInstance());
        }
    }

    public void doInjectBeanByMethod(BeanDefinition beanDefinition, Method method) throws Exception {
        //这里主要是要把set方法筛出来...就是判断有几个参数 我们只处理一个的 当然一般的set方法都是单个参数的
        //也许这就是为什么get set 方法都要generate出来形成一个java pojo
        //只检查方法上了 参数上就不检查了
        SummerAutowired autowired = method.getAnnotation(SummerAutowired.class);
        SummerValue value = method.getAnnotation(SummerValue.class);
        if (Objects.isNull(autowired) && Objects.isNull(value)) {
            return;
        }
        if (!Objects.isNull(autowired) && !Objects.isNull(value)) {
            throw new Exception("字段不能同时使用value与autowired");
        }
        //获取参数
        Parameter[] parameters = method.getParameters();
        if (parameters.length > 1) {
            throw new Exception("set 方法只能有一个参数");
        }
        method.setAccessible(true);
        Object o = getProxiedInstance(beanDefinition);
        if (!Objects.isNull(autowired)) {
            //autowired注入
            String name = autowired.name();
            BeanDefinition dependOnBeanDefinition = name.isEmpty() ? findBeanDefinitionByType(parameters[0].getType()) : findBeanDefinitionByName(name);
            method.invoke(o, dependOnBeanDefinition.getInstance());
        } else {
            //value注入
            String s = value.value();
            Object param = resolver.getProperty(s, parameters[0].getType());
            method.invoke(o, param);
        }
    }

    public boolean subOfBeanPostProcessor(BeanDefinition beanDefinition) {
        if (BeanPostProcessor.class.isAssignableFrom(beanDefinition.getBeanClass())) {
            return true;
        }
        return false;
    }
    //我们需要设置一个getProxied方法 来得到被代理的对象
    public Object getProxiedInstance(BeanDefinition beanDefinition){
        List<BeanPostProcessor> reversedBeanPostProcessors = new ArrayList<>(this.beanPostProcessors);
        Collections.reverse(reversedBeanPostProcessors);
        //注意注意 这里如果要实现多重代理 要把这个存放BeanPostProcessor的List翻转过来
        //因为要先从最后一层代理 获取到上一层被代理的对象 一层一层剥开最后返回的那就是最原始的对象
        //如果不是反转过来 那就先会拿到最初的对象 再一层一层拿下去就是得到了倒数第二层被代理的对象
        Object proxied = null;
        for (BeanPostProcessor beanPostProcessor : reversedBeanPostProcessors) {
            //一定要传名字 用户会通过唯一的beanName来找到原来的对象
            proxied = beanPostProcessor.postProcessOnSetProperty(beanDefinition.getInstance(),beanDefinition.getName());;
        }
        return proxied;
    }

    public void close() {
        this.beanInfo.values().forEach(def -> {
            System.out.println(def.getName() + " has been clear");
        });
        this.beanInfo.clear();
        ApplicationContextUtils.setApplicationContext(null);
    }
    public BeanDefinition findBeanDefinition(String beanName){
        return this.beanInfo.get(beanName);

    }
}
