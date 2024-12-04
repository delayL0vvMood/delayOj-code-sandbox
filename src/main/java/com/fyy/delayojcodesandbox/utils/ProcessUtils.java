package com.fyy.delayojcodesandbox.utils;


import cn.hutool.core.util.StrUtil;
import com.fyy.delayojcodesandbox.model.ExecuteMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.StopWatch;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
/*
* 进程工具类
* */

public class ProcessUtils {


    /*
    * 执行进程并获取信息
    * 从终端获取返回信息
    * @Param runProcess 执行进程
    * @Param onName 执行进程名称(运行，编译。。。。)
    * */
    public static ExecuteMessage runProcessAndGetMessage (Process runProcess  ,String onName){
        ExecuteMessage executeMessage = new ExecuteMessage();

        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            int exitValue = runProcess.waitFor();
            executeMessage.setExitValue(exitValue);
            // 编译失败
            if (exitValue != 0) {
                System.out.println(onName + "失败, 错误码： "+ exitValue);
                System.out.println();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                List<String> complieOutputList = new ArrayList<>();
                String complieOutputLine;
                while ((complieOutputLine = bufferedReader.readLine()) != null) {
                    complieOutputList.add(complieOutputLine);
                }
                executeMessage.setMessage(StringUtils.join(complieOutputList, "\n"));

                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(runProcess.getErrorStream()));
                List<String> errorComplieOutputList = new ArrayList<>();
                String errorComplieOutputLine;
                while ((errorComplieOutputLine = errorBufferedReader.readLine()) != null) {
                    errorComplieOutputList.add(errorComplieOutputLine);
                }
                executeMessage.setErrorMessage(StringUtils.join(errorComplieOutputList, "\n"));

            } else { // 编译成功
                System.out.println(onName + "成功");
                StringBuilder complieOutputStringBuilder = new StringBuilder();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                List<String> complieOutputList = new ArrayList<>();
                String complieOutputLine;
                while ((complieOutputLine = bufferedReader.readLine()) != null) {
                    complieOutputList.add(complieOutputLine);
                }
                executeMessage.setMessage(StringUtils.join(complieOutputList, "\n"));
            }
            stopWatch.stop();
            executeMessage.setTime(stopWatch.getTotalTimeMillis());
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }

        return executeMessage;
    }



    /**
     * 执行交互式进程并获取信息
     * 用于acmoj模式
     * @param runProcess
     * @param args  键盘输入值，用户手动输入，获取结果值
     * @return
     */
    public static ExecuteMessage runInteractProcessAndGetMessage(Process runProcess, String args) {
        ExecuteMessage executeMessage = new ExecuteMessage();

        try {
            // 向控制台输入程序
            OutputStream outputStream = runProcess.getOutputStream();
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
            String[] s = args.split(" ");
            String join = StrUtil.join("\n", s) + "\n";
            outputStreamWriter.write(join);
            // 相当于按了回车，执行输入的发送
            outputStreamWriter.flush();

            // 分批获取进程的正常输出
            InputStream inputStream = runProcess.getInputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder compileOutputStringBuilder = new StringBuilder();
            // 逐行读取
            String compileOutputLine;
            while ((compileOutputLine = bufferedReader.readLine()) != null) {
                compileOutputStringBuilder.append(compileOutputLine);
            }
            executeMessage.setMessage(compileOutputStringBuilder.toString());
            // 记得资源的释放，否则会卡死
            outputStreamWriter.close();
            outputStream.close();
            inputStream.close();
            runProcess.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return executeMessage;
    }
}
