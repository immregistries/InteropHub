package org.airahub.interophub.servlet;

import java.io.PrintWriter;

/**
 * Shared renderer for the ES topic detail panel HTML and interactive
 * JavaScript.
 * Used by both EsTopicsServlet (popup mode) and EsTopicDetailServlet (full-page
 * mode).
 */
public class EsTopicDetailRenderer {

        /**
         * Renders the detail sheet HTML structure.
         *
         * @param renderOverlay when true, also renders the backdrop overlay div (popup
         *                      mode);
         *                      when false, the overlay is omitted (full-page mode).
         */
        public static void renderDetailSheetHtml(PrintWriter out, boolean canInteract, boolean canReview,
                        boolean renderOverlay) {
                if (renderOverlay) {
                        out.println("    <div id=\"es-detail-overlay\" class=\"es-detail-overlay\" hidden></div>");
                }
                out.println("    <aside id=\"es-detail-sheet\" class=\"es-detail-sheet\" hidden>");
                out.println("      <h2 id=\"es-detail-title\"></h2>");
                out.println("      <p id=\"es-detail-stage\" class=\"es-detail-stage\"></p>");
                out.println("      <p id=\"es-detail-neighborhood\" class=\"es-detail-stage\" hidden></p>");
                out.println("      <p id=\"es-detail-topic-type\" class=\"es-detail-stage\" hidden></p>");
                out.println("      <p id=\"es-detail-policy-status\" class=\"es-detail-stage\" hidden></p>");
                out.println("      <p id=\"es-detail-description\" class=\"es-detail-description\"></p>");
                out.println(
                                "      <p><a id=\"es-detail-confluence-url\" href=\"#\" target=\"_blank\" rel=\"noopener\" hidden>View in Confluence</a></p>");
                out.println(
                                "      <p><a id=\"es-detail-meetings-link\" href=\"#\" hidden>View all scheduled meetings \u2192</a></p>");
                out.println(
                                "      <p><a id=\"es-detail-permalink\" href=\"#\" hidden>View full details page \u2197</a></p>");
                out.println("      <div id=\"es-detail-appearances\" hidden style=\"margin-bottom:0.75rem;\">");
                out.println("        <p style=\"font-size:0.85rem; font-weight:600; color:#0f1720; margin:0 0 0.35rem;\">Upcoming Meetings</p>");
                out.println("        <ul id=\"es-detail-appearances-list\" style=\"margin:0; padding-left:1.1rem; display:grid; gap:0.25rem; font-size:0.87rem;\"></ul>");
                out.println("      </div>");
                if (canInteract) {
                        out.println("      <div id=\"es-detail-follow-wrap\" class=\"es-detail-comments-wrap\" hidden>");
                        out.println("        <div class=\"es-detail-actions\">");
                        out.println("          <button type=\"button\" id=\"es-detail-follow-toggle\"></button>");
                        out.println("        </div>");
                        out.println("      </div>");

                        out.println("      <div id=\"es-detail-meeting-wrap\" class=\"es-detail-comments-wrap\" hidden>");
                        out.println("        <p id=\"es-detail-meeting-name\" class=\"es-topic-meta\" style=\"margin-top:0\"></p>");
                        out.println("        <p id=\"es-detail-meeting-description\" class=\"es-topic-meta\"></p>");
                        out.println("        <div class=\"es-detail-actions\">");
                        out.println("          <button type=\"button\" id=\"es-detail-meeting-toggle\"></button>");
                        out.println("        </div>");
                        out.println("      </div>");
                }
                out.println("      <div id=\"es-detail-comments-wrap\" class=\"es-detail-comments-wrap\" hidden>");
                out.println("        <ul id=\"es-detail-comments-list\" class=\"es-detail-comments-list\" hidden></ul>");
                out.println("      </div>");
                if (canReview) {
                        out.println(
                                        "      <label for=\"es-detail-comment\" class=\"es-review-comment-label\">Optional comment or question</label>");
                        out.println("      <textarea id=\"es-detail-comment\" class=\"es-review-comment-input\" maxlength=\"2000\""
                                        + " placeholder=\"Add a comment/question for this topic\"></textarea>");
                        out.println("      <p id=\"es-detail-comment-status\" class=\"es-review-comment-status\" hidden></p>");
                        out.println("      <div class=\"es-detail-actions\">");
                        out.println("        <button type=\"button\" id=\"es-detail-save-comment\">Save Comment</button>");
                        out.println(
                                        "        <button type=\"button\" id=\"es-detail-close\" class=\"es-secondary-button\">Close</button>");
                        out.println("      </div>");
                } else {
                        out.println("      <div class=\"es-detail-actions\">");
                        out.println(
                                        "        <button type=\"button\" id=\"es-detail-close\" class=\"es-secondary-button\">Close</button>");
                        out.println("      </div>");
                }
                out.println("    </aside>");
        }

