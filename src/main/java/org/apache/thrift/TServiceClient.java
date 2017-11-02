/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.thrift;

import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TProtocol;

/**
 * A TServiceClient is used to communicate with a TService implementation
 * across protocols and transports.
 */
public abstract class TServiceClient {
    public TServiceClient(TProtocol prot) {
        this(prot, prot);
    }

    public TServiceClient(TProtocol iprot, TProtocol oprot) {
        iprot_ = iprot;
        oprot_ = oprot;
    }

    protected TProtocol iprot_;
    protected TProtocol oprot_;

    protected int seqid_;

    /**
     * Get the TProtocol being used as the input (read) protocol.
     *
     * @return the TProtocol being used as the input (read) protocol.
     */
    public TProtocol getInputProtocol() {
        return this.iprot_;
    }

    /**
     * Get the TProtocol being used as the output (write) protocol.
     *
     * @return the TProtocol being used as the output (write) protocol.
     */
    public TProtocol getOutputProtocol() {
        return this.oprot_;
    }

    protected void sendBase(String methodName, TBase<?, ?> args) throws TException {
        // 消息类型为调用
        sendBase(methodName, args, TMessageType.CALL);
    }

    protected void sendBaseOneway(String methodName, TBase<?, ?> args) throws TException {
        sendBase(methodName, args, TMessageType.ONEWAY);
    }

    private void sendBase(String methodName, TBase<?, ?> args, byte type) throws TException {
        // 开始写入消息
        oprot_.writeMessageBegin(new TMessage(methodName, type, ++seqid_));
        // 写入参数
        args.write(oprot_);
        // 结束写入消息
        oprot_.writeMessageEnd();
        //
        oprot_.getTransport().flush();
    }

    protected void receiveBase(TBase<?, ?> result, String methodName) throws TException {
        // 开始读取消息
        TMessage msg = iprot_.readMessageBegin();
        // 如果消息类型为exception,则代表服务端发生了非用户定义的异常
        if (msg.type == TMessageType.EXCEPTION) {
            // 读取异常
            TApplicationException x = TApplicationException.read(iprot_);
            // 读取消息结束
            iprot_.readMessageEnd();
            // 抛出读取到的异常
            throw x;
        }
        // 消息序列号不一致,则报错
        if (msg.seqid != seqid_) {
            throw new TApplicationException(TApplicationException.BAD_SEQUENCE_ID, methodName + " failed: out of sequence response");
        }
        // 读取结果
        result.read(iprot_);
        // 读取消息结束
        iprot_.readMessageEnd();
    }
}
