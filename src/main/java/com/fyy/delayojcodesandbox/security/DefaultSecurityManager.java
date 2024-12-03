package com.fyy.delayojcodesandbox.security;

import java.security.Permission;

public class DefaultSecurityManager extends SecurityManager{

    //检查所有权限的方法：
    /*
    *
    *
    * */
    @Override
    public void checkPermission(Permission perm) {
        System.out.println("默认无权限限制");
        System.out.println(perm);
    }

}
