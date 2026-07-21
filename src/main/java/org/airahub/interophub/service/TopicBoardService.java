package org.airahub.interophub.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.dao.EsTopicBoardDefinitionDao;
import org.airahub.interophub.dao.EsTopicBoardPathDao;
import org.airahub.interophub.dao.EsTopicBoardStageDao;
import org.airahub.interophub.dao.EsTopicCurationDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicPathDefinitionDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.dao.EsTopicStageDefinitionDao;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicBoardDefinition;
import org.airahub.interophub.model.EsTopicBoardPath;
import org.airahub.interophub.model.EsTopicBoardStage;
import org.airahub.interophub.model.EsTopicCuration;
import org.airahub.interophub.model.EsTopicPathDefinition;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.EsTopicStageDefinition;
import org.airahub.interophub.model.User;

public class TopicBoardService {

    private final EsTopicBoardDefinitionDao boardDefinitionDao;
    private final EsTopicBoardStageDao boardStageDao;
    private final EsTopicBoardPathDao boardPathDao;
    private final EsTopicDao topicDao;
    private final EsTopicSpaceDao topicSpaceDao;
    private final EsTopicStageDefinitionDao stageDefinitionDao;
    private final EsTopicPathDefinitionDao pathDefinitionDao;
    private final EsTopicCurationDao curationDao;
    private final TopicSpaceAccessService topicSpaceAccessService;

    public TopicBoardService() {
        this.boardDefinitionDao = new EsTopicBoardDefinitionDao();
        this.boardStageDao = new EsTopicBoardStageDao();
        this.boardPathDao = new EsTopicBoardPathDao();
        this.topicDao = new EsTopicDao();
        this.topicSpaceDao = new EsTopicSpaceDao();
        this.stageDefinitionDao = new EsTopicStageDefinitionDao();
        this.pathDefinitionDao = new EsTopicPathDefinitionDao();
        this.curationDao = new EsTopicCurationDao();
        this.topicSpaceAccessService = new TopicSpaceAccessService();
    }

    public Optional<BoardView> loadBoardByCodeForDisplay(String boardCode, User viewer) {
        Optional<EsTopicBoardDefinition> board = boardDefinitionDao.findByBoardCode(boardCode)
                .filter(b -> Boolean.TRUE.equals(b.getIsActive()));
        if (board.isEmpty()) {
            return Optional.empty();
        }
        EsTopicBoardDefinition definition = board.get();
        if (!topicSpaceAccessService.canViewSpaceId(viewer, definition.getEsTopicSpaceId())) {
            return Optional.empty();
        }
        return Optional.of(buildBoardView(definition));
    }

