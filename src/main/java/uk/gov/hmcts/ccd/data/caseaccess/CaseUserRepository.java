package uk.gov.hmcts.ccd.data.caseaccess;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.List;

@Named
@Singleton
public class CaseUserRepository {

    private final CaseUserAuditRepository auditRepo;

    @PersistenceContext
    private EntityManager em;

    @Inject
    public CaseUserRepository(CaseUserAuditRepository caseUserAuditRepository) {
        this.auditRepo = caseUserAuditRepository;
    }

    public void grantAccess(Long caseId, String userId, String caseRole) {
        em.merge(new CaseUserEntity(caseId, userId, caseRole));
        auditRepo.auditGrant(caseId, userId, caseRole);
    }

    public void revokeAccess(Long caseId, String userId, String caseRole) {
        CaseUserEntity primaryKey = new CaseUserEntity(caseId, userId, caseRole);
        CaseUserEntity caseUser = em.find(CaseUserEntity.class, primaryKey.getCasePrimaryKey());

        if (caseUser != null) {
            em.remove(caseUser);
            auditRepo.auditRevoke(caseId, userId, caseRole);
        }
    }

    public List<Long> findCasesUserIdHasAccessTo(final String userId) {
        TypedQuery<Long> namedQuery = em.createNamedQuery(CaseUserEntity.GET_ALL_CASES_USER_HAS_ACCESS_TO, Long.class);
        namedQuery.setParameter("userId", userId);

        return namedQuery.getResultList();
    }

    public List<String> findCaseRoles(final Long caseId, final String userId) {
        TypedQuery<String> namedQuery = em.createNamedQuery(CaseUserEntity.GET_ALL_CASE_ROLES_USER_HAS_ACCESS_FOR_A_CASE, String.class);
        namedQuery.setParameter("userId", userId);
        namedQuery.setParameter("caseDataId", caseId);

        return namedQuery.getResultList();
    }
}
