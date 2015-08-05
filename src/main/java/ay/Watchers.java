package ay;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import rx.Observable;
import rx.Scheduler;
import rx.Subscription;
import rx.functions.Action1;
import rx.subjects.*;

import java.lang.annotation.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <h3>Yet another EventBus based on RxJava's {@link Subject} and Guava's {@link Cache}.</h3>
 *
 * <p>It's thread-safe, memory-friendly, with the simplest API in the world.</p>
 *
 * <h5>1. define an event, by extending the {@link Watcher} interface</h5>
 * <pre>
 *     public interface PushWatcher extends Watcher {
 *         void notify(PushMessage msg);
 *     }
 * </pre>
 *
 * <h5>2. listener to an event, by implementing the methods. </h5>
 * <pre>
 *      PushWatcher watcher = new PushWatcher() {
 *          public void notify(PushMessage msg) {
 *              Log.d("watcher", "receive msg");
 *          }
 *      };
 * </pre>
 *
 * <h5>3. bind or unbind an event. </h5>
 * <pre>
 *      Watchers.bind(watcher);
 *      Watchers.unbind(watcher);
 * </pre>
 *
 * <h5>3. trigger an event. </h5>
 * <pre>
 *     Watchers.of(PushWatcher.class).notify(new PushMessage());
 * </pre>
 *
 * <h5>Event more..</h5>
 * <pre>
 *     public class MyFragment extends Fragment implement PushWatcher, NetworkChangedWatcher {
 *          public void notify(PushMessage msg) {
 *              Log.d("watcher", "receive msg: " + msg);
 *          }
 *          public void onNetworkChanged(boolean isConnected) {
 *              Log.d("watcher", "network connected: " + isConnected);
 *          }
 *          protected void onStart() {
 *              Watchers.bind(this);
 *          }
 *          protected void onStop() {
 *              Watchers.unbind(this);
 *          }
 *     }
 * </pre>
 */
public class Watchers {

