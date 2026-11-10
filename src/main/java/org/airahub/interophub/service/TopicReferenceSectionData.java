package org.airahub.interophub.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.dao.EsGuideHostedVersionDao;
import org.airahub.interophub.model.EsGuideHostedVersion;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.GuideRows.DocumentRow;
import org.airahub.interophub.service.GuideRows.UrlBinding;
import org.airahub.interophub.service.TopicReferenceDigest.FlagNote;
import org.airahub.interophub.service.TopicReferenceDigest.OpenThread;
import org.hibernate.Session;

/**
 * The Topic Reference block on the topic page. At most two active guides, with capped links and lists.
 */
public final class TopicReferenceSectionData {
    static final int EXCERPT_LIMIT = 100;
    private static final int HUB_VERSION_LIMIT = 3;
    private static final int EXTERNAL_LINK_LIMIT = 5;

    private TopicReferenceSectionData() {
    }

    /**
     * Who sees the section. Editors only for now. Widen this to canViewTopic when readers should see it.
     * Management links stay behind canEditTopic either way.
     */
    public static boolean visibleTo(User viewer, EsTopic topic, boolean canEditTopic) {
        return viewer != null && topic != null && topic.getEsTopicId() != null && canEditTopic;
    }

    /** Null when the viewer should not see the section, or the topic has no active guide. */
    public static View load(User viewer, EsTopic topic, EsTopicSpace space, String contextPath) {
        if (!visibleTo(viewer, topic, true)) {
            return null;
        }
        boolean editor = new TopicSpaceAccessService().canEditTopic(viewer, topic);
        if (!visibleTo(viewer, topic, editor)) {
            return null;
        }
        List<DocumentRow> active = new ArrayList<>();
        for (DocumentRow document : new GuideRegistrationService().documentsForTopic(topic.getEsTopicId())) {
            if (document != null && document.active && document.id != null) {
                active.add(document);
            }
        }
        if (active.isEmpty()) {
            return null;
        }
        boolean privateSpace = space != null && space.getVisibility() == EsTopicSpace.Visibility.PRIVATE;
        GuideCommentService comments = new GuideCommentService();
        int shown = Math.min(2, active.size());
        List<Guide> guides = new ArrayList<>();
        for (int i = 0; i < shown; i++) {
            DocumentRow document = active.get(i);
            boolean hub = GuideHtmlRules.HUB.equals(document.hosting);
            List<ExternalUrl> urls = new ArrayList<>();
            for (UrlBinding binding : document.urls) {
                urls.add(new ExternalUrl(binding.label, binding.url));
            }
            guides.add(present(contextPath, topic.getEsTopicId(), document.documentKey, document.title, document.hosting,
                    privateSpace, urls, hub ? versions(document.id) : List.of(), comments.digest(document.id)));
        }
        return view(topic.getEsTopicId(), editor, contextPath, guides, active.size() - shown);
    }

    public static View view(long topicId, boolean editor, String contextPath, List<Guide> guides, int extraGuides) {
        String context = context(contextPath);
        return new View(topicId, editor, context + "/es/topic-guide-comments/" + topicId,
                context + "/es/topic-guide/" + topicId, guides, extraGuides);
    }

