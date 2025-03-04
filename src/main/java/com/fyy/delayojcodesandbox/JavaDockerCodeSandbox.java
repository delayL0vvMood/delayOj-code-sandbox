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
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate  implements CodeSandbox {
    public static void main(String[] args) throws InterruptedException {
        JavaDockerCodeSandbox javaDockerCodeSandbox = new JavaDockerCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setLanguage("java");
        String code = ResourceUtil.readStr("testcode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        //String code = ResourceUtil.readStr("testcode/unsafeCode/RunFileError.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setInputList(Arrays.asList("1 2" , "3 4" , "5 6"));
        ExecuteCodeResponse executeCodeResponse = javaDockerCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);

    }
    /*
     * 设置时间限制，控制代码运行时间
     * */
    private static final long TIME_OUT = 5000L;

    //初始化开关，第一次启动拉取镜像
    private static final Boolean FIRST_INIT = false;

    @Override
    public List<ExecuteMessage> runFile(List<String> inputList, File userCodeFile) {
        // 3. 创建容器，将用户代码传到容器内
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
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
        //hostConfig.withSecurityOpts(Arrays.asList(""));
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
            System.out.println("ssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssss: " +execCreateCmdResponse);
            System.out.println("创建执行指令: " +execCreateCmdResponse);

            ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] message = {null};
            final String[] errorMessage = {null};
            long time = 0l;

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
            final long[] maxMemory = {0l};
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
            try {
                stopWatch.start();
                //执行容器
                dockerClient
                        .execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion();
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();  //获取上次执行stopWatch的时间
                statsCmd.close();
            } catch (InterruptedException e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }
            /*try {
                Thread.sleep(10000l);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }*/
            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
            executeMessageList.add(executeMessage);
        }
        return executeMessageList;
    }




}
