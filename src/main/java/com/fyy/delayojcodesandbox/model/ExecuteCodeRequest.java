package com.fyy.delayojcodesandbox.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/*
* 代码沙箱的请求信息
* */
public class ExecuteCodeRequest {
    /*
    * 代码语言
    * */
    private String language;

    /*
    * 代码
    * */
    private String code;

    /*
    * 输入样例列表
    * */
    private List<String> inputList;
}