    public static Guide present(String contextPath, long topicId, String documentKey, String title, String hosting,
            boolean privateSpace, List<ExternalUrl> urls, List<VersionChoice> versions, TopicReferenceDigest digest) {
        TopicReferenceDigest stats = digest == null ? TopicReferenceDigest.empty() : digest;
        boolean hub = GuideHtmlRules.HUB.equals(hosting);
        List<Link> links = hub
                ? hubLinks(contextPath, topicId, documentKey, versions)
                : externalLinks(contextPath, topicId, documentKey, urls);
        String open = openHref(contextPath, documentKey);
        String manage = manageHref(contextPath, topicId, documentKey, null);
        String flagsHref = stats.flagCount() > 0 && stats.firstAnchor() != null
                ? open + "&anchor=" + encode(stats.firstAnchor())
                : manage;
        List<OpenThread> threads = stats.openThreads();
        if (threads.size() > TopicReferenceDigest.LIST_LIMIT) {
            threads = threads.subList(0, TopicReferenceDigest.LIST_LIMIT);
        }
        List<Attention> needsAttention = new ArrayList<>();
        for (OpenThread thread : threads) {
            String replies = thread.replies() == 1 ? "1 reply" : thread.replies() + " replies";
            needsAttention.add(new Attention(open + "&comment=" + thread.id(), excerpt(thread.text()),
                    author(thread.author()) + " · " + GuideDisplayDates.day(thread.activityAt()) + " · " + replies));
        }
        List<FlagNote> notes = stats.flagNotes();
        if (notes.size() > TopicReferenceDigest.FLAG_LIMIT) {
            notes = notes.subList(0, TopicReferenceDigest.FLAG_LIMIT);
        }
        List<FlagLine> flagged = new ArrayList<>();
        for (FlagNote note : notes) {
            String anchor = note.anchor() == null ? "" : note.anchor();
            String date = GuideDisplayDates.day(note.at());
            String meta = anchor.isBlank() ? date : anchor + " · " + date;
            String href = anchor.isBlank() ? manage : open + "&anchor=" + encode(anchor);
            flagged.add(new FlagLine(href, excerpt(note.note()), meta));
        }
        return new Guide(title == null || title.isBlank() ? documentKey : title,
                hub ? "Hosted on InteropHub" : "Published externally",
                privateSpace && !hub, links,
                new Count("Open", stats.open(), manageHref(contextPath, topicId, documentKey, "OPEN")),
                new Count("Disposed", stats.disposed(), manageHref(contextPath, topicId, documentKey, "DISPOSED")),
                new Count("Closed", stats.closed(), manageHref(contextPath, topicId, documentKey, "CLOSED")),
                new Count("Flags", stats.flagCount(), flagsHref),
                activity(stats), needsAttention, stats.open(), flagged, stats.flagCount(), manage);
    }

    /** Newest label first: latest, then main, then three version tags, then "+N earlier". */
    public static List<Link> hubLinks(String contextPath, long topicId, String documentKey, List<VersionChoice> uploads) {
        Map<String, VersionChoice> newest = new LinkedHashMap<>();
        List<VersionChoice> ordered = new ArrayList<>();
        if (uploads != null) {
            ordered.addAll(uploads);
        }
        ordered.sort(Comparator.comparing((VersionChoice choice) -> uploadedAt(choice), Comparator.reverseOrder()));
        for (VersionChoice choice : ordered) {
            if (choice == null || choice.label() == null || choice.label().isBlank()) {
                continue;
            }
            newest.putIfAbsent(choice.label(), choice);
        }
        VersionChoice latest = newest.remove("latest");
        VersionChoice main = newest.remove("main");
        List<VersionChoice> rest = new ArrayList<>(newest.values());
        rest.sort(Comparator.comparing((VersionChoice choice) -> uploadedAt(choice), Comparator.reverseOrder()));
        List<VersionChoice> shown = new ArrayList<>();
        if (latest != null) {
            shown.add(latest);
        }
        if (main != null) {
            shown.add(main);
        }
        int versionSlots = Math.min(HUB_VERSION_LIMIT, rest.size());
        shown.addAll(rest.subList(0, versionSlots));
        return links(contextPath, topicId, documentKey, shown, rest.size() - versionSlots, "earlier");
    }

    /** Registration order. The first link opens through /guide/open. At most five, then "+N more". */
    public static List<Link> externalLinks(String contextPath, long topicId, String documentKey, List<ExternalUrl> urls) {
        List<ExternalUrl> present = new ArrayList<>();
        if (urls != null) {
            for (ExternalUrl url : urls) {
                if (url != null && url.url() != null && !url.url().isBlank()) {
                    present.add(url);
                }
            }
        }
        int shown = Math.min(EXTERNAL_LINK_LIMIT, present.size());
        List<Link> links = new ArrayList<>();
        String open = openHref(contextPath, documentKey);
        for (int i = 0; i < shown; i++) {
            ExternalUrl url = present.get(i);
            String label = url.label() == null || url.label().isBlank() ? "Guide" : url.label();
            links.add(new Link(label, i == 0 ? open : url.url(), i == 0));
        }
        int more = present.size() - shown;
        if (more > 0) {
            links.add(new Link("+" + more + " more", settingsHref(contextPath, topicId), false));
        }
        if (links.isEmpty()) {
            links.add(new Link("Open the guide", open, true));
        }
        return links;
    }

