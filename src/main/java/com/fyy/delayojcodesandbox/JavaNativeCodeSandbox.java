package com.fyy.delayojcodesandbox;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import com.fyy.delayojcodesandbox.model.ExecuteCodeRequest;
import com.fyy.delayojcodesandbox.model.ExecuteCodeResponse;
import com.fyy.delayojcodesandbox.model.ExecuteMessage;
import com.fyy.delayojcodesandbox.model.JudgeInfo;
import com.fyy.delayojcodesandbox.utils.ProcessUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class JavaNativeCodeSandbox implements CodeSandbox {

    private static final String GLOBAL_CODE_PATH_NAME = "tempCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    public static void main(String[] args) {
        JavaNativeCodeSandbox javaNativeCodeSandbox = new JavaNativeCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setLanguage("java");
        String code = ResourceUtil.readStr("testcode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setInputList(Arrays.asList("1 2" , "3 4" , "5 6"));
        javaNativeCodeSandbox.executeCode(executeCodeRequest);

    }



    /*
    *
    * @Param executeCodeRequest 用户传来的数据
    * * @return
    * */
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {

        /*
        * 获取用户传来的数据
        * */
        String language = executeCodeRequest.getLanguage();
        String code = executeCodeRequest.getCode();
        List<String> inputList = executeCodeRequest.getInputList();

        /*
        * 1,将用户代码保存为文件
        * */
        //判断文件夹是否存在
        String userDir = System.getProperty("user.dir");
        //使用 File.separator 代替 //  因为在windows系统中是//，在linux系统中是/，使用 File.separator 可以兼容两种系统
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_PATH_NAME;

        //判断文件夹是否存在，不存在则创建
        if(!FileUtil.exist(globalCodePathName)){
            FileUtil.mkdir(globalCodePathName);
        }
        //把用户代码保存为文件  把用户的代码分级隔离存放
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID().toString() + "java";
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        File userCodeFile = new File(userCodePath);

        // 2. 编译用户代码 得到class文件
         String compilecmd = String.format("javac -encoding utf-8 %s", userCodePath);
         try {
             Process compileProcess = Runtime.getRuntime().exec(compilecmd);
             ExecuteMessage executeMassage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
             System.out.println(executeMassage);
         } catch (Exception e) {
             return  getErrorResponse(e);
         }

         // 3. 运行用户代码
        ArrayList<ExecuteMessage> executeMessageList = new ArrayList<>();
        // 输入用例：
        for(String inputArgs : inputList){
            String runCmd = String.format("java -Dfile.encoding=UTF-8 -cp %s Main %s" , userCodeParentPath, inputArgs);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                ExecuteMessage executeMassage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行");
                System.out.println(executeMassage);
            } catch (Exception e) {
                return  getErrorResponse(e);
            }
        }

        //4,收集整理输出结果：
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        long maxTime = 0; //记录最大时间
        for(ExecuteMessage executeMessage : executeMessageList){
            String errorMessage = executeMessage.getErrorMessage();
            if(StrUtil.isNotEmpty(errorMessage)){
                executeCodeResponse.setMassage(errorMessage);
                //执行中出现错误
                executeCodeResponse.setStatus(3);
                break;
            }
            outputList.add(executeMessage.getMessage());
            if(executeMessage.getTime() != null){
                maxTime = Math.max(maxTime, executeMessage.getTime());
            }
        }
        //如果正常执行完
        if(outputList.size() == inputList.size()){
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);

        JudgeInfo judgeInfo = new JudgeInfo();
        //此处使用时间内存最大值  便于统计后续判题是否超时
        judgeInfo.setTime(maxTime);
        //获取运行内存占用：
        //judgeInfo.setMemory();
        executeCodeResponse.setJudgeInfo(judgeInfo);

        //5,文件清理：
        if(userCodeFile.getParentFile() != null){  //防止服务器空间不足自动删除
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除"+(del?"成功":"失败"));

        }



        return executeCodeResponse;
    }


    /*
    * 获取错误信息
    * @param code 代码
    *
    * */
    private ExecuteCodeResponse getErrorResponse(Throwable e){
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMassage(e.getMessage());
        //表示代码沙箱错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;

    }


}
