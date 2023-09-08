package com.example.impl.mvc;

import com.example.impl.PropertyResolver;
import com.example.impl.SummerAnnotationApplicationContext;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

import java.io.IOException;

public class ContextLoaderListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        //获取tomcat的context
        ServletContext servletContext = sce.getServletContext();
        servletContext.setRequestCharacterEncoding("UTF-8");
        servletContext.setResponseCharacterEncoding("UTF-8");
        //要指定配置类启动 所以我们要拿到配置类
        Object bootConfig = servletContext.getInitParameter("bootConfig");
        //创建容器
        SummerAnnotationApplicationContext context = null;
        try {
            context = createContext(bootConfig.getClass());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        //创建PropertyResolver
        PropertyResolver propertyResolver;
        try {
            propertyResolver = WebUtil.createPropertyResolver();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //把ioc容器在tomcat的上下文中创建一个映射
        servletContext.setAttribute("summerContext", context);
        //接下来要让dispatcher路由分发器来根据不同的路径处理 本质上它是一个servlet
        //所以我们要让这个dispatcher持有ioc容器
        //但是我们应该不能把创建dispatcher的逻辑放在这个listener里面 可能会有再创建的逻辑？？
        //初始化DispatcherServlet
        WebUtil.initDispatcherServlet(servletContext, propertyResolver);
    }
    public SummerAnnotationApplicationContext createContext(Class clazz) throws Exception {
        //需要AppConfig.class
        return new SummerAnnotationApplicationContext(clazz,null);

    }

}

