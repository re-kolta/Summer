package com.example.impl.mvc;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.TimeZone;

public class FreeMarkerViewResolver implements ViewResolver{
    //模版存放的路径 要去这个路径下面找 文件夹路径
    final String templatePath;
    //模版编码格式
    final String templateEncoding;
    //container上下文
    final ServletContext context;

    Configuration configuration;

    public FreeMarkerViewResolver(ServletContext servletContext, String templatePath, String templateEncoding) {
        this.context = servletContext;
        this.templatePath = templatePath;
        this.templateEncoding = templateEncoding;
    }


    @Override
    public void init() {
        //创建configuration
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setTemplateLoader(new WebappTemplateLoaderForJakarta(this.context, this.templatePath));
        // Recommended settings for new projects:
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setFallbackOnNullLoopVariable(false);
        cfg.setSQLDateAndTimeTimeZone(TimeZone.getDefault());
        this.configuration = cfg;

    }

    @Override
    public void render(String viewName, Map<String, Object> model, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //拿到模版
        Template template = null;
        template = this.configuration.getTemplate(viewName);
        PrintWriter writer = resp.getWriter();
        try {
            template.process(model,writer);
        } catch (TemplateException e) {
            throw new RuntimeException(e);
        }
        writer.flush();
    }
}
