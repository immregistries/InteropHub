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
 * The Topic Reference block on the topic page. At most two guides, with capped links and lists.
 * Editors see every guide and label. Readers who can view the topic see a guide only when it
 * has a Current or Listed label (plan 3.1), and its counts follow the Current label's comment mode.
 */
public final class TopicReferenceSectionData {
    static final int EXCERPT_LIMIT = 100;
    private static final int HUB_VERSION_LIMIT = 3;
    private static final int EXTERNAL_LINK_LIMIT = 5;

    private TopicReferenceSectionData() {
    }

    /**
     * Whether the viewer sees the section whatever the labels say: topic editors.
     * Readers see it only for guides with a Current or Listed label ({@link #load}).
     * Management links stay behind canEditTopic either way.
     */
    public static boolean visibleTo(User viewer, EsTopic topic, boolean canEditTopic) {
        return viewer != null && topic != null && topic.getEsTopicId() != null && canEditTopic;
    }

    /** Null when the viewer should not see the section, or no guide is shown to them. */
    public static View load(User viewer, EsTopic topic, EsTopicSpace space, String contextPath) {
        if (viewer == null || topic == null || topic.getEsTopicId() == null) {
            return null;
        }
        TopicSpaceAccessService access = new TopicSpaceAccessService();
        boolean editor = access.canEditTopic(viewer, topic);
        if (!editor && !access.canViewTopic(viewer, topic)) {
            return null;
        }
        GuideCommentService comments = new GuideCommentService();
        List<Candidate> candidates = new ArrayList<>();
        for (DocumentRow document : new GuideRegistrationService().documentsForTopic(topic.getEsTopicId())) {
            if (document == null || !document.active || document.id == null) {
                continue;
            }
            boolean hub = GuideHtmlRules.HUB.equals(document.hosting);
            List<VersionChoice> uploads = hub ? versions(document.id) : List.of();
            GuideLabels.Facts facts = comments.labelFacts(document.id);
            List<LabelChoice> labels = labelChoices(contextPath, document, facts, uploads);
            boolean forReaders = labels.stream().anyMatch(choice -> GuideLabels.readerListed(choice.display()));
            if (!editor && !forReaders) {
                continue;
            }
            candidates.add(new Candidate(document, uploads, labels, forReaders, facts));
        }
        if (candidates.isEmpty()) {
            return null;
        }
        boolean privateSpace = space != null && space.getVisibility() == EsTopicSpace.Visibility.PRIVATE;
        int shown = Math.min(2, candidates.size());
        boolean readersSee = false;
        List<Guide> guides = new ArrayList<>();
        for (int i = 0; i < shown; i++) {
            Candidate candidate = candidates.get(i);
            DocumentRow document = candidate.document;
            readersSee |= candidate.forReaders;
            GuideLabels.Facts facts = candidate.facts;
            String current = facts.current();
            TopicReferenceDigest digest = editor
                    ? comments.digest(document.id)
                    : comments.digestFor(document.id, current == null ? null : facts.policy(current));
            if (!editor || hasSettings(candidate.labels)) {
                guides.add(presentLabels(contextPath, topic.getEsTopicId(), document.documentKey, document.title,
                        document.hosting, privateSpace, candidate.labels, digest, editor, candidate.forReaders));
            } else {
                List<ExternalUrl> urls = new ArrayList<>();
                for (UrlBinding binding : document.urls) {
                    urls.add(new ExternalUrl(binding.label, binding.url));
                }
                guides.add(present(contextPath, topic.getEsTopicId(), document.documentKey, document.title,
                        document.hosting, privateSpace, urls, candidate.uploads, digest));
            }
        }
        if (readersSee) {
            for (int i = 0; i < guides.size(); i++) {
                if (!candidates.get(i).forReaders) {
                    guides.set(i, guides.get(i).asEditorsOnly());
                }
            }
        }
        return view(topic.getEsTopicId(), editor, contextPath, guides, candidates.size() - shown, readersSee);
    }

    public static View view(long topicId, boolean editor, String contextPath, List<Guide> guides, int extraGuides) {
        return view(topicId, editor, contextPath, guides, extraGuides, false);
    }

    public static View view(long topicId, boolean editor, String contextPath, List<Guide> guides, int extraGuides,
            boolean readersSee) {
        String context = context(contextPath);
        return new View(topicId, editor, context + "/es/topic-guide-comments/" + topicId,
                context + "/es/topic-guide/" + topicId, guides, extraGuides, readersSee);
    }

    /** Editors' view of a guide whose labels have no settings: every label as it was before labels had states. */
    public static Guide present(String contextPath, long topicId, String documentKey, String title, String hosting,
            boolean privateSpace, List<ExternalUrl> urls, List<VersionChoice> versions, TopicReferenceDigest digest) {
        boolean hub = GuideHtmlRules.HUB.equals(hosting);
        List<Link> links = hub
                ? hubLinks(contextPath, topicId, documentKey, versions)
                : externalLinks(contextPath, topicId, documentKey, urls);
        return guide(contextPath, topicId, documentKey, title, hosting, privateSpace, links, List.of(), List.of(),
                digest, true, null, false);
    }

