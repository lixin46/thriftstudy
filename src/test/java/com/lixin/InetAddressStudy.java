package com.lixin;

import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

/**
 * Created by lixin46 on 2017/11/1.
 */
public class InetAddressStudy {

    @Test
    public void test1() throws UnknownHostException {
        InetAddress addr = InetAddress.getLocalHost();
        System.out.println(addr.getClass().getName());
        System.out.println(addr.getHostAddress());
        System.out.println(addr.getHostName());

    }

    /**
     * 只要第一个字符不是16进制数字或者冒号,则当做hostname进行dns解析,
     * 否则按点拆分成数组,且如果数组元素都可被解析为数字的话,会被直接当做ipv4,
     * 创建一个InetAddress对象并返回,此时hostname为null,在调用getHostName()时再根据ip反解域名.
     *
     * @throws UnknownHostException
     */
    @Test
    public void test2() throws UnknownHostException {
        InetAddress[] allByName = InetAddress.getAllByName("broadcasthost");
    }

    @Test
    public void test3() {
        int num = Character.digit('g', 16);
        System.out.println(num);
    }

    /**
     * InetAddress对象在调用getHostName()方法时,才会根据ip获取hostname
     * @throws UnknownHostException
     */
    @Test
    public void test4() throws UnknownHostException {
        InetAddress[] allByName = InetAddress.getAllByName("127.0.0.1");
        String hostName = allByName[0].getHostName();
        System.out.println(hostName);
    }

    /**
     * 当根据ip地址无法找到hostname时,就以ip地址作为hostname
     * @throws UnknownHostException
     */
    @Test
    public void test5() throws UnknownHostException {
        InetAddress[] allByName = InetAddress.getAllByName("1.2.3.4");
        String hostName = allByName[0].getHostName();
        System.out.println(hostName);
    }

    /**
     * 创建InetSocketAddress时,只指定端口的话,默认获取一个代表任何本地地址的ip,
     * 通常是0.0.0.0
     */
    @Test
    public void test6() {
        InetSocketAddress socketAddr = new InetSocketAddress(2000);
        InetAddress addr = socketAddr.getAddress();
        System.out.println(addr.getHostAddress());
        System.out.println(addr.getHostName());
    }
}
