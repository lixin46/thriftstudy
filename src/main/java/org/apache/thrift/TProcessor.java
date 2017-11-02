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

import org.apache.thrift.protocol.TProtocol;

/**
 * 处理器通用接口,
 * 它从输入协议读取数据,处理,并将结果通过输出协议写出.
 * 所有通过idl文件定义的服务类,内部都包含一个TProcessor接口的实现类
 */
public interface TProcessor {

    /**
     * @param in
     * @param out
     * @return 是否成功处理
     * @throws TException
     */
    boolean process(TProtocol in, TProtocol out)
            throws TException;
}
