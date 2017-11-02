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

import org.apache.thrift.TException;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple singlethreaded server for testing.
 */

/**
 * 简单的单线程Server实现,这个实现通常用于测试
 */
public class TSimpleServer extends TServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TSimpleServer.class.getName());

    public TSimpleServer(AbstractServerArgs args) {
        super(args);
    }

    public void serve() {
        try {
            /**
             * ServerTransport开启监听,如何???
             */
            serverTransport_.listen();
        } catch (TTransportException ttx) {
            LOGGER.error("Error occurred during listening.", ttx);
            return;
        }

        // Run the preServe event
        if (eventHandler_ != null) {
            // 进行事件通知
            eventHandler_.preServe();
        }

        // 设置状态为服务中
        setServing(true);

        // 当stopped为false时,循环
        while (!stopped_) {
            TTransport client = null;
            TProcessor processor = null;
            TTransport inputTransport = null;
            TTransport outputTransport = null;
            TProtocol inputProtocol = null;
            TProtocol outputProtocol = null;
            ServerContext connectionContext = null;
            try {
                // 接受client
                client = serverTransport_.accept();
                if (client != null) {
                    // 根据Transport获取Processor对象,也就是具体的服务实现
                    processor = processorFactory_.getProcessor(client);
                    // 调用工厂产生输入传输对象
                    // 原样返回
                    inputTransport = inputTransportFactory_.getTransport(client);
                    // 调用工厂产生输出传输对象
                    // 原样返回
                    outputTransport = outputTransportFactory_.getTransport(client);
                    // 根据输入Transport,调用工厂产生输入协议对象
                    // TProtocol对象内部持有TTransport
                    inputProtocol = inputProtocolFactory_.getProtocol(inputTransport);
                    // 根据输出Transport,调用工厂产生输出协议对象
                    outputProtocol = outputProtocolFactory_.getProtocol(outputTransport);
                    // 存在时间处理器
                    if (eventHandler_ != null) {
                        // 创建ServerContext连接上线问
                        connectionContext = eventHandler_.createContext(inputProtocol, outputProtocol);
                    }

                    while (true) {
                        if (eventHandler_ != null) {
                            // 处理上下文
                            eventHandler_.processContext(connectionContext, inputTransport, outputTransport);
                        }
                        // 调用Processor进行处理,如果返回false,则退出循环
                        if (!processor.process(inputProtocol, outputProtocol)) {
                            break;
                        }
                    }
                }
            } catch (TTransportException ttx) {
                // Client died, just move on
            } catch (TException tx) {
                if (!stopped_) {
                    LOGGER.error("Thrift error occurred during processing of message.", tx);
                }
            } catch (Exception x) {
                if (!stopped_) {
                    LOGGER.error("Error occurred during processing of message.", x);
                }
            }

            if (eventHandler_ != null) {
                // 删除ServerContext
                eventHandler_.deleteContext(connectionContext, inputProtocol, outputProtocol);
            }

            if (inputTransport != null) {
                inputTransport.close();
            }

            if (outputTransport != null) {
                outputTransport.close();
            }

        }
        // 当退出循环的时候,服务器也就结束停止了
        setServing(false);
    }

    public void stop() {
        stopped_ = true;
        serverTransport_.interrupt();
    }
}
