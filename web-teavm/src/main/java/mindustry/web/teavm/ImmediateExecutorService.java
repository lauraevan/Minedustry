package mindustry.web.teavm;

import java.util.*;
import java.util.concurrent.*;

/** Deterministic single-thread executor for the first browser milestone. */
public final class ImmediateExecutorService extends AbstractExecutorService{
    private boolean shutdown;
    @Override public void shutdown(){ shutdown = true; }
    @Override public List<Runnable> shutdownNow(){ shutdown = true; return Collections.emptyList(); }
    @Override public boolean isShutdown(){ return shutdown; }
    @Override public boolean isTerminated(){ return shutdown; }
    @Override public boolean awaitTermination(long timeout, TimeUnit unit){ return shutdown; }
    @Override public void execute(Runnable command){ if(shutdown) throw new RejectedExecutionException(); command.run(); }
}
