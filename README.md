# Watchers

Yet another EventBus for Android with a simpler API.

Based on [*RxJava's Subject*][1] and [*Guava's Cache*][2].

## Goals

 0. Simpler API
 1. Thread-safe
 2. Fast
 3. No memory leak
 4. Callback scheduling

### Getting Started

#### 1. Define an event, by extending the `Watcher` interface

	public interface PushWatcher extends Watcher {
	    void notify(PushMessage msg);
	}

#### 2. Listener to an event, by implementing the methods.

	PushWatcher watcher = new PushWatcher() {
	    public void notify(PushMessage msg) {
	        Log.d("watcher", "receive msg");
	    }
	};

#### 3. bind or unbind an event, trigger an event.

	Watchers.bind(watcher);
	Watchers.unbind(watcher); // not exactly necessary.
	Watchers.of(PushWatcher.class).notify(new PushMessage());

## Why reinventing the wheel?

### Proxy-based API design

[Retrofit][3] show me how elegant that a Proxy-based interface could be, I started to rethink what can I use that for API design of a EventBus.

[Guava EventBus][4], [square/otto][5] and [greenrobot/EventBus][6], their APIs look so similar: define an event; binding/unbinding an event; trigger an event. `Event Object` are necessary, `post()` method are necessary, register and unregister are paired, otherwise listeners will leak, the type of an event was exposed to user as `@Subscriber`, or `@Produce` by synchronized call like [square/otto][7].

When I dig into to these APIs, compare to the Java language itself, I found something useful.

1. A `interface` type (subclass from `Class`), can be used as the type of an event.
2. A `implement` keyword, can be used to show who are interest in the event.
3. A `Method` object from `java.lang.reflect`, can be used as  the action of an event, arguments from a method can be used as the context of the action
4. When someone decided to listen to more events, all it need to do is implementing more interfaces.

The `interface` (as a watcher) is a category of `Method` (as actions), The `Method` object is unique for one interface (as a watcher), so if there is an action a producer want to post, just write a method, arguments as context will deliver to the consumer when the method is called (as the event triggered).

To summarised, the interface (as a watcher) represent **one goal, two roles.**

**One goal** means, this interface is a contract from producer to consumer, `methodA` from consumer only interest in `methodA()` call from producer with same type (the interface), who proxied this call to all the consumers who implemented the interface with a `methodA`. The Java language itself already ensure that the method object from an interface (as a watcher) are the same as all the class that implemented it.

**Two roles** means: 

*As a producer*, its `Class` are used for distinguish different types of events, its methods are used for distinguish different types of actions.

*As a consumer*, all the methods it implemented an interface (or a watcher), represented that it’s interested in the event. So if that instance is bound into `Watchers`, `Watchers` subscribe the instance into the interface (as a watcher) it implemented, when those actions triggers, the instance will be notified.

### Thread-safety, Performance, WeakReference

Since Watchers is backed by Guava's Cache, thread-safety and performance shouldn't be an issue. 

Memory leaking handling was based on `weakKeys()` and `weakValues()` from `CacheBuilder`, Guava' Cache handle that really great already.

So there is no need for specific `unbind()`, unless you don’t want the object to receive any notification like we do. (see the example below `onStart/onStop`)

### Callback Scheduling

RxJava's Subject was chosen because of two specific reasons: rich supports for subscriber behaviour and task scheduling.

[Backpressure][8] support is the main target we are dealing with.

> For example, we have a `LoadingWatcher` that emit really fast for showing download progress, we use `sample` to limit callback frequency (for ui thread), and `BehaviorSubject`for whoever listen to the watcher, it should received the latest progress.

So there is a `@Config` annotation, use for different types of `Subject` behaviour, and `backpressure` handling, etc..

Callback scheduling make easy when use RxJava’s `observeOn()`, so we just pass into `bind(watcher, scheduler)`, all things just  done well.

## Complex example.

#### Define more watchers

	public interface NetworkChangedWatcher {
	    void onNetworkChanged(boolean isConnected);
	}
	
	@Config(subject = Subjects.BEHAVIOR, sample = 50)
	public interface LoadingWatcher {
	    void onDownloadProgress(int id, float progress);
	}

#### Bound into fragment, with callback in main thread.

	public class MyFragment extends Fragment implement 
	        PushWatcher, NetworkChangedWatcher, LoadingWatcher {
	    @Override
	    public void notify(PushMessage msg) {
	        Log.d("watcher", "receive msg: " + msg);
	    }
	
	    @Override
	    public void onNetworkChanged(boolean isConnected) {
	        Log.d("watcher", "network connected: " + isConnected);
	    }
	    @Override
	    public void onDownloadProgress(int id, float progress) {
	        Log.d("watcher", "progress " + progress);
	    }
	
	    @Override
	    protected void onStart() {
	        Watchers.bind(this, AndroidSchedulers.mainThread());
	    }
	
	    @Override
	    protected void onStop() {
	        Watchers.unbind(this);
	    }
	}



[1]:	http://reactivex.io/documentation/subject.html
[2]:	https://code.google.com/p/guava-libraries/wiki/CachesExplained
[3]:	https://github.com/square/retrofit
[4]:	https://code.google.com/p/guava-libraries/wiki/EventBusExplained
[5]:	http://square.github.io/otto/
[6]:	https://github.com/greenrobot/EventBus
[7]:	http://square.github.io/otto/
[8]:	http://reactivex.io/documentation/operators/backpressure.html