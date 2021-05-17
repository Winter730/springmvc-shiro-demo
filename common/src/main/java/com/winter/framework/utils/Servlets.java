package com.winter.framework.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * Servlet工具类
 */
public class Servlets {
    protected static Logger logger = LoggerFactory.getLogger(Servlets.class);

    public static HttpServletRequest getRequest() {
        try {
            return ((ServletRequestAttributes)RequestContextHolder.getRequestAttributes()).getRequest();
        } catch (Exception e) {
            return null;
        }
    }

}