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

import org.apache.thrift.TException;
import org.apache.thrift.protocol.*;
import org.apache.thrift.scheme.IScheme;

import java.nio.ByteBuffer;

public interface TProtocol {

    void writeMessageBegin(TMessage message) throws TException;

    void writeMessageEnd() throws TException;

    void writeStructBegin(TStruct struct) throws TException;

    void writeStructEnd() throws TException;

    void writeFieldBegin(TField field) throws TException;

    void writeFieldEnd() throws TException;

    void writeFieldStop() throws TException;

    void writeMapBegin(TMap map) throws TException;

    void writeMapEnd() throws TException;

    void writeListBegin(TList list) throws TException;

    void writeListEnd() throws TException;

    void writeSetBegin(TSet set) throws TException;

    void writeSetEnd() throws TException;

    void writeBool(boolean b) throws TException;

    void writeByte(byte b) throws TException;

    void writeI16(short i16) throws TException;

    void writeI32(int i32) throws TException;

    void writeI64(long i64) throws TException;

    void writeDouble(double dub) throws TException;

    void writeString(String str) throws TException;

    void writeBinary(ByteBuffer buf) throws TException;

    TMessage readMessageBegin() throws TException;

    void readMessageEnd() throws TException;

    TStruct readStructBegin() throws TException;

    void readStructEnd() throws TException;

    TField readFieldBegin() throws TException;

    void readFieldEnd() throws TException;

    TMap readMapBegin() throws TException;

    void readMapEnd() throws TException;

    TList readListBegin() throws TException;

    void readListEnd() throws TException;

    TSet readSetBegin() throws TException;

    void readSetEnd() throws TException;

    boolean readBool() throws TException;

    byte readByte() throws TException;

    short readI16() throws TException;

    int readI32() throws TException;

    long readI64() throws TException;

    double readDouble() throws TException;

    String readString() throws TException;

    ByteBuffer readBinary() throws TException;

    void reset();

    Class<? extends IScheme> getScheme();
}
