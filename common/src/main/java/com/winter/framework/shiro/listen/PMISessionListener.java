package com.winter.framework.shiro.listen;

import org.apache.shiro.session.Session;
import org.apache.shiro.session.SessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by winter on 2021/3/5
 */
public class PMISessionListener implements SessionListener {
    private static final Logger logger = LoggerFactory.getLogger(PMISessionListener.class);
    @Override
    public void onStart(Session session) {
        logger.info("会话创建:" + session.getId());
    }

    @Override
    public void onStop(Session session) {
        logger.info("会话停止:" + session.getId());
    }

    @Override
    public void onExpiration(Session session) {
        logger.info("会话过期:" + session.getId());
    }
}
