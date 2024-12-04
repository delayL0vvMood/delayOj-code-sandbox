package com.fyy.delayojcodesandbox.controller;

import com.fyy.delayojcodesandbox.JavaNativeCodeSandbox;
import com.fyy.delayojcodesandbox.model.ExecuteCodeRequest;
import com.fyy.delayojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController ("/main")
public class MainController {

    //  定义请求头和请求参数
    private final static String AUT_REQUEST_HEADER = "Authorization";
    private final static String AUT_REQUEST_SECRET = "Bearer123456789";

    @Resource
    JavaNativeCodeSandbox javaNativeCodeSandbox;

    @PostMapping("/executeCode")
    ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest , HttpServletRequest request , HttpServletResponse response){
        String authHeader = request.getHeader(AUT_REQUEST_HEADER);
        if(!AUT_REQUEST_SECRET.equals(authHeader)){
            response.setStatus(403);
            return null;
        }
        if(executeCodeRequest == null){
            throw new RuntimeException("请求参数为空");
        }
        System.out.println(executeCodeRequest.getCode());
        return javaNativeCodeSandbox.executeCode(executeCodeRequest);
    }


}
