package adliya.uz.functioncatalogservice.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationAndReportStateTest {

    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");

    @Test void verificationStatusNeverPresentsChangedOrOldContentAsCurrent() {
        assertThat(VerificationStatus.of(null, false, NOW)).isEqualTo(VerificationStatus.UNVERIFIED);
        assertThat(VerificationStatus.of(NOW.minusSeconds(60), false, NOW)).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(VerificationStatus.of(NOW.minusSeconds(60), true, NOW)).isEqualTo(VerificationStatus.OUTDATED);
        assertThat(VerificationStatus.of(NOW.minus(VerificationStatus.RECHECK_AFTER).minusSeconds(1), false, NOW))
                .isEqualTo(VerificationStatus.DUE);
    }

    @Test void onlyVerifiedCardsBecomeOutdated() {
        var unverified = OrgFunction.builder().build();
        assertThat(unverified.markVerificationOutdated()).isFalse();
        assertThat(unverified.isVerificationOutdated()).isFalse();
        var verified = OrgFunction.builder().lastVerifiedAt(NOW).build();
        assertThat(verified.markVerificationOutdated()).isTrue();
        assertThat(verified.markVerificationOutdated()).isFalse();
        assertThat(verified.verificationStatus(NOW)).isEqualTo(VerificationStatus.OUTDATED);
    }

    @Test void reportStatusesFollowTheReviewWorkflow() {
        assertThat(ReportStatus.NEW.canMoveTo(ReportStatus.IN_PROGRESS)).isTrue();
        assertThat(ReportStatus.NEW.canMoveTo(ReportStatus.REJECTED)).isTrue();
        assertThat(ReportStatus.NEW.canMoveTo(ReportStatus.RESOLVED)).isTrue();
        assertThat(ReportStatus.IN_PROGRESS.canMoveTo(ReportStatus.RESOLVED)).isTrue();
        assertThat(ReportStatus.IN_PROGRESS.canMoveTo(ReportStatus.NEW)).isFalse();
        assertThat(ReportStatus.RESOLVED.canMoveTo(ReportStatus.IN_PROGRESS)).isTrue();
        assertThat(ReportStatus.RESOLVED.canMoveTo(ReportStatus.REJECTED)).isFalse();
        assertThat(ReportStatus.REJECTED.canMoveTo(ReportStatus.RESOLVED)).isFalse();
        assertThat(ReportStatus.REJECTED.canMoveTo(ReportStatus.REJECTED)).isFalse();
    }

    @Test void reportCategoriesMatchTheReportedEntity() {
        assertThat(ReportCategory.FEES_OR_TIMING.appliesTo(ReportEntityType.FUNCTION)).isTrue();
        assertThat(ReportCategory.FEES_OR_TIMING.appliesTo(ReportEntityType.ORGANIZATION)).isFalse();
        assertThat(ReportCategory.CONTACT_DETAILS.appliesTo(ReportEntityType.ORGANIZATION)).isTrue();
        assertThat(ReportCategory.CONTACT_DETAILS.appliesTo(ReportEntityType.FUNCTION)).isFalse();
    }
}
