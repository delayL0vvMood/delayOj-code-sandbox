package com.fyy.delayojcodesandbox;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import com.fyy.delayojcodesandbox.model.ExecuteCodeRequest;
import com.fyy.delayojcodesandbox.model.ExecuteCodeResponse;
import com.fyy.delayojcodesandbox.model.ExecuteMessage;
import com.fyy.delayojcodesandbox.model.JudgeInfo;
import com.fyy.delayojcodesandbox.security.DefaultSecurityManager;
import com.fyy.delayojcodesandbox.security.DenySecurityManager;
import com.fyy.delayojcodesandbox.security.MySecurityManager;
import com.fyy.delayojcodesandbox.utils.ProcessUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class JavaNativeCodeSandbox implements CodeSandbox {

    private static final String GLOBAL_CODE_PATH_NAME = "tempCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    private static final String SECURITY_MANAGER_PATH = "C:\\Users\\fengyaoyang\\IdeaProjects\\delayoj-code-sandbox\\src\\main\\resources\\security";
    private static final String SECURITY_MANAGER_CLASS_NAME = "MySecurityManager";
    /*
    * 设置时间限制，控制代码运行时间
    * */
    private static final long TIME_OUT = 5000L;

    /*
    * 设置黑名单：
    * 木马程序
    * */
    private static final List<String> BLACK_LIST = Arrays.asList("Runtime", "ProcessBuilder", "Process", "ProcessHandle",  "System.err", "System.in", "System.getProperty", "System.getenv", "System.exit", "System.load", "System.loadLibrary", "System.gc", "System.runFinalization", "System.setSecurityManager", "System.getSecurityManager","Files", "exec");

    //初始化字典树
    private static final WordTree WORD_TREE ;
    static {
        WORD_TREE = new WordTree();
        WORD_TREE.addWords(BLACK_LIST);
    }




    public static void main(String[] args) {
        JavaNativeCodeSandbox javaNativeCodeSandbox = new JavaNativeCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setLanguage("java");
        String code = ResourceUtil.readStr("testcode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        //String code = ResourceUtil.readStr("testcode/unsafeCode/RunFileError.java", StandardCharsets.UTF_8);
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

        //使用编写的安全管理器
        //System.setSecurityManager(new MySecurityManager());
        /*
        * 获取用户传来的数据
        * */
        String language = executeCodeRequest.getLanguage();
        String code = executeCodeRequest.getCode();
        List<String> inputList = executeCodeRequest.getInputList();

    /*    //校验代码中是否存在黑名单
        FoundWord foundWord = WORD_TREE.matchWord(code);
        if(foundWord != null){
            System.out.println("包含禁止词  "+foundWord.getFoundWord());
            return null;
        }*/


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
            //String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArgs);
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s;%s -Djava.security.manager=%s Main %s" , userCodeParentPath,SECURITY_MANAGER_PATH,SECURITY_MANAGER_CLASS_NAME, inputArgs);

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
