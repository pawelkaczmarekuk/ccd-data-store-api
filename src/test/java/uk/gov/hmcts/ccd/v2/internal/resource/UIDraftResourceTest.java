package uk.gov.hmcts.ccd.v2.internal.resource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import uk.gov.hmcts.ccd.domain.model.draft.DraftResponse;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertAll;
import static uk.gov.hmcts.ccd.domain.service.common.TestBuildersUtil.CaseDraftBuilder.newCaseDraft;
import static uk.gov.hmcts.ccd.domain.service.common.TestBuildersUtil.DraftResponseBuilder.newDraftResponse;

class UIDraftResourceTest {
    private static final String CASE_TYPE_ID = "caseTypeId";
    private static final String LINK_SELF = String.format("/internal/case-types/%s/drafts", CASE_TYPE_ID);
    private DraftResponse draftResponse = newDraftResponse().withDocument(newCaseDraft().withCaseTypeId(CASE_TYPE_ID).build()).build();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    @DisplayName("should copy draft response profile")
    void shouldCopyUserProfile() {
        final UIDraftResource resource = new UIDraftResource(draftResponse);

        assertAll(
            () -> assertThat(resource.getDraftResponse(), sameInstance(draftResponse))
        );
    }

    @Test
    @DisplayName("should link to itself")
    void shouldLinkToSelf() {
        final UIDraftResource resource = new UIDraftResource(draftResponse);

        assertThat(resource.getLink("self").getHref(), equalTo(LINK_SELF));
    }

}
