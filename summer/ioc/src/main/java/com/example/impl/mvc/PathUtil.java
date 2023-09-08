package com.example.impl.mvc;

import jakarta.servlet.ServletException;

import java.util.regex.Pattern;

public class PathUtil {
    public static Pattern getUrlPattern(String url) throws ServletException {
        String regPath = url.replaceAll("\\{([a-zA-Z][a-zA-Z0-9]*)\\}", "(?<$1>[^/]*)");
        if (regPath.indexOf('{') >= 0 || regPath.indexOf('}') >= 0) {
            throw new ServletException("Invalid path: " + url);
        }
        return Pattern.compile("^" + regPath + "$");
    }
}
