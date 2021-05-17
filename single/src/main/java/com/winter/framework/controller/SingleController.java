package com.winter.framework.controller;

import com.winter.framework.base.BaseController;
import com.winter.framework.base.WebResult;
import com.winter.framework.base.WebResultConstant;
import com.winter.framework.utils.PropertiesFileUtil;
import com.winter.framework.utils.StringUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.LockedAccountException;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.util.Map;

/**
 * 单机登录,非会话登录
 * Created by winter on 2021/4/24
 */
@Controller
@RequestMapping("/sso")
public class SingleController extends BaseController {
    private static final Logger logger = LoggerFactory.getLogger(SingleController.class);


    @RequestMapping(value = "/index", method = RequestMethod.GET)
    public String index(HttpServletRequest request) throws Exception{
        String appId = request.getParameter("appid");
        String backUrl = request.getParameter("backUrl");
        if(StringUtils.isBlank(appId)) {
            throw new RuntimeException("无效访问");
        }
        return "redirect:/sso/login?backUrl=" + URLEncoder.encode(backUrl, "UTF-8");
    }

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String login(HttpServletRequest request) {
        return "/sso/login";
    }

    @RequestMapping(value = "/login", method = RequestMethod.POST)
    @ResponseBody
    public Object login(HttpServletRequest request, HttpServletResponse response, ModelMap modelMap) {
        Map<String, String[]> map = request.getParameterMap();
        String userName = request.getParameter("username");
        String password = request.getParameter("password");
        String rememberMe = request.getParameter("rememberMe");
        if (StringUtils.isBlank(userName)) {
            return new WebResult(WebResultConstant.EMPTY_USERNAME, "帐号不能为空！");
        }
        if(StringUtils.isBlank(password)) {
            return new WebResult(WebResultConstant.EMPTY_PASSWORD, "密码不能为空！");
        }
        Subject subject = SecurityUtils.getSubject();
        // 使用Shiro认证登录
        UsernamePasswordToken usernamePasswordToken = new UsernamePasswordToken(userName, password);
        try {
            if(BooleanUtils.toBoolean(rememberMe)) {
                usernamePasswordToken.setRememberMe(true);
            } else {
                usernamePasswordToken.setRememberMe(false);
            }

            subject.login(usernamePasswordToken);
        } catch (UnknownAccountException e) {
            return new WebResult(WebResultConstant.INVALID_USERNAME, "帐号不存在！");
        } catch (IncorrectCredentialsException e) {
            return new WebResult(WebResultConstant.INVALID_PASSWORD, "密码错误！");
        } catch (LockedAccountException e) {
            return new WebResult(WebResultConstant.INVALID_ACCOUNT, "帐号已锁定！");
        }
        //回跳登录前地址
        String backUrl = request.getParameter("backUrl");
        if(StringUtils.isBlank(backUrl)) {
            backUrl = request.getContextPath();
            WebResult webResult = new WebResult(WebResultConstant.SUCCESS, backUrl);
            return webResult;
        } else {
            WebResult webResult = new WebResult(WebResultConstant.SUCCESS, backUrl);
            return webResult;
        }
    }

    @RequestMapping(value = "/logout", method = RequestMethod.GET)
    public String logout(HttpServletRequest request) {
        //shiro退出登录
        SecurityUtils.getSubject().logout();
        //跳回原地址
        String redirectUrl = request.getHeader("Referer");
        if(null == redirectUrl) {
            redirectUrl = "/";
        }
        return "redirect:" + redirectUrl;
    }
}

