package uk.gov.hmcts.ccd.datastore.tests.functional.elasticsearch;

import java.util.function.Supplier;

import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.gov.hmcts.ccd.datastore.tests.AATHelper;
import uk.gov.hmcts.ccd.datastore.tests.TestData;
import uk.gov.hmcts.ccd.datastore.tests.helper.elastic.ElasticsearchTestDataLoaderExtension;

@ExtendWith(ElasticsearchTestDataLoaderExtension.class)
public class ElasticsearchCrossCaseTypeSearchTest extends ElasticsearchBaseTest {

    public static final String TEXT_FIELD_REFERENCE = TestData.uniqueReference();

    ElasticsearchCrossCaseTypeSearchTest(AATHelper aat) {
        super(aat);
    }

    @BeforeAll
    static void setUp() {
        assertElasticsearchEnabled();
    }

    @Nested
    @DisplayName("Cross case type search using alias")
    class SearchByAlias {

        @Test
        @DisplayName("should return cases across multiple case types for exact match on search alias field")
        void shouldReturnCaseForPrivateUser() {
            searchCaseAndAssertCaseReference(asPrivateCaseworker(false), ES_FIELD_TEXT, testData.get(TEXT_FIELD_REFERENCE));
        }

    }

    @Nested
    @DisplayName("Cross case type sort by alias")
    class SortByAlias {

        @Test
        @DisplayName("should sort cases across multiple case types on a search alias field")
        void shouldReturnCase() {
            searchCaseAndAssertCaseReference(asPrivateCaseworker(false), ES_FIELD_TEXT, testData.get(TEXT_FIELD_REFERENCE));
        }

    }

    @Nested
    @DisplayName("Cross case type case data filter in response")
    class FilterByAlias {

        @Test
        @DisplayName("should return metadata and case data for source filter with aliases")
        void shouldReturnMetadataAndCaseData() {
            searchCaseAndAssertCaseReference(asPrivateCaseworker(false), ES_FIELD_TEXT, testData.get(TEXT_FIELD_REFERENCE));
        }

        @Test
        @DisplayName("should return metadata only when source filter is not requested")
        void shouldReturnMetadataOnly() {
            searchCaseAndAssertCaseReference(asPrivateCaseworker(false), ES_FIELD_CASE_REFERENCE, testData.get(TEXT_FIELD_REFERENCE));
        }

    }

    private void searchCaseForExactMatchAndVerifyResponse(String field, String value) {
        String jsonSearchRequest = ElasticsearchSearchRequest.exactMatch(CASE_DATA_FIELD_PREFIX + field, value);

        ValidatableResponse response = searchCase(asPrivateCaseworker(false), jsonSearchRequest);

        assertSingleCaseReturned(response);
        assertField(response, RESPONSE_CASE_DATA_FIELDS_PREFIX + field, value);
        assertField(response, CASE_ID, testData.get(EXACT_MATCH_TEST_REFERENCE));
    }

    private ValidatableResponse searchCaseAndAssertCaseReference(Supplier<RequestSpecification> asUser, String field, Object value) {
        ValidatableResponse response = searchCase(asUser, field, value);
        assertSingleCaseReturned(response);
        assertField(response, CASE_ID, value);
        return response;
    }

    private void searchCaseAndAssertCaseNotReturned(Supplier<RequestSpecification> asUser, String field, Object value) {
        ValidatableResponse response = searchCase(asUser, field, value);
        assertNoCaseReturned(response);
    }

    private ValidatableResponse searchCase(Supplier<RequestSpecification> asUser, String field, Object value) {
        String jsonSearchRequest = ElasticsearchSearchRequest.exactMatch(field, value);
        return searchCase(asUser, jsonSearchRequest);
    }

}
