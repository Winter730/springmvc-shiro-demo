package com.winter.framework.shiro.filter;

import com.winter.framework.utils.PropertiesFileUtil;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.filter.authc.AuthenticationFilter;
import org.apache.shiro.web.util.WebUtils;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

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
        WebUtils.toHttp(servletResponse).sendRedirect(ssoServerUrl.append("/sso/login").toString());
        return false;
    }


}
