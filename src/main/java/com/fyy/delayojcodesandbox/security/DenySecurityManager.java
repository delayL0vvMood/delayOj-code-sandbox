package com.fyy.delayojcodesandbox.security;

import java.security.Permission;

/*
* 禁用所有的权限管理器
* */
public class DenySecurityManager extends SecurityManager{

    @Override
    public void checkExec(String cmd) {
        super.checkExec(cmd);
    }

    @Override
    public void checkRead(String file) {
        super.checkRead(file);
    }

    @Override
    public void checkWrite(String file) {
        super.checkWrite(file);
    }

    @Override
    public void checkDelete(String file) {
        super.checkDelete(file);
    }

    @Override
    public void checkConnect(String host, int port) {
        super.checkConnect(host, port);
    }

    //检查所有权限的方法：
    @Override
    public void checkPermission(Permission perm) {
        throw new SecurityException("权限异常 " + perm.toString());
    }

}
