package com.winter.framework.shiro.session;


import org.apache.shiro.session.Session;
import org.apache.shiro.session.mgt.eis.SessionIdGenerator;

import java.io.Serializable;
import java.util.UUID;

/**
 * Created by winter on 2021/5/14
 */
public class JavaUUIDSessionIdGenerator implements SessionIdGenerator {
    @Override
    public Serializable generateId(Session session) {
        System.out.println("generateId");
        return UUID.randomUUID().toString().replaceAll("-", "");
        //return "123456789";
    }
}
