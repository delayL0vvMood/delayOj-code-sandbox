package com.fyy.delayojcodesandbox;
import com.fyy.delayojcodesandbox.model.ExecuteCodeRequest;
import com.fyy.delayojcodesandbox.model.ExecuteCodeResponse;

public interface CodeSandbox {


    /*
    * 执行代码
    * @Param executeCodeRequest 代码执行请求
    * */
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);

}
