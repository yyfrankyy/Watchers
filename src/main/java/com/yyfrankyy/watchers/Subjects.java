package com.yyfrankyy.watchers;

import rx.subjects.*;

/** all kinds of subjects. */
public enum Subjects {
    /** {@link PublishSubject} */
    PUBLISH {
        @Override
        Subject<Context, Context> create() {
            return PublishSubject.create();
        }
    },
    /** {@link BehaviorSubject} */
    BEHAVIOR {
        @Override
        Subject<Context, Context> create() {
            return BehaviorSubject.create();
        }
    },
    /** {@link AsyncSubject} */
    ASYNC {
        @Override
        Subject<Context, Context> create() {
            return AsyncSubject.create();
        }
    },
    /** {@link ReplaySubject} */
    REPLAY {
        @Override
        Subject<Context, Context> create() {
            return ReplaySubject.create();
        }
    };
    abstract Subject<Context, Context> create();
}
