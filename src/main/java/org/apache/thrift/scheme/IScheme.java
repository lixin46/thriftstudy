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
package org.apache.thrift.scheme;

import org.apache.thrift.TBase;

/**
 * 方案定义.
 * 主要定义了对结构数据的读写,由子类实现具体的读写方式,子类代码全部根据idl文件自动生成.
 *
 * thrift把方法的参数列表和返回值,都当做一种struct结构.
 * struct是field的集合.无论是形参,返回值还是声明的异常,都是field的一种.
 * @param <T>
 */
public interface IScheme<T extends TBase> {

    /**
     * 从TProtocol中读取结构数据
     * @param iproto
     * @param struct
     * @throws org.apache.thrift.TException
     */
    void read(org.apache.thrift.protocol.TProtocol iproto, T struct) throws org.apache.thrift.TException;

    /**
     * 向TProtocol中写入结构数据
     * @param oproto
     * @param struct
     * @throws org.apache.thrift.TException
     */
    void write(org.apache.thrift.protocol.TProtocol oproto, T struct) throws org.apache.thrift.TException;

}
