package com.example.impl.mvc;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ModelAndView {
    private String view;
    private Map<String,Object> model = new HashMap<>();
    int status;

    public ModelAndView(){

    }


    public void addModels(Map<String,Object> models){

        this.model.putAll(models);
    }
    public void addModel(String key,Object value){
        this.model.put(key,value);
    }
    public Map<String,Object> getModel(){
        return this.model;
    }
    public String getView(){
        return this.view;
    }
    public int getStatus(){
        return this.status;
    }
}