    /**
     * A guide with label states. Readers get the Current label as "Read the guide" and Listed
     * labels under "Other versions"; Unlisted, Authors only, and Retired labels are left out.
     * Editors get every label: Current first, then the rest with badges, and Retired labels
     * in their own group. {@code readersSee} says whether readers see this guide at all; when
     * they do not, the section's "Editors only" badge covers it and Unlisted labels carry no badge.
     */
    public static Guide presentLabels(String contextPath, long topicId, String documentKey, String title,
            String hosting, boolean privateSpace, List<LabelChoice> labels, TopicReferenceDigest digest,
            boolean editor, boolean readersSee) {
        List<LabelChoice> all = labels == null ? List.of() : labels;
        LabelChoice current = null;
        for (LabelChoice choice : all) {
            if (GuideLabels.CURRENT.equals(choice.display())) {
                current = choice;
                break;
            }
        }
        String open = openHref(contextPath, documentKey);
        List<Link> main = new ArrayList<>();
        List<Link> others = new ArrayList<>();
        List<Link> retired = new ArrayList<>();
        if (current != null) {
            String href = open + "&label=" + encode(current.label());
            main.add(editor
                    ? new Link(current.label(), href, true, "Current")
                    : new Link("Read the guide", href, true, null));
        }
        for (LabelChoice choice : all) {
            if (choice == current) {
                continue;
            }
            String display = choice.display();
            if (!editor) {
                if (GuideLabels.LISTED.equals(display)) {
                    others.add(new Link(choice.label(), choice.href(), false, null));
                }
                continue;
            }
            if (GuideLabels.RETIRED.equals(display)) {
                retired.add(new Link(choice.label(), choice.href(), false, null));
                continue;
            }
            String badge = switch (display) {
                case GuideLabels.AUTHORS_ONLY -> "Authors only";
                case GuideLabels.UNLISTED -> readersSee ? "Unlisted" : null;
                default -> null;
            };
            others.add(new Link(choice.label(), choice.href(), false, badge));
        }
        if (editor && main.isEmpty() && !others.isEmpty()) {
            Link first = others.remove(0);
            main.add(new Link(first.label(), open + "&label=" + encode(first.label()), true, first.badge()));
        }
        if (editor && main.isEmpty()) {
            main.add(new Link("Open the guide", open, true, null));
        }
        String readerLabel = current == null ? null : current.label();
        boolean commentsShown = editor || current == null || !GuideLabels.CLOSED.equals(current.commentMode());
        return guide(contextPath, topicId, documentKey, title, hosting, privateSpace, main, others, retired, digest,
                editor, readerLabel, !commentsShown);
    }