    /** base interface for identify this is a watcher. */
    @Config
    public interface Watcher {

    }

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
        /** {@link ReplaySubject} */
        REPLAY {
            @Override
            Subject<Context, Context> create() {
                return ReplaySubject.create();
            }
        };
        abstract Subject<Context, Context> create();
    }

    static class Context {
        final AtomicBoolean consumed = new AtomicBoolean(false);
        final Method method;
        final Object[] args;

        Context(Method method, Object[] args) {
            this.method = method;
            this.args = args;
        }
    }

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

    static final Watchers instance = new Watchers();

    final ConcurrentMap<Class<? extends Watcher>, Cache<Watcher, Subscription>>
            consumers = new ConcurrentHashMap<>();
    final ConcurrentMap<Class<? extends Watcher>, Subject<Context, Context>>
            producers = new ConcurrentHashMap<>();

    final ConcurrentMap<Class<? extends Watcher>, Watcher> watchers =
            new ConcurrentHashMap<>();

    private Watchers() { }

    /** obtain a watcher adapter for trigger. */
    public static <T extends Watcher> T of(Class<T> clz) {
        return instance.getWatcher(clz);
    }

    /** bind the watcher into its adapters. with specific callback Scheduler. */
    public static void bind(Watcher watcher, Scheduler observeOn) {
        instance.bindWatcher(watcher, observeOn);
    }

    /** bind the watcher into its adapters. with same Scheduler as the adapter triggers. */
    public static void bind(Watcher watcher) {
        instance.bindWatcher(watcher);
    }

    /** unbind the watcher from its adapters. */
    public static void unbind(Watcher watcher) {
        instance.unbindWatcher(watcher);
    }

    public static void unbindAll(Class<? extends Watcher> clazz) {
        instance.unbindAllWatchers(clazz);
    }

    <T extends Watcher> T getWatcher(Class<T> clz) {
        if (!watchers.containsKey(clz)) {
            watchers.putIfAbsent(clz, create(clz));
        }

        return clz.cast(watchers.get(clz));
    }

    static <T extends Watcher> T create(Class<T> clazz) {
        if (!clazz.isInterface()) {
            throw new IllegalArgumentException(
                    "Only interface endpoint definitions are supported.");
        }
        if (!(clazz.getInterfaces().length == 1 &&
                Watcher.class.equals(clazz.getInterfaces()[0]))) {
            throw new IllegalArgumentException(
                    "Interface definitions must extend Watcher interface.");
        }
        return clazz.cast(Proxy.newProxyInstance(
                clazz.getClassLoader(), new Class[]{clazz}, new WatcherHandler(clazz)));
    }

    static class WatcherHandler implements InvocationHandler {
        private final Class<? extends Watcher> clazz;
        WatcherHandler(Class<? extends Watcher> clazz) {
            this.clazz = clazz;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            instance.trigger(clazz, method, args);
            return null;
        }
    }

    private void bindWatcher(Watcher watcher) {
        bindWatcher(watcher, null);
    }

    private void bindWatcher(final Watcher watcher, Scheduler observeOn) {
        HashSet<Class<? extends Watcher>> classes = findWatchers(watcher);

        for (Class<? extends Watcher> clazz : classes) {
            bindWatcher(watcher, observeOn, clazz);
        }
    }

    private void bindWatcher(final Watcher watcher, Scheduler observeOnScheduler,
                             Class<? extends Watcher> clazz) {
        prepare(clazz);

        Cache<Watcher, Subscription> watchers = consumers.get(clazz);

        if (watchers.getIfPresent(watcher) == null) {
            Observable<Context> obs = producers.get(clazz);
            final Config config = getWatcherConfig(clazz);
            if (config.sample() > 0 && config.timeunit() != null) {
                obs = obs.sample(config.sample(), config.timeunit());
            }
            if (config.backpressureDrop()) {
                obs = obs.onBackpressureDrop();
            }
            if (config.backpressureBuffer() > 0) {
                obs = obs.onBackpressureBuffer(config.backpressureBuffer());
            }
            if (observeOnScheduler != null) {
                obs = obs.observeOn(observeOnScheduler);
            }
            watchers.put(watcher, obs.subscribe(new Action1<Context>() {
                @Override
                public void call(Context context) {
                    if (config.once() && context.consumed.getAndSet(true)) return;
                    try {
                        Class<?> clazz = watcher.getClass();
                        Method method = clazz.getMethod(
                                context.method.getName(),
                                context.method.getParameterTypes());
                        method.invoke(watcher, context.args);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }));
        }
    }

    /** find all watchers from this object. */
    private HashSet<Class<? extends Watcher>> findWatchers(Watcher watcher) {
        Class<?> clazz = watcher.getClass();
        HashSet<Class<?>> interfaces = getAllInterfaces(clazz);
        interfaces.remove(Watcher.class);
        HashSet<Class<? extends Watcher>> watchers = new HashSet<>();
        for (Class<?> interfaze : interfaces) {
            if (isExtendsFrom(Watcher.class, interfaze)) {
                //noinspection unchecked
                watchers.add((Class<? extends Watcher>) interfaze);
            }
        }
        return watchers;
    }

    private HashSet<Class<?>> getAllInterfaces(Class<?> clazz) {
        HashSet<Class<?>> ret = new HashSet<>();
        do {
            Class<?>[] interfaces = clazz.getInterfaces();
            if (interfaces.length > 0) {
                for (Class<?> interfaze : interfaces) {
                    if (!ret.contains(interfaze)) {
                        ret.addAll(getAllInterfaces(interfaze));
                    }
                }
                ret.addAll(Arrays.asList(interfaces));
            }
            Class<?> superClass = clazz.getSuperclass();
            if (superClass == null) break;
            clazz = superClass;
        } while (clazz != Object.class);
        return ret;
    }

    private boolean isExtendsFrom(Class<?> from, Class<?> target) {
        if (from == target) return true;
        Class<?>[] interfaces = target.getInterfaces();
        for (Class<?> interfaze : interfaces) {
            if (isExtendsFrom(from, interfaze)) return true;
        }
        return false;
    }

    private void prepare(Class<? extends Watcher> clazz) {
        Subject<Context, Context> cache = producers.get(clazz);
        if (cache == null) {
            cache = createSubject(clazz);
            producers.putIfAbsent(clazz, cache);
        }

        Cache<Watcher, Subscription> watchers = consumers.get(clazz);
        if (watchers == null) {
            watchers =
                CacheBuilder.newBuilder().weakKeys().weakValues()
                    .removalListener(new RemovalListener<Watcher, Subscription>() {
                        @Override
                        public void onRemoval(
                                RemovalNotification<Watcher, Subscription> notification) {
                            Subscription sub = notification.getValue();
                            if (sub != null && !sub.isUnsubscribed()) {
                                sub.unsubscribe();
                            }
                        }
                    }).build();
            consumers.putIfAbsent(clazz, watchers);
        }
    }

    private void unbindWatcher(Watcher watcher) {
        HashSet<Class<? extends Watcher>> classes = findWatchers(watcher);
        for (Class<? extends Watcher> clazz : classes) {
            unbindWatcher(watcher, clazz);
        }
    }

    private void unbindWatcher(Watcher watcher, Class<? extends Watcher> clazz) {
        prepare(clazz);
        Cache<Watcher, Subscription> cache = consumers.get(clazz);
        if (cache != null) {
            cache.invalidate(watcher);
        }
    }

    /** for test */
    void unbindAllWatchers(Class<? extends Watcher> clazz) {
        prepare(clazz);
        consumers.get(clazz).invalidateAll();
    }

    private void trigger(Class<? extends Watcher> clazz, Method baseMethod, Object...args) {
        prepare(clazz);
        Subject<Context, Context> cache = producers.get(clazz);
        cache.onNext(new Context(baseMethod, args));
    }

    private Subject<Context, Context> createSubject(Class<? extends Watcher> clazz) {
        return getWatcherConfig(clazz).subject().create();
    }

    private Config getWatcherConfig(Class<? extends Watcher> clazz) {
        Config config = clazz.getAnnotation(Config.class);
        return config != null ? config : Watcher.class.getAnnotation(Config.class);
    }
}
