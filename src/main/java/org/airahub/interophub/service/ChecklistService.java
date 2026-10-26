package org.airahub.interophub.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.airahub.interophub.dao.EsChecklistDao;
import org.airahub.interophub.dao.EsChecklistItemDao;
import org.airahub.interophub.dao.EsChecklistTemplateDao;
import org.airahub.interophub.dao.EsChecklistTemplateItemDao;
import org.airahub.interophub.dao.EsInitiativeDao;
import org.airahub.interophub.dao.EsInitiativeManagerDao;
import org.airahub.interophub.dao.EsInitiativeTimelineEntryDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsChecklist;
import org.airahub.interophub.model.EsChecklistItem;
import org.airahub.interophub.model.EsChecklistTemplate;
import org.airahub.interophub.model.EsChecklistTemplateItem;
import org.airahub.interophub.model.EsInitiative;
import org.airahub.interophub.model.EsInitiativeManager;
import org.airahub.interophub.model.EsInitiativeTimelineEntry;
import org.airahub.interophub.model.User;

/**
 * Checklist copies, template uploads, and the shared initiative timeline.
 * Callers enforce {@link InitiativeAccess} before mutating.
 */
public class ChecklistService {

    public record TemplatePreview(ChecklistTemplateParser.Parsed parsed, List<String> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }

