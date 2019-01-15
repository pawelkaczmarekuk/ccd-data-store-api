package uk.gov.hmcts.ccd.domain.service.search.elasticsearch.security;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jooq.lambda.function.Functions.not;
import static uk.gov.hmcts.ccd.domain.service.common.AccessControlService.CAN_READ;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.ccd.data.user.CachedUserRepository;
import uk.gov.hmcts.ccd.data.user.UserRepository;
import uk.gov.hmcts.ccd.domain.model.definition.CaseDetails;
import uk.gov.hmcts.ccd.domain.model.definition.CaseType;
import uk.gov.hmcts.ccd.domain.model.search.CaseSearchResult;
import uk.gov.hmcts.ccd.domain.service.common.AccessControlService;
import uk.gov.hmcts.ccd.domain.service.common.ObjectMapperService;
import uk.gov.hmcts.ccd.domain.service.common.SecurityClassificationService;
import uk.gov.hmcts.ccd.domain.service.search.elasticsearch.CaseSearchOperation;
import uk.gov.hmcts.ccd.domain.service.search.elasticsearch.CrossCaseTypeSearchRequest;
import uk.gov.hmcts.ccd.domain.service.search.elasticsearch.ElasticsearchCaseSearchOperation;
import uk.gov.hmcts.ccd.domain.service.security.AuthorisedCaseDefinitionDataService;

@Service
@Qualifier(AuthorisedCaseSearchOperation.QUALIFIER)
public class AuthorisedCaseSearchOperation implements CaseSearchOperation {

    public static final String QUALIFIER = "AuthorisedCaseSearchOperation";
    private static final String DOT_SEPARATOR = ".";
    private static final String DOT_SEPARATOR_REGEX = "\\.";
    private static final String JSON_EXPR_LOGICAL_SEPARATOR = "/";

    private final CaseSearchOperation caseSearchOperation;
    private final AuthorisedCaseDefinitionDataService authorisedCaseDefinitionDataService;
    private final AccessControlService accessControlService;
    private final SecurityClassificationService classificationService;
    private final ObjectMapperService objectMapperService;
    private final UserRepository userRepository;

    @Autowired
    public AuthorisedCaseSearchOperation(
        @Qualifier(ElasticsearchCaseSearchOperation.QUALIFIER) CaseSearchOperation caseSearchOperation,
        AuthorisedCaseDefinitionDataService authorisedCaseDefinitionDataService,
        AccessControlService accessControlService,
        SecurityClassificationService classificationService,
        ObjectMapperService objectMapperService,
        @Qualifier(CachedUserRepository.QUALIFIER) UserRepository userRepository) {

        this.caseSearchOperation = caseSearchOperation;
        this.authorisedCaseDefinitionDataService = authorisedCaseDefinitionDataService;
        this.accessControlService = accessControlService;
        this.classificationService = classificationService;
        this.objectMapperService = objectMapperService;
        this.userRepository = userRepository;
    }

    @Override
    public CaseSearchResult execute(CrossCaseTypeSearchRequest searchRequest) {
        List<CaseType> authorisedCaseTypes = getAuthorisedCaseTypes(searchRequest);
        CrossCaseTypeSearchRequest authorisedSearchRequest = createAuthorisedSearchRequest(authorisedCaseTypes, searchRequest);

        return searchCasesAndFilterFieldsByAccess(authorisedCaseTypes, authorisedSearchRequest);
    }

