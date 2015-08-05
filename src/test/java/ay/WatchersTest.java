package ay;

import org.junit.Before;
import org.junit.Test;
import rx.schedulers.Schedulers;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class WatchersTest {
    interface DefaultWatcher extends Watchers.Watcher {
        void test(boolean success);
    }

    @Watchers.Config(subject = Watchers.Subjects.BEHAVIOR)
    interface BehaviorWatcher extends Watchers.Watcher {
        void test(boolean success);
    }

    @Watchers.Config(subject = Watchers.Subjects.REPLAY)
    interface ReplayWatcher extends Watchers.Watcher {
        void test(boolean success);
    }

    @Before public void clean() throws Exception {
        Watchers.unbindAll(DefaultWatcher.class);
        Watchers.unbindAll(BehaviorWatcher.class);
    }

    @Test public void bindAfterTrigger() throws Exception {
        DefaultWatcher test = new DefaultWatcher() {
            @Override
            public void test(boolean success) {
                fail();
            }
        };
        Watchers.of(DefaultWatcher.class).test(true);
        Watchers.bind(test);
    }

    @Test public void bindBeforeTrigger() throws Exception {
        DefaultWatcher test = new DefaultWatcher() {
            @Override
            public void test(boolean success) {
                assertTrue(success);
            }
        };
        Watchers.bind(test);
        Watchers.of(DefaultWatcher.class).test(true);
    }

    @Test public void unbind() throws Exception {
        DefaultWatcher test = oneTimeWatcher();
        Watchers.bind(test);
        Watchers.of(DefaultWatcher.class).test(true);
        Watchers.unbind(test);
        Watchers.of(DefaultWatcher.class).test(true);
    }

    @Test public void bindMultipleTimesForSameObject() throws Exception {
        DefaultWatcher test = oneTimeWatcher();
        Watchers.bind(test);
        Watchers.bind(test);
        Watchers.of(DefaultWatcher.class).test(true);
    }

    @Test public void bindBeforeTriggerBehavior() throws Exception {
        BehaviorWatcher test = new BehaviorWatcher() {
            @Override
            public void test(boolean success) {
                assertTrue(success);
            }
        };
        Watchers.bind(test);
        Watchers.of(BehaviorWatcher.class).test(true);
    }

    @Test public void bindBeforeTriggerReplay() throws Exception {
        ReplayWatcher test = new ReplayWatcher() {
            @Override
            public void test(boolean success) {
                assertTrue(success);
            }
        };
        Watchers.bind(test);
        Watchers.of(ReplayWatcher.class).test(true);
    }

    @Test public void runInSameThread() throws Exception {
        final Thread thread = Thread.currentThread();
        DefaultWatcher test = new DefaultWatcher() {
            @Override
            public void test(boolean success) {
                assertTrue(thread == Thread.currentThread());
            }
        };
        Watchers.bind(test);
        Watchers.of(DefaultWatcher.class).test(true);
    }

    @Test public void runInDifferentThread() throws Exception {
        final Thread thread = Thread.currentThread();
        DefaultWatcher test = new DefaultWatcher() {
            @Override
            public void test(boolean success) {
                assertTrue(thread != Thread.currentThread());
            }
        };
        Watchers.bind(test, Schedulers.newThread());
        Watchers.of(DefaultWatcher.class).test(true);
    }

    private DefaultWatcher oneTimeWatcher() {
        final AtomicInteger integer = new AtomicInteger(0);
        return new DefaultWatcher() {
            @Override
            public void test(boolean success) {
                if (integer.getAndIncrement() == 0) {
                    assertTrue(success);
                } else {
                    fail();
                }
            }
        };
    }
}
