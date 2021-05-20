package com.winter.framework.base;

import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.session.InvalidSessionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Created by winter on 2021/3/11
 */
public abstract class BaseController {
    protected static final Logger logger = LoggerFactory.getLogger(BaseController.class);

    @ExceptionHandler
    public String exceptionHandler(HttpServletRequest request, HttpServletResponse response, Exception exception) {
        logger.error("统一异常处理:" ,exception);
        request.setAttribute("ex" ,exception);
        if (null != request.getHeader("X-Requested-With") && "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
            request.setAttribute("requestHeader", "ajax");
        }
        // shiro没有权限异常
        if (exception instanceof UnauthorizedException) {
            return "/403";
        }
        // shiro会话已过期异常
        if (exception instanceof InvalidSessionException) {
            return "/error";
        }
        return "/error";
    }

}
