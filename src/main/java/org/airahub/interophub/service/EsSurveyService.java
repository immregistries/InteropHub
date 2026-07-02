package org.airahub.interophub.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.airahub.interophub.dao.EsSurveyAnswerDao;
import org.airahub.interophub.dao.EsSurveyDao;
import org.airahub.interophub.dao.EsSurveyQuestionDao;
import org.airahub.interophub.dao.EsSurveyResponseDao;
import org.airahub.interophub.dao.EsTopicMeetingSurveyDao;
import org.airahub.interophub.model.EsMeetingAttendance;
import org.airahub.interophub.model.EsSurvey;
import org.airahub.interophub.model.EsSurvey.SurveyStatus;
import org.airahub.interophub.model.EsSurveyAnswer;
import org.airahub.interophub.model.EsSurveyQuestion;
import org.airahub.interophub.model.EsSurveyQuestion.QuestionType;
import org.airahub.interophub.model.EsSurveyResponse;
import org.airahub.interophub.model.EsTopicMeetingSurvey;
import org.airahub.interophub.model.EsTopicMeetingSurvey.AssignmentStatus;
import org.airahub.interophub.model.User;

public class EsSurveyService {

    private static final Logger LOGGER = Logger.getLogger(EsSurveyService.class.getName());

    private final EsSurveyDao surveyDao;
    private final EsSurveyQuestionDao questionDao;
    private final EsTopicMeetingSurveyDao assignmentDao;
    private final EsSurveyResponseDao responseDao;
    private final EsSurveyAnswerDao answerDao;

    public EsSurveyService() {
        this.surveyDao = new EsSurveyDao();
        this.questionDao = new EsSurveyQuestionDao();
        this.assignmentDao = new EsTopicMeetingSurveyDao();
        this.responseDao = new EsSurveyResponseDao();
        this.answerDao = new EsSurveyAnswerDao();
    }

    // -------------------------------------------------------------------------
    // Survey CRUD
    // -------------------------------------------------------------------------

    public List<EsSurvey> listSurveys() {
        return surveyDao.findAllOrdered();
    }

    public Optional<EsSurvey> getSurvey(Long esSurveyId) {
        return surveyDao.findById(esSurveyId);
    }

    public EsSurvey createSurvey(String name, String description, Long createdByUserId) {
        EsSurvey survey = new EsSurvey();
        survey.setSurveyName(name.trim());
        survey.setSurveyDescription(description);
        survey.setStatus(SurveyStatus.DRAFT);
        survey.setCreatedByUserId(createdByUserId);
        survey.setSurveyKey(generateSurveyKey(name));
        return surveyDao.save(survey);
    }

    public EsSurvey updateDraftSurvey(Long esSurveyId, String name, String description) {
        EsSurvey survey = surveyDao.findById(esSurveyId)
                .orElseThrow(() -> new IllegalArgumentException("Survey not found: " + esSurveyId));
        if (survey.getStatus() != SurveyStatus.DRAFT) {
            throw new IllegalStateException("Survey can only be edited while in DRAFT status.");
        }
        survey.setSurveyName(name.trim());
        survey.setSurveyDescription(description);
        return surveyDao.save(survey);
    }

    // -------------------------------------------------------------------------
    // Question management
    // -------------------------------------------------------------------------

    public EsSurveyQuestion addQuestion(Long esSurveyId, String questionText, QuestionType questionType,
            boolean required) {
        EsSurvey survey = surveyDao.findById(esSurveyId)
                .orElseThrow(() -> new IllegalArgumentException("Survey not found: " + esSurveyId));
        if (survey.getStatus() != SurveyStatus.DRAFT) {
            throw new IllegalStateException("Questions can only be added to a DRAFT survey.");
        }
        int nextOrder = questionDao.maxDisplayOrder(esSurveyId) + 1;
        EsSurveyQuestion question = new EsSurveyQuestion();
        question.setEsSurveyId(esSurveyId);
        question.setQuestionText(questionText.trim());
        question.setQuestionType(questionType);
        question.setRequired(required);
        question.setDisplayOrder(nextOrder);
        return questionDao.save(question);
    }

