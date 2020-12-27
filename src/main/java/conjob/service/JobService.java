package conjob.service;

import com.spotify.docker.client.exceptions.DockerException;
import conjob.config.JobConfig;
import conjob.core.job.RunJobRateLimiter;
import conjob.core.job.config.ConfigUtil;
import conjob.core.job.model.JobRun;
import conjob.core.job.model.JobRunConclusion;
import conjob.core.job.model.PullStrategy;
import conjob.service.convert.JobResponseConverter;

import javax.ws.rs.core.Response;
import java.util.Optional;
import java.util.concurrent.*;

public class JobService {
    private static final String SECRETS_VOLUME_MOUNT_PATH = "/run/build/secrets";
    private static final String SECRETS_VOLUME_MOUNT_OPTIONS = "ro";

    private static final int TIMED_OUT_EXIT_CODE = -1;

    private final RunJobRateLimiter runJobRateLimiter;
    private final JobConfig.LimitConfig limitConfig;
    private final DockerAdapter dockerAdapter;

    public JobService(
            DockerAdapter dockerAdapter,
            RunJobRateLimiter runJobRateLimiter, JobConfig.LimitConfig limitConfig) {
        this.dockerAdapter = dockerAdapter;
        this.runJobRateLimiter = runJobRateLimiter;
        this.limitConfig = limitConfig;
    }

    public Response createResponse(String imageName) throws DockerException, InterruptedException, SecretStoreException {
        return createResponse(imageName, "");
    }

    public Response createResponse(String imageName, String input) throws DockerException, InterruptedException, SecretStoreException {
        return createResponse(imageName, input, PullStrategy.ALWAYS.name());
    }

    public Response createResponse(String imageName, String input, String pullStrategyName) throws DockerException, InterruptedException, SecretStoreException {
        PullStrategy pullStrategy = PullStrategy.valueOf(pullStrategyName.toUpperCase());
        JobRun jobRun = runJob(imageName, input, pullStrategy);
        return createResponseFrom(jobRun);
    }

    public Response createJsonResponse(String imageName) throws DockerException, InterruptedException, SecretStoreException {
        return createJsonResponse(imageName, "");
    }

    public Response createJsonResponse(String imageName, String input) throws DockerException, InterruptedException, SecretStoreException {
        return createJsonResponse(imageName, input, PullStrategy.ALWAYS.name());
    }

    public Response createJsonResponse(String imageName, String input, String pullStrategyName) throws DockerException, InterruptedException, SecretStoreException {
        PullStrategy pullStrategy = PullStrategy.valueOf(pullStrategyName.toUpperCase());
        JobRun jobRun = runJob(imageName, input, pullStrategy);
        return createJsonResponseFrom(jobRun);
    }

    private Response createResponseFrom(JobRun jobRun) {
        return new ResponseCreator().create(jobRun.getConclusion())
                .entity(jobRun.getOutput())
                .build();
    }

    private Response createJsonResponseFrom(JobRun jobRun) {
        return new ResponseCreator().create(jobRun.getConclusion())
                .entity(new JobResponseConverter().from(jobRun))
                .build();
    }

    private JobRun runJob(String imageName, String input, PullStrategy pullStrategy)
            throws SecretStoreException {
        long maxTimeoutSeconds = limitConfig.getMaxTimeoutSeconds();
        int maxKillTimeoutSeconds = Math.toIntExact(limitConfig.getMaxKillTimeoutSeconds());

        JobRun jobRun;
        if (runJobRateLimiter.isAtLimit()) {
            jobRun = new JobRun(JobRunConclusion.REJECTED, "", -1);
        } else {
            String correspondingSecretsVolumeName = new ConfigUtil().translateToVolumeName(imageName);
            String secretId = new SecretStore(dockerAdapter)
                    .findSecret(correspondingSecretsVolumeName)
                    .orElse(null);

            JobRunConfig jobRunConfig = new JobRunConfigCreator().getContainerConfig(imageName, input, secretId);

            Optional<String> jobId = Optional.empty();
            try {
                jobId = new JobRunCreator(dockerAdapter).createJob(jobRunConfig, pullStrategy);
            } catch (CreateJobRunException | JobUpdateException e2) {
                runJobRateLimiter.decrementRunningJobsCount();
            }

            if (jobId.isEmpty()) {
                jobRun = new JobRun(JobRunConclusion.NOT_FOUND, "", -1);
            } else {
                JobRunOutcome outcome = runContainer(jobId.get(), maxTimeoutSeconds, maxKillTimeoutSeconds);

                JobRunConclusion jobRunConclusion;
                if (outcome.getExitStatusCode() == TIMED_OUT_EXIT_CODE) {
                    jobRunConclusion = JobRunConclusion.TIMED_OUT;
                } else if (outcome.getExitStatusCode() != 0) {
                    jobRunConclusion = JobRunConclusion.FAILURE;
                } else {
                    jobRunConclusion = JobRunConclusion.SUCCESS;
                }

                jobRun = new JobRun(jobRunConclusion, outcome.getOutput(), outcome.getExitStatusCode());
                runJobRateLimiter.decrementRunningJobsCount();
            }
        }

        return jobRun;
    }

    private JobRunOutcome runContainer(String containerId, long timeoutSeconds, int killTimeoutSeconds) {
        Long exitStatusCode;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Long> future = executor.submit(new WaitForContainer(dockerAdapter, containerId));
        try {
            exitStatusCode = future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException | InterruptedException ignored) {
            try {
                exitStatusCode = dockerAdapter.stopContainer(containerId, killTimeoutSeconds);
                // The container could finish naturally before the job timeout but before the stop-to-kill timeout.
                // TODO: Should this be 0L or existStatusCode? If above could have succeeded then we want the latter.
                exitStatusCode = wasStoppedOrKilled(exitStatusCode) ? -1 : 0L;
            } catch (StopJobRunException e) {
                exitStatusCode = -1L;
            }
        // TODO: Does this need to be in a finally block?
        } finally {
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