        /**
         * Renders the shared interactive JavaScript for the detail panel.
         * Exposes {@code window.openDetail(row)} for callers to invoke.
         *
         * @param closeBackUrl when null, closing hides the popup (list page behaviour);
         *                     when non-null, closing navigates to that URL (full-page
         *                     behaviour).
         */
        public static void renderDetailInteractScript(PrintWriter out, String contextPath, String campaignCode,
                        boolean canInteract, boolean canReview, String closeBackUrl) {
                out.println("  <script>");
                out.println("    (function() {");
                out.println("      var canInteract = " + canInteract + ";");
                out.println("      var canReview = " + canReview + ";");
                out.println("      var campaignCode = " + quoteJs(campaignCode) + ";");
                out.println("      var closeBackUrl = " + quoteJs(closeBackUrl) + ";");
                out.println("      var detailOverlay = document.getElementById('es-detail-overlay');");
                out.println("      var detailSheet = document.getElementById('es-detail-sheet');");
                out.println("      var detailTitle = document.getElementById('es-detail-title');");
                out.println("      var detailStage = document.getElementById('es-detail-stage');");
                out.println("      var detailNeighborhood = document.getElementById('es-detail-neighborhood');");
                out.println("      var detailTopicType = document.getElementById('es-detail-topic-type');");
                out.println("      var detailPolicyStatus = document.getElementById('es-detail-policy-status');");
                out.println("      var detailDescription = document.getElementById('es-detail-description');");
                out.println("      var detailConfluence = document.getElementById('es-detail-confluence-url');");
                out.println("      var detailMeetingsLink = document.getElementById('es-detail-meetings-link');");
                out.println("      var detailPermalink = document.getElementById('es-detail-permalink');");
                out.println("      var detailAppearances = document.getElementById('es-detail-appearances');");
                out.println("      var detailCommentsWrap = document.getElementById('es-detail-comments-wrap');");
                out.println("      var detailCommentsList = document.getElementById('es-detail-comments-list');");
                out.println("      var detailFollowWrap = document.getElementById('es-detail-follow-wrap');");
                out.println("      var detailFollowToggle = document.getElementById('es-detail-follow-toggle');");
                out.println("      var detailMeetingWrap = document.getElementById('es-detail-meeting-wrap');");
                out.println("      var detailMeetingName = document.getElementById('es-detail-meeting-name');");
                out.println("      var detailMeetingDescription = document.getElementById('es-detail-meeting-description');");
                out.println("      var detailMeetingToggle = document.getElementById('es-detail-meeting-toggle');");
                out.println("      var detailComment = document.getElementById('es-detail-comment');");
                out.println("      var detailCommentStatus = document.getElementById('es-detail-comment-status');");
                out.println("      var detailSaveComment = document.getElementById('es-detail-save-comment');");
                out.println("      var detailClose = document.getElementById('es-detail-close');");
                out.println("      var currentDetailTopicId = null;");
                out.println("      var currentMeetingId = null;");
                out.println("      var currentFollowed = false;");
                out.println("      var currentMeetingStatus = '';");
                out.println("      var sessionComments = {};");

                // Update close button label for page mode
                out.println("      if (detailClose && closeBackUrl) { detailClose.textContent = '\\u2190 All Topics'; }");

                out.println("      function renderComments(existing, topicId) {");
                out.println("        var merged = existing.slice();");
                out.println("        if (sessionComments[topicId]) { merged = merged.concat(sessionComments[topicId]); }");
                out.println("        detailCommentsList.innerHTML = ''; ");
                out.println("        if (merged.length === 0) {");
                out.println("          if (detailCommentsWrap) { detailCommentsWrap.hidden = true; }");
                out.println("          detailCommentsList.hidden = true;");
                out.println("          return;");
                out.println("        }");
                out.println("        if (detailCommentsWrap) { detailCommentsWrap.hidden = false; }");
                out.println("        detailCommentsList.hidden = false;");
                out.println("        merged.forEach(function(text) {");
                out.println("          var li = document.createElement('li');");
                out.println("          li.textContent = text;");
                out.println("          detailCommentsList.appendChild(li);");
                out.println("        });");
                out.println("      }");

                out.println("      window.openDetail = function(row) {");
                out.println("        currentDetailTopicId = row.getAttribute('data-topic-id');");
                out.println("        currentMeetingId = row.getAttribute('data-meeting-id') || null;");
                out.println("        currentFollowed = row.getAttribute('data-is-followed') === '1';");
                out.println("        currentMeetingStatus = (row.getAttribute('data-meeting-status') || '').toUpperCase();");
                out.println("        detailTitle.textContent = row.getAttribute('data-topic-name') || ''; ");
                out.println(
                                "        detailStage.textContent = 'Stage: ' + ((row.getAttribute('data-topic-stage') || '').trim() || 'Other');");

                out.println("        var neighborhood = (row.getAttribute('data-topic-neighborhood') || '').trim();");
                out.println(
                                "        if (neighborhood) { detailNeighborhood.hidden = false; detailNeighborhood.textContent = 'Neighborhood: ' + neighborhood; } else { detailNeighborhood.hidden = true; detailNeighborhood.textContent = ''; }");

                out.println("        var topicType = (row.getAttribute('data-topic-type') || '').trim();");
                out.println(
                                "        if (topicType) { detailTopicType.hidden = false; detailTopicType.textContent = 'Topic type: ' + topicType; } else { detailTopicType.hidden = true; detailTopicType.textContent = ''; }");

                out.println("        var policy = (row.getAttribute('data-policy-status') || '').trim();");
                out.println(
                                "        if (policy) { detailPolicyStatus.hidden = false; detailPolicyStatus.textContent = 'Policy status: ' + policy; } else { detailPolicyStatus.hidden = true; detailPolicyStatus.textContent = ''; }");

                out.println(
                                "        detailDescription.textContent = row.getAttribute('data-topic-description') || 'No description available.';");

                out.println("        var confluenceUrl = (row.getAttribute('data-confluence-url') || '').trim();");
                out.println(
                                "        if (confluenceUrl) { detailConfluence.hidden = false; detailConfluence.href = confluenceUrl; } else { detailConfluence.hidden = true; detailConfluence.removeAttribute('href'); }");

                out.println("        if (detailMeetingsLink) {");
                out.println("          if (currentMeetingId) {");
                out.println("            detailMeetingsLink.hidden = false;");
                out.println("            detailMeetingsLink.href = '" + contextPath
                                + "/es/meetings?seriesId=' + currentMeetingId;");
                out.println("          } else {");
                out.println("            detailMeetingsLink.hidden = true;");
                out.println("          }");
                out.println("        }");

                out.println("        if (detailPermalink) {");
                out.println("          if (!closeBackUrl && currentDetailTopicId) {");
                out.println("            detailPermalink.hidden = false;");
                out.println("            detailPermalink.href = '" + contextPath
                                + "/es/topic/' + currentDetailTopicId;");
                out.println("          } else {");
                out.println("            detailPermalink.hidden = true;");
                out.println("          }");
                out.println("        }");

                out.println("        var agendaMeetings = [];");
                out.println("        try { agendaMeetings = JSON.parse(row.getAttribute('data-agenda-meetings') || '[]'); } catch(e) { agendaMeetings = []; }");
                out.println("        if (Array.isArray(agendaMeetings) && agendaMeetings.length > 0 && detailAppearances) {");
                out.println("          var appearList = document.getElementById('es-detail-appearances-list');");
                out.println("          if (appearList) {");
                out.println("            appearList.innerHTML = '';");
                out.println("            agendaMeetings.forEach(function(m) {");
                out.println("              var li = document.createElement('li');");
                out.println("              var text = m.name || 'Meeting';");
                out.println("              if (m.date) { text = m.date + ' \\u2014 ' + text; }");
                out.println("              li.textContent = text;");
                out.println("              appearList.appendChild(li);");
                out.println("            });");
                out.println("          }");
                out.println("          detailAppearances.hidden = false;");
                out.println("        } else if (detailAppearances) {");
                out.println("          detailAppearances.hidden = true;");
                out.println("        }");

                out.println("        if (canInteract && detailFollowWrap && detailFollowToggle) {");
                out.println("          detailFollowWrap.hidden = false;");
                out.println("          detailFollowToggle.textContent = currentFollowed ? 'Unfollow Topic' : 'Follow Topic';");
                out.println("          detailFollowToggle.setAttribute('data-followed', currentFollowed ? '1' : '0');");
                out.println("          detailFollowToggle.classList.toggle('es-secondary-button', currentFollowed);");
                out.println("        }");

                out.println("        if (canInteract && detailMeetingWrap) {");
                out.println("          var meetingName = (row.getAttribute('data-meeting-name') || '').trim();");
                out.println("          var meetingDescription = (row.getAttribute('data-meeting-description') || '').trim();");
                out.println("          var hasMeeting = !!currentMeetingId;");
                out.println("          detailMeetingWrap.hidden = !hasMeeting;");
                out.println("          if (hasMeeting) {");
                out.println(
                                "            if (detailMeetingName) { detailMeetingName.textContent = meetingName ? ('Meeting: ' + meetingName) : 'Meeting available for this topic.'; }");
                out.println(
                                "            if (detailMeetingDescription) { detailMeetingDescription.textContent = meetingDescription; detailMeetingDescription.hidden = meetingDescription.length === 0; }");
                out.println(
                                "            var requested = currentMeetingStatus === 'REQUESTED' || currentMeetingStatus === 'APPROVED';");
                out.println("            if (detailMeetingToggle) {");
                out.println(
                                "              detailMeetingToggle.textContent = requested ? 'Unrequest Meeting' : 'Request to Join Meeting';");
                out.println("              detailMeetingToggle.classList.toggle('es-secondary-button', requested);");
                out.println("            }");
                out.println("          }");
                out.println("        }");

                out.println("        var parsedComments = []; ");
                out.println(
                                "        try { parsedComments = JSON.parse(row.getAttribute('data-user-comments') || '[]'); } catch (e) { parsedComments = []; }");
                out.println(
                                "        renderComments(Array.isArray(parsedComments) ? parsedComments : [], currentDetailTopicId);");

                out.println(
                                "        if (detailCommentStatus) { detailCommentStatus.hidden = true; detailCommentStatus.textContent = ''; }");
                out.println("        if (detailComment) { detailComment.value = ''; }");
                out.println("        if (detailOverlay) { detailOverlay.hidden = false; }");
                out.println("        detailSheet.hidden = false;");
                out.println("        if (!closeBackUrl) { document.body.classList.add('es-sheet-open'); }");
                out.println("      };");

                out.println("      function closeDetail() {");
                out.println("        if (closeBackUrl) { window.location.href = closeBackUrl; return; }");
                out.println("        currentDetailTopicId = null;");
                out.println("        currentMeetingId = null;");
                out.println("        if (detailOverlay) { detailOverlay.hidden = true; }");
                out.println("        detailSheet.hidden = true;");
                out.println("        document.body.classList.remove('es-sheet-open');");
                out.println("      }");

                if (canInteract) {
                        out.println("      if (canInteract && detailFollowToggle) {");
                        out.println("        detailFollowToggle.addEventListener('click', function() {");
                        out.println("          if (!currentDetailTopicId) { return; }");
                        out.println("          var shouldFollow = !currentFollowed;");
                        out.println("          var params = new URLSearchParams();");
                        out.println("          params.set('topicId', String(currentDetailTopicId));");
                        out.println("          params.set('action', shouldFollow ? 'follow' : 'unfollow');");
                        out.println("          fetch('" + contextPath + "/es/topics/follow-toggle', {");
                        out.println("            method: 'POST',");
                        out.println("            headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },");
                        out.println("            body: params.toString()");
                        out.println("          }).then(function(res) { return res.json(); }).then(function(json) {");
                        out.println("            if (!json || !json.ok) {");
                        out.println(
                                        "              window.alert((json && json.error) ? json.error : 'Unable to update follow status.');");
                        out.println("              return;");
                        out.println("            }");
                        out.println("            window.location.reload();");
                        out.println("          }).catch(function() {");
                        out.println("            window.alert('Unable to update follow status.');");
                        out.println("          });");
                        out.println("        });");
                        out.println("      }");

                        out.println("      if (canInteract && detailMeetingToggle) {");
                        out.println("        detailMeetingToggle.addEventListener('click', function() {");
                        out.println("          if (!currentDetailTopicId || !currentMeetingId) { return; }");
                        out.println(
                                        "          var requested = currentMeetingStatus === 'REQUESTED' || currentMeetingStatus === 'APPROVED';");
                        out.println("          var params = new URLSearchParams();");
                        out.println("          params.set('topicId', String(currentDetailTopicId));");
                        out.println("          params.set('meetingId', String(currentMeetingId));");
                        out.println("          params.set('action', requested ? 'unrequest' : 'request');");
                        out.println("          fetch('" + contextPath + "/es/topics/meeting-toggle', {");
                        out.println("            method: 'POST',");
                        out.println("            headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },");
                        out.println("            body: params.toString()");
                        out.println("          }).then(function(res) { return res.json(); }).then(function(json) {");
                        out.println("            if (!json || !json.ok) {");
                        out.println(
                                        "              window.alert((json && json.error) ? json.error : 'Unable to update meeting request.');");
                        out.println("              return;");
                        out.println("            }");
                        out.println("            if (json.requested) {");
                        out.println("              window.location.reload();");
                        out.println("              return;");
                        out.println("            }");
                        out.println("            currentMeetingStatus = (json.membershipStatus || '').toUpperCase();");
                        out.println(
                                        "            var nowRequested = currentMeetingStatus === 'REQUESTED' || currentMeetingStatus === 'APPROVED';");
                        out.println(
                                        "            detailMeetingToggle.textContent = nowRequested ? 'Unrequest Meeting' : 'Request to Join Meeting';");
                        out.println("            detailMeetingToggle.classList.toggle('es-secondary-button', nowRequested);");
                        out.println(
                                        "            var row = document.querySelector('.es-topic-row[data-topic-id=\"' + currentDetailTopicId + '\"]');");
                        out.println("            if (row) {");
                        out.println("              row.setAttribute('data-meeting-status', currentMeetingStatus);");
                        out.println("              row.classList.remove('is-meeting-registered');");
                        out.println("            }");
                        out.println("          }).catch(function() {");
                        out.println("            window.alert('Unable to update meeting request.');");
                        out.println("          });");
                        out.println("        });");
                        out.println("      }");
                }

                if (canReview) {
                        out.println("      if (canReview && detailSaveComment) {");
                        out.println("        detailSaveComment.addEventListener('click', function() {");
                        out.println("          if (!currentDetailTopicId || !campaignCode) { return; }");
                        out.println("          var text = (detailComment.value || '').trim();");
                        out.println("          if (text.length === 0) {");
                        out.println("            detailCommentStatus.hidden = false;");
                        out.println("            detailCommentStatus.textContent = 'Enter a comment before saving.';");
                        out.println("            return;");
                        out.println("          }");
                        out.println("          var params = new URLSearchParams();");
                        out.println("          params.set('campaignCode', campaignCode);");
                        out.println("          params.set('topicId', currentDetailTopicId);");
                        out.println("          params.set('commentText', text);");
                        out.println("          fetch('" + contextPath + "/es/review/comment', {");
                        out.println("            method: 'POST',");
                        out.println("            headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },");
                        out.println("            body: params.toString()");
                        out.println("          }).then(function(res) { return res.json(); }).then(function(json) {");
                        out.println("            if (!json || !json.ok) {");
                        out.println("              detailCommentStatus.hidden = false;");
                        out.println(
                                        "              detailCommentStatus.textContent = (json && json.error) ? json.error : 'Unable to save comment.';");
                        out.println("              return;");
                        out.println("            }");
                        out.println(
                                        "            if (!sessionComments[currentDetailTopicId]) { sessionComments[currentDetailTopicId] = []; }");
                        out.println("            sessionComments[currentDetailTopicId].push(text);");
                        out.println("            var existing = []; ");
                        out.println("            try {");
                        out.println(
                                        "              var row = document.querySelector('.es-topic-row[data-topic-id=\"' + currentDetailTopicId + '\"]');");
                        out.println(
                                        "              existing = JSON.parse((row && row.getAttribute('data-user-comments')) || '[]');");
                        out.println("            } catch (e) { existing = []; }");
                        out.println("            renderComments(Array.isArray(existing) ? existing : [], currentDetailTopicId);");
                        out.println("            detailComment.value = ''; ");
                        out.println("            detailCommentStatus.hidden = false;");
                        out.println("            detailCommentStatus.textContent = 'Comment saved.';");
                        out.println("          }).catch(function() {");
                        out.println("            detailCommentStatus.hidden = false;");
                        out.println("            detailCommentStatus.textContent = 'Unable to save comment.';");
                        out.println("          });");
                        out.println("        });");
                        out.println("      }");
                }

                out.println("      if (detailOverlay) { detailOverlay.addEventListener('click', closeDetail); }");
                out.println("      if (detailClose) { detailClose.addEventListener('click', closeDetail); }");
                out.println("    })();");
                out.println("  </script>");
        }

        private static String quoteJs(String value) {
                if (value == null) {
                        return "null";
                }
                return "\"" + value
                                .replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                                .replace("\r", "")
                                .replace("\n", "\\n")
                                + "\"";
        }
}
