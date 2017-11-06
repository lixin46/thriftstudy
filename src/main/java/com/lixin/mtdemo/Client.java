package com.lixin.mtdemo;

import com.lixin.thrift.service.UserService;
import com.lixin.thrift.vo.UserVo;
import org.apache.thrift.TException;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by lixin46 on 2017/11/2.
 */
public class Client {

    public static void main(String[] args) throws TException {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("classpath:applicationContext-client.xml");
        UserService.Iface service = context.getBean(UserService.Iface.class);
        UserVo user = service.findUserById(1);
        System.out.println(user);

    }
}
