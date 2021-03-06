package conjob.core.job;

import conjob.core.job.exception.ReadLogsException;
import conjob.core.job.exception.RunJobException;
import conjob.core.job.exception.StopJobRunException;
import conjob.core.job.model.JobRunOutcome;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

@Slf4j
public class JobRunner {
    private final DockerAdapter dockerAdapter;

    public JobRunner(DockerAdapter dockerAdapter) {
        this.dockerAdapter = dockerAdapter;
    }

    public JobRunOutcome runContainer(String containerId, long timeoutSeconds, int killTimeoutSeconds) {
        Long exitStatusCode;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Long> future = executor.submit(new WaitForContainer(dockerAdapter, containerId));
        try {
            exitStatusCode = future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException | InterruptedException ex) {
            log.warn("Problem finishing job: {}", ex.getMessage(), ex);
            try {
                exitStatusCode = dockerAdapter.stopContainer(containerId, killTimeoutSeconds);
                // The container could finish naturally before the job timeout but before the stop-to-kill timeout.
                exitStatusCode = wasStoppedOrKilled(exitStatusCode) ? -1 : exitStatusCode;
            } catch (StopJobRunException e) {
                exitStatusCode = -1L;
            }
        } finally {
            // TODO: Does this need to be in a finally block?
            executor.shutdownNow();
        }

        String output;
        try {
            output = dockerAdapter.readAllLogsUntilExit(containerId);
        } catch (ReadLogsException e) {
            output = "";
        }
        return new JobRunOutcome(exitStatusCode, output);
    }

    private boolean wasStoppedOrKilled(Long exitCode) {
        final int SIGKILL = 137;
        final int SIGTERM = 143;
        return exitCode == SIGKILL || exitCode == SIGTERM;
    }

    static class WaitForContainer implements Callable<Long> {
        private final DockerAdapter dockerAdapter;
        private final String containerId;

        public WaitForContainer(DockerAdapter dockerClient, String containerId) {
            this.dockerAdapter = dockerClient;
            this.containerId = containerId;
        }

        @Override
        public Long call() throws RunJobException {
            return dockerAdapter.startContainerThenWaitForExit(containerId);
        }
    }
}
