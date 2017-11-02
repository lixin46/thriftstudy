package org.apache.thrift;

import org.apache.thrift.protocol.*;

import java.util.Collections;
import java.util.Map;

/**
 * 基本的处理器,
 * 负责从TProtocol in中读取数据,并将处理结果写入TProtocol out中,
 * 实际的处理过程由ProcessFunction完成.
 *
 * TBaseProcessor子类根据idl文件自动生成,保存成service类的内部类,其只负责向父类注册ProcessFunction实现.
 *
 * @param <I>
 */
public abstract class TBaseProcessor<I> implements TProcessor {

    // 服务接口
    private final I iface;
    /**
     * 过程映射,key为方法名称,value为过程函数
     */
    private final Map<String, ProcessFunction<I, ? extends TBase>> processMap;

    protected TBaseProcessor(I iface, Map<String, ProcessFunction<I, ? extends TBase>> processFunctionMap) {
        this.iface = iface;
        this.processMap = processFunctionMap;
    }

    public Map<String, ProcessFunction<I, ? extends TBase>> getProcessMapView() {
        return Collections.unmodifiableMap(processMap);
    }

    @Override
    public boolean process(TProtocol in, TProtocol out) throws TException {
        // 读取消息(消息类型通常为CALL或ONEWAY)
        TMessage msg = in.readMessageBegin();
        // 根据消息名称,查找过程函数
        ProcessFunction fn = processMap.get(msg.name);
        // 如果未找到
        if (fn == null) {
            // 跳过???
            TProtocolUtil.skip(in, TType.STRUCT);
            // 读取消息结束
            in.readMessageEnd();
            // 应用异常,无效的方法名
            TApplicationException x = new TApplicationException(TApplicationException.UNKNOWN_METHOD, "Invalid method name: '" + msg.name + "'");
            // 把异常封装为消息,写入
            out.writeMessageBegin(new TMessage(msg.name, TMessageType.EXCEPTION, msg.seqid));
            x.write(out);
            out.writeMessageEnd();
            // 调用传输对象刷新
            out.getTransport().flush();
            return true;
        }
        // 调用过程函数处理
        fn.process(msg.seqid, in, out, iface);
        return true;
    }
}
