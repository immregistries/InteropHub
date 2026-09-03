package org.airahub.interophub.service.digest;

/**
 * One piece of digest content for one recipient. {@link DailyDigestService}
 * groups notices by recipient and, within a recipient, by section title.
 */
public record DigestNotice(
        String recipientEmail,
        String recipientEmailNormalized,
        Long recipientUserId,
        String sectionTitle,
        String bodyText) {
}