    private List<CaseType> getAuthorisedCaseTypes(CrossCaseTypeSearchRequest searchRequest) {
        return searchRequest.getCaseTypeIds()
            .stream()
            .map(caseTypeId -> authorisedCaseDefinitionDataService.getAuthorisedCaseType(caseTypeId, CAN_READ).orElse(null))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private CrossCaseTypeSearchRequest createAuthorisedSearchRequest(List<CaseType> authorisedCaseTypes, CrossCaseTypeSearchRequest originalSearchRequest) {
        List<String> authorisedCaseTypeIds = authorisedCaseTypes.stream().map(CaseType::getId).collect(Collectors.toList());

        return new CrossCaseTypeSearchRequest.Builder()
            .withCaseTypes(authorisedCaseTypeIds)
            .withSearchRequest(originalSearchRequest.getSearchRequestJsonNode())
            .withMultiCaseTypeSearch(originalSearchRequest.isMultiCaseTypeSearch())
            .withSourceFilterAliasFields(originalSearchRequest.getAliasFields())
            .build();
    }

    private CaseSearchResult searchCasesAndFilterFieldsByAccess(List<CaseType> authorisedCaseTypes, CrossCaseTypeSearchRequest authorisedSearchRequest) {
        if (authorisedCaseTypes.isEmpty()) {
            return CaseSearchResult.EMPTY;
        }

        CaseSearchResult result = caseSearchOperation.execute(authorisedSearchRequest);
        filterCaseDataByCaseType(authorisedCaseTypes, result.getCases(), authorisedSearchRequest);

        return result;
    }

    private void filterCaseDataByCaseType(List<CaseType> authorisedCaseTypes, List<CaseDetails> cases, CrossCaseTypeSearchRequest authorisedSearchRequest) {
        Map<String, CaseType> caseTypeIdByCaseType = authorisedCaseTypes
            .stream()
            .collect(Collectors.toMap(CaseType::getId, Function.identity()));

        cases.stream()
            .filter(caseDetails -> caseTypeIdByCaseType.containsKey(caseDetails.getCaseTypeId()))
            .forEach(caseDetails -> filterCaseData(caseTypeIdByCaseType.get(caseDetails.getCaseTypeId()), caseDetails, authorisedSearchRequest));
    }

    private void filterCaseData(CaseType authorisedCaseType, CaseDetails caseDetails, CrossCaseTypeSearchRequest authorisedSearchRequest) {
        filterCaseDataByAclAccess(authorisedCaseType, caseDetails);
        filterCaseDataBySecurityClassification(caseDetails);
        filterCaseDataForMultiCaseTypeSearch(authorisedSearchRequest, authorisedCaseType, caseDetails);
    }

    private void filterCaseDataByAclAccess(CaseType authorisedCaseType, CaseDetails caseDetails) {
        JsonNode caseData = caseDataToJsonNode(caseDetails);
        JsonNode accessFilteredData = accessControlService.filterCaseFieldsByAccess(caseData, authorisedCaseType.getCaseFields(), getUserRoles(), CAN_READ);
        caseDetails.setData(jsonNodeToCaseData(accessFilteredData));
    }

    private void filterCaseDataBySecurityClassification(CaseDetails caseDetails) {
        classificationService.applyClassification(caseDetails);
    }

    /**
     * Filters the case data to the aliases that were passed in the _source filter of the search request. For e.g. if the case data is
     * "case_data": {
     *   "PersonFirstName" : "J",
     *   "PersonLastName": "Baker",
     *   "PersonAddress": {
     *     "city": "London",
     *     "postcode": "W4"
     *   }
     * }
     * <p>
     * and the source filter is
     * "_source": ["alias.lastName", "alias.postcode"]
     * where alias.lastName = case_data.PersonLastName and alias.postcode = case_data.PersonAddress.postcode
     * <p>
     * the case data will be filtered and transformed to
     * "case_data": {
     *   "lastName": "Baker",
     *   "postcode": "W4",
     * }
     * <p>
     * If no source filter was passed then this will remove case data and return only metadata.
     */
    private void filterCaseDataForMultiCaseTypeSearch(CrossCaseTypeSearchRequest searchRequest, CaseType authorisedCaseType, CaseDetails caseDetails) {
        if (searchRequest.isMultiCaseTypeSearch() && caseDetails.getData() != null) {
            JsonNode caseData = caseDataToJsonNode(caseDetails);
            JsonNode filteredMultiCaseTypeSearchData = objectMapperService.createEmptyJsonNode();

            authorisedCaseType.getSearchAliasFields()
                .stream()
                .filter(searchRequest::hasAliasField)
                .forEach(searchAliasField -> findCaseFieldPathInCaseData(caseData, searchAliasField.getCaseFieldPath())
                    .filter(not(JsonNode::isMissingNode))
                    .ifPresent(jsonNode -> ((ObjectNode) filteredMultiCaseTypeSearchData).set(searchAliasField.getId(), jsonNode)));

            caseDetails.setData(jsonNodeToCaseData(filteredMultiCaseTypeSearchData));
        }
    }

    private Optional<JsonNode> findCaseFieldPathInCaseData(JsonNode caseData, String path) {
        String jsonPointerExpr;
        if (path.contains(DOT_SEPARATOR)) {
            jsonPointerExpr = JSON_EXPR_LOGICAL_SEPARATOR + path.replaceAll(DOT_SEPARATOR_REGEX, JSON_EXPR_LOGICAL_SEPARATOR);
        } else {
            jsonPointerExpr = JSON_EXPR_LOGICAL_SEPARATOR + path;
        }

        return Optional.of(caseData.at(jsonPointerExpr));
    }

    private Set<String> getUserRoles() {
        return userRepository.getUserRoles();
    }

    private JsonNode caseDataToJsonNode(CaseDetails caseDetails) {
        return objectMapperService.convertObjectToJsonNode(caseDetails.getData());
    }

    private Map<String, JsonNode> jsonNodeToCaseData(JsonNode jsonNode) {
        return objectMapperService.convertJsonNodeToMap(jsonNode);
    }

}
