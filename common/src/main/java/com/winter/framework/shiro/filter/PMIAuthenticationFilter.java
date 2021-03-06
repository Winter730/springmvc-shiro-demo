package com.winter.framework.shiro.filter;

import com.alibaba.fastjson.JSONObject;
import com.winter.framework.shiro.constant.PMIConstant;
import com.winter.framework.shiro.session.PMISession;
import com.winter.framework.shiro.session.PMISessionDao;
import com.winter.framework.utils.PropertiesFileUtil;
import com.winter.framework.utils.RedisUtil;
import com.winter.framework.utils.RequestParameterUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.filter.authc.AuthenticationFilter;
import org.apache.shiro.web.util.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * 重写authc过滤器
 * Created by winter on 2021/3/5
 */
public class PMIAuthenticationFilter extends AuthenticationFilter {
    private static final Logger logger = LoggerFactory.getLogger(PMIAuthenticationFilter.class);
    // client会话key
    private final static String PMI_SHIRO_SESSION_CLIENT = "pmi-shiro-session-client";

    private final static String PMI_SHIRO_CONNECTIDS = "pmi-shiro-connectIds";

    @Autowired
    private PMISessionDao pmiSessionDao;

    @Override
    protected boolean isAccessAllowed(ServletRequest request, ServletResponse response, Object mappedValue) {
        Subject subject = getSubject(request, response);
        Session session = subject.getSession();
        //判断请求类型
        String PMIType = PropertiesFileUtil.getInstance("client").get("pmi.type");
        session.setAttribute(PMIConstant.PMI_TYPE, PMIType);
        if("client".equals(PMIType)) {
            return validateClient(request, response);
        }
        if("server".equals(PMIType)) {
            return subject.isAuthenticated();
        }
        return false;
    }

    @Override
    protected boolean onAccessDenied(ServletRequest servletRequest, ServletResponse servletResponse) throws Exception {
        StringBuffer ssoServerUrl = new StringBuffer(PropertiesFileUtil.getInstance("client").get("pmi.sso.server.url"));
        //server需要登录
        String PMIType = PropertiesFileUtil.getInstance("client").get("pmi.type");
        if("server".equals(PMIType)) {
            WebUtils.toHttp(servletResponse).sendRedirect(ssoServerUrl.append("/sso/login").toString());
            return false;
        }
        ssoServerUrl.append("/sso/index").append("?").append("appid").append("=").append(PropertiesFileUtil.getInstance("client").get("app.name"));
        //回跳地址
        HttpServletRequest httpServletRequest = WebUtils.toHttp(servletRequest);
        StringBuffer backUrl = httpServletRequest.getRequestURL();
        String queryString = httpServletRequest.getQueryString();
        if(StringUtils.isNotBlank(queryString)) {
            backUrl.append("?").append(queryString);
        }
        ssoServerUrl.append("&").append("backUrl").append("=").append(URLEncoder.encode(backUrl.toString(), "utf-8"));
        WebUtils.toHttp(servletResponse).sendRedirect(ssoServerUrl.toString());
        return false;
    }

    /**
     * 认证中心登录成功带回code
     * 只有从会话会经过这个方法
     */
    private boolean validateClient(ServletRequest request, ServletResponse response) {
        Subject subject = getSubject(request, response);
        Session session = subject.getSession();
        String sessionId = session.getId().toString();
        //判断局部会话是否登录
        try{
            String cacheClientSession = RedisUtil.get(PMI_SHIRO_SESSION_CLIENT + "-" + sessionId);
            if(StringUtils.isNotBlank(cacheClientSession)) {
                //更新有效期
                RedisUtil.set(PMI_SHIRO_SESSION_CLIENT + "-" + sessionId, cacheClientSession, (int)session.getTimeout() / 1000);


                //移除url中的code参数
                if(null != request.getParameter("code")){
                    String backUrl = RequestParameterUtil.getParameterWithOutCode(WebUtils.toHttp(request));
                    HttpServletResponse httpServletResponse = WebUtils.toHttp(response);
                    try {
                        httpServletResponse.sendRedirect(backUrl);
                    } catch (IOException e) {
                        logger.error("局部会话已登录,移除code参数跳转出错:", e);
                    }
                }  else {
                    return true;
                }
            }
        } catch (Exception e){
            logger.error(e.getMessage(), e);
        }
        // 判断是否有认证中心code
        String code = request.getParameter("pmi_code");
        // 已拿到code
        if(StringUtils.isNotBlank(code)) {
            //HttpPost去校验code
            try {
                StringBuffer ssoServerUrl = new StringBuffer(PropertiesFileUtil.getInstance("client").get("pmi.sso.server.url"));
                HttpClient httpClient = new DefaultHttpClient();
                HttpPost httpPost = new HttpPost(ssoServerUrl.toString() + "/sso/code");

                List<NameValuePair> nameValuePairs = new ArrayList<>();
                nameValuePairs.add(new BasicNameValuePair("code", code));
                httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

                HttpResponse httpResponse = httpClient.execute(httpPost);
                if(httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                    HttpEntity httpEntity = httpResponse.getEntity();
                    JSONObject result = JSONObject.parseObject(EntityUtils.toString(httpEntity));
                    if(1 == result.getIntValue("code") && result.getString("data").equals(code)){
                        Jedis jedis = RedisUtil.getJedis();
                        jedis.sadd(PMI_SHIRO_CONNECTIDS + "-" + code,PMI_SHIRO_SESSION_CLIENT + "-" + sessionId);
                        jedis.close();

                        pmiSessionDao.updateStatus(sessionId, PMISession.OnlineStatus.on_line);
                        jedis = RedisUtil.getJedis();
                        Long number = jedis.scard(PMI_SHIRO_CONNECTIDS + "-" + code);
                        jedis.close();
                        logger.info("当前code={}，对应的注册系统个数：{}个", code, number);
                        // 返回请求资源
                        try {
                            // 移除url中的token参数(此处会导致验证通过后，仍然要进行一次验证，不过如果去掉的话，将会暴露pmi_code参数，影响安全性，暂无其他方案，先搁置)

                            String backUrl = RequestParameterUtil.getParameterWithOutCode(WebUtils.toHttp(request));
                            HttpServletResponse httpServletResponse = WebUtils.toHttp(response);
                            httpServletResponse.sendRedirect(backUrl);
                            return true;
                        } catch (IOException e) {
                           logger.error("已拿到code，移除code参数跳转出错：", e);
                        }
                    } else {
                        logger.warn(result.getString("data"));
                    }
                }
            } catch (IOException e) {
                logger.error("验证token失败：", e);
            }
        }
        return false;
    }

}
