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

package com.lixin;

import org.apache.thrift.transport.TTransportException;

import java.io.Closeable;

/**
 * Generic class that encapsulates the I/O layer. This is basically a thin
 * wrapper around the combined functionality of Java input/output streams.
 */

/**
 * IO层的通用封装.
 */
public interface TTransport extends Closeable {

    boolean isOpen();

    boolean peek();

    void open() throws TTransportException;

    void close();

    int read(byte[] buf, int off, int len) throws TTransportException;

    int readAll(byte[] buf, int off, int len) throws TTransportException;

    void write(byte[] buf) throws TTransportException;

    void write(byte[] buf, int off, int len) throws TTransportException;

    void flush() throws TTransportException;

    byte[] getBuffer();

    int getBufferPosition();

    int getBytesRemainingInBuffer();

    void consumeBuffer(int len);
}
