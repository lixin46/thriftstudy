package com.lixin.demo;

import org.apache.thrift.TException;
import shared.SharedStruct;
import tutorial.Calculator;
import tutorial.InvalidOperation;
import tutorial.Work;

/**
 * Created by lixin46 on 2017/10/23.
 */
public class CalculatorHandler implements Calculator.Iface{
    @Override
    public void ping() throws TException {
        System.out.println("calling ping");

    }

    @Override
    public int add(int num1, int num2) throws TException {
        return num1 + num2;
    }

    @Override
    public int calculate(int logid, Work w) throws InvalidOperation, TException {
        System.out.println("calculate logid:"+logid);
        throw new InvalidOperation(1,"无效的操作:"+logid);
//        return logid;
    }

    @Override
    public void zip() throws TException {
        System.out.println("calling zip");
    }

    @Override
    public SharedStruct getStruct(int key) throws TException {
        return new SharedStruct(1,"hello world");
    }
}
