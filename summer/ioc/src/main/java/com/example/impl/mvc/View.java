package com.example.impl.mvc;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;

public interface View {

    default String getContentType() {
        return null;
    }

    void render( Map<String, Object> model, HttpServletRequest request, HttpServletResponse response) throws Exception;
}
