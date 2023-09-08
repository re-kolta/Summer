package com.example.impl.mvc;

import com.example.impl.PropertyResolver;
import com.example.impl.ProxyResolver;
import com.example.impl.SummerAnnotationApplicationContext;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRegistration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;
import java.util.Properties;

public class WebUtil {
    //向tomcat里面添加一个diapatcher
    public static void initDispatcherServlet(ServletContext servletContext,PropertyResolver propertyResolver){
        //不明白为什么要用按个applicationUtil拿创建好的容器
        DispatcherServlet dispatcherServlet = new DispatcherServlet((SummerAnnotationApplicationContext) servletContext.getAttribute("context"),propertyResolver);
        var dispatcher = servletContext.addServlet("dispatcher", dispatcherServlet);
        //设置好这个之后 dispatcher就会接管所有的/*的请求
        dispatcher.addMapping("/");
        dispatcher.setLoadOnStartup(0);
    }
    public static PropertyResolver createPropertyResolver() throws IOException {
        //拿到配置的路径
        String configPath = "/application.properties";
        Properties properties = new Properties();
        //好像要拿到文件流
        properties.load(getPropStream(configPath));
        PropertyResolver propertyResolver = new PropertyResolver(properties);
        return propertyResolver;
    }
    public static InputStream getPropStream(String path){
        if(path.startsWith("/")){
            path = path.substring(1);
        }
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if(Objects.isNull(loader)){
            loader = WebUtil.class.getClassLoader();
        }
        InputStream resourceAsStream = loader.getResourceAsStream(path);
        return resourceAsStream;
    }
}
