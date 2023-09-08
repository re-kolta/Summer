package com.example.impl.mvc;

import com.example.impl.BeanDefinition;
import com.example.impl.PropertyResolver;
import com.example.impl.SummerAnnotationApplicationContext;
import com.example.impl.annotation.SummerController;
import com.example.impl.annotation.SummerGetMapping;
import com.example.impl.annotation.SummerPostMapping;
import com.example.impl.annotation.SummerRestController;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class DispatcherServlet extends HttpServlet {

    List<Dispatcher> getDispatchers = new ArrayList<>();
    List<Dispatcher> postDispatchers = new ArrayList<>();
    String staticResource = "/static";
    String icon = "/icon";

    PropertyResolver propertyResolver = null;
    ViewResolver viewResolver;

    //ioc容器得持有
    private SummerAnnotationApplicationContext context;
    public DispatcherServlet(SummerAnnotationApplicationContext context, PropertyResolver propertyResolver) {
        this.context = context;
        this.propertyResolver = propertyResolver;
        if(!staticResource.endsWith("/")){
            this.staticResource = this.staticResource + "/";
        }
        try {
            this.viewResolver = (ViewResolver) context.findBeanDefinitionByType(ViewResolver.class).getInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    //我们好像需要写一个init 他就是会被容器调用？？
    @Override
    public void init() throws ServletException{
        //获取所有的beanDefinition
        List<BeanDefinition> beanDefinitions = context.findBeanDefinitionsByType(Object.class);
        for(BeanDefinition beanDefinition:beanDefinitions){
            Class clazz = beanDefinition.getBeanClass();
            Object o = beanDefinition.getInstance();
            SummerController controller = (SummerController) clazz.getAnnotation(SummerController.class);
            SummerRestController restController = (SummerRestController) clazz.getAnnotation(SummerRestController.class);
            //这里不知道Spring会怎么处理这样的问题：一个Controller 一个Configuration返回的Controller。。。
            if(!Objects.isNull(controller)&&!Objects.isNull(restController)){
                throw new ServletException("controller上不能同时被@Controller和@RestController注解");
            }
            if(Objects.isNull(controller)){
                //rest format
                //也不设置RequestMapping了
                geneDispatcher(o,true,beanDefinition.getName());
            }
        }
    }
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //收到get请求 解析
        //uri就是后面的路径
        String uri = req.getRequestURI();
        if(uri.equals(icon)||uri.startsWith(staticResource)){
            //静态资源 get没有参数
            handleResource(uri,req,resp);
        }else {
            //controller
            try {
                handleController(uri,req,resp);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 根据容器内bean生成Dispatcher
     * @param o controller类
     * @param isRest 是否被rest修饰
     */
    public void geneDispatcher(Object o,boolean isRest,String beanName) throws ServletException {
        //controller里面涉及继承怎么办？ aop这里不清楚
        Method[] declaredMethods = o.getClass().getDeclaredMethods();
        Dispatcher dispatcher = null;
        for (Method method:declaredMethods){
            SummerGetMapping getAnno = method.getAnnotation(SummerGetMapping.class);
            SummerPostMapping postAnno = method.getAnnotation(SummerPostMapping.class);
            if(!Objects.isNull(getAnno)&&!Objects.isNull(postAnno)){
                throw new ServletException(method + "同时使用getMapping和postMapping");
            }
            if(Objects.isNull(postAnno)){
                //get
                String path = getAnno.value();
                dispatcher = new Dispatcher(isRest, path, o, method);
                this.getDispatchers.add(dispatcher);
            }
            if(Objects.isNull(getAnno)){
                //post
                String path = postAnno.value();
                dispatcher = new Dispatcher(isRest,path,o,method);
                this.postDispatchers.add(dispatcher);
            }
            //父类处理 如果有继承关系
        }
    }
    public void handleResource(String uri,HttpServletRequest request,HttpServletResponse response) throws IOException {
        ServletContext servletContext = request.getServletContext();
        InputStream inputStream = servletContext.getResourceAsStream(uri);
        if(Objects.isNull(inputStream)){
            response.sendError(404,"NOT FOUND");
        }else {
            String file = uri;
            int index = uri.lastIndexOf("/");
            if(index >= 0){
                file = uri.substring(index +1 );
            }
            String mime = servletContext.getMimeType(file);
            if(mime == null){
                mime = "application/octet-stream";
            }
            response.setContentType(mime);
            ServletOutputStream outputStream = response.getOutputStream();
            inputStream.transferTo(outputStream);
            outputStream.flush();
        }

    }
    public void handleController(String uri,HttpServletRequest request,HttpServletResponse response) throws Exception {
        //检索dispatcher
        for (Dispatcher dispatcher:getDispatchers){
            //匹配URL 这些工作让dispatcher做 这里就是处理回来的信息
            DispatcherResult process = dispatcher.process(uri, request, response);
            if(process.hasProcessed()){
                //处理过
                //获取controller方法的结果 再看返回json还是页面还是啥。。
                Object r = process.result();
                if(dispatcher.isRest){
                    //json写到response
                    response.setContentType("application/json");
                    //String的话就是纯字符 可以直接用Writer 如果是byte的话得用字节流？？大概是这个意思
                    if(r instanceof String s){
                        PrintWriter writer = response.getWriter();
                        writer.write(s);
                        writer.flush();
                    }
                    else if(r instanceof byte[] s){
                        ServletOutputStream outputStream = response.getOutputStream();
                        outputStream.write(s);
                        outputStream.flush();
                    }else if(!dispatcher.isVoid){
                        PrintWriter writer = response.getWriter();
                        JsonUtil.writeJson(writer,r);
                        writer.flush();
                    }else {
                        throw new Exception("something wrong when transfer return value to json");
                    }
                }else {
                    //mvc
                    response.setContentType("text/html");
                    //一共需要处理两种情况 String ModelAndView 别的抛出错误
                    if(r instanceof String s){
                        //forward redirect view
                        if(s.startsWith("forward:")){
                            request.getRequestDispatcher(s.substring(8)).forward(request,response);
                        } else if (s.startsWith("redirect:")) {
                            response.sendRedirect(s.substring(9));
                        }else{
                            //到指定页面 就是执行这个方法 让response的body里面填上这个页面的流文件
                            this.viewResolver.render(s, new HashMap<>(), request, response);
                        }
                    }else if(r instanceof ModelAndView mv){
                        String view = mv.getView();
                        if (view.startsWith("redirect:")) {
                            // send redirect:
                            response.sendRedirect(view.substring(9));
                        } else {
                            this.viewResolver.render(view, mv.getModel(), request, response);
                        }
                    }
                }
            }

        }
    }


}
