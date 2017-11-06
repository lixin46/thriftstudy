package com.lixin.charset;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

/**
 * Created by lixin46 on 2017/11/5.
 */
public class CustomCharset extends Charset {

    public static final Charset cs = new CustomCharset();

    private CustomCharset() {
        super("abc", new String[0]);
    }

    @Override
    public boolean contains(Charset cs) {
        return false;
    }

    @Override
    public CharsetDecoder newDecoder() {
        return null;
    }

    @Override
    public CharsetEncoder newEncoder() {
        return null;
    }
}
