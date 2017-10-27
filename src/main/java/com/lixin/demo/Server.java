package com.lixin.demo;

import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import tutorial.Calculator;

/**
 * Created by lixin46 on 2017/10/23.
 */
public class Server {

    public static CalculatorHandler handler;

    public static Calculator.Processor processor;

    public static void main(String[] args) {

        try {
            handler = new CalculatorHandler();
            processor = new Calculator.Processor(handler);

            Runnable simple = new Runnable() {
                public void run() {
                    try {
                        // 1.创建传输层
                        TServerTransport serverTransport = new TServerSocket(9090);
                        // 2.创建TServer
                        TServer server = new TSimpleServer(new TServer.Args(serverTransport).processor(processor));

                        System.out.println("Starting the simple server...");
                        server.serve();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };

            new Thread(simple).start();
        } catch (Exception x) {
            x.printStackTrace();
        }
    }



}
