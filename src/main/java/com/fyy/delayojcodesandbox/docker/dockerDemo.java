package com.fyy.delayojcodesandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.dockerjava.core.command.PullImageResultCallback;

import java.util.List;

public class dockerDemo {
    public static void main(String[] args) throws InterruptedException {
        //获取dockerclient
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        String image = "nginx:latest";
        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
        PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
            //当收到一个消息时调用
            @Override
            public void onNext(PullResponseItem item) {
                System.out.println("收到消息：" + item);
                super.onNext(item);
            }
        };
        pullImageCmd.exec(pullImageResultCallback).awaitCompletion();
        System.out.println("拉取完成");

        //创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        CreateContainerResponse createContainerResponse = containerCmd
                .withCmd("echo", "hello Docker")
                .exec();
        System.out.println(createContainerResponse);
        String containerId = createContainerResponse.getId();
        //fee8a0395ec2dffec73b8d7b32a13f9fc8e1bd74c8514ef8f1d6388bb36149d3    容器id

        //查看容器状态
        ListContainersCmd listContainersCmd = dockerClient.listContainersCmd();
        List<Container> list = listContainersCmd.withShowAll(true).exec();
        for (Container container : list) {
            System.out.println(container);
        }


        //启动容器
        dockerClient.startContainerCmd(containerId).exec();
        //查看日志
        LogContainerResultCallback logContainerResultCallback = new LogContainerResultCallback() { //实现回调接口，
            @Override
            public void onNext(Frame item) {
                System.out.println(item.getStreamType());
                System.out.println("日志：" + new String(item.getPayload()));
                super.onNext(item);
            }
        };
        //异步获取日志，防止日志过大，程序等待日志
        dockerClient.logContainerCmd(containerId)
                .withStdErr(true)
                .withStdOut(true)
                .exec(logContainerResultCallback)
                .awaitCompletion();//阻塞等待日志输出

        //删除容器
        dockerClient.removeContainerCmd(containerId)
                .withForce(true)//强制删除
                .exec();

        //删除镜像
        //dockerClient.removeImageCmd(image).exec();


    }
}
