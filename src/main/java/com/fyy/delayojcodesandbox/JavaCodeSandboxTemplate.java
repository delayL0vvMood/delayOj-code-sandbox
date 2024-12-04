package com.fyy.delayojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.fyy.delayojcodesandbox.model.ExecuteCodeRequest;
import com.fyy.delayojcodesandbox.model.ExecuteCodeResponse;
import com.fyy.delayojcodesandbox.model.ExecuteMessage;
import com.fyy.delayojcodesandbox.model.JudgeInfo;
import com.fyy.delayojcodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/*
*  java 代码沙箱模板方法实现
* */
@Slf4j
@Component
public abstract class JavaCodeSandboxTemplate implements CodeSandbox{
    //定义流程
    private static final String GLOBAL_CODE_PATH_NAME = "tempCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    /*
     * 设置时间限制，控制代码运行时间
     * */
    private static final long TIME_OUT = 5000L;

    /*
     *
     * @Param executeCodeRequest 用户传来的数据
     * @Return
     * */
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        String code = executeCodeRequest.getCode();
        List<String> inputList = executeCodeRequest.getInputList();

        //1,把用户代码保存文件
        File userCodeFile = saveCodeToFile(code);

        // 2. 编译用户代码 得到class文件
        ExecuteMessage compileFileExecuteMessage = compileFile(userCodeFile);
        System.out.println(compileFileExecuteMessage);

        // 3. 运行用户代码
        List<ExecuteMessage> executeMessageList = runFile(inputList , userCodeFile);

        //4,收集整理输出结果：
        ExecuteCodeResponse outputResponse = getOutputResponse(executeMessageList);

        //5,文件清理：
        boolean b = deleteFile(userCodeFile);
        if(!b){
            log.error("deleteFile error" + userCodeFile.getAbsolutePath());
        }

        return outputResponse;
    }



    /*
    * 1，保存用户文件
    * @Param code
    * */
    public File saveCodeToFile(String code){
        String userDir = System.getProperty("user.dir");
        //使用 File.separator 代替 //  因为在windows系统中是//，在linux系统中是/，使用 File.separator 可以兼容两种系统
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_PATH_NAME;
        //把用户代码保存为文件  把用户的代码分级隔离存放
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID().toString() + "java";

        //判断文件夹是否存在，不存在则创建
        if(!FileUtil.exist(globalCodePathName)){
            FileUtil.mkdir(globalCodePathName);
        }
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        File userCodeFile = new File(userCodePath);
        return userCodeFile;
    }

    /*
    *  2，编译文件
    * @Param userCodeFile
    *
    * */
    public ExecuteMessage compileFile(File userCodeFile){
        String userCodePath = userCodeFile.getAbsolutePath();
        String compilecmd = String.format("javac -encoding utf-8 %s", userCodePath);
        try {
            Process compileProcess = Runtime.getRuntime().exec(compilecmd);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");

            if(executeMessage.getExitValue() != 0){
                throw new RuntimeException("编译错误");
            }
            return executeMessage;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /*
    * 3，执行文件，获得执行结果列表
    * @Param inputList
    * @Param userCodeFile
    *
    * */
    public List<ExecuteMessage> runFile(List<String> inputList , File userCodeFile){
        ArrayList<ExecuteMessage> executeMessageList = new ArrayList<>();
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        // 输入用例：
        for(String inputArgs : inputList){
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArgs);
            //String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s;%s -Djava.security.manager=%s Main %s" , userCodeParentPath,SECURITY_MANAGER_PATH,SECURITY_MANAGER_CLASS_NAME, inputArgs);

            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                        runProcess.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                ExecuteMessage executeMassage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行");
                System.out.println(executeMassage);
                executeMessageList.add(executeMassage);
            } catch (Exception e) {
                throw new RuntimeException("程序执行异常"+e);
            }
        }
        return executeMessageList;
    }

    /*
    * 4,整理输出结果返回运行响应
    *
    * */
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList ){
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
        if(outputList.size() == executeMessageList.size()){
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);

        JudgeInfo judgeInfo = new JudgeInfo();
        //此处使用时间内存最大值  便于统计后续判题是否超时
        judgeInfo.setTime(maxTime);
        //获取运行内存占用：
        //judgeInfo.setMemory();
        executeCodeResponse.setJudgeInfo(judgeInfo);

        return executeCodeResponse;
    }

    /*
    * 5,删除文件
    * @Param userCodeFile
    * @Return
    * */
    public boolean deleteFile(File userCodeFile){
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        if(userCodeFile.getParentFile() != null){  //防止服务器空间不足自动删除
            boolean del = FileUtil.del(userCodeParentPath);
            return del;
        }
        return true;
    }

    /*
     * 6,获取错误信息
     * @Param code 代码
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
