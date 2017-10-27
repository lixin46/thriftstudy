package com.lixin.demo;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import tutorial.Calculator;
import tutorial.InvalidOperation;
import tutorial.Operation;
import tutorial.Work;

/**
 * Created by lixin46 on 2017/10/23.
 */
public class Client {

    public static void main(String[] args) {

        try {
            // 1.创建传输层
            TTransport transport = new TSocket("localhost", 9090);
            // 打开连接
            transport.open();

            // 2.创建协议对象,用于数据IO
            TProtocol protocol = new TBinaryProtocol(transport);
            // 3.使用指定的service类创建Client
            Calculator.Client client = new Calculator.Client(protocol);

            perform(client);

            transport.close();
        } catch (TException x) {
            x.printStackTrace();
        }
    }

    private static void perform(Calculator.Client client) throws TException {
//        client.ping();
//        System.out.println("ping()");
//
//        int sum = client.add(1, 1);
//        System.out.println("1+1=" + sum);
//
        Work work = new Work();
//
        work.op = Operation.DIVIDE;
        work.num1 = 1;
        work.num2 = 0;
//        try {
//            int quotient = client.calculate(1, work);
//            System.out.println("Whoa we can divide by 0");
//        } catch (InvalidOperation io) {
//            System.out.println("Invalid operation: " + io.why);
//        }
//
//        work.op = Operation.SUBTRACT;
//        work.num1 = 15;
//        work.num2 = 10;
        try {
            int diff = client.calculate(1, work);
            System.out.println("15-10=" + diff);
        } catch (InvalidOperation io) {
            System.out.println("Invalid operation: " + io.why);
        }

//        SharedStruct log = client.getStruct(1);
//        System.out.println("Check log: " + log.value);
    }
}