    public EsSurveyQuestion updateQuestion(Long esSurveyQuestionId, String questionText, boolean required) {
        EsSurveyQuestion question = questionDao.findById(esSurveyQuestionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + esSurveyQuestionId));
        EsSurvey survey = surveyDao.findById(question.getEsSurveyId())
                .orElseThrow(() -> new IllegalArgumentException("Survey not found for question."));
        if (survey.getStatus() != SurveyStatus.DRAFT) {
            throw new IllegalStateException("Questions can only be edited on a DRAFT survey.");
        }
        question.setQuestionText(questionText.trim());
        question.setRequired(required);
        return questionDao.saveOrUpdate(question);
    }

    public void reorderQuestions(Long esSurveyId, List<Long> orderedQuestionIds) {
        EsSurvey survey = surveyDao.findById(esSurveyId)
                .orElseThrow(() -> new IllegalArgumentException("Survey not found: " + esSurveyId));
        if (survey.getStatus() != SurveyStatus.DRAFT) {
            throw new IllegalStateException("Questions can only be reordered on a DRAFT survey.");
        }
        int order = 1;
        for (Long questionId : orderedQuestionIds) {
            EsSurveyQuestion question = questionDao.findById(questionId).orElse(null);
            if (question != null && esSurveyId.equals(question.getEsSurveyId())) {
                question.setDisplayOrder(order++);
                questionDao.saveOrUpdate(question);
            }
        }
    }

    public List<EsSurveyQuestion> listQuestions(Long esSurveyId) {
        return questionDao.findBySurveyIdOrdered(esSurveyId);
    }

    // -------------------------------------------------------------------------
    // Status transitions
    // -------------------------------------------------------------------------

    public EsSurvey markReady(Long esSurveyId, Long adminUserId) {
        EsSurvey survey = surveyDao.findById(esSurveyId)
                .orElseThrow(() -> new IllegalArgumentException("Survey not found: " + esSurveyId));
        if (survey.getStatus() != SurveyStatus.DRAFT) {
            throw new IllegalStateException("Only a DRAFT survey can be marked READY.");
        }
        List<EsSurveyQuestion> questions = questionDao.findBySurveyIdOrdered(esSurveyId);
        if (questions.isEmpty()) {
            throw new IllegalStateException("Survey must have at least one question before it can be marked READY.");
        }
        survey.setStatus(SurveyStatus.READY);
        survey.setReadyAt(LocalDateTime.now());
        return surveyDao.save(survey);
    }

    public EsSurvey closeSurvey(Long esSurveyId) {
        EsSurvey survey = surveyDao.findById(esSurveyId)
                .orElseThrow(() -> new IllegalArgumentException("Survey not found: " + esSurveyId));
        survey.setStatus(SurveyStatus.CLOSED);
        survey.setClosedAt(LocalDateTime.now());
        return surveyDao.save(survey);
    }

    // -------------------------------------------------------------------------
    // Assignment (EsTopicMeetingSurvey) management
    // -------------------------------------------------------------------------

    public EsTopicMeetingSurvey createTopicMeetingSurvey(Long esTopicMeetingId, Long esSurveyId,
            LocalDate startDate, LocalDate endDate, Long adminUserId) {
        EsSurvey survey = surveyDao.findById(esSurveyId)
                .orElseThrow(() -> new IllegalArgumentException("Survey not found: " + esSurveyId));
        if (survey.getStatus() != SurveyStatus.READY) {
            throw new IllegalStateException("Only a READY survey can be assigned to a meeting.");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be on or after start date.");
        }
        EsTopicMeetingSurvey assignment = new EsTopicMeetingSurvey();
        assignment.setEsTopicMeetingId(esTopicMeetingId);
        assignment.setEsSurveyId(esSurveyId);
        assignment.setStartDate(startDate);
        assignment.setEndDate(endDate);
        assignment.setStatus(AssignmentStatus.ACTIVE);
        assignment.setCreatedByUserId(adminUserId);
        return assignmentDao.save(assignment);
    }

    public EsTopicMeetingSurvey updateTopicMeetingSurvey(Long assignmentId, LocalDate startDate,
            LocalDate endDate, AssignmentStatus status) {
        EsTopicMeetingSurvey assignment = assignmentDao.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + assignmentId));
        if (assignment.getStatus() == AssignmentStatus.CLOSED && status == AssignmentStatus.CLOSED) {
            // already closed — still allow date edits but disallow re-opening
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be on or after start date.");
        }
        assignment.setStartDate(startDate);
        assignment.setEndDate(endDate);
        assignment.setStatus(status);
        return assignmentDao.saveOrUpdate(assignment);
    }

    public List<EsTopicMeetingSurvey> listTopicMeetingSurveys() {
        return assignmentDao.findAllOrdered();
    }

    public Optional<EsTopicMeetingSurvey> getTopicMeetingSurvey(Long assignmentId) {
        return assignmentDao.findById(assignmentId);
    }

    // -------------------------------------------------------------------------
    // Attendance-triggered survey lookup
    // -------------------------------------------------------------------------

    public Optional<EsTopicMeetingSurvey> findPendingSurveyForAttendance(EsMeetingAttendance attendance,
            User loggedInUser) {
        try {
            if (attendance == null || attendance.getEsTopicMeetingId() == null) {
                return Optional.empty();
            }
            LocalDate today = LocalDate.now();
            List<EsTopicMeetingSurvey> activeAssignments = assignmentDao.findActive(
                    attendance.getEsTopicMeetingId(), today);
            Long userId = loggedInUser != null ? loggedInUser.getUserId() : attendance.getUserId();
            String emailNormalized = attendance.getEmailNormalized();

            for (EsTopicMeetingSurvey assignment : activeAssignments) {
                // Verify the linked survey is still READY
                EsSurvey survey = surveyDao.findById(assignment.getEsSurveyId()).orElse(null);
                if (survey == null || survey.getStatus() != SurveyStatus.READY) {
                    continue;
                }
                if (!hasResponded(assignment, userId, emailNormalized)) {
                    return Optional.of(assignment);
                }
            }
            return Optional.empty();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error checking for pending survey for attendance id="
                    + (attendance != null ? attendance.getEsMeetingAttendanceId() : "null"), ex);
            return Optional.empty();
        }
    }

    public boolean hasResponded(EsTopicMeetingSurvey assignment, Long userId, String emailNormalized) {
        if (assignment == null) {
            return false;
        }
        Long assignmentId = assignment.getEsTopicMeetingSurveyId();
        if (userId != null) {
            if (responseDao.findByTopicMeetingSurveyIdAndUserId(assignmentId, userId).isPresent()) {
                return true;
            }
        }
        if (emailNormalized != null) {
            if (responseDao.findByTopicMeetingSurveyIdAndEmailNormalized(assignmentId, emailNormalized).isPresent()) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Survey response submission
    // -------------------------------------------------------------------------

    public EsSurveyResponse submitSurveyResponse(EsTopicMeetingSurvey assignment,
            EsMeetingAttendance attendance, User loggedInUser, Map<Long, String> answersByQuestionId) {

        Long userId = loggedInUser != null ? loggedInUser.getUserId() : attendance.getUserId();
        String emailNormalized = attendance.getEmailNormalized();

        if (hasResponded(assignment, userId, emailNormalized)) {
            throw new IllegalStateException("You have already submitted a response for this survey.");
        }

        List<EsSurveyQuestion> questions = questionDao.findBySurveyIdOrdered(assignment.getEsSurveyId());

        // Validate required questions and Likert ranges
        List<String> errors = new ArrayList<>();
        for (EsSurveyQuestion question : questions) {
            String rawAnswer = answersByQuestionId.get(question.getEsSurveyQuestionId());
            if (question.isRequired() && (rawAnswer == null || rawAnswer.isBlank())) {
                errors.add("Question " + question.getDisplayOrder() + " is required.");
                continue;
            }
            if (rawAnswer != null && !rawAnswer.isBlank()
                    && question.getQuestionType() == QuestionType.LIKERT_1_5) {
                try {
                    int val = Integer.parseInt(rawAnswer.trim());
                    if (val < 1 || val > 5) {
                        errors.add("Question " + question.getDisplayOrder() + " must be rated 1 to 5.");
                    }
                } catch (NumberFormatException ex) {
                    errors.add("Question " + question.getDisplayOrder() + " requires a numeric rating.");
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", errors));
        }

        // Save response
        EsSurveyResponse response = new EsSurveyResponse();
        response.setEsTopicMeetingSurveyId(assignment.getEsTopicMeetingSurveyId());
        response.setEsMeetingId(attendance.getEsMeetingId());
        response.setEsMeetingAttendanceId(attendance.getEsMeetingAttendanceId());
        response.setUserId(userId);
        response.setEmail(attendance.getEmail());
        response.setEmailNormalized(emailNormalized);
        response.setFirstName(attendance.getFirstName());
        response.setLastName(attendance.getLastName());
        response.setOrganization(attendance.getOrganization());
        EsSurveyResponse saved = responseDao.saveOrUpdate(response);

        // Save answers
        for (EsSurveyQuestion question : questions) {
            String rawAnswer = answersByQuestionId.get(question.getEsSurveyQuestionId());
            if (rawAnswer == null || rawAnswer.isBlank()) {
                continue;
            }
            EsSurveyAnswer answer = new EsSurveyAnswer();
            answer.setEsSurveyResponseId(saved.getEsSurveyResponseId());
            answer.setEsSurveyQuestionId(question.getEsSurveyQuestionId());
            if (question.getQuestionType() == QuestionType.LIKERT_1_5) {
                answer.setNumericValue(Integer.parseInt(rawAnswer.trim()));
            } else {
                answer.setTextValue(rawAnswer.trim());
            }
            answerDao.save(answer);
        }

        return saved;
    }

    // -------------------------------------------------------------------------
    // Aggregate results
    // -------------------------------------------------------------------------

    public SurveyResultsData getAggregateResults(Long esTopicMeetingSurveyId) {
        return getAggregateResults(esTopicMeetingSurveyId, true);
    }

    public SurveyResultsData getAggregateResults(Long esTopicMeetingSurveyId, boolean includeAdmin) {
        EsTopicMeetingSurvey assignment = assignmentDao.findById(esTopicMeetingSurveyId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + esTopicMeetingSurveyId));
        EsSurvey survey = surveyDao.findById(assignment.getEsSurveyId())
                .orElseThrow(() -> new IllegalArgumentException("Survey not found."));
        List<EsSurveyQuestion> questions = questionDao.findBySurveyIdOrdered(assignment.getEsSurveyId());

        long totalResponseCount = responseDao.countByTopicMeetingSurveyId(esTopicMeetingSurveyId);
        long responseCount = includeAdmin ? totalResponseCount
                : responseDao.countByTopicMeetingSurveyIdExcludingAdmin(esTopicMeetingSurveyId);
        long excludedAdminCount = totalResponseCount - responseCount;

        List<QuestionResult> questionResults = new ArrayList<>();
        for (EsSurveyQuestion question : questions) {
            List<EsSurveyAnswer> answers = loadAnswersForQuestion(esTopicMeetingSurveyId,
                    question.getEsSurveyQuestionId(), includeAdmin);
            questionResults.add(buildQuestionResult(question, answers));
        }
        return new SurveyResultsData(assignment, survey, responseCount, excludedAdminCount, questionResults);
    }

    private List<EsSurveyAnswer> loadAnswersForQuestion(Long esTopicMeetingSurveyId, Long questionId,
            boolean includeAdmin) {
        try (org.hibernate.Session session = org.airahub.interophub.config.HibernateUtil
                .getSessionFactory().openSession()) {
            String hql = "select a from EsSurveyAnswer a"
                    + " join EsSurveyResponse r on a.esSurveyResponseId = r.esSurveyResponseId"
                    + " where r.esTopicMeetingSurveyId = :assignmentId"
                    + " and a.esSurveyQuestionId = :questionId";
            if (!includeAdmin) {
                hql += " and (r.userId is null"
                        + "  or r.userId not in (select u.userId from User u where u.isAdmin = true))";
            }
            return session.createQuery(hql, EsSurveyAnswer.class)
                    .setParameter("assignmentId", esTopicMeetingSurveyId)
                    .setParameter("questionId", questionId)
                    .getResultList();
        }
    }

    private QuestionResult buildQuestionResult(EsSurveyQuestion question, List<EsSurveyAnswer> answers) {
        if (question.getQuestionType() == QuestionType.LIKERT_1_5) {
            Map<Integer, Integer> distribution = new HashMap<>();
            for (int i = 1; i <= 5; i++) {
                distribution.put(i, 0);
            }
            int total = 0;
            int count = 0;
            for (EsSurveyAnswer answer : answers) {
                if (answer.getNumericValue() != null) {
                    int val = answer.getNumericValue();
                    distribution.merge(val, 1, Integer::sum);
                    total += val;
                    count++;
                }
            }
            double average = count > 0 ? (double) total / count : 0.0;
            return new QuestionResult(question, count, average, distribution, List.of());
        } else {
            List<String> textAnswers = new ArrayList<>();
            for (EsSurveyAnswer answer : answers) {
                if (answer.getTextValue() != null && !answer.getTextValue().isBlank()) {
                    textAnswers.add(answer.getTextValue());
                }
            }
            return new QuestionResult(question, textAnswers.size(), 0.0, Map.of(), textAnswers);
        }
    }

    // -------------------------------------------------------------------------
    // Result data holders
    // -------------------------------------------------------------------------

    public static class SurveyResultsData {
        private final EsTopicMeetingSurvey assignment;
        private final EsSurvey survey;
        private final long responseCount;
        private final long excludedAdminCount;
        private final List<QuestionResult> questionResults;

        public SurveyResultsData(EsTopicMeetingSurvey assignment, EsSurvey survey,
                long responseCount, long excludedAdminCount, List<QuestionResult> questionResults) {
            this.assignment = assignment;
            this.survey = survey;
            this.responseCount = responseCount;
            this.excludedAdminCount = excludedAdminCount;
            this.questionResults = questionResults;
        }

        public EsTopicMeetingSurvey getAssignment() {
            return assignment;
        }

        public EsSurvey getSurvey() {
            return survey;
        }

        public long getResponseCount() {
            return responseCount;
        }

        public long getExcludedAdminCount() {
            return excludedAdminCount;
        }

        public List<QuestionResult> getQuestionResults() {
            return questionResults;
        }
    }

    public static class QuestionResult {
        private final EsSurveyQuestion question;
        private final int count;
        private final double average;
        private final Map<Integer, Integer> distribution;
        private final List<String> textAnswers;

        public QuestionResult(EsSurveyQuestion question, int count, double average,
                Map<Integer, Integer> distribution, List<String> textAnswers) {
            this.question = question;
            this.count = count;
            this.average = average;
            this.distribution = distribution;
            this.textAnswers = textAnswers;
        }

        public EsSurveyQuestion getQuestion() {
            return question;
        }

        public int getCount() {
            return count;
        }

        public double getAverage() {
            return average;
        }

        public Map<Integer, Integer> getDistribution() {
            return distribution;
        }

        public List<String> getTextAnswers() {
            return textAnswers;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String generateSurveyKey(String name) {
        String base = name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (base.length() > 60) {
            base = base.substring(0, 60);
        }
        return base + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
