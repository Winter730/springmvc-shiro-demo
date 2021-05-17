package com.winter.framework.shiro.realm;

import com.winter.framework.dao.Permission;
import com.winter.framework.dao.Role;
import com.winter.framework.dao.User;
import com.winter.framework.utils.MD5Util;
import com.winter.framework.utils.StringUtils;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 领域：realms
 * 相当于datasource数据源，securityManager进行安全认证需要通过Realm获取用户权限数据，比如：如果用户身份数据在数据库那么realm就需要从数据库获取用户身份信息。
 * 注意：不要把realm理解成只是从数据源取数据，在realm中还有认证授权校验的相关的代码。
 *
 * 此处的角色、权限理论上应该从数据库中获取，作为demo采用默认枚举类
 * Created by winter on 2021/4/26
 */
public class MyRealm extends AuthorizingRealm {

    /**
     * 授权: 验证权限时调用
     * @param principalCollection
     * @return
     */
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principalCollection) {
        String userName = (String) principalCollection.getPrimaryPrincipal();

        User user = User.getUser(userName);
        //当前用户所有角色
        Role role =  Role.getRole(userName);
        Set<String> roles = new HashSet<>();
        roles.add(role.getName());

        //当前用户所有权限
        List<Permission> permissionList = Permission.getPermission(userName);
        Set<String> permissions = new HashSet<>();
        for(Permission permission : permissionList){
            if(StringUtils.isNotBlank(permission.getPermissionValue())) {
                permissions.add(permission.getPermissionValue());
            }
        }

        SimpleAuthorizationInfo simpleAuthorizationInfo = new SimpleAuthorizationInfo();
        simpleAuthorizationInfo.setStringPermissions(permissions);
        simpleAuthorizationInfo.setRoles(roles);
        return simpleAuthorizationInfo;
    }

    /**
     * 认证：登录时调用
     * @param authenticationToken
     * @return
     * @throws AuthenticationException
     */
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authenticationToken) throws AuthenticationException {
        String userName = (String) authenticationToken.getPrincipal();
        String password = new String((char[]) authenticationToken.getCredentials());


        // 查询用户信息
        User user = User.getUser(userName);

        if(null == user) {
            throw new UnknownAccountException();
        }
        if(!user.getPassword().equals(MD5Util.md5(password + user.getSalt()))){
            throw new IncorrectCredentialsException();
        }
        if(user.getLocked() == 1) {
            throw new LockedAccountException();
        }

        return new SimpleAuthenticationInfo(userName, password, getName());
    }
}
