package nanogate;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;

public class NanoGateSimulation extends Simulation {

  // Read parameters from system properties (passed via -D on the command line)
  String baseUrl = System.getProperty("baseUrl", "http://localhost:8080");
  int rampUsersCount = Integer.getInteger("rampUsers", 10);
  int rampDurationSeconds = Integer.getInteger("rampDuration", 10);
  int constantUsersCount = Integer.getInteger("constantUsers", 5);
  int constantDurationSeconds = Integer.getInteger("constantDuration", 60);

  HttpProtocolBuilder httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .header("X-API-Key", "test-key");

  ScenarioBuilder scn = scenario("NanoGate API Load Test")
    .exec(
      http("Get Order")
        .get("/api/orders/123")
        .check(status().is(200))
    )
    .pause(1)
    .exec(
      http("Get User Report")
        .get("/api/users/reports/monthly")
        .check(status().is(200))
    )
    .pause(1)
    .exec(
      http("Get User")
        .get("/api/users/123")
        .check(status().is(200))
    )
    .pause(1)
    .exec(
      http("Get Header Test")
        .get("/api/headers/test")
        .check(status().is(200))
    )
    .pause(1)
    .exec(
      http("Get Public Data")
        .get("/api/public/data")
        .check(status().in(200, 429))
    );

  {
    setUp(
      scn.injectOpen(
        rampUsers(rampUsersCount).during(Duration.ofSeconds(rampDurationSeconds)),
        constantUsersPerSec(constantUsersCount).during(Duration.ofSeconds(constantDurationSeconds))
      )
    ).protocols(httpProtocol);
  }
}
