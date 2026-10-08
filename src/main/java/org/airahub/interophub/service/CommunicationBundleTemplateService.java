package org.airahub.interophub.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.airahub.interophub.dao.EsCommunicationBundlePurposeDao;
import org.airahub.interophub.dao.EsCommunicationBundleTemplateDao;
import org.airahub.interophub.dao.EsCommunicationBundleTemplateComponentDao;
import org.airahub.interophub.model.EsCommunicationBundlePurpose;
import org.airahub.interophub.model.EsCommunicationBundleTemplate;
import org.airahub.interophub.model.EsCommunicationBundleTemplateComponent;
import org.airahub.interophub.model.EsCommunicationBundleTemplateComponent.Cardinality;
import org.airahub.interophub.model.EsCommunicationBundleTemplateComponent.Kind;
import org.airahub.interophub.model.User;

public class CommunicationBundleTemplateService {
    private final AuthFlowService auth = new AuthFlowService();
    private final EsCommunicationBundlePurposeDao purposes = new EsCommunicationBundlePurposeDao();
    private final EsCommunicationBundleTemplateDao templates = new EsCommunicationBundleTemplateDao();
    private final EsCommunicationBundleTemplateComponentDao components = new EsCommunicationBundleTemplateComponentDao();

    public record ComponentInput(String key, String label, String prompt, Kind kind,
            boolean required, Cardinality cardinality, int order) {
        public void validate() {
            if (key == null || !key.matches("[a-z][a-z0-9_]{0,79}")) {
                throw new IllegalArgumentException("Use a semantic key of 1-80 lowercase letters, digits or underscores, starting with a letter.");
            }
            if (label == null || label.isBlank() || label.length() > 140
                    || (prompt != null && prompt.length() > 20000) || order < 0) {
                throw new IllegalArgumentException("Enter a display name (up to 140 characters), a prompt (up to 20000), and a non-negative display order.");
            }
            Cardinality expected = kind == Kind.STRUCTURED_LIST || kind == Kind.RESOURCE_COLLECTION
                    ? Cardinality.REPEATING : Cardinality.SINGLE;
            if (kind == null || cardinality != expected) {
                throw new IllegalArgumentException("Text and resource components are single; lists and resource collections are repeating.");
            }
        }

        public void applyTo(EsCommunicationBundleTemplateComponent component) {
            validate();
            component.setSemanticKey(key);
            component.setDisplayName(label.trim());
            component.setAuthoringPrompt(prompt == null || prompt.isBlank() ? null : prompt.trim());
            component.setKind(kind);
            component.setRequired(required);
            component.setCardinality(cardinality);
            component.setDisplayOrder(order);
        }
    }

    public List<EsCommunicationBundlePurpose> listPurposes(User user) {
        requireAdmin(user);
        return purposes.findAll().stream().sorted(java.util.Comparator.comparing(
                EsCommunicationBundlePurpose::getDisplayName)).toList();
    }

    public List<EsCommunicationBundleTemplate> listVersions(User user, Long purposeId) {
        requireAdmin(user);
        return templates.findByPurpose(purposeId);
    }

    public EsCommunicationBundleTemplate getTemplate(User user, Long templateId) {
        requireAdmin(user);
        return templates.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template was not found."));
    }

    public List<EsCommunicationBundleTemplateComponent> listComponents(User user, Long templateId) {
        getTemplate(user, templateId);
        return components.findByTemplateId(templateId);
    }

    public EsCommunicationBundleTemplate createDraft(User user, Long purposeId, Long sourceId) {
        requireAdmin(user);
        return templates.createDraftVersion(purposeId, sourceId, user.getUserId());
    }

    public void saveComponent(User user, Long templateId, Long componentId, ComponentInput input) {
        requireAdmin(user);
        input.validate();
        templates.saveDraftComponent(templateId, componentId, input);
    }

    public void removeComponent(User user, Long templateId, Long componentId) {
        requireAdmin(user);
        templates.removeDraftComponent(templateId, componentId);
    }

    public void activate(User user, Long templateId) {
        requireAdmin(user);
        templates.activateDraft(templateId);
    }

    private void requireAdmin(User user) {
        if (user == null || !auth.isAdminUser(user)) {
            throw new SecurityException("Only InteropHub administrators can manage bundle templates.");
        }
    }

    public static void validateComponents(String purposeKey, List<EsCommunicationBundleTemplateComponent> components) {
        if (components.isEmpty()) {
            throw new IllegalArgumentException("Add at least one component before activating a template.");
        }
        var keys = new HashSet<String>();
        var orders = new HashSet<Integer>();
        for (var component : components) {
            new ComponentInput(component.getSemanticKey(), component.getDisplayName(), component.getAuthoringPrompt(),
                    component.getKind(), component.isRequired(), component.getCardinality(), component.getDisplayOrder()).validate();
            if (!keys.add(component.getSemanticKey()) || !orders.add(component.getDisplayOrder())) {
                throw new IllegalArgumentException("Component semantic keys and display orders must be unique.");
            }
        }
        if (CommunicationBundleService.TOPIC_ORIENTATION_KEY.equals(purposeKey)) {
            Map<String, Kind> fixed = Map.of("primary_infographic", Kind.RESOURCE, "one_pager", Kind.RESOURCE,
                    "primary_presentation", Kind.RESOURCE, "additional_resources", Kind.RESOURCE_COLLECTION);
            for (var entry : fixed.entrySet()) {
                if (components.stream().noneMatch(c -> entry.getKey().equals(c.getSemanticKey()) && entry.getValue() == c.getKind())) {
                    throw new IllegalArgumentException("Orientation must retain the " + entry.getKey() + " resource role and its kind.");
                }
            }
        }
        if (StarterPacketService.PURPOSE_KEY.equals(purposeKey)) {
            Map<String, Kind> fixed = Map.of("title", Kind.TEXT, "teaser_summary", Kind.TEXT,
                    "teaser_image", Kind.RESOURCE, "explanation", Kind.TEXT, "starting_points", Kind.STRUCTURED_LIST,
                    "next_actions", Kind.STRUCTURED_LIST, "supporting_resources", Kind.RESOURCE_COLLECTION);
            for (var entry : fixed.entrySet()) {
                if (components.stream().noneMatch(c -> entry.getKey().equals(c.getSemanticKey()) && entry.getValue() == c.getKind())) {
                    throw new IllegalArgumentException("Starter Packet must retain the " + entry.getKey() + " component and its kind.");
                }
            }
            if (components.stream().noneMatch(c -> "title".equals(c.getSemanticKey()) && c.isRequired())) {
                throw new IllegalArgumentException("Starter Packet title must remain required.");
            }
        }
    }
}
