package io.slice.stream.engine.analyzer.fake;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

public class DirectExecutorService extends AbstractExecutorService {

    private int executedTaskCount = 0;

    @Override
    public void execute(Runnable command) {
        executedTaskCount++;
        command.run();
    }

    public int getExecutedTaskCount() {
        return executedTaskCount;
    }

    @Override
    public void shutdown() {}

    @Override
    public List<Runnable> shutdownNow() {
        return List.of();
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return true;
    }
}