        public String errorText() {
            return String.join("\n", errors);
        }
    }

    private final EsChecklistTemplateDao templateDao;
    private final EsChecklistTemplateItemDao templateItemDao;
    private final EsChecklistDao checklistDao;
    private final EsChecklistItemDao itemDao;
    private final EsInitiativeTimelineEntryDao timelineDao;
    private final EsInitiativeDao initiativeDao;
    private final EsInitiativeManagerDao managerDao;
    private final UserDao userDao;

    public ChecklistService() {
        this.templateDao = new EsChecklistTemplateDao();
        this.templateItemDao = new EsChecklistTemplateItemDao();
        this.checklistDao = new EsChecklistDao();
        this.itemDao = new EsChecklistItemDao();
        this.timelineDao = new EsInitiativeTimelineEntryDao();
        this.initiativeDao = new EsInitiativeDao();
        this.managerDao = new EsInitiativeManagerDao();
        this.userDao = new UserDao();
    }

    public void copyCurrentIfPresent(EsInitiative initiative, User actor) {
        if (initiative == null || checklistDao.findByInitiativeId(initiative.getEsInitiativeId()).isPresent()) {
            return;
        }
        templateDao.findCurrent(EsChecklistTemplate.Kind.INITIATIVE)
                .ifPresent(template -> copyTemplate(initiative, template));
    }

    public void startChecklist(EsInitiative initiative) {
        if (checklistDao.findByInitiativeId(initiative.getEsInitiativeId()).isPresent()) {
            throw new IllegalArgumentException("This initiative already has a checklist.");
        }
        EsChecklistTemplate template = templateDao.findCurrent(EsChecklistTemplate.Kind.INITIATIVE)
                .orElseThrow(() -> new IllegalArgumentException("There is no current initiative template."));
        copyTemplate(initiative, template);
    }

    public Optional<EsChecklist> findForInitiative(Long initiativeId) {
        return checklistDao.findByInitiativeId(initiativeId);
    }

    public List<EsChecklistItem> items(Long checklistId) {
        return itemDao.findByChecklistId(checklistId);
    }

    public List<EsChecklistTemplate> templates() {
        return templateDao.findAllOrderByKindAndVersion();
    }

    public Optional<EsChecklistTemplate> findTemplate(Long templateId) {
        return templateDao.findById(templateId);
    }

    public Optional<EsChecklistTemplate> currentTemplate(EsChecklistTemplate.Kind kind) {
        return templateDao.findCurrent(kind);
    }

    public List<EsChecklistTemplateItem> templateItems(Long templateId) {
        return templateItemDao.findByTemplateId(templateId);
    }

    public boolean upgradeAvailable(EsInitiative initiative, EsChecklist checklist) {
        if (initiative == null || checklist == null || !acceptsUpgrade(initiative)) {
            return false;
        }
        return newerTemplate(checklist).isPresent();
    }

    public TemplatePreview previewUpload(String markdown) {
        ChecklistTemplateParser.Parsed parsed = ChecklistTemplateParser.parse(markdown);
        List<String> errors = new ArrayList<>();
        for (ChecklistTemplateParser.ParseError error : parsed.errors()) {
            errors.add("Line " + error.lineNumber() + ": " + error.message());
        }
        if (parsed.kind() != null && parsed.version() != null) {
            EsChecklistTemplate.Kind kind = EsChecklistTemplate.Kind.valueOf(parsed.kind());
            if (templateDao.findByKindAndVersion(kind, parsed.version()).isPresent()) {
                errors.add("Line " + versionLine(markdown) + ": Version " + parsed.version() + " already exists for "
                        + parsed.kind() + ".");
            }
        }
        return new TemplatePreview(parsed, List.copyOf(errors));
    }

    public EsChecklistTemplate saveUpload(User actor, String markdown) {
        TemplatePreview preview = previewUpload(markdown);
        if (!preview.ok()) {
            throw new IllegalArgumentException(preview.errorText());
        }
        ChecklistTemplateParser.Parsed parsed = preview.parsed();
        EsChecklistTemplate.Kind kind = EsChecklistTemplate.Kind.valueOf(parsed.kind());
        templateDao.findCurrent(kind).ifPresent(current -> {
            current.setCurrent(false);
            templateDao.save(current);
        });
        EsChecklistTemplate template = new EsChecklistTemplate();
        template.setTemplateKind(kind);
        template.setVersionNo(parsed.version());
        template.setTitle(limit(parsed.title(), 200));
        template.setSourceMarkdown(markdown);
        template.setCurrent(true);
        template.setUploadedByUserId(actor == null ? null : actor.getUserId());
        template = templateDao.save(template);
        for (ChecklistTemplateParser.Item item : parsed.items()) {
            templateItemDao.save(toTemplateItem(template.getEsChecklistTemplateId(), item));
        }
        return template;
    }

    public ChecklistFeedback.Report feedback(EsChecklistTemplate.Kind kind) {
        List<EsChecklist> checklists = checklistDao.findByKind(kind);
        Map<Long, EsChecklist> byId = new HashMap<>();
        Map<Long, String> initiativeNames = new HashMap<>();
        Set<Long> templateIds = new HashSet<>();
        for (EsChecklist checklist : checklists) {
            byId.put(checklist.getEsChecklistId(), checklist);
            templateIds.add(checklist.getAppliedEsChecklistTemplateId());
            initiativeDao.findById(checklist.getEsInitiativeId())
                    .ifPresent(initiative -> initiativeNames.put(checklist.getEsChecklistId(),
                            initiative.getInitiativeName()));
        }
        Map<String, EsChecklistTemplateItem> templateByChecklistAndKey = new HashMap<>();
        for (EsChecklistTemplateItem templateItem : templateItemDao.findByTemplateIds(templateIds)) {
            for (EsChecklist checklist : checklists) {
                if (checklist.getAppliedEsChecklistTemplateId().equals(templateItem.getEsChecklistTemplateId())) {
                    templateByChecklistAndKey.put(checklist.getEsChecklistId() + ":" + templateItem.getItemKey(),
                            templateItem);
                }
            }
        }
        List<ChecklistFeedback.Observed> observed = new ArrayList<>();
        for (EsChecklistItem item : itemDao.findByChecklistIds(byId.keySet())) {
            EsChecklistTemplateItem templateItem = templateByChecklistAndKey.get(item.getEsChecklistId() + ":"
                    + item.getItemKey());
            observed.add(new ChecklistFeedback.Observed(
                    initiativeNames.getOrDefault(item.getEsChecklistId(), "Initiative"),
                    item.getItemKey(),
                    item.getItemOrigin().name(),
                    Boolean.TRUE.equals(item.getRemoved()),
                    Boolean.TRUE.equals(item.getLocallyEdited()),
                    item.getItemStatus() == EsChecklistItem.Status.NOT_APPLICABLE,
                    item.getPhaseLabel(),
                    item.getTitle(),
                    item.getGuidance(),
                    templateItem == null ? null : templateItem.getTitle(),
                    templateItem == null ? null : templateItem.getGuidance()));
        }
        return ChecklistFeedback.collect(observed);
    }

    public ChecklistMerge.Result previewUpgrade(EsInitiative initiative) {
        EsChecklist checklist = requireChecklist(initiative);
        EsChecklistTemplate next = newerTemplate(checklist)
                .orElseThrow(() -> new IllegalArgumentException("This checklist is already on the current template."));
        return ChecklistMerge.merge(states(itemDao.findByChecklistId(checklist.getEsChecklistId())),
                parserItems(templateItemDao.findByTemplateId(next.getEsChecklistTemplateId())));
    }

    public void applyUpgrade(User actor, EsInitiative initiative) {
        if (!acceptsUpgrade(initiative)) {
            throw new IllegalArgumentException("Closed and archived initiatives keep the template they ran with.");
        }
        EsChecklist checklist = requireChecklist(initiative);
        EsChecklistTemplate applied = templateDao.findById(checklist.getAppliedEsChecklistTemplateId())
                .orElseThrow(() -> new IllegalArgumentException("The applied template is missing."));
        EsChecklistTemplate next = newerTemplate(checklist)
                .orElseThrow(() -> new IllegalArgumentException("This checklist is already on the current template."));
        List<EsChecklistItem> existing = itemDao.findByChecklistId(checklist.getEsChecklistId());
        ChecklistMerge.Result result = ChecklistMerge.merge(states(existing),
                parserItems(templateItemDao.findByTemplateId(next.getEsChecklistTemplateId())));
        Map<String, EsChecklistItem> byKey = new HashMap<>();
        for (EsChecklistItem item : existing) {
            byKey.put(item.getItemKey(), item);
        }
        for (ChecklistMerge.State state : result.items()) {
            EsChecklistItem item = byKey.get(state.key());
            if (item == null) {
                item = new EsChecklistItem();
                item.setEsChecklistId(checklist.getEsChecklistId());
                item.setItemKey(state.key());
                item.setCreatedAt(LocalDateTime.now());
                applyState(item, state);
                itemDao.save(item);
            } else {
                applyState(item, state);
                itemDao.save(item);
            }
        }
        checklist.setAppliedEsChecklistTemplateId(next.getEsChecklistTemplateId());
        checklistDao.save(checklist);
        record(initiative.getEsInitiativeId(), EsInitiativeTimelineEntry.SubjectType.CHECKLIST,
                checklist.getEsChecklistId(), actor, EsInitiativeTimelineEntry.EntryType.TEMPLATE_UPGRADE,
                "v" + applied.getVersionNo(), "v" + next.getVersionNo(),
                ChecklistMerge.upgradeNote(applied.getVersionNo(), next.getVersionNo(), result));
    }

    public String download(EsInitiative initiative) {
        EsChecklist checklist = requireChecklist(initiative);
        List<ChecklistTemplateParser.Item> items = new ArrayList<>();
        for (EsChecklistItem item : itemDao.findByChecklistId(checklist.getEsChecklistId())) {
            if (Boolean.TRUE.equals(item.getRemoved())) {
                continue;
            }
            items.add(new ChecklistTemplateParser.Item(item.getItemKey(), item.getPhaseLabel(), item.getTitle(),
                    item.getGuidance(), item.getDueAnchor(), item.getDueOffsetDays(), item.getCheckCode(),
                    item.getDisplayOrder() == null ? items.size() + 1 : item.getDisplayOrder(), 0));
        }
        return ChecklistMarkdown.write(initiative.getInitiativeName() + " checklist", checklist.getTemplateKind().name(),
                items);
    }

    public void setStatus(User actor, EsInitiative initiative, Long itemId, EsChecklistItem.Status status, String note) {
        EsChecklistItem item = requireItem(initiative, itemId);
        if (status == null) {
            throw new IllegalArgumentException("Choose a status.");
        }
        InitiativeRosterRules.PlannedEntry planned = InitiativeRosterRules.planStatusUpdate(
                item.getItemStatus().name(), status.name(), note);
        if (planned == null) {
            return;
        }
        if (planned.kind() == InitiativeRosterRules.PlannedKind.STATUS_CHANGE) {
            item.setItemStatus(status);
            item.setStatusChangedAt(LocalDateTime.now());
            item.setStatusChangedByUserId(actor.getUserId());
            itemDao.save(item);
        }
        recordPlanned(initiative, item, actor, planned);
    }

    public EsChecklistItem addItem(User actor, EsInitiative initiative, String phase, String title, String guidance,
            Long afterItemId) {
        EsChecklist checklist = requireChecklist(initiative);
        String phaseLabel = requireText(phase, "Phase", 200);
        String itemTitle = requireText(title, "Title", 500);
        List<EsChecklistItem> items = itemDao.findByChecklistId(checklist.getEsChecklistId());
        EsChecklistItem created = new EsChecklistItem();
        created.setEsChecklistId(checklist.getEsChecklistId());
        created.setItemKey(uniqueKey(itemTitle, items));
        created.setItemOrigin(EsChecklistItem.Origin.CUSTOM);
        created.setInCurrentTemplate(false);
        created.setRemoved(false);
        created.setLocallyEdited(false);
        created.setPhaseLabel(phaseLabel);
        created.setTitle(itemTitle);
        created.setGuidance(cleanGuidance(guidance));
        created.setDisplayOrder(0);
        created.setItemStatus(EsChecklistItem.Status.OPEN);
        created.setStatusChangedAt(LocalDateTime.now());
        created.setStatusChangedByUserId(actor.getUserId());
        created = itemDao.save(created);
        placeNewItem(items, created, afterItemId, phaseLabel);
        record(initiative.getEsInitiativeId(), EsInitiativeTimelineEntry.SubjectType.CHECKLIST_ITEM,
                created.getEsChecklistItemId(), actor, EsInitiativeTimelineEntry.EntryType.STATUS_CHANGE, null,
                EsChecklistItem.Status.OPEN.getLabel(), null);
        return created;
    }

    public void editItem(User actor, EsInitiative initiative, Long itemId, String title, String guidance) {
        EsChecklistItem item = requireItem(initiative, itemId);
        String itemTitle = requireText(title, "Title", 500);
        String itemGuidance = cleanGuidance(guidance);
        String previousTitle = item.getTitle();
        String previousGuidance = item.getGuidance();
        item.setTitle(itemTitle);
        item.setGuidance(itemGuidance);
        if (item.getItemOrigin() == EsChecklistItem.Origin.TEMPLATE) {
            item.setLocallyEdited(!sameText(itemTitle, templateTitle(initiative, item))
                    || !sameText(itemGuidance, templateGuidance(initiative, item)));
        }
        itemDao.save(item);
        recordField(initiative, item, actor, "Title", previousTitle, itemTitle);
        recordField(initiative, item, actor, "Guidance", previousGuidance, itemGuidance);
    }

    public void removeItem(User actor, EsInitiative initiative, Long itemId) {
        EsChecklistItem item = requireItem(initiative, itemId);
        if (Boolean.TRUE.equals(item.getRemoved())) {
            return;
        }
        item.setRemoved(true);
        itemDao.save(item);
        recordField(initiative, item, actor, "Removed", "visible", "removed");
    }

    public void restoreItem(User actor, EsInitiative initiative, Long itemId) {
        EsChecklistItem item = requireItem(initiative, itemId);
        if (!Boolean.TRUE.equals(item.getRemoved())) {
            return;
        }
        item.setRemoved(false);
        itemDao.save(item);
        recordField(initiative, item, actor, "Restored", "removed", "visible");
    }

    public void move(EsInitiative initiative, Long itemId, String direction) {
        EsChecklist checklist = requireChecklist(initiative);
        List<EsChecklistItem> items = itemDao.findByChecklistId(checklist.getEsChecklistId());
        EsChecklistItem item = findItem(items, itemId);
        List<EsChecklistItem> phase = new ArrayList<>();
        for (EsChecklistItem candidate : items) {
            if (!Boolean.TRUE.equals(candidate.getRemoved()) && item.getPhaseLabel().equals(candidate.getPhaseLabel())) {
                phase.add(candidate);
            }
        }
        phase.sort(Comparator.comparing(EsChecklistItem::getDisplayOrder));
        int index = phase.indexOf(item);
        int swap = "up".equals(direction) ? index - 1 : index + 1;
        if (index < 0 || swap < 0 || swap >= phase.size()) {
            return;
        }
        EsChecklistItem other = phase.get(swap);
        Integer order = item.getDisplayOrder();
        item.setDisplayOrder(other.getDisplayOrder());
        other.setDisplayOrder(order);
        itemDao.save(item);
        itemDao.save(other);
    }

    public void reorder(EsInitiative initiative, Long itemId, Long beforeItemId) {
        reorder(initiative, itemId, beforeItemId, null);
    }

    /** When {@code phase} is set, the item lands in that phase. Otherwise it takes the phase of the item it precedes. */
    public void reorder(EsInitiative initiative, Long itemId, Long beforeItemId, String phase) {
        EsChecklist checklist = requireChecklist(initiative);
        List<EsChecklistItem> items = itemDao.findByChecklistId(checklist.getEsChecklistId());
        EsChecklistItem moving = findItem(items, itemId);
        String phaseLabel = phase == null || phase.isBlank() ? null : requireText(phase, "Phase", 200);
        List<EsChecklistItem> visible = new ArrayList<>();
        for (EsChecklistItem candidate : items) {
            if (!Boolean.TRUE.equals(candidate.getRemoved()) && !candidate.getEsChecklistItemId().equals(itemId)) {
                visible.add(candidate);
            }
        }
        visible.sort(Comparator.comparing(EsChecklistItem::getDisplayOrder));
        int index = visible.size();
        if (beforeItemId != null) {
            for (int i = 0; i < visible.size(); i++) {
                if (beforeItemId.equals(visible.get(i).getEsChecklistItemId())) {
                    index = i;
                    if (phaseLabel == null) {
                        moving.setPhaseLabel(visible.get(i).getPhaseLabel());
                    }
                    break;
                }
            }
        }
        if (phaseLabel != null) {
            moving.setPhaseLabel(phaseLabel);
        }
        visible.add(index, moving);
        int order = 10;
        for (EsChecklistItem candidate : visible) {
            candidate.setDisplayOrder(order);
            itemDao.save(candidate);
            order += 10;
        }
    }

    public void renamePhase(EsInitiative initiative, String fromPhase, String toPhase) {
        EsChecklist checklist = requireChecklist(initiative);
        String from = requireText(fromPhase, "Phase", 200);
        String to = requireText(toPhase, "Phase", 200);
        if (from.equals(to)) {
            return;
        }
        boolean found = false;
        for (EsChecklistItem item : itemDao.findByChecklistId(checklist.getEsChecklistId())) {
            if (from.equals(item.getPhaseLabel())) {
                item.setPhaseLabel(to);
                itemDao.save(item);
                found = true;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("That phase was not found.");
        }
    }

    public void assign(User actor, EsInitiative initiative, Long itemId, Long userId) {
        EsChecklistItem item = requireItem(initiative, itemId);
        if (userId != null) {
            boolean manager = false;
            for (EsInitiativeManager candidate : managerDao.findByInitiativeId(initiative.getEsInitiativeId())) {
                if (userId.equals(candidate.getUserId())) {
                    manager = true;
                }
            }
            if (!manager) {
                throw new IllegalArgumentException("Choose one of this initiative's managers.");
            }
        }
        String from = userLabel(item.getAssignedUserId());
        item.setAssignedUserId(userId);
        itemDao.save(item);
        recordField(initiative, item, actor, "Assignee", from, userLabel(userId));
    }

    public void setDueOverride(User actor, EsInitiative initiative, Long itemId, LocalDate due) {
        EsChecklistItem item = requireItem(initiative, itemId);
        String from = item.getDueDateOverride() == null ? null : item.getDueDateOverride().toString();
        item.setDueDateOverride(due);
        itemDao.save(item);
        recordField(initiative, item, actor, "Due date", from, due == null ? null : due.toString());
    }

    private void copyTemplate(EsInitiative initiative, EsChecklistTemplate template) {
        EsChecklist checklist = new EsChecklist();
        checklist.setEsInitiativeId(initiative.getEsInitiativeId());
        checklist.setTemplateKind(template.getTemplateKind());
        checklist.setAppliedEsChecklistTemplateId(template.getEsChecklistTemplateId());
        checklist = checklistDao.save(checklist);
        for (EsChecklistTemplateItem source : templateItemDao.findByTemplateId(template.getEsChecklistTemplateId())) {
            EsChecklistItem item = new EsChecklistItem();
            item.setEsChecklistId(checklist.getEsChecklistId());
            item.setItemKey(source.getItemKey());
            item.setItemOrigin(EsChecklistItem.Origin.TEMPLATE);
            item.setInCurrentTemplate(true);
            item.setRemoved(false);
            item.setLocallyEdited(false);
            item.setPhaseLabel(source.getPhaseLabel());
            item.setTitle(source.getTitle());
            item.setGuidance(source.getGuidance());
            item.setDueAnchor(source.getDueAnchor());
            item.setDueOffsetDays(source.getDueOffsetDays());
            item.setCheckCode(source.getCheckCode());
            item.setDisplayOrder(source.getDisplayOrder());
            item.setItemStatus(EsChecklistItem.Status.OPEN);
            itemDao.save(item);
        }
    }

    private Optional<EsChecklistTemplate> newerTemplate(EsChecklist checklist) {
        return templateDao.findCurrent(checklist.getTemplateKind()).filter(current -> {
            EsChecklistTemplate applied = templateDao.findById(checklist.getAppliedEsChecklistTemplateId()).orElse(null);
            return applied == null || current.getVersionNo() > applied.getVersionNo();
        });
    }

    private static boolean acceptsUpgrade(EsInitiative initiative) {
        return initiative.getStatus() != EsInitiative.Status.CLOSED
                && initiative.getStatus() != EsInitiative.Status.ARCHIVED;
    }

    private EsChecklist requireChecklist(EsInitiative initiative) {
        return checklistDao.findByInitiativeId(initiative.getEsInitiativeId())
                .orElseThrow(() -> new IllegalArgumentException("This initiative does not have a checklist yet."));
    }

    private EsChecklistItem requireItem(EsInitiative initiative, Long itemId) {
        EsChecklist checklist = requireChecklist(initiative);
        return itemDao.findById(itemId)
                .filter(item -> checklist.getEsChecklistId().equals(item.getEsChecklistId()))
                .orElseThrow(() -> new IllegalArgumentException("That checklist item was not found."));
    }

    private static EsChecklistItem findItem(List<EsChecklistItem> items, Long itemId) {
        for (EsChecklistItem item : items) {
            if (item.getEsChecklistItemId().equals(itemId)) {
                return item;
            }
        }
        throw new IllegalArgumentException("That checklist item was not found.");
    }

    private String templateTitle(EsInitiative initiative, EsChecklistItem item) {
        EsChecklistTemplateItem templateItem = templateItem(initiative, item.getItemKey());
        return templateItem == null ? null : templateItem.getTitle();
    }

    private String templateGuidance(EsInitiative initiative, EsChecklistItem item) {
        EsChecklistTemplateItem templateItem = templateItem(initiative, item.getItemKey());
        return templateItem == null ? null : templateItem.getGuidance();
    }

    private EsChecklistTemplateItem templateItem(EsInitiative initiative, String key) {
        EsChecklist checklist = checklistDao.findByInitiativeId(initiative.getEsInitiativeId()).orElse(null);
        if (checklist == null) {
            return null;
        }
        for (EsChecklistTemplateItem candidate : templateItemDao.findByTemplateId(
                checklist.getAppliedEsChecklistTemplateId())) {
            if (key.equals(candidate.getItemKey())) {
                return candidate;
            }
        }
        return null;
    }

    private static void applyState(EsChecklistItem item, ChecklistMerge.State state) {
        item.setItemOrigin(EsChecklistItem.Origin.valueOf(state.origin()));
        item.setRemoved(state.removed());
        item.setLocallyEdited(state.locallyEdited());
        item.setInCurrentTemplate(state.inCurrentTemplate());
        item.setPhaseLabel(state.phase());
        item.setTitle(state.title());
        item.setGuidance(state.guidance());
        item.setDueAnchor(state.dueAnchor());
        item.setDueOffsetDays(state.dueOffsetDays());
        item.setCheckCode(state.checkCode());
        item.setDisplayOrder(state.displayOrder());
        item.setItemStatus(EsChecklistItem.Status.valueOf(state.status()));
        item.setAssignedUserId(state.assignedUserId());
        item.setDueDateOverride(state.dueDateOverride());
    }

    private static List<ChecklistMerge.State> states(List<EsChecklistItem> items) {
        List<ChecklistMerge.State> states = new ArrayList<>();
        for (EsChecklistItem item : items) {
            states.add(new ChecklistMerge.State(item.getItemKey(), item.getItemOrigin().name(),
                    Boolean.TRUE.equals(item.getRemoved()), Boolean.TRUE.equals(item.getLocallyEdited()),
                    Boolean.TRUE.equals(item.getInCurrentTemplate()), item.getPhaseLabel(), item.getTitle(),
                    item.getGuidance(), item.getDueAnchor(), item.getDueOffsetDays(), item.getCheckCode(),
                    item.getDisplayOrder() == null ? 0 : item.getDisplayOrder(), item.getItemStatus().name(),
                    item.getAssignedUserId(), item.getDueDateOverride()));
        }
        return states;
    }

    private static List<ChecklistTemplateParser.Item> parserItems(List<EsChecklistTemplateItem> items) {
        List<ChecklistTemplateParser.Item> parsed = new ArrayList<>();
        for (EsChecklistTemplateItem item : items) {
            parsed.add(new ChecklistTemplateParser.Item(item.getItemKey(), item.getPhaseLabel(), item.getTitle(),
                    item.getGuidance(), item.getDueAnchor(), item.getDueOffsetDays(), item.getCheckCode(),
                    item.getDisplayOrder() == null ? parsed.size() + 1 : item.getDisplayOrder(), 0));
        }
        return parsed;
    }

    private static EsChecklistTemplateItem toTemplateItem(Long templateId, ChecklistTemplateParser.Item item) {
        EsChecklistTemplateItem stored = new EsChecklistTemplateItem();
        stored.setEsChecklistTemplateId(templateId);
        stored.setItemKey(item.key());
        stored.setPhaseLabel(limit(item.phase(), 200));
        stored.setTitle(limit(item.title(), 500));
        stored.setGuidance(item.guidance());
        stored.setDueAnchor(item.dueAnchor());
        stored.setDueOffsetDays(item.dueOffsetDays());
        stored.setCheckCode(item.checkCode());
        stored.setDisplayOrder(item.displayOrder());
        return stored;
    }

    private static String uniqueKey(String title, List<EsChecklistItem> items) {
        String base = ChecklistTemplateParser.slug(title);
        if (base.isEmpty()) {
            throw new IllegalArgumentException("A key could not be made from the title.");
        }
        String key = base;
        int suffix = 2;
        while (containsKey(items, key)) {
            String extra = "-" + suffix;
            key = (base.length() + extra.length() > 80 ? base.substring(0, 80 - extra.length()) : base) + extra;
            suffix++;
        }
        return key;
    }

    private static boolean containsKey(List<EsChecklistItem> items, String key) {
        for (EsChecklistItem item : items) {
            if (key.equals(item.getItemKey())) {
                return true;
            }
        }
        return false;
    }

    private void placeNewItem(List<EsChecklistItem> existing, EsChecklistItem created, Long afterItemId, String phase) {
        List<EsChecklistItem> visible = new ArrayList<>();
        for (EsChecklistItem item : existing) {
            if (!Boolean.TRUE.equals(item.getRemoved())) {
                visible.add(item);
            }
        }
        visible.sort(Comparator.comparing(item -> item.getDisplayOrder() == null ? 0 : item.getDisplayOrder()));
        int index = visible.size();
        if (afterItemId != null) {
            for (int i = 0; i < visible.size(); i++) {
                if (afterItemId.equals(visible.get(i).getEsChecklistItemId())) {
                    index = i + 1;
                    break;
                }
            }
        } else {
            for (int i = 0; i < visible.size(); i++) {
                if (phase.equals(visible.get(i).getPhaseLabel())) {
                    index = i + 1;
                }
            }
        }
        visible.add(index, created);
        int order = 10;
        for (EsChecklistItem item : visible) {
            item.setDisplayOrder(order);
            itemDao.save(item);
            order += 10;
        }
    }

    private static int versionLine(String markdown) {
        if (markdown == null) {
            return 1;
        }
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        boolean inComment = false;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (inComment) {
                if (line.contains("-->")) {
                    inComment = false;
                }
                continue;
            }
            if (line.contains("<!--")) {
                inComment = !line.contains("-->");
                continue;
            }
            if (line.trim().startsWith("version:")) {
                return index + 1;
            }
        }
        return 1;
    }

    private void recordPlanned(EsInitiative initiative, EsChecklistItem item, User actor,
            InitiativeRosterRules.PlannedEntry planned) {
        if (planned.kind() == InitiativeRosterRules.PlannedKind.STATUS_CHANGE) {
            record(initiative.getEsInitiativeId(), EsInitiativeTimelineEntry.SubjectType.CHECKLIST_ITEM,
                    item.getEsChecklistItemId(), actor, EsInitiativeTimelineEntry.EntryType.STATUS_CHANGE,
                    EsChecklistItem.Status.labelOf(planned.fromValue()),
                    EsChecklistItem.Status.labelOf(planned.toValue()), planned.note());
        } else {
            record(initiative.getEsInitiativeId(), EsInitiativeTimelineEntry.SubjectType.CHECKLIST_ITEM,
                    item.getEsChecklistItemId(), actor, EsInitiativeTimelineEntry.EntryType.COMMENT, null, null,
                    planned.note());
        }
    }

    private void recordField(EsInitiative initiative, EsChecklistItem item, User actor, String field, String from,
            String to) {
        if (sameText(from, to)) {
            return;
        }
        record(initiative.getEsInitiativeId(), EsInitiativeTimelineEntry.SubjectType.CHECKLIST_ITEM,
                item.getEsChecklistItemId(), actor, EsInitiativeTimelineEntry.EntryType.FIELD_CHANGE, from, to, field);
    }

    private void record(Long initiativeId, EsInitiativeTimelineEntry.SubjectType type, Long subjectId, User actor,
            EsInitiativeTimelineEntry.EntryType entryType, String from, String to, String note) {
        EsInitiativeTimelineEntry entry = new EsInitiativeTimelineEntry();
        entry.setEsInitiativeId(initiativeId);
        entry.setSubjectType(type);
        entry.setSubjectId(subjectId);
        entry.setEntryType(entryType);
        entry.setFromValue(from);
        entry.setToValue(to);
        entry.setNoteText(note);
        entry.setCreatedByUserId(actor == null ? null : actor.getUserId());
        timelineDao.save(entry);
    }

    private String userLabel(Long userId) {
        if (userId == null) {
            return null;
        }
        return userDao.findById(userId).map(user -> {
            String name = user.getFullName();
            return name == null || name.isBlank() ? user.getEmail() : name;
        }).orElse("a manager");
    }

    private static String cleanGuidance(String guidance) {
        if (guidance == null) {
            return null;
        }
        String trimmed = guidance.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 8000) {
            throw new IllegalArgumentException("Guidance must be 8000 characters or less.");
        }
        return trimmed;
    }

    private static String requireText(String value, String label, int max) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(label + " must be " + max + " characters or less.");
        }
        return trimmed;
    }

    private static String limit(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private static boolean sameText(String left, String right) {
        String a = left == null || left.isBlank() ? null : left.trim();
        String b = right == null || right.isBlank() ? null : right.trim();
        return java.util.Objects.equals(a, b);
    }
}
