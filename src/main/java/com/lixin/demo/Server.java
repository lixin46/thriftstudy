package com.lixin.demo;

import com.lixin.thrift.service.impl.UserServiceImpl;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;

/**
 * Created by lixin46 on 2017/10/23.
 */
public class Server {

    // 创建服务实现
    public static UserService.Iface userService = new UserServiceImpl();

    // 创建处理器???
    public static UserService.Processor processor = new  UserService.Processor(userService);;

    public static void main(String[] args) {

        doMain();
    }

    public static void doMain() {
        Runnable simple = new Runnable() {
            public void run() {
                try {
                    // 1.创建传输对象
                    TServerTransport serverTransport = new TServerSocket(9090);
                    // 2.创建TServer,服务器
                    TServer server = new TSimpleServer(new TServer.Args(serverTransport).processor(processor));
                    System.out.println("Starting the simple server...");
                    // 开始服务
                    server.serve();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        new Thread(simple).start();
        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run() {

            }
        });
    }


}
