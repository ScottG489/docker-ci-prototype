package dci.resource;

import com.spotify.docker.client.exceptions.DockerException;
import dci.api.JobResponse;
import dci.api.JobResultResponse;
import dci.core.job.JobService;
import dci.core.job.model.Job;
import dci.core.job.model.JobResult;
import dci.resource.convert.JobResponseConverter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/build")
@Slf4j
public class BuildResource {
    private final JobService jobService;

    public BuildResource(JobService jobService) {
        this.jobService = jobService;
    }

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response handlePost(@NotEmpty @QueryParam("image") String imageName, String input)
            throws DockerException, InterruptedException {
        return createResponse(imageName, input);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response handleJsonPost(@NotEmpty @QueryParam("image") String imageName, String input)
            throws DockerException, InterruptedException {
        return createJsonResponse(imageName, input);
    }

    @GET
    @Produces({MediaType.WILDCARD, MediaType.TEXT_PLAIN})
    public Response handleGet(@NotEmpty @QueryParam("image") String imageName)
            throws DockerException, InterruptedException {
        return createResponse(imageName, null);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response handleJsonGet(@NotEmpty @QueryParam("image") String imageName)
            throws DockerException, InterruptedException {
        return createJsonResponse(imageName, null);
    }

    private Response createResponse(String imageName, String input)
            throws DockerException, InterruptedException {
        log.info("Running image: '{}'", imageName);
        Job job = jobService.getJob(imageName, input);

        return createResponseWithStatus(job)
                .entity(job.getJobRun().getOutput())
                .build();
    }

    private Response createJsonResponse(String imageName, String input)
            throws DockerException, InterruptedException {
        log.info("Running image: '{}'", imageName);
        Job job = jobService.getJob(imageName, input);

        JobResponse jobResponse = new JobResponseConverter().from(job);

        return createResponseWithStatus(job)
                .entity(jobResponse)
                .build();
    }

    private Response.ResponseBuilder createResponseWithStatus(Job job) {
        Response.ResponseBuilder responseBuilder;
        JobResult jobResult = job.getResult();
        long exitCode = job.getJobRun().getExitCode();

        if (jobResult.equals(JobResult.FINISHED)) {
            if (exitCode == 0) {
                responseBuilder = Response.ok();
            } else {
                responseBuilder = Response.status(Response.Status.BAD_REQUEST);
            }
        } else if (jobResult.equals(JobResult.NOT_FOUND)) {
            responseBuilder = Response.status(Response.Status.NOT_FOUND);
        } else {
            responseBuilder = Response.status(Response.Status.INTERNAL_SERVER_ERROR);
        }

        return responseBuilder;
    }
}
