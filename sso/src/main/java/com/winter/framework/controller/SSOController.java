package com.winter.framework.controller;

import com.winter.framework.base.BaseController;
import com.winter.framework.base.WebResult;
import com.winter.framework.base.WebResultConstant;
import com.winter.framework.shiro.session.PMISession;
import com.winter.framework.shiro.session.PMISessionDao;
import com.winter.framework.utils.RedisUtil;
import com.winter.framework.utils.StringUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.LockedAccountException;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import redis.clients.jedis.Jedis;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.util.*;

/**
 * Created by winter on 2021/3/14
 */
@Controller
@RequestMapping("/sso")
public class SSOController extends BaseController {
    private static final Logger logger = LoggerFactory.getLogger(SSOController.class);
    // sso服务器用户信息
    private final static String PMI_SHIRO_USER = "pmi-shiro-user";

    // sso服务器授权令牌
    private final static String PMI_SERVER_CODE = "pmi-server-code";

    @Autowired
    PMISessionDao pmiSessionDao;

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
        Subject subject = SecurityUtils.getSubject();
        Session session = subject.getSession();
        String serverSessionId = session.getId().toString();
        //判断是否已登录,如果已登录,则回跳
        String code = RedisUtil.get(PMI_SERVER_CODE + "-" + serverSessionId);
        String userName = (String) subject.getPrincipal();
        //code校验值
        if(StringUtils.isNotBlank(code)) {
            //回跳
            String backUrl = request.getParameter("backUrl");
            if (StringUtils.isBlank(backUrl)) {
                backUrl = "/";
            } else {
                if (backUrl.contains("?")) {
                    backUrl += "&pmi_code=" + code + "&pmi_username=" + userName;
                } else {
                    backUrl += "?pmi_code=" + code + "&pmi_username=" + userName;
                }
            }
            logger.info("认证中心账号通过,带code回跳: {}", backUrl);
            return "redirect:" + backUrl;
        }

        return "/sso/login";
    }

    @RequestMapping(value = "/login", method = RequestMethod.POST)
    @ResponseBody
    /**
     * 此处有以下可能:
     * 1.用户首次登录
     * 2.用户非首次登录,来自同一台机器
     * 3.用户非首次登录，来自不同机器
     */
    public Object login(HttpServletRequest request, HttpServletResponse response, ModelMap modelMap) {
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
        Session session =  subject.getSession();
        String sessionId = session.getId().toString();
        //判断是否已登录,如果已登录,则回跳,防止重复登录,同时需判断，是否为同一IP，如果不为同一IP,需删除原会话，重新登录
        String oldSessionId = RedisUtil.get(PMI_SHIRO_USER + "-" + userName);
        if(!StringUtils.isBlank(oldSessionId) && ! sessionId.equals(oldSessionId)){
            pmiSessionDao.deleteOldSession(oldSessionId);
        }

        if(StringUtils.isBlank(oldSessionId) || (StringUtils.isNotBlank(oldSessionId) && !sessionId.equals(oldSessionId))) {
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
            //更新session状态
            //全局会话sessionID列表,供会话管理
            RedisUtil.set(PMI_SHIRO_USER + "-" + userName,sessionId);
            //code校验值,目前以server的sessionId作为校验值
            RedisUtil.set(PMI_SERVER_CODE + "-" + sessionId, sessionId, (int)subject.getSession().getTimeout() / 1000);
            //更新会话状态
            pmiSessionDao.updateStatus(sessionId, PMISession.OnlineStatus.on_line);
        }

        //回跳登录前地址
        String backUrl = request.getParameter("backUrl");
        if(StringUtils.isNotBlank(sessionId)) {
            if (backUrl.contains("?")) {
                backUrl += "&pmi_code=" + sessionId + "&pmi_username=" + userName;
            } else {
                backUrl += "?pmi_code=" + sessionId + "&pmi_username=" + userName;
            }
        }
        if(StringUtils.isBlank(backUrl)) {
            backUrl = request.getContextPath();
            WebResult webResult = new WebResult(WebResultConstant.SUCCESS, backUrl);
            return webResult;
        } else {
            WebResult webResult = new WebResult(WebResultConstant.SUCCESS, backUrl);
            return webResult;
        }
    }

    @RequestMapping(value = "/code", method = RequestMethod.POST)
    @ResponseBody
    public Object code(HttpServletRequest request) {
        String codeParam = request.getParameter("code");
        String code = RedisUtil.get(PMI_SERVER_CODE + "-" + codeParam);
        if(StringUtils.isBlank(codeParam) || !codeParam.equals(code)){
            new WebResult(WebResultConstant.FAILED, "无效code");
        }
        return new WebResult(WebResultConstant.SUCCESS, code);
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

