package conjob.core.job;

public class JobRunConfigCreator {
    public JobRunConfig getContainerConfig(String imageName, String input, String secretId) {
        JobRunConfig jobRunConfig;
        if (input != null && !input.isEmpty()) {
            jobRunConfig = new JobRunConfig(imageName, input, secretId);
        } else {
            jobRunConfig = new JobRunConfig(imageName, null, secretId);
        }

        return jobRunConfig;
    }
}
