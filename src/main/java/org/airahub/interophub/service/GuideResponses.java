package org.airahub.interophub.service;

import java.util.List;
import org.airahub.interophub.service.GuideRows.Disposition;
import org.airahub.interophub.service.GuideRows.DocumentRow;
import org.airahub.interophub.service.GuideRows.Person;
import org.airahub.interophub.service.GuideRows.TopicRow;
import org.airahub.interophub.service.GuideRows.UrlBinding;
import org.json.JSONArray;
import org.json.JSONObject;

/** Contract 5.1 document JSON, plus the registration extras from Section 7. */
final class GuideResponses {
    private GuideResponses() {
    }

    static JSONObject document(DocumentRow document, TopicRow topic, boolean canView, boolean canEdit,
            Person user, List<Disposition> dispositions, String serverTime, String topicUrl,
            List<String> added, List<String> removed, Boolean dryRun) {
        JSONObject json = new JSONObject();
        json.put("documentKey", document.documentKey);
        json.put("title", document.title);
        json.put("isActive", document.active);
        if (canView && topic != null) {
            JSONObject topicJson = new JSONObject();
            topicJson.put("topicId", topic.id);
            topicJson.put("topicCode", topic.code);
            topicJson.put("topicName", topic.name);
            topicJson.put("url", topicUrl);
            json.put("topic", topicJson);
        } else {
            json.put("topic", JSONObject.NULL);
        }
        json.put("repositoryUrl", document.repositoryUrl == null ? JSONObject.NULL : document.repositoryUrl);
        JSONArray urls = new JSONArray();
        for (UrlBinding binding : document.urls) {
            JSONObject url = new JSONObject();
            url.put("url", binding.url);
            url.put("label", binding.label);
            urls.put(url);
        }
        json.put("urls", urls);
        JSONObject permissions = new JSONObject();
        permissions.put("canView", canView);
        permissions.put("canComment", canView && document.active);
        permissions.put("isAuthor", canEdit);
        json.put("permissions", permissions);
        json.put("user", person(user));
        JSONArray dispositionJson = new JSONArray();
        for (Disposition disposition : dispositions) {
            JSONObject row = new JSONObject();
            row.put("code", disposition.code);
            row.put("label", disposition.label);
            dispositionJson.put(row);
        }
        json.put("dispositions", dispositionJson);
        json.put("editWindowDays", GuideCommentService.EDIT_WINDOW_DAYS);
        json.put("serverTime", serverTime);
        if (added != null) {
            JSONObject changes = new JSONObject();
            changes.put("added", new JSONArray(added));
            changes.put("removed", new JSONArray(removed));
            json.put("allowlistChanges", changes);
            json.put("dryRun", dryRun != null && dryRun);
        }
        return json;
    }

    static JSONObject person(Person person) {
        JSONObject json = new JSONObject();
        if (person == null) {
            json.put("userId", JSONObject.NULL);
            json.put("displayName", "");
            json.put("organization", JSONObject.NULL);
            return json;
        }
        json.put("userId", person.userId);
        json.put("displayName", person.displayName == null ? "" : person.displayName);
        json.put("organization", person.organization == null || person.organization.isBlank()
                ? JSONObject.NULL : person.organization);
        return json;
    }
}
