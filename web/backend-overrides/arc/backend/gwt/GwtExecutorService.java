package arc.backend.gwt;

import java.util.*;
import java.util.concurrent.*;

/**
 * Browser-safe ExecutorService.
 *
 * The first current-Mindustry web milestone deliberately executes submitted work
 * synchronously.  This preserves code which submits work and immediately waits for
 * it, and avoids deadlocks from pretending browser JavaScript has JVM threads.
 * Expensive systems can later be moved to Web Workers behind the same contract.
 */
public class GwtExecutorService implements ExecutorService{
    private boolean shutdown;

    private void ensureRunning(){
        if(shutdown) throw new RejectedExecutionException("browser executor is shut down");
    }

    @Override
    public void execute(Runnable command){
        ensureRunning();
        command.run();
    }

    @Override
    public void shutdown(){
        shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow(){
        shutdown = true;
        return new ArrayList<>();
    }

    @Override
    public boolean isShutdown(){
        return shutdown;
    }

    @Override
    public boolean isTerminated(){
        return shutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit){
        return shutdown;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task){
        ensureRunning();
        try{
            return ImmediateFuture.success(task.call());
        }catch(Throwable error){
            return ImmediateFuture.failure(error);
        }
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result){
        ensureRunning();
        try{
            task.run();
            return ImmediateFuture.success(result);
        }catch(Throwable error){
            return ImmediateFuture.failure(error);
        }
    }

    @Override
    public Future<?> submit(Runnable task){
        return submit(task, null);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks){
        ensureRunning();
        List<Future<T>> out = new ArrayList<>();
        for(Callable<T> task : tasks) out.add(submit(task));
        return out;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit){
        return invokeAll(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws ExecutionException{
        ensureRunning();
        Throwable last = null;
        for(Callable<T> task : tasks){
            try{
                return task.call();
            }catch(Throwable error){
                last = error;
            }
        }
        throw new ExecutionException(last == null ? new IllegalArgumentException("no tasks") : last);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws ExecutionException{
        return invokeAny(tasks);
    }

    private static final class ImmediateFuture<T> implements Future<T>{
        private final T value;
        private final Throwable error;

        private ImmediateFuture(T value, Throwable error){
            this.value = value;
            this.error = error;
        }

        static <T> ImmediateFuture<T> success(T value){
            return new ImmediateFuture<>(value, null);
        }

        static <T> ImmediateFuture<T> failure(Throwable error){
            return new ImmediateFuture<>(null, error);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning){
            return false;
        }

        @Override
        public boolean isCancelled(){
            return false;
        }

        @Override
        public boolean isDone(){
            return true;
        }

        @Override
        public T get() throws ExecutionException{
            if(error != null) throw new ExecutionException(error);
            return value;
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws ExecutionException{
            return get();
        }
    }
}
