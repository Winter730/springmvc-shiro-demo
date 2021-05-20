package com.winter.framework.shiro.session;

import com.winter.framework.shiro.constant.PMIConstant;
import com.winter.framework.utils.PropertiesFileUtil;
import com.winter.framework.utils.RedisUtil;
import com.winter.framework.utils.SerializableUtil;
import com.winter.framework.utils.Servlets;
import org.apache.commons.lang.ObjectUtils;
import org.apache.shiro.cache.Cache;
import org.apache.shiro.session.Session;
import org.apache.shiro.session.mgt.ValidatingSession;
import org.apache.shiro.session.mgt.eis.EnterpriseCacheSessionDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.util.*;

/**
 * Created by winter on 2021/5/13
 */
public class PMISessionDao extends EnterpriseCacheSessionDAO {

    private static final Logger logger = LoggerFactory.getLogger(PMISessionDao.class);
    // 会话key
    private final static String PMI_SHIRO_SESSION = "pmi-shiro-session";
    // sso服务器授权令牌
    private final static String PMI_SERVER_CODE = "pmi-server-code";
    // 以sso服务器sessionId关联的从session列表
    private final static String PMI_SHIRO_CONNECTIDS = "pmi-shiro-connectIds";

    @Override
    //此时会话已创建,但仅是创建状态，并未登录用户，故在该步骤不需要进行会话持久化,直接保存在cookie中即可
    protected Serializable doCreate(Session session) {
        Serializable sessionId = super.doCreate(session);
        String PMIType = PropertiesFileUtil.getInstance("client").get("pmi.type");
        logger.info("doCreate >>>>> type = {}, sessionId={}", PMIType, session.getId());
        return sessionId;
    }

    @Override
    //getSession，此时session可能有以下情况:
    //1.session在cache中存在,redis中不存在，取cache中存在的session即可
    //2.session在cache中不存在(已过期),redis中存在,取redis中存在的session
    //3.session在cache和redis中都不存在,返回null，此时会创建新会话
    //4.session在cache和redis中都存在,无需查询redis,取缓存中的即可。
    protected Session doReadSession(Serializable sessionId) {
        //从缓存中取Session
        String PMIType = PropertiesFileUtil.getInstance("client").get("pmi.type");
        Cache<Serializable,Session> sessionCache = this.getActiveSessionsCache();
        PMISession session = (PMISession) sessionCache.get(sessionId);
        if(session != null){
            logger.info("doReadSession use cache >>>>> type = {}, sessionId={}", PMIType, sessionId);
            return session;
        }
        session = (PMISession) SerializableUtil.deserialize(RedisUtil.get(PMI_SHIRO_SESSION + "-" + PMIType + "-" +sessionId));
        logger.info("doReadSession use redis >>>>> type = {}, sessionId={}", PMIType, sessionId);
        return session;
    }

    @Override
    protected void doUpdate(Session session) {
        //如果会话过期/停止 没必要再更新了
        if(session instanceof ValidatingSession && !((ValidatingSession)session).isValid()) {
            return;
        }
        HttpServletRequest request = Servlets.getRequest();
        if(request == null) {
            return;
        }
        //更新session的最后一次访问时间
        PMISession pmiSession = (PMISession) session;
        PMISession cachePMISession = (PMISession) doReadSession(session.getId());
        if(null != cachePMISession) {
            pmiSession.setStatus(cachePMISession.getStatus());
            pmiSession.setAttribute("FORCE_LOGOUT", cachePMISession.getAttribute("FORCE_LOGOUT"));
        }
        //在线状态才更新
        if(pmiSession.getStatus() == PMISession.OnlineStatus.on_line){
            RedisUtil.set(PMI_SHIRO_SESSION + "_" + session.getId(), SerializableUtil.serialize(session), (int) session.getTimeout() / 1000);
        }
        logger.info("doUpdate >>>>> sessionId={}", session.getId());
    }

    @Override
    protected void doDelete(Session session) {
        String sessionId = session.getId().toString();
        String PMIType = ObjectUtils.toString(session.getAttribute(PMIConstant.PMI_TYPE));
    }


    /**
     * 更改在线状态
     */
    public void updateStatus(Serializable sessionId, PMISession.OnlineStatus onlineStatus){
        Cache<Serializable,Session> sessionCache = this.getActiveSessionsCache();
        String PMIType = PropertiesFileUtil.getInstance("client").get("pmi.type");
        PMISession session = (PMISession) sessionCache.get(sessionId);
        if(null == session) {
            return;
        }
        session.setStatus(onlineStatus);
        try {
            RedisUtil.set(PMI_SHIRO_SESSION + "-" + PMIType + "-" + session.getId(), SerializableUtil.serialize(session), (int)session.getTimeout() / 1000);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * 删除旧会话信息
     */
    public void deleteOldSession(Serializable sessionId){
        //删除旧code校验值
        RedisUtil.remove(PMI_SERVER_CODE + "-" + sessionId);
        //删除旧会话
        String PMIType = PropertiesFileUtil.getInstance("client").get("pmi.type");
        RedisUtil.remove(PMI_SHIRO_SESSION + "-" + PMIType + "-" + sessionId);
        //根据sessionId获取关联的从服务器列表
        Jedis jedis = RedisUtil.getJedis();
        Set<String> set = jedis.smembers(PMI_SHIRO_CONNECTIDS + "-" + sessionId);
        //删除关联的从服务器列表会话
        for(String data : set) {
            RedisUtil.remove(data);
        }
        RedisUtil.remove(PMI_SHIRO_CONNECTIDS + "-" + sessionId);
    }
}
