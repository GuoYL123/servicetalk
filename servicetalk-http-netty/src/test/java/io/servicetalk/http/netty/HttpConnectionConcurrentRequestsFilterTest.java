/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.http.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.client.api.MaxRequestLimitExceededException;
import io.servicetalk.concurrent.api.PublisherRule;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.Mockito;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Completable.never;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.http.api.StreamingHttpConnection.SettingKey.MAX_CONCURRENCY;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class HttpConnectionConcurrentRequestsFilterTest {
    private static final BufferAllocator allocator = DEFAULT_ALLOCATOR;
    private static final StreamingHttpRequestResponseFactory reqRespFactory =
            new DefaultStreamingHttpRequestResponseFactory(allocator, DefaultHttpHeadersFactory.INSTANCE);
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final PublisherRule<Buffer> response1Publisher = new PublisherRule<>();
    @Rule
    public final PublisherRule<Buffer> response2Publisher = new PublisherRule<>();
    @Rule
    public final PublisherRule<Buffer> response3Publisher = new PublisherRule<>();

    @Test
    public void decrementWaitsUntilResponsePayloadIsComplete() throws ExecutionException, InterruptedException {
        StreamingHttpConnection mockConnection = Mockito.mock(StreamingHttpConnection.class);
        when(mockConnection.onClose()).thenReturn(never());
        when(mockConnection.getSettingStream(eq(MAX_CONCURRENCY))).thenReturn(just(2));
        when(mockConnection.request(any())).thenReturn(
                success(reqRespFactory.ok().setPayloadBody(response1Publisher.getPublisher())),
                success(reqRespFactory.ok().setPayloadBody(response2Publisher.getPublisher())),
                success(reqRespFactory.ok().setPayloadBody(response3Publisher.getPublisher())));
        StreamingHttpConnection limitedConnection =
                new StreamingHttpConnectionConcurrentRequestsFilter(mockConnection, 2);
        StreamingHttpResponse resp1 = awaitIndefinitelyNonNull(
                limitedConnection.request(limitedConnection.get("/foo")));
        awaitIndefinitelyNonNull(limitedConnection.request(limitedConnection.get("/bar")));
        try {
            awaitIndefinitely(limitedConnection.request(limitedConnection.get("/baz")));
            fail();
        } catch (ExecutionException e) {
            assertThat(e.getCause(), is(instanceOf(MaxRequestLimitExceededException.class)));
        }

        // Consume the first response payload and ignore the content.
        resp1.getPayloadBody().forEach(chunk -> { });
        response1Publisher.sendItems(EMPTY_BUFFER);
        response1Publisher.complete();

        // Verify that a new request can be made after the first request completed.
        awaitIndefinitelyNonNull(limitedConnection.request(limitedConnection.get("/baz")));
    }
}
