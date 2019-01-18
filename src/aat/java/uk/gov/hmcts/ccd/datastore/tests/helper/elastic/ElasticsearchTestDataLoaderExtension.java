package uk.gov.hmcts.ccd.datastore.tests.helper.elastic;

import static org.hamcrest.CoreMatchers.equalTo;
import static uk.gov.hmcts.ccd.datastore.tests.fixture.AATCaseType.AAT_PRIVATE_CASE_TYPE;
import static uk.gov.hmcts.ccd.datastore.tests.functional.elasticsearch.ElasticsearchBaseTest.assertElasticsearchEnabled;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.ccd.datastore.tests.TestData;
import uk.gov.hmcts.ccd.datastore.tests.fixture.AATCaseBuilder;
import uk.gov.hmcts.ccd.datastore.tests.fixture.AATCaseType;
import uk.gov.hmcts.ccd.datastore.tests.functional.elasticsearch.ElasticSearchTextFieldTest;
import uk.gov.hmcts.ccd.datastore.tests.functional.elasticsearch.ElasticsearchCaseSearchSecurityTest;
import uk.gov.hmcts.ccd.datastore.tests.helper.TestDataLoaderExtension;

public class ElasticsearchTestDataLoaderExtension extends TestDataLoaderExtension {

    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchTestDataLoaderExtension.class);

    private static final String CASE_INDEX_NAME = "aat_private_cases-000001";
    private static final String CASE_INDEX_ALIAS = "aat_private_cases";

    private final ElasticsearchHelper elasticsearchHelper = new ElasticsearchHelper();

    @Override
    protected void loadData() {
        assertElasticsearchEnabled();

        LOG.info("importing definition");
        importDefinition();

        LOG.info("creating test case data");
        createCases();
    }

    @Override
    public void close() {
        LOG.info("Deleting index and alias");
        deleteIndexAndAlias();
    }

    private void createCases() {
        // create test cases in the alphabetical order of test class names
        createCasesForCaseSearchSecurityTest();
        createCasesForTextSearchTest();
        waitUntilLogstashIndexesCaseData(elasticsearchHelper.getLogstashReadDelay());
    }

    private void createCasesForCaseSearchSecurityTest() {
        TestData testData = TestData.getInstance();

        testData.put(ElasticsearchCaseSearchSecurityTest.CASE_TYPE_SECURITY_TEST_REFERENCE,
                     createCase(asPrivateCaseworker(true), AAT_PRIVATE_CASE_TYPE, AATCaseBuilder.EmptyCase.build()));
        testData.put(ElasticsearchCaseSearchSecurityTest.CASE_STATE_SECURITY_TEST_REFERENCE,
                     createCaseAndProgressState(asPrivateCaseworker(true), AAT_PRIVATE_CASE_TYPE));
        testData.put(ElasticsearchCaseSearchSecurityTest.CASE_FIELD_SECURITY_TEST_REFERENCE,
                     createCase(asRestrictedCaseworker(true),
                                AAT_PRIVATE_CASE_TYPE,
                                AATCaseType.CaseData.builder().emailField(ElasticsearchCaseSearchSecurityTest.EMAIL_ID_VALUE).build()));
    }

    private void createCasesForTextSearchTest() {
        TestData testData = TestData.getInstance();

        testData.put(ElasticSearchTextFieldTest.SEARCH_UPDATED_CASE_TEST_REFERENCE,
                     createCaseAndProgressState(asPrivateCaseworker(true), AAT_PRIVATE_CASE_TYPE));
        testData.put(ElasticSearchTextFieldTest.EXACT_MATCH_TEST_REFERENCE,
                     createCase(asPrivateCaseworker(true), AAT_PRIVATE_CASE_TYPE, AATCaseBuilder.FullCase.build()));
    }

    private void createCasesForCrossCaseTypeSearchTest() {
        TestData testData = TestData.getInstance();

        testData.put(ElasticSearchTextFieldTest.SEARCH_UPDATED_CASE_TEST_REFERENCE,
                     createCaseAndProgressState(asPrivateCaseworker(true), AAT_PRIVATE_CASE_TYPE));
        testData.put(ElasticSearchTextFieldTest.EXACT_MATCH_TEST_REFERENCE,
                     createCase(asPrivateCaseworker(true), AAT_PRIVATE_CASE_TYPE, AATCaseBuilder.FullCase.build()));
    }

    private void deleteIndexAndAlias() {
        deleteIndexAlias(CASE_INDEX_NAME, CASE_INDEX_ALIAS);
        deleteIndex(CASE_INDEX_NAME);
    }

    private void deleteIndexAlias(String indexName, String indexAlias) {
        asElasticsearchApiUser()
            .when()
            .delete(getCaseIndexAliasApi(indexName, indexAlias))
            .then()
            .statusCode(200)
            .body("acknowledged", equalTo(true));
    }

    private void deleteIndex(String indexName) {
        asElasticsearchApiUser()
            .when()
            .delete(indexName)
            .then()
            .statusCode(200)
            .body("acknowledged", equalTo(true));
    }

    private RequestSpecification asElasticsearchApiUser() {
        return RestAssured.given(new RequestSpecBuilder()
                                     .setBaseUri(elasticsearchHelper.getElasticsearchBaseUri())
                                     .build());
    }

    private String getCaseIndexAliasApi(String indexName, String indexAlias) {
        return indexName + "/_alias/" + indexAlias;
    }

    private void waitUntilLogstashIndexesCaseData(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
