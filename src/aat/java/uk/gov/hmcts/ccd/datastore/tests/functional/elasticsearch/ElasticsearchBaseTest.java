package uk.gov.hmcts.ccd.datastore.tests.functional.elasticsearch;

import java.util.function.Supplier;

import static java.util.Optional.ofNullable;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static uk.gov.hmcts.ccd.datastore.tests.fixture.AATCaseType.AAT_PRIVATE2_CASE_TYPE;
import static uk.gov.hmcts.ccd.datastore.tests.fixture.AATCaseType.AAT_PRIVATE_CASE_TYPE;

import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.ccd.datastore.tests.AATHelper;
import uk.gov.hmcts.ccd.datastore.tests.BaseTest;
import uk.gov.hmcts.ccd.datastore.tests.TestData;
import uk.gov.hmcts.ccd.datastore.tests.helper.elastic.ElasticsearchTestDataLoaderExtension;

public abstract class ElasticsearchBaseTest extends BaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchTestDataLoaderExtension.class);
    public static final String EXACT_MATCH_TEST_REFERENCE = TestData.uniqueReference();

    static final String CASE_DATA_FIELD_PREFIX = "data.";
    static final String RESPONSE_CASE_DATA_FIELDS_PREFIX = "case_data.";
    static final String CASE_ID = "id";
    static final String ES_FIELD_CASE_TYPE = "case_type_id";
    static final String ES_FIELD_STATE = "state";
    static final String ES_FIELD_CASE_REFERENCE = "reference";
    static final String ES_FIELD_EMAIL_ID = "EmailField";
    static final String ES_FIELD_TEXT_ALIAS = "alias.TextFieldAlias";
    static final String ES_FIELD_NUMBER_ALIAS = "alias.NumberFieldAlias";
    static final String ES_FIELD_EMAIL_ALIAS = "alias.EmailFieldAlias";

    private static final String CASE_TYPE_ID_PARAM = "ctid";
    private static final String CASE_SEARCH_API = "/searchCases";

    final TestData testData = TestData.getInstance();

    ElasticsearchBaseTest(AATHelper aat) {
        super(aat);
    }

    public static void assertElasticsearchEnabled() {
        // stop execution of these tests if Elasticsearch is not enabled
        LOG.info("ELASTIC_SEARCH_ENABLED: {}", System.getenv("ELASTIC_SEARCH_ENABLED"));
        boolean elasticsearchEnabled = ofNullable(System.getenv("ELASTIC_SEARCH_ENABLED")).map(Boolean::valueOf).orElse(false);
        assumeTrue(elasticsearchEnabled, () -> "Ignoring Elasticsearch tests, variable ELASTIC_SEARCH_ENABLED not set");
    }


    ValidatableResponse searchCase(Supplier<RequestSpecification> requestSpecification, String jsonSearchRequest, String... caseTypes) {
        return requestSpecification.get()
            .given()
            .log()
            .body()
            .queryParam(CASE_TYPE_ID_PARAM, caseTypes)
            .contentType(ContentType.JSON)
            .body(jsonSearchRequest)
            .when()
            .post(CASE_SEARCH_API)
            .then()
            .statusCode(200);
    }

    ValidatableResponse searchCase(Supplier<RequestSpecification> requestSpecification, String jsonSearchRequest) {
        return searchCase(requestSpecification, jsonSearchRequest, AAT_PRIVATE_CASE_TYPE);
    }

    void assertSingleCaseReturned(ValidatableResponse response) {
        assertCaseListSizeInResponse(response, 1);
    }

    void assertCaseListSizeInResponse(ValidatableResponse response, int expectedSize) {
        response.body("cases.size()", is(expectedSize));
    }

    void assertNoCaseReturned(ValidatableResponse response) {
        response.body("cases.size()", is(0));
    }

    ValidatableResponse searchAcrossCaseTypes(Supplier<RequestSpecification> asUser, String field, Object value) {
        String jsonSearchRequest = ElasticsearchSearchRequest.exactMatch(field, value);
        return searchCase(asUser, jsonSearchRequest, AAT_PRIVATE_CASE_TYPE, AAT_PRIVATE2_CASE_TYPE);
    }

    void assertCaseReferencesInResponse(ValidatableResponse response, Object... values) {
        assertCaseListSizeInResponse(response, 2);
        assertField(response, CASE_ID, values);
    }

    void assertField(ValidatableResponse response, String field, Object... expectedValues) {
        response.body("cases." + field, hasItems(expectedValues));
    }

}
