package com.lixin;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

/**
 * Created by lixin46 on 2017/11/4.
 */
public class BytBufferStudy {

    @Test
    public void test1() {
        /**
         * 创建缓冲区,
         * position=0,limit=1024,capacity=1024,mark=-1
         */
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.put((byte)'h')
                .put((byte)'e')
                .put((byte)'l')
                .put((byte)'l')
                .put((byte)'o');
        assertEquals(5,buffer.position());
        assertEquals(1024,buffer.limit());
        assertEquals(1024,buffer.capacity());

        // 写转读
        buffer.flip();
        assertEquals(0,buffer.position());
        assertEquals(5,buffer.limit());
        assertEquals(1024,buffer.capacity());
    }
}
