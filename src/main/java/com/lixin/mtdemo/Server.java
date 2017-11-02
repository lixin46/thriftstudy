package com.lixin.mtdemo;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by lixin46 on 2017/11/2.
 */
public class Server {

    public static void main(String[] args) {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("classpath:applicationContext-server.xml");
        System.out.println("服务启动:"+context.getId());
    }
}
