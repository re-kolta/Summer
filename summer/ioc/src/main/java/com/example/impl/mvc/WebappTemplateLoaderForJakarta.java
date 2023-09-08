package com.example.impl.mvc;

import freemarker.cache.TemplateLoader;
import freemarker.cache.TemplateLoaderUtils;
import freemarker.cache.URLTemplateSource;
import freemarker.template.utility.CollectionUtils;
import freemarker.template.utility.NullArgumentException;
import freemarker.template.utility.StringUtil;
import jakarta.servlet.ServletContext;

import java.io.*;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;

//原本的freemarker不支持 Jakarta之后的类 只支持javax 所以我们重新写一个适配Jakarta的
public class WebappTemplateLoaderForJakarta implements TemplateLoader {
    private final ServletContext servletContext;
    private final String subdirPath;
    private Boolean urlConnectionUsesCaches;
    private boolean attemptFileAccess;

    public WebappTemplateLoaderForJakarta(ServletContext servletContext) {
        this(servletContext, "/");
    }

    public WebappTemplateLoaderForJakarta(ServletContext servletContext, String subdirPath) {
        this.attemptFileAccess = true;
        NullArgumentException.check("servletContext", servletContext);
        NullArgumentException.check("subdirPath", subdirPath);
        subdirPath = subdirPath.replace('\\', '/');
        if (!subdirPath.endsWith("/")) {
            subdirPath = subdirPath + "/";
        }

        if (!subdirPath.startsWith("/")) {
            subdirPath = "/" + subdirPath;
        }

        this.subdirPath = subdirPath;
        this.servletContext = servletContext;
    }

    public Object findTemplateSource(String name) throws IOException {
        String fullPath = subdirPath + name;

        try {
            String realPath = servletContext.getRealPath(fullPath);
            if (realPath != null) {
                File file = new File(realPath);
                if (file.canRead() && file.isFile()) {
                    return file;
                }
            }
        } catch (SecurityException e) {
            ;// ignore
        }
        return null;
    }

    public long getLastModified(Object templateSource) {
        if (templateSource instanceof File) {
            return ((File) templateSource).lastModified();
        }
        return 0;
    }

    public Reader getReader(Object templateSource, String encoding) throws IOException {
        return templateSource instanceof File ? new InputStreamReader(new FileInputStream((File)templateSource), encoding) : new InputStreamReader(((URLTemplateSource)templateSource).getInputStream(), encoding);
    }

    public void closeTemplateSource(Object templateSource) throws IOException {

    }

    public Boolean getURLConnectionUsesCaches() {
        return this.urlConnectionUsesCaches;
    }

    public void setURLConnectionUsesCaches(Boolean urlConnectionUsesCaches) {
        this.urlConnectionUsesCaches = urlConnectionUsesCaches;
    }


    public boolean getAttemptFileAccess() {
        return this.attemptFileAccess;
    }

    public void setAttemptFileAccess(boolean attemptLoadingFromFile) {
        this.attemptFileAccess = attemptLoadingFromFile;
    }
}
