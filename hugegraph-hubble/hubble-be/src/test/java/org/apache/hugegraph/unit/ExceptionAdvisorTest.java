/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.apache.hugegraph.unit;

import java.lang.reflect.Field;
import org.apache.hugegraph.common.Constant;
import org.apache.hugegraph.common.Response;
import org.apache.hugegraph.exception.ExternalException;
import org.apache.hugegraph.exception.GenericException;
import org.apache.hugegraph.exception.IllegalGremlinException;
import org.apache.hugegraph.exception.InternalException;
import org.apache.hugegraph.exception.ParameterizedException;
import org.apache.hugegraph.handler.ExceptionAdvisor;
import org.apache.hugegraph.handler.MessageSourceHandler;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class ExceptionAdvisorTest {

    @Test
    public void testHandleInternalException() throws Exception {
        RuntimeException cause = new RuntimeException("cause");
        ExceptionAdvisor advisor = advisor("translated");

        Response response = advisor.exceptionHandler(
                new InternalException("internal.%s", cause, "error"));

        Assert.assertEquals(Constant.STATUS_INTERNAL_ERROR,
                            response.getStatus());
        Assert.assertEquals("translated", response.getMessage());
        Assert.assertSame(cause, response.getCause());
    }

    @Test
    public void testHandleExternalExceptionUsesExceptionStatus()
            throws Exception {
        RuntimeException cause = new RuntimeException("cause");
        ExceptionAdvisor advisor = advisor("translated");

        Response response = advisor.exceptionHandler(
                new ExternalException(422, "external.%s", cause, "error"));

        Assert.assertEquals(422, response.getStatus());
        Assert.assertEquals("translated", response.getMessage());
        Assert.assertSame(cause, response.getCause());
    }

    @Test
    public void testHandleParameterizedException() throws Exception {
        RuntimeException cause = new RuntimeException("cause");
        ExceptionAdvisor advisor = advisor("translated");

        Response response = advisor.exceptionHandler(
                new ParameterizedException("parameter.%s", cause, "error"));

        Assert.assertEquals(Constant.STATUS_BAD_REQUEST, response.getStatus());
        Assert.assertEquals("translated", response.getMessage());
        Assert.assertSame(cause, response.getCause());
    }

    @Test
    public void testHandleIllegalGremlinException() throws Exception {
        RuntimeException cause = new RuntimeException("cause");
        ExceptionAdvisor advisor = advisor("translated");

        Response response = advisor.exceptionHandler(
                new IllegalGremlinException("illegal.gremlin", cause,
                                            "g.V()"));

        Assert.assertEquals(Constant.STATUS_ILLEGAL_GREMLIN,
                            response.getStatus());
        Assert.assertEquals("translated", response.getMessage());
        Assert.assertSame(cause, response.getCause());
    }

    @Test
    public void testHandleGenericExceptionUsesFixedMessage() throws Exception {
        ExceptionAdvisor advisor = advisor("unused");

        Response response = advisor.exceptionHandler(
                new GenericException(new RuntimeException("connect failed")));

        Assert.assertEquals(Constant.STATUS_BAD_REQUEST, response.getStatus());
        Assert.assertEquals("Faied to connect the graph server. Please refer " +
                            "to the Hubble log for details.",
                            response.getMessage());
        Assert.assertNull(response.getCause());
    }

    @Test
    public void testHandleRawExceptionFallsBackToOriginalMessage()
            throws Exception {
        MessageSourceHandler handler = Mockito.mock(MessageSourceHandler.class);
        Mockito.when(handler.getMessage(Mockito.anyString(),
                                        Mockito.<Object[]>any()))
               .thenThrow(new IllegalStateException("no bundle"));
        ExceptionAdvisor advisor = advisor(handler);

        Response response = advisor.exceptionHandler(
                new RuntimeException("plain error"));

        Assert.assertEquals(Constant.STATUS_BAD_REQUEST, response.getStatus());
        Assert.assertEquals("plain error", response.getMessage());
    }

    @Test
    public void testHandleMessageConvertsNullArgumentToQuestionMark()
            throws Exception {
        MessageSourceHandler handler = Mockito.mock(MessageSourceHandler.class);
        Mockito.when(handler.getMessage(Mockito.eq("external.null"),
                                        Mockito.eq("?")))
               .thenReturn("[?]");
        ExceptionAdvisor advisor = advisor(handler);

        Response response = advisor.exceptionHandler(
                new ExternalException("external.%s", (Object) null));

        Assert.assertEquals("[?]", response.getMessage());
    }

    private static ExceptionAdvisor advisor(String message) throws Exception {
        MessageSourceHandler handler = Mockito.mock(MessageSourceHandler.class);
        Mockito.when(handler.getMessage(Mockito.anyString(),
                                        Mockito.<Object[]>any()))
               .thenReturn(message);
        return advisor(handler);
    }

    private static ExceptionAdvisor advisor(MessageSourceHandler handler)
            throws Exception {
        ExceptionAdvisor advisor = new ExceptionAdvisor();
        Field field = ExceptionAdvisor.class.getDeclaredField(
                "messageSourceHandler");
        field.setAccessible(true);
        field.set(advisor, handler);
        return advisor;
    }
}
