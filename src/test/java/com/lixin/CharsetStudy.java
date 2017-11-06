package com.lixin;

import org.junit.Test;
import sun.java2d.pipe.SpanIterator;

import javax.sound.midi.Soundbank;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.spi.CharsetProvider;
import java.util.*;

/**
 * Created by lixin46 on 2017/11/5.
 */
public class CharsetStudy {

    public CharsetStudy() {
//        Charset defaultCharset = Charset.defaultCharset();
    }

    @Test
    public void test1() {
        SortedMap<String, Charset> charsets = Charset.availableCharsets();
        for (Map.Entry<String,Charset> cs : charsets.entrySet()) {
            Charset value = cs.getValue();
            System.out.println("name:"+value.name());
            System.out.println("aliases:"+value.aliases());
            System.out.println("displayName:"+value.displayName());
        }

    }

    @Test
    public void test2() {
        Charset cs = Charset.forName("utf8");
        System.out.println(cs.name());
        Set<String> aliases = cs.aliases();
        System.out.println(aliases);
    }

    @Test
    public void test3() throws CharacterCodingException {
        Charset cs = Charset.forName("utf8");

        CharsetEncoder encoder = cs.newEncoder();
        ByteBuffer encoded = encoder.encode(CharBuffer.wrap("你好"));

    }

    /**
     * 测试自定义字符集是否生效
     */
    @Test
    public void test4() {
        boolean supported = Charset.isSupported("abc");
        System.out.println(supported);
    }

    @Test
    public void test5() {
        ServiceLoader<CharsetProvider> loaded = ServiceLoader.load(CharsetProvider.class, ClassLoader.getSystemClassLoader());
        Iterator<CharsetProvider> i = loaded.iterator();
        for (;i.hasNext();) {
            CharsetProvider provider = i.next();
            System.out.println(provider.getClass().getName());

        }
    }
}
