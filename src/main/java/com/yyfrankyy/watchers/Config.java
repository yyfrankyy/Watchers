package com.yyfrankyy.watchers;

import rx.Observable;
import rx.subjects.PublishSubject;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/** Configuration for watcher. */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Config {
    /** choose a subject, or use {@link PublishSubject} by default. */
    Subjects subject() default Subjects.PUBLISH;
    /** delegate for {@link Observable#sample(long, TimeUnit)} */
    long sample() default 0;
    /** delegate for {@link Observable#sample(long, TimeUnit)} */
    TimeUnit timeunit() default TimeUnit.MILLISECONDS;
    /** delegate for {@link Observable#onBackpressureDrop()} */
    boolean backpressureDrop() default false;
    /** delegate for {@link Observable#onBackpressureBuffer(long)} */
    int backpressureBuffer() default 0;
    /** consume the arguments just once, for the listeners whom first bind to this watcher. */
    boolean once() default false;
}