    private static List<Link> links(String contextPath, long topicId, String documentKey, List<VersionChoice> shown,
            int remaining, String remainingWord) {
        List<Link> links = new ArrayList<>();
        String open = openHref(contextPath, documentKey);
        for (int i = 0; i < shown.size(); i++) {
            VersionChoice choice = shown.get(i);
            String href = i == 0 ? open : viewHref(contextPath, documentKey, choice.label());
            links.add(new Link(choice.label(), href, i == 0));
        }
        if (remaining > 0) {
            links.add(new Link("+" + remaining + " " + remainingWord, settingsHref(contextPath, topicId), false));
        }
        if (links.isEmpty()) {
            links.add(new Link("Open the guide", open, true));
        }
        return links;
    }

    private static List<VersionChoice> versions(long documentId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<VersionChoice> choices = new ArrayList<>();
            for (EsGuideHostedVersion row : new EsGuideHostedVersionDao().findByDocumentId(session, documentId)) {
                choices.add(new VersionChoice(row.getLabel(), row.getUploadedAt()));
            }
            return choices;
        }
    }

    static String activity(TopicReferenceDigest digest) {
        return digest.lastActivity() == null ? null : "Last activity " + GuideDisplayDates.day(digest.lastActivity());
    }

    static String excerpt(String text) {
        String collapsed = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (collapsed.length() <= EXCERPT_LIMIT) {
            return collapsed;
        }
        return collapsed.substring(0, EXCERPT_LIMIT - 3) + "...";
    }

    private static String author(String name) {
        return name == null || name.isBlank() ? "Someone" : name;
    }

    private static LocalDateTime uploadedAt(VersionChoice choice) {
        return choice == null || choice.uploadedAt() == null ? LocalDateTime.MIN : choice.uploadedAt();
    }

    private static String openHref(String contextPath, String documentKey) {
        return context(contextPath) + "/guide/open?doc=" + encode(documentKey);
    }

    private static String viewHref(String contextPath, String documentKey, String label) {
        return context(contextPath) + "/guide/view/" + encode(documentKey) + "/" + encode(label) + "/";
    }

    private static String settingsHref(String contextPath, long topicId) {
        return context(contextPath) + "/es/topic-guide/" + topicId;
    }

    private static String manageHref(String contextPath, long topicId, String documentKey, String show) {
        String href = context(contextPath) + "/es/topic-guide-comments/" + topicId + "?document=" + encode(documentKey);
        return show == null ? href : href + "&show=" + encode(show);
    }

    private static String context(String contextPath) {
        return contextPath == null ? "" : contextPath;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public record VersionChoice(String label, LocalDateTime uploadedAt) {
    }

    public record ExternalUrl(String label, String url) {
    }

    public record Link(String label, String href, boolean primary) {
    }

    public record Count(String label, int value, String href) {
    }

    public record Attention(String href, String excerpt, String meta) {
    }

    public record FlagLine(String href, String note, String meta) {
    }

    public record Guide(String title, String hostingBadge, boolean externalOnPrivate, List<Link> links, Count open,
            Count disposed, Count closed, Count flags, String activity, List<Attention> needsAttention, int openTotal,
            List<FlagLine> flagged, int flagTotal, String manageHref) {
        public Guide {
            links = List.copyOf(links == null ? List.of() : links);
            needsAttention = List.copyOf(needsAttention == null ? List.of() : needsAttention);
            flagged = List.copyOf(flagged == null ? List.of() : flagged);
        }
    }

    public record View(long topicId, boolean editor, String commentsHref, String settingsHref, List<Guide> guides,
            int extraGuides) {
        public View {
            guides = List.copyOf(guides == null ? List.of() : guides);
            commentsHref = commentsHref == null ? "" : commentsHref;
            settingsHref = settingsHref == null ? "" : settingsHref;
        }
    }
}
