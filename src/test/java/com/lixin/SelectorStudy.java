package com.lixin;

import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/**
 * Created by lixin46 on 2017/11/4.
 */
public class SelectorStudy {

    @Test
    public void test1() throws IOException {
        // 默认KQueueSelectorImpl
        Selector selector = Selector.open();
        SocketChannel channel1 = SocketChannel.open(new InetSocketAddress(InetAddress.getByName("www.baidu.com"),80));
        SelectionKey key = channel1.register(selector, SelectionKey.OP_READ);
        int readyCount = selector.select();

    }
}
