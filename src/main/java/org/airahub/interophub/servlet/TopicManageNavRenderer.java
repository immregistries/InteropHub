package org.airahub.interophub.servlet;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.airahub.interophub.dao.EsCommentDao;
import org.airahub.interophub.dao.EsMeetingAgendaItemDao;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicCurationDao;
import org.airahub.interophub.dao.EsTopicRelationshipDao;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAgendaItem;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.TopicSpaceAccessService;

/**
 * Renders the "Manage This Topic" navigation card linking to each
 * {@link TopicManageView}, an Edit Topic link (open to anyone who can reach
 * this card, i.e. admins and active Topic Champions/Support), and an
 * admin-only link to Meeting Polls. Used both on the topic-manage page
 * itself (with the active view highlighted) and on the topic detail page (as
 * a jump-off menu, with no active view).
 */
final class TopicManageNavRenderer {

    private TopicManageNavRenderer() {
    }

    /** Counts shown next to each menu item, when greater than zero. */
    record TopicManageCounts(int followers, int meetings, int comments, int relationships, int curated) {
        int forView(TopicManageView view) {
            return switch (view) {
                case FOLLOWERS -> followers;
                case MEETINGS -> meetings;
                case COMMENTS -> comments;
                case RELATIONSHIPS -> relationships;
                case CURATED -> curated;
            };
        }
    }

    /**
     * Computes the counts shown in the nav menu. Cheap enough to run on every
     * page load regardless of which view (if any) is active.
     */
    static TopicManageCounts computeCounts(Long topicId, User viewer,
            EsSubscriptionDao subscriptionDao, EsMeetingAgendaItemDao agendaItemDao, EsMeetingDao meetingDao,
            EsCommentDao commentDao, EsTopicRelationshipDao relationshipDao, EsTopicCurationDao curationDao,
            TopicSpaceAccessService topicSpaceAccessService) {
        int followers = subscriptionDao.findActiveByTopicId(topicId).size();

        Set<Long> meetingIds = agendaItemDao.findByTopicId(topicId).stream()
                .map(EsMeetingAgendaItem::getEsMeetingId)
                .collect(Collectors.toSet());
        List<EsMeeting> meetings = new ArrayList<>();
        for (Long meetingId : meetingIds) {
            meetingDao.findById(meetingId).ifPresent(m -> {
                if (m.getStatus() != EsMeeting.MeetingStatus.CANCELLED) {
                    meetings.add(m);
                }
            });
        }
        int meetingCount = topicSpaceAccessService.filterVisibleMeetings(viewer, meetings).size();

        int comments = commentDao.findByTopicId(topicId).size();
        int relationships = topicSpaceAccessService
                .filterVisibleRelationships(viewer, relationshipDao.findByFromTopicId(topicId)).size();
        int curated = topicSpaceAccessService
                .filterVisibleCurations(viewer, curationDao.findByCuratorTopicId(topicId)).size();

        return new TopicManageCounts(followers, meetingCount, comments, relationships, curated);
    }

    static void render(PrintWriter out, String contextPath, Long topicId, TopicManageView activeView,
            boolean isAdmin, Long topicMeetingSeriesId, boolean includeViewTopicLink, TopicManageCounts counts) {
        out.println("          <section class=\"aira-section-card\">");
        out.println(
                "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">Manage This Topic</h2></div>");
        out.println("            <div class=\"aira-section-card__body aira-stack aira-stack--compact\">");
        if (includeViewTopicLink) {
            out.println("              <a class=\"aira-recent-topic\" href=\"" + contextPath + "/es/topic/"
                    + topicId + "\">");
            out.println("                <span class=\"aira-recent-topic__title\">View Topic Page</span>");
            out.println("              </a>");
        }
        for (TopicManageView menuView : TopicManageView.values()) {
            String ariaCurrent = menuView == activeView ? " aria-current=\"page\"" : "";
            int count = counts.forView(menuView);
            out.println("              <a class=\"aira-recent-topic\" href=\"" + contextPath + "/es/topic-manage/"
                    + topicId + "/" + menuView.slug + "\"" + ariaCurrent + ">");
            if (count > 0) {
                out.println("                <span><span class=\"aira-recent-topic__title\">" + menuView.label
                        + "</span><span class=\"aira-recent-topic__meta\">" + count + "</span></span>");
            } else {
                out.println("                <span class=\"aira-recent-topic__title\">" + menuView.label
                        + "</span>");
            }
            out.println("              </a>");
        }
        out.println("              <a class=\"aira-recent-topic\" href=\"" + contextPath + "/es/topic-edit/"
                + topicId + "\">");
        out.println("                <span class=\"aira-recent-topic__title\">Edit Topic</span>");
        out.println("              </a>");
        if (isAdmin && topicMeetingSeriesId != null) {
            out.println("              <a class=\"aira-recent-topic\" href=\"" + contextPath
                    + "/admin/es/meeting-polls?esTopicMeetingId=" + topicMeetingSeriesId + "\">");
            out.println("                <span><span class=\"aira-recent-topic__title\">Meeting Polls</span>"
                    + "<span class=\"aira-recent-topic__meta\">Admin</span></span>");
            out.println("              </a>");
        }
        out.println("            </div>");
        out.println("          </section>");
    }
}
