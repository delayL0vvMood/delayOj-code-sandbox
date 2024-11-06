package com.fyy.delayojcodesandbox.model;

import lombok.Data;

/*
* 判题信息
* */
@Data
public class JudgeInfo {
    /*
    * 时间
    * */
    private Long time;

    /*
    * 空间
    * */
    private Long memory;


    /*
    * 程序执行信息
    * */
    private String message;
}
