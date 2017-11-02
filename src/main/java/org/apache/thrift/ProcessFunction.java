/**
 *
 */
package org.apache.thrift;

import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ProcessFunction<I, T extends TBase> {
    private final String methodName;

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessFunction.class.getName());

    public ProcessFunction(String methodName) {
        this.methodName = methodName;
    }

    public final void process(int seqid, TProtocol iprot, TProtocol oprot, I iface) throws TException {
        // 获取空的参数列表实例???
        T args = getEmptyArgsInstance();
        try {
            // 读取参数???
            args.read(iprot);
        } catch (TProtocolException e) {
            // 读取消息结束
            iprot.readMessageEnd();
            // 应用异常
            TApplicationException x = new TApplicationException(TApplicationException.PROTOCOL_ERROR, e.getMessage());
            //
            oprot.writeMessageBegin(new TMessage(getMethodName(), TMessageType.EXCEPTION, seqid));
            x.write(oprot);
            oprot.writeMessageEnd();
            oprot.getTransport().flush();
            return;
        }

        iprot.readMessageEnd();
        TBase result = null;

        try {
            // 获取结果对象
            result = getResult(iface, args);
        } catch (TException tex) {
            LOGGER.error("Internal error processing " + getMethodName(), tex);
            // 应用异常
            TApplicationException x = new TApplicationException(TApplicationException.INTERNAL_ERROR,
                    "Internal error processing " + getMethodName());
            oprot.writeMessageBegin(new TMessage(getMethodName(), TMessageType.EXCEPTION, seqid));
            x.write(oprot);
            oprot.writeMessageEnd();
            oprot.getTransport().flush();
            return;
        }

        // 如果isOneway=false
        if (!isOneway()) {
            // 创建一个重播消息
            oprot.writeMessageBegin(new TMessage(getMethodName(), TMessageType.REPLY, seqid));
            result.write(oprot);
            oprot.writeMessageEnd();
            // 刷新
            oprot.getTransport().flush();
        }
    }

    protected abstract boolean isOneway();

    public abstract TBase getResult(I iface, T args) throws TException;

    public abstract T getEmptyArgsInstance();

    public String getMethodName() {
        return methodName;
    }
}
