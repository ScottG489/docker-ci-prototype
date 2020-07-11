package dci.healthcheck;

import org.junit.Before;
import org.junit.Test;

import static dci.util.RestAssuredUtil.setAdminBaseUri;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.matchesRegex;

public class VersionTest {
    @Before
    public void setup() {
        setAdminBaseUri();
    }

    @Test
    public void testVersion() {
        given()
                .get("/healthcheck")
        .then()
                .body("version.message", matchesRegex("[0-9a-f]{5,40}"));
    }
}