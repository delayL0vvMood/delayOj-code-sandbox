package com.fyy.delayojcodesandbox.security;

import java.security.Permission;

/*
* 禁用所有的权限管理器
* */
public class MySecurityManager extends SecurityManager{

    //检测程序是否可执行文件
    @Override
    public void checkExec(String cmd) {
        throw new SecurityException("checkExec  权限异常" + cmd);
    }

    //是否写文件（只读文件）
    @Override
    public void checkRead(String file) {
        throw new SecurityException("checkRead  权限异常" + file);
    }

    //是否写文件（可写文件）
    @Override
    public void checkWrite(String file) {
        throw new SecurityException("checkWrite  权限异常" + file);
    }

    //是否删除文件
    @Override
    public void checkDelete(String file) {
        throw new SecurityException("checkDelete  权限异常" + file);
    }

    //是否连接网络
    @Override
    public void checkConnect(String host, int port) {
        super.checkConnect(host, port);
    }


}
