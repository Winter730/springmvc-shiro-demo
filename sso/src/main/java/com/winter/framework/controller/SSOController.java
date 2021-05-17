package com.winter.framework.controller;

import com.winter.framework.base.BaseController;
import com.winter.framework.base.WebResult;
import com.winter.framework.base.WebResultConstant;
import com.winter.framework.shiro.session.PMISession;
import com.winter.framework.shiro.session.PMISessionDao;
import com.winter.framework.utils.RedisUtil;
import com.winter.framework.utils.SerializableUtil;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Created by winter on 2021/3/14
 */
@Controller
@RequestMapping("/sso")
public class SSOController extends BaseController {
    private static final Logger logger = LoggerFactory.getLogger(SSOController.class);
    // 会话key
    private final static String PMI_SHIRO_SESSION_ID = "pmi-shiro-session-id";
    //全局会话Key
    private final static String PMI_SERVER_SESSION_ID = "pmi-server-session-id";
    //全局会话key列表
    private final static String PMI_SERVER_SESSION_IDS = "pmi-server-session-ids";
    //code key
    private final static String PMI_SERVER_CODE= "pmi-server-code";

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
        String code = RedisUtil.get(PMI_SERVER_SESSION_ID + "_" + serverSessionId);
        //code校验值
        if(StringUtils.isNotBlank(code)) {
            //回跳
            String backUrl = request.getParameter("backUrl");
            if (StringUtils.isBlank(backUrl)) {
                backUrl = "/";
            } else {
                if (backUrl.contains("?")) {
                    backUrl += "&pmi_code=" + code;
                } else {
                    backUrl += "?pmi_code=" + code;
                }
            }
            logger.debug("认证中心账号通过,带code回跳: {}", backUrl);
            return "redirect:" + backUrl;
        }

        return "/sso/login";
    }

    @RequestMapping(value = "/login", method = RequestMethod.POST)
    @ResponseBody
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
        Session session = subject.getSession();
        String sessionId = session.getId().toString();
        //判断是否已登录,如果已登录,则回跳,防止重复登录
        String hasCode = RedisUtil.get(PMI_SERVER_SESSION_ID + "_" + sessionId);
        //code校验值
        if(StringUtils.isBlank(hasCode)) {
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
           // pmiSessionDao.updateStatus(sessionId, PMISession.OnlineStatus.on_line);
            //全局会话sessionID列表,供会话管理
            Jedis shareJedis = RedisUtil.getJedis();
            try {
                shareJedis.lpush(PMI_SERVER_SESSION_IDS, sessionId);
            } finally {
                shareJedis.close();
            }

            //默认验证账号密码正确,创建code
            String code = UUID.randomUUID().toString();
            //全局会话的code
            RedisUtil.set(PMI_SERVER_SESSION_ID + "_" + sessionId, code, (int)subject.getSession().getTimeout() / 1000);
            //code校验值
            RedisUtil.set(PMI_SERVER_CODE + "_" + code, code, (int)subject.getSession().getTimeout() / 1000);
            //登录成功，会话持久化(会话信息应包含sessionId以及用户信息，以确保用户在其他地方登录后，会话应当失效)
            RedisUtil.set(PMI_SHIRO_SESSION_ID + "_" + userName, sessionId, (int)session.getTimeout() / 1000);
            hasCode = code;
        }
        //回跳登录前地址
        String backUrl = request.getParameter("backUrl");
        if(StringUtils.isNotBlank(hasCode)) {
            if (backUrl.contains("?")) {
                backUrl += "&pmi_code=" + hasCode;
            } else {
                backUrl += "?pmi_code=" + hasCode;
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
        String code = RedisUtil.get(PMI_SERVER_CODE + "_" + codeParam);
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

