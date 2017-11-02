package com.lixin.demo;

import com.lixin.thrift.exception.UserNotFountException;
import com.lixin.thrift.service.UserService;
import com.lixin.thrift.vo.UserVo;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

/**
 * Created by lixin46 on 2017/10/23.
 */
public class Client {

    public static void main(String[] args) throws TTransportException {
        // 1.创建传输对象
        try (TTransport transport = new TSocket("localhost", 9090)) {
            // 打开连接
            transport.open();

            // 2.创建协议对象,用于数据IO
            TProtocol protocol = new TBinaryProtocol(transport);
            // 3.使用指定的service类创建Client
            UserService.Client client = new UserService.Client(protocol);

            UserVo user = client.findUserById(3);
            System.out.println(user==null?null:String.format("name=%s,age=%d",user.name,user.age));
        } catch (UserNotFountException e) {
            e.printStackTrace();
        } catch (TException e) {
            e.printStackTrace();
        }

    }

}
