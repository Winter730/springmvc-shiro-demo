package com.winter.framework.shiro.session;

import com.winter.framework.shiro.constant.PMIConstant;
import com.winter.framework.utils.RedisUtil;
import com.winter.framework.utils.SerializableUtil;
import com.winter.framework.utils.Servlets;
import org.apache.commons.lang.ObjectUtils;
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
    private final static String PMI_SHIRO_SESSION_ID = "pmi-shiro-session-id";
    // 全局会话key
    private final static String PMI_SERVER_SESSION_ID = "pmi-server-session-id";
    // 全局会话列表key
    private final static String PMI_SERVER_SESSION_IDS = "pmi-server-session-ids";
    // code key
    private final static String PMI_SERVER_CODE = "pmi-server-code";
    // 局部会话key
    private final static String PMI_CLIENT_SESSION_ID = "pmi-client-session-id";
    // 单点同一个code所有局部会话key
    private final static String PMI_CLIENT_SESSION_IDS = "pmi-client-session-ids";

    @Override
    //此时会话已创建,但仅是创建状态，并未登录用户，故在该步骤不需要进行会话持久化。
    protected Serializable doCreate(Session session) {
        Serializable sessionId = super.doCreate(session);
        logger.info("doCreate >>>>> sessionId={}", session.getId());
        return sessionId;
    }

    @Override
    protected Session doReadSession(Serializable sessionId) {
        //从缓存中取Session,直接从ehcache中读取即可
        Session session =  super.doReadSession(sessionId);
        logger.info("doReadSession >>>>> sessionId={}", sessionId);
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
            RedisUtil.set(PMI_SHIRO_SESSION_ID + "_" + session.getId(), SerializableUtil.serialize(session), (int) session.getTimeout() / 1000);
        }
        logger.info("doUpdate >>>>> sessionId={}", session.getId());
    }

    @Override
    protected void doDelete(Session session) {
        String sessionId = session.getId().toString();
        String PMIType = ObjectUtils.toString(session.getAttribute(PMIConstant.PMI_TYPE));
        try {
            if("client".equals(PMIType)){
                // 删除局部会话和同一code注册的局部会话
                String code = RedisUtil.get(PMI_CLIENT_SESSION_ID + "_" + sessionId);
                Jedis jedis = RedisUtil.getJedis();
                jedis.del(PMI_CLIENT_SESSION_ID + "_" + sessionId);
                jedis.srem(PMI_CLIENT_SESSION_IDS + "_" + code, sessionId);
                jedis.close();
            }
            if("server".equals(PMIType)){
                // 当前全局会话code
                String code = RedisUtil.get(PMI_SERVER_SESSION_ID + "_" + sessionId);
                // 清除全局会话
                RedisUtil.remove(PMI_SERVER_SESSION_ID + "_" + sessionId);
                // 清除code校验值
                RedisUtil.remove(PMI_SERVER_CODE + "_" + code);
                // 清除所有局部会话
                Jedis jedis = RedisUtil.getJedis();
                Set<String> clientSessionIds = new HashSet<>(Arrays.asList(RedisUtil.get(PMI_CLIENT_SESSION_IDS + "" + code)));
                for(String clientSessionId :clientSessionIds) {
                    jedis.del(PMI_CLIENT_SESSION_ID + "_" + clientSessionId);
                    jedis.srem(PMI_CLIENT_SESSION_IDS + "_" + code, clientSessionId);
                }

                logger.debug("当前code={}，对应的注册系统个数：{}个", code, jedis.scard(PMI_CLIENT_SESSION_IDS + "_" + code));
                jedis.close();
                //维护会话id列表，提供会话分页管理
                RedisUtil.lrem(PMI_SERVER_SESSION_IDS, 1, sessionId);

            }
            //删除session
            //RedisUtil.remove(PMI_SHIRO_SESSION_ID + "_" + sessionId);
            logger.debug("doDelete >>>>> sessionId={}", sessionId);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * 获取会话列表
     */
    public Map getActiveSessions(int offset, int limit){
        Map sessions = new HashMap();
        Jedis jedis = RedisUtil.getJedis();
        // 获取在线会话总数
        long total = jedis.llen(PMI_SERVER_SESSION_IDS);
        // 获取当前页会话详情
        List<String> ids = jedis.lrange(PMI_SERVER_SESSION_IDS, offset, (offset + limit - 1));
        List<Session> rows = new ArrayList<>();
        for(String id : ids) {
            String session = RedisUtil.get(PMI_SHIRO_SESSION_ID + "_" + id);
            //过滤redis过期session
            if (null == session) {
                RedisUtil.lrem(PMI_SERVER_SESSION_IDS, 1, id);
                total = total - 1;
                continue;
            }
            rows.add(SerializableUtil.deserialize(session));
        }
        jedis.close();
        sessions.put("total", total);
        sessions.put("rows", rows);
        return sessions;
    }

    /**
     * 强制退出
     */
    public int forceOut(String ids) {
        String[] sessionIds = ids.split(",");
        for(String sessionId : sessionIds) {
            //会话增加强制退出属性标识，当此会话访问系统时，判断有该标识，则退出登录
            try {
                String session = RedisUtil.get(PMI_SHIRO_SESSION_ID + "_" + sessionId);
                PMISession PMISession = (PMISession) SerializableUtil.deserialize(session);
                PMISession.setStatus(com.winter.framework.shiro.session.PMISession.OnlineStatus.force_logout);
                PMISession.setAttribute("FORCE_LOGOUT", "FORCE_LOGOUT");
                RedisUtil.set(PMI_SHIRO_SESSION_ID + "" + sessionId, SerializableUtil.serialize(PMISession),(int)PMISession.getTimeout() / 1000);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
        return sessionIds.length;
    }

    /**
     * 更改在线状态
     */
    public void updateStatus(Serializable sessionId, PMISession.OnlineStatus onlineStatus){
        //PMISession session = (PMISession) doReadSession(sessionId);
        PMISession session = (PMISession) SerializableUtil.deserialize (RedisUtil.get(PMI_CLIENT_SESSION_ID + sessionId));
        if(null == session) {
            return;
        }
        session.setStatus(onlineStatus);
        try {
            RedisUtil.set(PMI_SHIRO_SESSION_ID + "_" + session.getId(), SerializableUtil.serialize(session), (int)session.getTimeout() / 1000);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }
}