    private static Guide guide(String contextPath, long topicId, String documentKey, String title, String hosting,
            boolean privateSpace, List<Link> links, List<Link> otherVersions, List<Link> retired,
            TopicReferenceDigest digest, boolean editor, String readerLabel, boolean commentsHidden) {
        TopicReferenceDigest stats = digest == null ? TopicReferenceDigest.empty() : digest;
        boolean hub = GuideHtmlRules.HUB.equals(hosting);
        String open = openHref(contextPath, documentKey);
        if (!editor && readerLabel != null) {
            open = open + "&label=" + encode(readerLabel);
        }
        String manage = manageHref(contextPath, topicId, documentKey, null);
        String readerTarget = open;
        String flagsHref = stats.flagCount() > 0 && stats.firstAnchor() != null
                ? open + "&anchor=" + encode(stats.firstAnchor())
                : (editor ? manage : readerTarget);
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
            String href = anchor.isBlank() ? (editor ? manage : readerTarget) : open + "&anchor=" + encode(anchor);
            flagged.add(new FlagLine(href, excerpt(note.note()), meta));
        }
        String openCount = editor ? manageHref(contextPath, topicId, documentKey, "OPEN") : readerTarget;
        String disposedCount = editor ? manageHref(contextPath, topicId, documentKey, "DISPOSED") : readerTarget;
        String closedCount = editor ? manageHref(contextPath, topicId, documentKey, "CLOSED") : readerTarget;
        return new Guide(title == null || title.isBlank() ? documentKey : title,
                hub ? "Hosted on InteropHub" : "Published externally",
                privateSpace && !hub, links,
                new Count("Open", stats.open(), openCount),
                new Count("Disposed", stats.disposed(), disposedCount),
                new Count("Closed", stats.closed(), closedCount),
                new Count("Flags", stats.flagCount(), flagsHref),
                activity(stats), needsAttention, stats.open(), flagged, stats.flagCount(),
                editor ? manage : readerTarget, otherVersions, retired, !editor, commentsHidden, false);
    }

    /**
     * The labels a guide can link to, with their display state and comment mode: a hosted
     * guide's uploaded labels (newest first after latest and main), or an external guide's
     * registered URLs in registration order. Labels without a page (build-only) are left out.
     */
    static List<LabelChoice> labelChoices(String contextPath, DocumentRow document, GuideLabels.Facts facts,
            List<VersionChoice> uploads) {
        List<LabelChoice> choices = new ArrayList<>();
        if (GuideHtmlRules.HUB.equals(document.hosting)) {
            Map<String, LocalDateTime> newest = new LinkedHashMap<>();
            for (VersionChoice upload : uploads) {
                if (upload != null && upload.label() != null) {
                    newest.merge(upload.label(), uploadedAt(upload), (left, right) -> left.isAfter(right) ? left : right);
                }
            }
            List<String> labels = new ArrayList<>(newest.keySet());
            labels.sort(Comparator.comparingInt(GuideLabels::rank)
                    .thenComparing((String label) -> newest.get(label), Comparator.reverseOrder())
                    .thenComparing(Comparator.naturalOrder()));
            for (String label : labels) {
                var setting = facts.setting(label);
                choices.add(new LabelChoice(label, setting.display, setting.commentMode,
                        viewHref(contextPath, document.documentKey, label), newest.get(label)));
            }
        } else {
            for (UrlBinding binding : document.urls) {
                if (binding == null || binding.label == null || binding.url == null) {
                    continue;
                }
                var setting = facts.setting(binding.label);
                choices.add(new LabelChoice(binding.label, setting.display, setting.commentMode, binding.url, null));
            }
        }
        return choices;
    }

    private static boolean hasSettings(List<LabelChoice> labels) {
        for (LabelChoice choice : labels) {
            if (!GuideLabels.UNLISTED.equals(choice.display())) {
                return true;
            }
        }
        return false;
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

    static String viewHref(String contextPath, String documentKey, String label) {
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

    private record Candidate(DocumentRow document, List<VersionChoice> uploads, List<LabelChoice> labels,
            boolean forReaders, GuideLabels.Facts facts) {
    }

    public record VersionChoice(String label, LocalDateTime uploadedAt) {
    }

    public record ExternalUrl(String label, String url) {
    }

    /**
     * One label of a guide on the topic page. {@code href} is the label's own page: a
     * context-relative hosted view path, or an external URL.
     */
    public record LabelChoice(String label, String display, String commentMode, String href, LocalDateTime at) {
        public LabelChoice {
            display = display == null ? GuideLabels.UNLISTED : display;
            commentMode = commentMode == null ? GuideLabels.OPEN : commentMode;
        }
    }

    /** {@code badge} marks a label readers cannot see ("Unlisted", "Authors only") or the Current one for editors. */
    public record Link(String label, String href, boolean primary, String badge) {
        public Link(String label, String href, boolean primary) {
            this(label, href, primary, null);
        }
    }

    public record Count(String label, int value, String href) {
    }

    public record Attention(String href, String excerpt, String meta) {
    }

    public record FlagLine(String href, String note, String meta) {
    }

    /**
     * {@code reader} is true for a viewer who cannot edit the topic: counts and lists link
     * into the guide instead of the comments page. {@code commentsHidden} is true when the
     * reader's Current label is Closed: no counts or lists at all.
     */
    public record Guide(String title, String hostingBadge, boolean externalOnPrivate, List<Link> links, Count open,
            Count disposed, Count closed, Count flags, String activity, List<Attention> needsAttention, int openTotal,
            List<FlagLine> flagged, int flagTotal, String manageHref, List<Link> otherVersions, List<Link> retired,
            boolean reader, boolean commentsHidden, boolean editorsOnly) {
        public Guide {
            links = List.copyOf(links == null ? List.of() : links);
            needsAttention = List.copyOf(needsAttention == null ? List.of() : needsAttention);
            flagged = List.copyOf(flagged == null ? List.of() : flagged);
            otherVersions = List.copyOf(otherVersions == null ? List.of() : otherVersions);
            retired = List.copyOf(retired == null ? List.of() : retired);
        }

        /** The same guide marked as one readers cannot see while the section is open to them. */
        public Guide asEditorsOnly() {
            return new Guide(title, hostingBadge, externalOnPrivate, links, open, disposed, closed, flags, activity,
                    needsAttention, openTotal, flagged, flagTotal, manageHref, otherVersions, retired, reader,
                    commentsHidden, true);
        }
    }

    /** {@code readersSee}: readers who can view the topic see at least one guide, so "Editors only" marks items, not the section. */
    public record View(long topicId, boolean editor, String commentsHref, String settingsHref, List<Guide> guides,
            int extraGuides, boolean readersSee) {
        public View {
            guides = List.copyOf(guides == null ? List.of() : guides);
            commentsHref = commentsHref == null ? "" : commentsHref;
            settingsHref = settingsHref == null ? "" : settingsHref;
        }
    }
}
