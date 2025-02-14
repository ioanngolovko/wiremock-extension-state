package org.wiremock.extensions.state.examples;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.Stubbing;
import com.github.tomakehurst.wiremock.store.Store;
import com.google.common.io.Resources;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.assertj.core.api.Assertions;
import org.awaitility.core.ThrowingRunnable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.wiremock.extensions.state.CaffeineStore;
import org.wiremock.extensions.state.StateExtension;
import org.wiremock.extensions.state.internal.ContextManager;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

/**
 * Sample test to demonstrate remote loading of stub mapping with this extension.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(SAME_THREAD)
public class StubMappingLoadingExampleTest {

    private static WireMockServer wireMockServer;
    private static final Store<String, Object> store = new CaffeineStore();
    static final ContextManager contextManager = new ContextManager(store);


    @BeforeAll
    public static void initWithTempDir() {
        WireMockConfiguration options = wireMockConfig().withRootDirectory(Resources.getResource("remoteloader").getPath())
                .templatingEnabled(true).globalTemplating(true)
                .extensions(new StateExtension(store));
        System.out.println(
                "Configuring WireMockServer with root directory: " + options.filesRoot().getPath());
        if (options.portNumber() == Options.DEFAULT_PORT) {
            options.dynamicPort();
        }

        wireMockServer = new WireMockServer(options);
        wireMockServer.start();
        WireMock.configureFor(wireMockServer.port());
        Stubbing wm = wireMockServer;

        Locale.setDefault(Locale.ENGLISH);
        WireMock.create().port(wireMockServer.port()).build();
    }

    @Test
    void testSimpleContext() throws Exception {
        String entityId = "IDDQD";
        String contextId = "dynamic-process-" + entityId;

        given()
                .accept(ContentType.JSON)
                .body("<items><item>" + entityId + "</item></items>")
                .post(new URI(wireMockServer.baseUrl() + "/dynamic/process"))
                .then()
                .statusCode(HttpStatus.SC_CREATED);

        awaitAndAssert(() -> Assertions.assertThat(contextManager.numUpdates(contextId)).isEqualTo(1));
        assertThat(store.get(contextId).isPresent(), is(true));


        given()
                .accept(ContentType.JSON)
                .get(new URI(wireMockServer.baseUrl() + "/dynamic/process/" + entityId))
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("result", equalTo(entityId));

        awaitAndAssert(() -> Assertions.assertThat(contextManager.numUpdates(contextId)).isEqualTo(1));
        assertThat(store.get(contextId).isPresent(), is(true));

        given()
                .accept(ContentType.JSON)
                .delete(new URI(wireMockServer.baseUrl() + "/dynamic/process/" + entityId))
                .then()
                .statusCode(HttpStatus.SC_OK);

        awaitAndAssert(() -> Assertions.assertThat(contextManager.numUpdates(contextId)).isEqualTo(0));
        assertThat(store.get(contextId).isPresent(), is(false));
    }

    @Test
    void testContextWithList() throws Exception {
        String queueId = "my-queue";
        String contextId = "queue-" + queueId;
        String itemId1 = "id1";
        String itemId2 = "anotherId";

        String noDataValue = "no data";

        // empty queue
        given()
            .get(new URI(wireMockServer.baseUrl() + "/queue/" + queueId))
            .then()
            .statusCode(HttpStatus.SC_OK)
            .body("item", equalTo(noDataValue));
        awaitAndAssert(() -> Assertions.assertThat(contextManager.numUpdates(contextId)).isEqualTo(0));

        // nothing to ack = error
        given()
            .post(new URI(wireMockServer.baseUrl() + "/queue/" + queueId + "/ack"))
            .then()
            .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        awaitAndAssert(() -> Assertions.assertThat(contextManager.numUpdates(contextId)).isEqualTo(0));

        // put into queue
        given()
            .post(new URI(wireMockServer.baseUrl() + "/queue/" + queueId + "/" + itemId1))
            .then()
            .statusCode(HttpStatus.SC_OK);
        awaitAndAssert(() -> Assertions.assertThat(contextManager.numUpdates(contextId)).isEqualTo(1));

        // get item1 from queue without ack
        given()
            .get(new URI(wireMockServer.baseUrl() + "/queue/" + queueId))
            .then()
            .statusCode(HttpStatus.SC_OK)
            .body("item", equalTo(itemId1));
        awaitAndAssert(() -> Assertions.assertThat(contextManager.numUpdates(contextId)).isEqualTo(1));

        // put another item into queue
        given()
            .post(new URI(wireMockServer.baseUrl() + "/queue/" + queueId + "/" + itemId2))
            .then()
            .statusCode(HttpStatus.SC_OK);
        awaitAndAssert(() -> Assertions.assertThat(contextManager.numUpdates(contextId)).isEqualTo(2));

        // get item1 from queue without ack
        given()
            .get(new URI(wireMockServer.baseUrl() + "/queue/" + queueId))
            .then()
            .statusCode(HttpStatus.SC_OK)
            .body("item", equalTo(itemId1));
        awaitAndAssert(() -> Assertions.assertThat(contextManager.numUpdates(contextId)).isEqualTo(2));

        // ack item 1
        given()
            .post(new URI(wireMockServer.baseUrl() + "/queue/" + queueId + "/ack"))
            .then()
            .statusCode(HttpStatus.SC_OK);
        awaitAndAssert(() -> Assertions.assertThat(contextManager.numUpdates(contextId)).isEqualTo(3));

        // get item2 from queue without ack
        given()
            .get(new URI(wireMockServer.baseUrl() + "/queue/" + queueId))
            .then()
            .statusCode(HttpStatus.SC_OK)
            .body("item", equalTo(itemId2));
        awaitAndAssert(() -> Assertions.assertThat(contextManager.numUpdates(contextId)).isEqualTo(3));

        // ack item 2
        given()
            .post(new URI(wireMockServer.baseUrl() + "/queue/" + queueId + "/ack"))
            .then()
            .statusCode(HttpStatus.SC_OK);
        awaitAndAssert(() -> Assertions.assertThat(contextManager.numUpdates(contextId)).isEqualTo(4));

        // nothing to ack = error
        given()
            .post(new URI(wireMockServer.baseUrl() + "/queue/" + queueId + "/ack"))
            .then()
            .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        awaitAndAssert(() -> Assertions.assertThat(contextManager.numUpdates(contextId)).isEqualTo(4));

        // empty queue
        given()
            .get(new URI(wireMockServer.baseUrl() + "/queue/" + queueId))
            .then()
            .statusCode(HttpStatus.SC_OK)
            .body("item", equalTo(noDataValue));
        awaitAndAssert(() -> Assertions.assertThat(contextManager.numUpdates(contextId)).isEqualTo(4));
    }

    private void awaitAndAssert(ThrowingRunnable assertion) {
        await()
                .pollInterval(Duration.ofMillis(10))
                .atMost(Duration.ofSeconds(5)).untilAsserted(assertion);
    }
}
