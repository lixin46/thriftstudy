package com.lixin.charset;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;

/**
 * Created by lixin46 on 2017/11/5.
 */
public class CustomCharsetDecoder extends CharsetDecoder {

    protected CustomCharsetDecoder(Charset cs, float averageCharsPerByte, float maxCharsPerByte) {
        super(cs, averageCharsPerByte, maxCharsPerByte);
    }

    @Override
    protected CoderResult decodeLoop(ByteBuffer in, CharBuffer out) {
        return CoderResult.OVERFLOW;
    }
}
