package com.lixin.charset;

import java.nio.charset.Charset;
import java.nio.charset.spi.CharsetProvider;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by lixin46 on 2017/11/5.
 */
public class CustomizedCharsetProvider  extends CharsetProvider{

    private List<Charset> cs = new ArrayList<>();

    public CustomizedCharsetProvider() {
        cs.add(CustomCharset.cs);
    }

    /**
     * 当前供应商拥有的所有字符集???
     * @return
     */
    @Override
    public Iterator<Charset> charsets() {
        return cs.iterator();
    }

    @Override
    public Charset charsetForName(String charsetName) {
        if("abc".equalsIgnoreCase(charsetName))
            return CustomCharset.cs;
        return null;
    }
}
