package fr.univcotedazur.dining.controllers;

import fr.univcotedazur.dining.controllers.dto.ItemDTO;
import fr.univcotedazur.dining.controllers.dto.StartOrderingDTO;
import fr.univcotedazur.dining.controllers.dto.TableCreationDTO;
import fr.univcotedazur.dining.models.OrderingLine;
import fr.univcotedazur.dining.repositories.TableOrderRepository;
import fr.univcotedazur.dining.repositories.TableRepository;
import io.restassured.http.ContentType;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static fr.univcotedazur.dining.controllers.DiningController.BASE_URI;
import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static io.restassured.module.mockmvc.RestAssuredMockMvc.when;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class DiningControllerTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.4.15"))
            .withReuse(true);

    @DynamicPropertySource
    static void mongoDbProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @BeforeAll
    static void initAll() {
        mongoDBContainer.start();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private TableOrderRepository tableOrderRepository;

    TableCreationDTO table1;
    StartOrderingDTO order1;
    ItemDTO twoPizzas;
    ItemDTO oneLasagna;
    ItemDTO threeCokes;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc);
        table1 = new TableCreationDTO();
        table1.setTableId(1L);
        given()
                .contentType(ContentType.JSON).body(table1).
                when()
                .post(TableController.BASE_URI).
                then()
                .statusCode(HttpStatus.SC_CREATED);
        order1 = new StartOrderingDTO();
        order1.setTableId(1L);
        order1.setCustomersCount(4);
        twoPizzas = new ItemDTO();
        // We don't set Item Id for tests
        twoPizzas.setShortName("pizza");
        twoPizzas.setHowMany(2);
        oneLasagna = new ItemDTO();
        oneLasagna.setShortName("lasagna");
        oneLasagna.setHowMany(1);
        threeCokes = new ItemDTO();
        threeCokes.setShortName("coke");
        threeCokes.setHowMany(3);
    }


    @AfterEach
    void tearDown() {
        tableRepository.deleteAll();
        tableOrderRepository.deleteAll();
    }

    @Test
    void createTableOrderSuccessfullyTwiceUnProcessable() {
        given()
                .contentType(ContentType.JSON).body(order1).
                when()
                .post(BASE_URI).
                then()
                .statusCode(HttpStatus.SC_CREATED)
                .body("id", notNullValue())
                .body("tableNumber", is(1))
                .body("customersCount", is(4))
                .body("opened", notNullValue())
                .body("billed", nullValue());
        given()
                .contentType(ContentType.JSON).body(order1).
                when()
                .post(BASE_URI).
                then()
                .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
    }

    @Test
    void addToTableOrder() {
        UUID orderId =
                given()
                        .contentType(ContentType.JSON).body(order1).
                        when()
                        .post(BASE_URI).
                        then()
                        .statusCode(HttpStatus.SC_CREATED)
                        .extract().jsonPath().getUUID("id");
        List<OrderingLine> lines =
                given()
                        .contentType(ContentType.JSON).body(twoPizzas).
                        when()
                        .post(BASE_URI + "/" + orderId).
                        then()
                        .statusCode(HttpStatus.SC_CREATED)
                        .body("id", equalTo(orderId.toString()))
                        .extract().response().jsonPath().getList("lines", OrderingLine.class);
        assertThat(lines.size(), is(1));
        OrderingLine line = lines.get(0);
        assertThat(line.getItem().getShortName(), equalTo("pizza"));
        assertThat(line.getHowMany(), is(2));
    }

    @Test
    void sendForPreparation() {
        UUID orderId =
                given()
                        .contentType(ContentType.JSON).body(order1).
                        when()
                        .post(BASE_URI).
                        then()
                        .statusCode(HttpStatus.SC_CREATED)
                        .extract().jsonPath().getUUID("id");
        given()
                .contentType(ContentType.JSON).body(twoPizzas).
                when()
                .post(BASE_URI + "/" + orderId).
                then()
                .statusCode(HttpStatus.SC_CREATED);
        List<OrderingLine> lines =
        given()
                .contentType(ContentType.JSON).body(threeCokes).
                when()
                .post(BASE_URI + "/" + orderId).
                then()
                .statusCode(HttpStatus.SC_CREATED)
                .extract().response().jsonPath().getList("lines", OrderingLine.class);
        assertThat(lines.size(), is(2));
        for (OrderingLine line : lines) {
            assertThat(line.isSentForPreparation(),is(false));
        }
        when()
                .post(BASE_URI + "/" + orderId + "/prepare").
                then()
                .statusCode(HttpStatus.SC_OK)
                .body("howManyItemsSentForPreparation",is(5));
        lines =
                when()
                        .get(BASE_URI + "/" + orderId).
                        then()
                        .statusCode(HttpStatus.SC_OK)
                        .extract().response().jsonPath().getList("lines", OrderingLine.class);
        for (OrderingLine line : lines) {
            assertThat(line.isSentForPreparation(),is(true));
        }
        given()
                .contentType(ContentType.JSON).body(oneLasagna).
                when()
                .post(BASE_URI + "/" + orderId).
                then()
                .statusCode(HttpStatus.SC_CREATED);
        when()
                .post(BASE_URI + "/" + orderId + "/prepare").
                then()
                .statusCode(HttpStatus.SC_OK)
                .body("howManyItemsSentForPreparation",is(1));
    }

    @Test
    void billTableTwiceUnprocessableCannotOrderAnymore() {
        UUID orderId =
                given()
                        .contentType(ContentType.JSON).body(order1).
                        when()
                        .post(BASE_URI).
                        then()
                        .statusCode(HttpStatus.SC_CREATED)
                        .extract().jsonPath().getUUID("id");
        given()
                .contentType(ContentType.JSON).body(twoPizzas).
                when()
                .post(BASE_URI + "/" + orderId).
                then()
                .statusCode(HttpStatus.SC_CREATED);
        when()
                .post(BASE_URI + "/" + orderId + "/bill").
                then()
                .statusCode(HttpStatus.SC_OK)
                .body("billed",notNullValue());
        when()
                .post(BASE_URI + "/" + orderId + "/bill").
                then()
                .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
        given()
                .contentType(ContentType.JSON).body(oneLasagna).
                when()
                .post(BASE_URI + "/" + orderId).
                then()
                .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
    }



}