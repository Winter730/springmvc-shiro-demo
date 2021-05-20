package com.winter.framework.shiro.filter;

import com.winter.framework.utils.PropertiesFileUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.filter.authc.AuthenticationFilter;
import org.apache.shiro.web.util.WebUtils;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.net.URLEncoder;

/**
 * Created by winter on 2021/4/26
 */
public class SingleAuthenticationFilter extends AuthenticationFilter {
    @Override
    protected boolean isAccessAllowed(ServletRequest request, ServletResponse response, Object mappedValue) {
        Subject subject = getSubject(request, response);
        return subject.isAuthenticated();
    }

    @Override
    protected boolean onAccessDenied(ServletRequest servletRequest, ServletResponse servletResponse) throws Exception {
        StringBuffer ssoServerUrl = new StringBuffer(PropertiesFileUtil.getInstance("client").get("pmi.sso.server.url"));
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


}
