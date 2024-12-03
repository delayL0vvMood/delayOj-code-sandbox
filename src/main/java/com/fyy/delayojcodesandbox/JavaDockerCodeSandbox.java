package com.fyy.delayojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.WordTree;
import com.fyy.delayojcodesandbox.model.ExecuteCodeRequest;
import com.fyy.delayojcodesandbox.model.ExecuteCodeResponse;
import com.fyy.delayojcodesandbox.model.ExecuteMessage;
import com.fyy.delayojcodesandbox.model.JudgeInfo;
import com.fyy.delayojcodesandbox.utils.ProcessUtils;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class JavaDockerCodeSandbox implements CodeSandbox {

    private static final String GLOBAL_CODE_PATH_NAME = "tempCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    /*
    * 设置时间限制，控制代码运行时间
    * */
    private static final long TIME_OUT = 5000L;

    //初始化开关，第一次启动拉取镜像
    private static final Boolean FIRST_INIT = false;





    public static void main(String[] args) throws InterruptedException {
        JavaDockerCodeSandbox javaDockerCodeSandbox = new JavaDockerCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setLanguage("java");
        String code = ResourceUtil.readStr("testcode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        //String code = ResourceUtil.readStr("testcode/unsafeCode/RunFileError.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setInputList(Arrays.asList("1 2" , "3 4" , "5 6"));
        javaDockerCodeSandbox.executeCode(executeCodeRequest);

    }
    /*
    *
    * @Param executeCodeRequest 用户传来的数据
    * * @return
    * */
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest){

        //使用编写的安全管理器
        //System.setSecurityManager(new MySecurityManager());
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

         // 3. 创建容器，将用户代码传到容器内

        //获取dockerclient
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        String image = "openjdk:8-alpine";

        if(FIRST_INIT){
            //拉去镜像
             PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
                    PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                        //当收到一个消息时调用
                        @Override
                        public void onNext(PullResponseItem item) {
                            System.out.println("收到消息：" + item);
                            super.onNext(item);
                        }
                    };
            try {
                pullImageCmd.exec(pullImageResultCallback).awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("拉取镜像异常");
                throw new RuntimeException(e);
            }
            System.out.println("拉取完成");
        }

        //创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        HostConfig hostConfig = new HostConfig();
        hostConfig.setBinds(new Bind(userCodeParentPath,new Volume("/app")));
        hostConfig.withMemory(100 * 1000 *1000l);  //设置内存限制
        hostConfig.withMemorySwap(0l); // 限制内存交换
        hostConfig.withCpuCount(1L);
        hostConfig.withSecurityOpts(Arrays.asList(""));
        //hostConfig   Bind 参数： 本地文件路径path，容器文件路径volume
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)      // 创建容器是，可以指定文件路径volumn映射，将本地文件同步到容器中，
                .withAttachStderr(true)  //把docker与本地链接 ，本地获取容器错误
                .withNetworkDisabled(true)   //设置网络关闭
                .withReadonlyRootfs(true)  //限制用户向root根目录写入
                .withAttachStdin(true)      //获取容器输入
                .withAttachStdout(true)     //获取容器输出
                .withTty(true)          //创建一个交互终端  实际上是一个守护进程的bin，等待用户输入
                .exec();
        System.out.println(createContainerResponse);
        String containerId = createContainerResponse.getId();

        //4,启动容器  执行代码
        dockerClient
                .startContainerCmd(containerId)
                .exec();

        //  以下代码对应着docker exec命令，相当于向docker容器中传入 java -cp 命令   执行命令获取结果
        //  例如：docker exec clever_pare java -cp /app Main 1 8
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for(String inputArgs : inputList){
            String[] inputArgsArr = inputArgs.split(" ");
            //hutool工具类 ， 拼接字符串数组
            String[] cmdArray = ArrayUtil.append( new String[]{"java","-cp","/app","Main"}, inputArgsArr);
            StopWatch stopWatch = new StopWatch();
            //注意 要把命令按照空格拆分成数组，否则可能会被识别成一个字符串而不是一个参数 ,
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                            .withCmd(cmdArray)
                            .withAttachStdin(true)
                            .withAttachStderr(true)
                            .withAttachStdout(true)
                            .exec();
            System.out.println("创建执行指令: " +execCreateCmdResponse);

            ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
            ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] message = {null};
            final String[] errorMessage = {null};
            long time = 0;

            String execId = execCreateCmdResponse.getId();
            final boolean[] isTimeOut = {true};
            //异步回调
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                @Override
                public void onComplete() {
                    isTimeOut[0] = false;
                    super.onComplete();
                }

                @Override
                public void onNext(Frame frame) {
                    //区分输出和错误输出
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        errorMessage[0] = new String(frame.getPayload());
                        System.out.println("输出错误" + errorMessage[0]);
                    } else {
                        message[0] = new String(frame.getPayload());
                        System.out.println("输出结果" + message[0]);
                    }
                    super.onNext(frame);
                }
            };
            final long[] maxMemory = {0};
            //获取内存
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onNext(Statistics statistics) {
                    Long usage = statistics.getMemoryStats().getUsage();
                    maxMemory[0] = Math.max(maxMemory[0], usage);
                    System.out.println("内存占用："+usage);
                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }

                @Override
                public void close() throws IOException {

                }
            });
            statsCmd.exec(statisticsResultCallback);
            statsCmd.close();

            try {
                stopWatch.start();
                //执行容器
                dockerClient
                        .execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion(TIME_OUT, TimeUnit.MICROSECONDS);
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();  //获取上次执行stopWatch的时间
            } catch (InterruptedException e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }
            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
            executeMessageList.add(executeMessage);

        }


        //封装结果
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
