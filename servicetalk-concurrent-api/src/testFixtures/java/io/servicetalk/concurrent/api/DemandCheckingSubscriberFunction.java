/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.PublisherSource.Subscriber;

import java.util.function.Function;

/**
 * Wraps {@link Subscriber}s in a {@link DemandCheckingSubscriber}.
 *
 * @param <T> Type of items received by the {@code Subscriber}.
 */
public final class DemandCheckingSubscriberFunction<T>
        implements Function<Subscriber<? super T>, Subscriber<? super T>> {

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
        return new DemandCheckingSubscriber<>(subscriber);
    }
}
