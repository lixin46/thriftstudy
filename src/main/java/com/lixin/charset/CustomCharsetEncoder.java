package com.lixin.charset;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

/**
 * Created by lixin46 on 2017/11/5.
 */
public class CustomCharsetEncoder extends CharsetEncoder {

    protected CustomCharsetEncoder(Charset cs, float averageBytesPerChar, float maxBytesPerChar, byte[] replacement) {
        super(cs, averageBytesPerChar, maxBytesPerChar, replacement);
    }

    @Override
    protected CoderResult encodeLoop(CharBuffer in, ByteBuffer out) {
        return CoderResult.OVERFLOW;
    }
}
