/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.eris.messaging;

public interface Session
{
    static final int CUMULATIVE = 0x01;

    Sender createSender(String address, SenderMode mode) throws TransportException, SessionException, TimeoutException;

    Receiver createReceiver(String address, ReceiverMode mode) throws TransportException, SessionException, ReceiverException, TimeoutException;

    Receiver createReceiver(String address, ReceiverMode mode, CreditMode creditMode) throws TransportException, SessionException, ReceiverException, TimeoutException;

    void accept(Message msg, int ... flags) throws ReceiverException;

    void reject(Message msg, int ... flags) throws ReceiverException;

    void release(Message msg, int ... flags) throws ReceiverException;

    void setCompletionListener(CompletionListener l) throws SessionException;

    void close() throws org.eris.messaging.TransportException;
}