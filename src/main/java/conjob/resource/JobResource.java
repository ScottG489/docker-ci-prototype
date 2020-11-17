package conjob.resource;

import com.spotify.docker.client.exceptions.DockerException;
import conjob.service.JobService;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.security.PermitAll;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/job/run")
@PermitAll
@Slf4j
public class JobResource {
    private final JobService jobService;

    public JobResource(JobService jobService) {
        this.jobService = jobService;
    }

    @POST
    @Produces({MediaType.WILDCARD, MediaType.TEXT_PLAIN})
    public Response handlePost(@NotEmpty @QueryParam("image") String imageName, String input,
                               @QueryParam("pull") @DefaultValue("always") String pullStrategy)
            throws DockerException, InterruptedException {
        return createResponse(imageName, input, pullStrategy);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response handleJsonPost(@NotEmpty @QueryParam("image") String imageName, String input,
                                   @QueryParam("pull") @DefaultValue("always") String pullStrategy)
            throws DockerException, InterruptedException {
        return createJsonResponse(imageName, input, pullStrategy);
    }

    @GET
    @Produces({MediaType.WILDCARD, MediaType.TEXT_PLAIN})
    public Response handleGet(@NotEmpty @QueryParam("image") String imageName,
                              @QueryParam("pull") @DefaultValue("always") String pullStrategy)
            throws DockerException, InterruptedException {
        return createResponse(imageName, null, pullStrategy);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response handleJsonGet(@NotEmpty @QueryParam("image") String imageName,
                                  @QueryParam("pull") @DefaultValue("always") String pullStrategy)
            throws DockerException, InterruptedException {
        return createJsonResponse(imageName, null, pullStrategy);
    }

    private Response createResponse(String imageName, String input, String pullStrategy)
            throws DockerException, InterruptedException {
        log.info("Running image: '{}'", imageName);
        return jobService.createResponse(imageName, input, pullStrategy);
    }

    private Response createJsonResponse(String imageName, String input, String pullStrategy)
            throws DockerException, InterruptedException {
        log.info("Running image: '{}'", imageName);
        return jobService.createJsonResponse(imageName, input, pullStrategy);
    }

}