    public List<AdminBoardRow> listBoardDefinitions() {
        List<EsTopicBoardDefinition> definitions = boardDefinitionDao.findAllOrdered();
        Map<Long, String> spaceNamesById = topicSpaceDao.findAllOrdered().stream()
                .filter(space -> space.getEsTopicSpaceId() != null)
                .collect(Collectors.toMap(
                        EsTopicSpace::getEsTopicSpaceId,
                        space -> safe(space.getSpaceName()),
                        (left, right) -> left,
                        LinkedHashMap::new));

        List<Long> curatorTopicIds = definitions.stream()
                .map(EsTopicBoardDefinition::getCuratorTopicId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> topicNamesById = topicDao.findTopicNamesByTopicIds(curatorTopicIds);

        List<AdminBoardRow> rows = new ArrayList<>();
        for (EsTopicBoardDefinition definition : definitions) {
            rows.add(new AdminBoardRow(
                    definition.getEsTopicBoardDefinitionId(),
                    safe(definition.getBoardCode()),
                    safe(definition.getBoardName()),
                    safe(spaceNamesById.get(definition.getEsTopicSpaceId())),
                    definition.getCuratorTopicId(),
                    safe(topicNamesById.get(definition.getCuratorTopicId())),
                    Boolean.TRUE.equals(definition.getIsActive())));
        }
        return rows;
    }

    public BoardEditData loadBoardEditData(Long boardDefinitionId) {
        EsTopicBoardDefinition definition;
        if (boardDefinitionId == null) {
            definition = new EsTopicBoardDefinition();
            definition.setIsActive(Boolean.TRUE);
            definition.setShowUnassignedPath(Boolean.FALSE);
            definition.setShowUnassignedStage(Boolean.FALSE);
        } else {
            definition = boardDefinitionDao.findById(boardDefinitionId)
                    .orElseThrow(() -> new ValidationException("Board definition was not found."));
        }

        Long selectedSpaceId = definition.getEsTopicSpaceId();
        OptionsBundle options = loadOptions(selectedSpaceId);

        Map<Long, Integer> selectedStageOrder = new LinkedHashMap<>();
        for (EsTopicBoardStage stage : boardStageDao
                .findByBoardDefinitionId(definition.getEsTopicBoardDefinitionId())) {
            if (stage.getEsTopicStageDefinitionId() != null) {
                selectedStageOrder.put(stage.getEsTopicStageDefinitionId(),
                        stage.getDisplayOrder() == null ? 0 : stage.getDisplayOrder());
            }
        }

        Map<Long, Integer> selectedPathOrder = new LinkedHashMap<>();
        for (EsTopicBoardPath path : boardPathDao.findByBoardDefinitionId(definition.getEsTopicBoardDefinitionId())) {
            if (path.getEsTopicPathDefinitionId() != null) {
                selectedPathOrder.put(path.getEsTopicPathDefinitionId(),
                        path.getDisplayOrder() == null ? 0 : path.getDisplayOrder());
            }
        }

        // Keep only active options in the currently selected space.
        selectedStageOrder.keySet().retainAll(options.activeStageIds());
        selectedPathOrder.keySet().retainAll(options.activePathIds());

        List<EsTopic> curatorCandidates = topicDao.findAllActiveOrderByTopicName();

        return new BoardEditData(definition, topicSpaceDao.findAllOrdered(), curatorCandidates, options,
                selectedStageOrder, selectedPathOrder);
    }

    public OptionsBundle loadOptions(Long topicSpaceId) {
        if (topicSpaceId == null) {
            return new OptionsBundle(List.of(), List.of(), Set.of(), Set.of());
        }
        List<EsTopicStageDefinition> stages = stageDefinitionDao.findAllOrderedBySpaceId(topicSpaceId).stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                .toList();
        List<EsTopicPathDefinition> paths = pathDefinitionDao.findAllOrderedBySpaceId(topicSpaceId).stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .toList();
        Set<Long> stageIds = stages.stream().map(EsTopicStageDefinition::getEsTopicStageDefinitionId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> pathIds = paths.stream().map(EsTopicPathDefinition::getEsTopicPathDefinitionId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new OptionsBundle(stages, paths, stageIds, pathIds);
    }

    public EsTopicBoardDefinition saveBoard(BoardSaveRequest request, boolean creating) {
        if (request == null) {
            throw new ValidationException("Board save request is required.");
        }

        EsTopicBoardDefinition definition;
        if (creating) {
            definition = new EsTopicBoardDefinition();
            String boardCode = normalizeCode(required(request.boardCode(), "Board code"));
            if (boardDefinitionDao.findByBoardCode(boardCode).isPresent()) {
                throw new ValidationException("Board code is already in use.");
            }
            definition.setBoardCode(boardCode);
        } else {
            Long boardId = request.boardDefinitionId();
            if (boardId == null) {
                throw new ValidationException("Board identifier is required for updates.");
            }
            definition = boardDefinitionDao.findById(boardId)
                    .orElseThrow(() -> new ValidationException("Board definition was not found."));
        }

        Long topicSpaceId = request.topicSpaceId();
        if (topicSpaceId == null) {
            throw new ValidationException("Topic Space is required.");
        }
        if (!topicSpaceDao.isActiveSpaceId(topicSpaceId)) {
            throw new ValidationException("Only active Topic Spaces can be used for boards.");
        }

        Long curatorTopicId = request.curatorTopicId();
        if (curatorTopicId != null) {
            EsTopic curator = topicDao.findById(curatorTopicId).orElse(null);
            if (curator == null || curator.getStatus() != EsTopic.EsTopicStatus.ACTIVE) {
                throw new ValidationException("Curator topic must reference an active topic.");
            }
        }

        OptionsBundle options = loadOptions(topicSpaceId);

        Map<Long, Integer> normalizedStageOrder = TopicBoardRules.normalizeDisplayOrder(
                defaultMap(request.stageOrderById()),
                options.activeStageIds());
        Map<Long, Integer> normalizedPathOrder = TopicBoardRules.normalizeDisplayOrder(
                defaultMap(request.pathOrderById()),
                options.activePathIds());

        boolean showUnassignedStage = request.showUnassignedStage();
        boolean showUnassignedPath = request.showUnassignedPath();

        if (normalizedStageOrder.isEmpty() && !showUnassignedStage) {
            throw new ValidationException("Select at least one active stage or enable Not assigned stage.");
        }
        if (normalizedPathOrder.isEmpty() && !showUnassignedPath) {
            throw new ValidationException("Select at least one active path or enable Not assigned path.");
        }

        definition.setBoardName(required(request.boardName(), "Board name"));
        definition.setBoardDescription(trimToNull(request.boardDescription()));
        definition.setEsTopicSpaceId(topicSpaceId);
        definition.setCuratorTopicId(curatorTopicId);
        definition.setShowUnassignedStage(showUnassignedStage);
        definition.setShowUnassignedPath(showUnassignedPath);
        definition.setIsActive(request.isActive());

        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();

            EsTopicBoardDefinition merged = (EsTopicBoardDefinition) session.merge(definition);
            Long boardId = merged.getEsTopicBoardDefinitionId();

            session.createMutationQuery("delete from EsTopicBoardStage s where s.esTopicBoardDefinitionId = :boardId")
                    .setParameter("boardId", boardId)
                    .executeUpdate();

            session.createMutationQuery("delete from EsTopicBoardPath p where p.esTopicBoardDefinitionId = :boardId")
                    .setParameter("boardId", boardId)
                    .executeUpdate();

            for (Map.Entry<Long, Integer> entry : normalizedStageOrder.entrySet()) {
                EsTopicBoardStage stage = new EsTopicBoardStage();
                stage.setEsTopicBoardDefinitionId(boardId);
                stage.setEsTopicStageDefinitionId(entry.getKey());
                stage.setDisplayOrder(entry.getValue());
                session.persist(stage);
            }

            for (Map.Entry<Long, Integer> entry : normalizedPathOrder.entrySet()) {
                EsTopicBoardPath path = new EsTopicBoardPath();
                path.setEsTopicBoardDefinitionId(boardId);
                path.setEsTopicPathDefinitionId(entry.getKey());
                path.setDisplayOrder(entry.getValue());
                session.persist(path);
            }

            tx.commit();
            return merged;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    public List<SearchTopicResult> searchActiveTopics(String boardCode, String query, User viewer, int maxResults) {
        BoardView board = loadBoardByCodeForDisplay(boardCode, viewer)
                .orElseThrow(() -> new ValidationException("Board was not found."));

        List<EsTopic> topics = topicDao.searchActiveBySpaceId(board.board().getEsTopicSpaceId(), query,
                maxResults <= 0 ? 30 : maxResults);

        Map<Long, EsTopicStageDefinition> stageById = stageDefinitionDao
                .findAllOrderedBySpaceId(board.board().getEsTopicSpaceId()).stream()
                .collect(Collectors.toMap(
                        EsTopicStageDefinition::getEsTopicStageDefinitionId,
                        s -> s,
                        (left, right) -> left,
                        LinkedHashMap::new));

        Map<Long, EsTopicPathDefinition> pathById = pathDefinitionDao
                .findAllOrderedBySpaceId(board.board().getEsTopicSpaceId()).stream()
                .collect(Collectors.toMap(
                        EsTopicPathDefinition::getEsTopicPathDefinitionId,
                        p -> p,
                        (left, right) -> left,
                        LinkedHashMap::new));

        List<SearchTopicResult> results = new ArrayList<>();
        for (EsTopic topic : topics) {
            String stageName = "Not assigned";
            if (topic.getEsTopicStageDefinitionId() != null) {
                EsTopicStageDefinition stage = stageById.get(topic.getEsTopicStageDefinitionId());
                stageName = stage == null ? safe(topic.getStage()) : safe(stage.getStageName());
                if (stageName.isBlank()) {
                    stageName = "Not assigned";
                }
            }

            String pathName = "Not assigned";
            if (topic.getEsTopicPathDefinitionId() != null) {
                EsTopicPathDefinition path = pathById.get(topic.getEsTopicPathDefinitionId());
                pathName = path == null ? safe(topic.getPath()) : safe(path.getPathName());
                if (pathName.isBlank()) {
                    pathName = "Not assigned";
                }
            }

            results.add(new SearchTopicResult(topic.getEsTopicId(), safe(topic.getTopicName()), stageName, pathName));
        }
        return results;
    }

    public PlacementResult placeTopic(String boardCode, Long topicId, Long targetStageDefinitionId,
            Long targetPathDefinitionId, User actingUser) {
        if (actingUser == null || actingUser.getUserId() == null) {
            throw new ValidationException("Authentication is required.");
        }
        if (topicId == null) {
            throw new ValidationException("Topic is required.");
        }

        BoardView boardView = loadBoardByCodeForDisplay(boardCode, actingUser)
                .orElseThrow(() -> new ValidationException("Board was not found."));

        BoardTargetValidation targetValidation = validateTarget(
                boardView,
                targetStageDefinitionId,
                targetPathDefinitionId);

        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();

            EsTopic topic = session.get(EsTopic.class, topicId);
            if (topic == null || topic.getStatus() != EsTopic.EsTopicStatus.ACTIVE) {
                throw new ValidationException("Topic was not found.");
            }
            if (!boardView.board().getEsTopicSpaceId().equals(topic.getEsTopicSpaceId())) {
                throw new ValidationException("Topic does not belong to this board's Topic Space.");
            }

            if (boardView.isCurated()) {
                ensureCurationExists(session, boardView.board(), topic.getEsTopicId(), actingUser.getUserId());
            }

            topic.setEsTopicStageDefinitionId(targetValidation.stageDefinitionId());
            topic.setEsTopicPathDefinitionId(targetValidation.pathDefinitionId());
            topic.setStage(targetValidation.stageName());
            topic.setPath(targetValidation.pathName());
            session.merge(topic);

            tx.commit();

            new DandelionSyncService().enqueueTopicUpsert(topic.getEsTopicId());

            return new PlacementResult(topic.getEsTopicId(), safe(topic.getTopicName()),
                    targetValidation.stageDefinitionId(), targetValidation.pathDefinitionId(),
                    boardView.isCurated());
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    public void removeFromCuratedBoard(String boardCode, Long topicId, User actingUser) {
        if (actingUser == null || actingUser.getUserId() == null) {
            throw new ValidationException("Authentication is required.");
        }
        if (topicId == null) {
            throw new ValidationException("Topic is required.");
        }

        BoardView boardView = loadBoardByCodeForDisplay(boardCode, actingUser)
                .orElseThrow(() -> new ValidationException("Board was not found."));

        if (!boardView.isCurated()) {
            throw new ValidationException("Remove from board is only available for curated boards.");
        }

        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();

            session.createMutationQuery(
                    "delete from EsTopicCuration c where c.curatorTopicId = :curatorId and c.curatedTopicId = :topicId")
                    .setParameter("curatorId", boardView.board().getCuratorTopicId())
                    .setParameter("topicId", topicId)
                    .executeUpdate();

            tx.commit();
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    private BoardTargetValidation validateTarget(BoardView boardView, Long stageDefinitionId, Long pathDefinitionId) {
        Long boardSpaceId = boardView.board().getEsTopicSpaceId();

        Long targetStageId = null;
        String targetStageName = null;
        if (stageDefinitionId == null) {
            if (!boardView.showUnassignedStage()) {
                throw new ValidationException("Not assigned stage is not enabled for this board.");
            }
        } else {
            EsTopicStageDefinition stage = boardView.stageById().get(stageDefinitionId);
            if (stage == null || !Boolean.TRUE.equals(stage.getIsActive())
                    || !boardSpaceId.equals(stage.getEsTopicSpaceId())
                    || !boardView.displayedStageIds().contains(stageDefinitionId)) {
                throw new ValidationException("Target stage is not valid for this board.");
            }
            targetStageId = stageDefinitionId;
            targetStageName = trimToNull(stage.getStageName());
        }

        Long targetPathId = null;
        String targetPathName = null;
        if (pathDefinitionId == null) {
            if (!boardView.showUnassignedPath()) {
                throw new ValidationException("Not assigned path is not enabled for this board.");
            }
        } else {
            EsTopicPathDefinition path = boardView.pathById().get(pathDefinitionId);
            if (path == null || !Boolean.TRUE.equals(path.getIsActive())
                    || !boardSpaceId.equals(path.getEsTopicSpaceId())
                    || !boardView.displayedPathIds().contains(pathDefinitionId)) {
                throw new ValidationException("Target path is not valid for this board.");
            }
            targetPathId = pathDefinitionId;
            targetPathName = trimToNull(path.getPathName());
        }

        return new BoardTargetValidation(targetStageId, targetPathId, targetStageName, targetPathName);
    }

    private void ensureCurationExists(org.hibernate.Session session, EsTopicBoardDefinition board,
            Long curatedTopicId, Long createdByUserId) {
        Long curatorTopicId = board.getCuratorTopicId();
        if (curatorTopicId == null) {
            return;
        }

        EsTopicCuration existing = session.createQuery(
                "from EsTopicCuration c where c.curatorTopicId = :curatorId and c.curatedTopicId = :curatedId",
                EsTopicCuration.class)
                .setParameter("curatorId", curatorTopicId)
                .setParameter("curatedId", curatedTopicId)
                .setMaxResults(1)
                .uniqueResult();
        if (existing != null) {
            return;
        }

        Integer maxDisplayOrder = session.createQuery(
                "select max(c.displayOrder) from EsTopicCuration c where c.curatorTopicId = :curatorId",
                Integer.class)
                .setParameter("curatorId", curatorTopicId)
                .uniqueResult();

        EsTopicCuration curation = new EsTopicCuration();
        curation.setCuratorTopicId(curatorTopicId);
        curation.setCuratedTopicId(curatedTopicId);
        curation.setCreatedByUserId(createdByUserId);
        curation.setDisplayOrder(maxDisplayOrder == null ? 0 : maxDisplayOrder + 1);
        session.persist(curation);
    }

    private BoardView buildBoardView(EsTopicBoardDefinition board) {
        List<EsTopicBoardStage> boardStages = boardStageDao
                .findByBoardDefinitionId(board.getEsTopicBoardDefinitionId());
        List<EsTopicBoardPath> boardPaths = boardPathDao.findByBoardDefinitionId(board.getEsTopicBoardDefinitionId());

        Map<Long, Integer> stageOrderById = boardStages.stream()
                .filter(s -> s.getEsTopicStageDefinitionId() != null)
                .collect(Collectors.toMap(
                        EsTopicBoardStage::getEsTopicStageDefinitionId,
                        s -> s.getDisplayOrder() == null ? 0 : s.getDisplayOrder(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<Long, Integer> pathOrderById = boardPaths.stream()
                .filter(p -> p.getEsTopicPathDefinitionId() != null)
                .collect(Collectors.toMap(
                        EsTopicBoardPath::getEsTopicPathDefinitionId,
                        p -> p.getDisplayOrder() == null ? 0 : p.getDisplayOrder(),
                        (left, right) -> left,
                        LinkedHashMap::new));

        List<EsTopicStageDefinition> stageDefinitions = stageDefinitionDao
                .findAllOrderedBySpaceId(board.getEsTopicSpaceId());
        List<EsTopicPathDefinition> pathDefinitions = pathDefinitionDao
                .findAllOrderedBySpaceId(board.getEsTopicSpaceId());

        Map<Long, EsTopicStageDefinition> stageById = stageDefinitions.stream()
                .filter(s -> s.getEsTopicStageDefinitionId() != null)
                .collect(Collectors.toMap(
                        EsTopicStageDefinition::getEsTopicStageDefinitionId,
                        s -> s,
                        (left, right) -> left,
                        LinkedHashMap::new));

        Map<Long, EsTopicPathDefinition> pathById = pathDefinitions.stream()
                .filter(p -> p.getEsTopicPathDefinitionId() != null)
                .collect(Collectors.toMap(
                        EsTopicPathDefinition::getEsTopicPathDefinitionId,
                        p -> p,
                        (left, right) -> left,
                        LinkedHashMap::new));

        List<EsTopicStageDefinition> displayedStages = TopicBoardRules
                .resolveDisplayedStages(stageDefinitions, stageOrderById);
        List<EsTopicPathDefinition> displayedPaths = TopicBoardRules
                .resolveDisplayedPaths(pathDefinitions, pathOrderById);

        Set<Long> displayedStageIds = displayedStages.stream()
                .map(EsTopicStageDefinition::getEsTopicStageDefinitionId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> displayedPathIds = displayedPaths.stream()
                .map(EsTopicPathDefinition::getEsTopicPathDefinitionId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<Long> curatedTopicIds = null;
        if (board.getCuratorTopicId() != null) {
            curatedTopicIds = curationDao.findByCuratorTopicId(board.getCuratorTopicId()).stream()
                    .map(EsTopicCuration::getCuratedTopicId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        List<EsTopic> topics = topicDao.findActiveBySpaceIdOrderByTopicName(board.getEsTopicSpaceId());
        Map<CellKey, List<TopicCard>> cardsByCell = new LinkedHashMap<>();

        boolean showUnassignedStage = Boolean.TRUE.equals(board.getShowUnassignedStage());
        boolean showUnassignedPath = Boolean.TRUE.equals(board.getShowUnassignedPath());

        for (EsTopic topic : topics) {
            if (curatedTopicIds != null && !curatedTopicIds.contains(topic.getEsTopicId())) {
                continue;
            }
            if (!TopicBoardRules.isTopicVisibleOnBoard(topic, board.getEsTopicSpaceId(), displayedStageIds,
                    displayedPathIds, showUnassignedStage, showUnassignedPath)) {
                continue;
            }

            CellKey key = new CellKey(topic.getEsTopicStageDefinitionId(), topic.getEsTopicPathDefinitionId());
            cardsByCell.computeIfAbsent(key, unused -> new ArrayList<>())
                    .add(new TopicCard(topic.getEsTopicId(), safe(topic.getTopicName())));
        }

        for (List<TopicCard> cards : cardsByCell.values()) {
            cards.sort(Comparator.comparing(c -> c.topicName().toLowerCase()));
        }

        return new BoardView(board, displayedStages, displayedPaths, displayedStageIds, displayedPathIds,
                showUnassignedStage, showUnassignedPath, board.getCuratorTopicId() != null,
                stageById, pathById, cardsByCell);
    }

    private String required(String value, String field) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new ValidationException(field + " is required.");
        }
        return normalized;
    }

    private String normalizeCode(String value) {
        String normalized = value.toLowerCase()
                .replaceAll("[^a-z0-9\\s_-]", "")
                .trim()
                .replaceAll("[\\s_]+", "-")
                .replaceAll("-+", "-");
        if (normalized.isBlank()) {
            throw new ValidationException("Board code must contain letters or numbers.");
        }
        return normalized;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private <K, V> Map<K, V> defaultMap(Map<K, V> value) {
        return value == null ? Map.of() : value;
    }

    public record CellKey(Long stageDefinitionId, Long pathDefinitionId) {
    }

    public record TopicCard(Long topicId, String topicName) {
    }

    public record BoardView(
            EsTopicBoardDefinition board,
            List<EsTopicStageDefinition> displayedStages,
            List<EsTopicPathDefinition> displayedPaths,
            Set<Long> displayedStageIds,
            Set<Long> displayedPathIds,
            boolean showUnassignedStage,
            boolean showUnassignedPath,
            boolean isCurated,
            Map<Long, EsTopicStageDefinition> stageById,
            Map<Long, EsTopicPathDefinition> pathById,
            Map<CellKey, List<TopicCard>> cardsByCell) {
    }

    public record SearchTopicResult(Long topicId, String topicName, String currentStageName, String currentPathName) {
    }

    public record PlacementResult(Long topicId, String topicName, Long stageDefinitionId, Long pathDefinitionId,
            boolean curatedBoard) {
    }

    public record AdminBoardRow(Long boardDefinitionId, String boardCode, String boardName, String topicSpaceName,
            Long curatorTopicId, String curatorTopicName, boolean active) {
    }

    public record OptionsBundle(
            List<EsTopicStageDefinition> activeStages,
            List<EsTopicPathDefinition> activePaths,
            Set<Long> activeStageIds,
            Set<Long> activePathIds) {
    }

    public record BoardEditData(
            EsTopicBoardDefinition board,
            List<EsTopicSpace> topicSpaces,
            List<EsTopic> curatorCandidates,
            OptionsBundle options,
            Map<Long, Integer> selectedStageOrder,
            Map<Long, Integer> selectedPathOrder) {
    }

    public record BoardSaveRequest(
            Long boardDefinitionId,
            String boardCode,
            String boardName,
            String boardDescription,
            Long topicSpaceId,
            Long curatorTopicId,
            boolean showUnassignedStage,
            boolean showUnassignedPath,
            boolean isActive,
            Map<Long, Integer> stageOrderById,
            Map<Long, Integer> pathOrderById) {
    }

    private record BoardTargetValidation(Long stageDefinitionId, Long pathDefinitionId, String stageName,
            String pathName) {
    }

    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static <T> List<T> sortedByDisplayOrder(Map<Long, Integer> orderById, Collection<T> values,
            java.util.function.Function<T, Long> idAccessor) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<T> sorted = new ArrayList<>(values);
        sorted.sort(Comparator
                .comparingInt((T value) -> orderById.getOrDefault(idAccessor.apply(value), Integer.MAX_VALUE)));
        return sorted;
    }
}
