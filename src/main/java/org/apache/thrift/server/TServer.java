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

package org.apache.thrift.server;

import org.apache.thrift.TProcessor;
import org.apache.thrift.TProcessorFactory;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TTransportFactory;


/**
 * Thrift服务的通用接口定义,
 * 对外提供启动和停止两种操作
 * serve();
 * stop();
 *
 * 同时可读写运行状态和时间处理器
 *
 * 自带参数类
 *
 * Server负责启动-->服务-->停止的逻辑
 */
public abstract class TServer {

    /**
     * AbstractServerArgs的最基本实现.
     * 不包含任何附加参数.
     * 因为AbstractServerArgs本身是抽象类,无法实例化.
     */
    public static class Args extends AbstractServerArgs<Args> {
        public Args(TServerTransport transport) {
            super(transport);
        }
    }

    /**
     * 服务器参数抽象定义,其中包含1个传输对象(ServerTransport)和5个工厂,
     * 工厂包括:
     * 1.处理器工厂(ProcessorFactory)
     * 2.输入传输工厂(TransportFactory)
     * 3.输出传输工厂(TransportFactory)
     * 4.输入协议工厂(ProtocolFactory)
     * 5.输出协议工厂(ProtocolFactory)
     *
     * @param <T>
     */
    public static abstract class AbstractServerArgs<T extends AbstractServerArgs<T>> {
        final TServerTransport serverTransport;
        // 默认且唯一的处理器工厂,提供一个单例的处理器
        TProcessorFactory processorFactory;
        TTransportFactory inputTransportFactory = new TTransportFactory();
        TTransportFactory outputTransportFactory = new TTransportFactory();
        TProtocolFactory inputProtocolFactory = new TBinaryProtocol.Factory();
        TProtocolFactory outputProtocolFactory = new TBinaryProtocol.Factory();

        /**
         * 唯一的构造方法
         * @param transport
         */
        public AbstractServerArgs(TServerTransport transport) {
            serverTransport = transport;
        }

        /**
         * 传入处理器工厂
         * @param factory
         * @return
         */
        public T processorFactory(TProcessorFactory factory) {
            this.processorFactory = factory;
            return (T) this;
        }

        /**
         * 把处理器封装为工厂
         * @param processor
         * @return
         */
        public T processor(TProcessor processor) {
            this.processorFactory = new TProcessorFactory(processor);
            return (T) this;
        }

        /**
         * 同时设置输入/输出传输工厂
         * @param factory
         * @return
         */
        public T transportFactory(TTransportFactory factory) {
            this.inputTransportFactory = factory;
            this.outputTransportFactory = factory;
            return (T) this;
        }

        /**
         * 设置输入传输工厂
         * @param factory
         * @return
         */
        public T inputTransportFactory(TTransportFactory factory) {
            this.inputTransportFactory = factory;
            return (T) this;
        }

        /**
         * 设置输出传输工厂
         * @param factory
         * @return
         */
        public T outputTransportFactory(TTransportFactory factory) {
            this.outputTransportFactory = factory;
            return (T) this;
        }

        /**
         * 同时设置输出输出协议工厂
         * @param factory
         * @return
         */
        public T protocolFactory(TProtocolFactory factory) {
            this.inputProtocolFactory = factory;
            this.outputProtocolFactory = factory;
            return (T) this;
        }

        /**
         * 设置输入协议工厂
         * @param factory
         * @return
         */
        public T inputProtocolFactory(TProtocolFactory factory) {
            this.inputProtocolFactory = factory;
            return (T) this;
        }

        /**
         * 设置输出协议工厂
         * @param factory
         * @return
         */
        public T outputProtocolFactory(TProtocolFactory factory) {
            this.outputProtocolFactory = factory;
            return (T) this;
        }
    }

    /**
     * Core processor
     */
    /**
     * 核心处理器
     */
    protected TProcessorFactory processorFactory_;

    /**
     * 服务端传输对象
     */
    protected TServerTransport serverTransport_;

    /**
     * 输入传输工厂
     */
    protected TTransportFactory inputTransportFactory_;

    /**
     * 输出传输工厂
     */
    protected TTransportFactory outputTransportFactory_;

    /**
     * 输入协议工厂
     */
    protected TProtocolFactory inputProtocolFactory_;

    /**
     * 输出协议工厂
     */
    protected TProtocolFactory outputProtocolFactory_;

    /**
     * 是否正在运行,由子类实现调用getter/setter控制
     */
    private boolean isServing;

    /**
     * 事件处理器???
     */
    protected TServerEventHandler eventHandler_;

    // Flag for stopping the server
    // Please see THRIFT-1795 for the usage of this flag
    /**
     * 是否应该停止,通过getter/setter控制
     */
    protected volatile boolean stopped_ = false;

    /**
     * 唯一构造方法
     * 把AbstractServerArgs中保存的一个ServerTransport和5个xxxFactory保存到自身
     * @param args
     */
    protected TServer(AbstractServerArgs args) {
        processorFactory_ = args.processorFactory;
        serverTransport_ = args.serverTransport;
        inputTransportFactory_ = args.inputTransportFactory;
        outputTransportFactory_ = args.outputTransportFactory;
        inputProtocolFactory_ = args.inputProtocolFactory;
        outputProtocolFactory_ = args.outputProtocolFactory;
    }

    /**
     * The run method fires up the server and gets things going.
     */
    /**
     * 启动服务
     */
    public abstract void serve();

    /**
     * Stop the server. This is optional on a per-implementation basis. Not
     * all servers are required to be cleanly stoppable.
     */
    /**
     * 停止服务
     */
    public void stop() {
    }

    public boolean isServing() {
        return isServing;
    }

    protected void setServing(boolean serving) {
        isServing = serving;
    }

    public void setServerEventHandler(TServerEventHandler eventHandler) {
        eventHandler_ = eventHandler;
    }

    public TServerEventHandler getEventHandler() {
        return eventHandler_;
    }

    public boolean getShouldStop() {
        return this.stopped_;
    }

    public void setShouldStop(boolean shouldStop) {
        this.stopped_ = shouldStop;
    }
}
