package uk.gov.hmcts.ccd.domain.service.aggregated;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;
import static uk.gov.hmcts.ccd.domain.service.common.AccessControlService.NO_CASE_TYPE_FOUND;
import static uk.gov.hmcts.ccd.domain.service.common.AccessControlService.NO_CASE_TYPE_FOUND_DETAILS;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.ccd.domain.model.definition.AccessControlList;
import uk.gov.hmcts.ccd.domain.model.definition.CaseType;
import uk.gov.hmcts.ccd.domain.model.search.SearchInput;
import uk.gov.hmcts.ccd.endpoint.exceptions.ResourceNotFoundException;

@Service
@Qualifier(AuthorisedFindSearchInputOperation.QUALIFIER)
public class AuthorisedFindSearchInputOperation implements FindSearchInputOperation {

    public static final String QUALIFIER = "authorised";
    private final FindSearchInputOperation findSearchInputOperation;
    private final GetCaseTypeOperation getCaseTypeOperation;

    public AuthorisedFindSearchInputOperation(@Qualifier(ClassifiedFindSearchInputOperation.QUALIFIER) final FindSearchInputOperation findSearchInputOperation,
                                              @Qualifier(AuthorisedGetCaseTypeOperation.QUALIFIER) final GetCaseTypeOperation getCaseTypeOperation) {
        this.findSearchInputOperation = findSearchInputOperation;
        this.getCaseTypeOperation = getCaseTypeOperation;
    }

    public List<SearchInput> execute(final String jurisdictionId, final String caseTypeId, Predicate<AccessControlList> access) {
        Optional<CaseType> caseType = this.getCaseTypeOperation.execute(caseTypeId, access);

        if (!caseType.isPresent()) {
            ResourceNotFoundException resourceNotFoundException = new ResourceNotFoundException(NO_CASE_TYPE_FOUND);
            resourceNotFoundException.withDetails(NO_CASE_TYPE_FOUND_DETAILS);
            throw resourceNotFoundException;
        }

        return findSearchInputOperation.execute(jurisdictionId, caseTypeId, access)
            .stream()
            .filter(searchInput -> caseType.get().getCaseFields()
                .stream()
                .anyMatch(caseField -> caseField.getId().equalsIgnoreCase(searchInput.getField().getId())))
            .collect(toList());
    }
}
