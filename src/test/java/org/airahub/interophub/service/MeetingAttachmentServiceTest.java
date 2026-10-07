package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.Function;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.dao.EsMeetingAgendaItemDao;
import org.airahub.interophub.dao.EsMeetingAgendaAttachmentDao;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAgendaItem;
import org.airahub.interophub.model.StoredFile;
import org.airahub.interophub.model.User;
import org.junit.jupiter.api.Test;

class MeetingAttachmentServiceTest {
    @Test
    void uploadsAndRemovalRequireBothViewAccessAndMeetingControls() {
        Fixture fixture = new Fixture();
        fixture.controlAllowed = false;
        assertThrows(SecurityException.class, fixture::upload);
        assertThrows(SecurityException.class,
                () -> fixture.service.detach(fixture.user, 10L, 20L, 30L));
        fixture.controlAllowed = true;
        fixture.viewAllowed = false;
        assertThrows(SecurityException.class, fixture::upload);
        assertFalse(fixture.storageCalled);
        assertFalse(fixture.registered);
        assertFalse(fixture.detached);
        assertThrows(SecurityException.class,
                () -> fixture.service.upload(null, 10L, 20L,
                        InputStream.nullInputStream(), "slides.pptx", null));
    }

    @Test
    void acceptedMeetingControllerCanAttachToAnotherPresentersItemAndDetachWithoutDeletingFile()
            throws IOException {
        Fixture fixture = new Fixture();
        StoredFile file = fixture.upload();
        assertTrue(fixture.registered);
        assertTrue(file.isDownloadOnly());
        assertEquals("slides.pptx", file.getOriginalFilename());
        fixture.service.detach(fixture.user, 10L, 20L, 30L);
        assertTrue(fixture.detached);
        assertEquals(1, fixture.uploadCount);
    }

    @Test
    void permissionIsRecheckedAfterUploadBeforeRegistration() {
        Fixture fixture = new Fixture();
        fixture.revokeDuringUpload = true;
        assertThrows(SecurityException.class, fixture::upload);
        assertTrue(fixture.storageCalled);
        assertFalse(fixture.registered);
    }

    @Test
    void invalidTargetsAndFileTypesNeverReachStorage() {
        Fixture fixture = new Fixture();
        fixture.item.setEsMeetingId(99L);
        assertThrows(IllegalArgumentException.class, fixture::upload);
        fixture.item.setEsMeetingId(10L);
        assertThrows(IllegalArgumentException.class, () -> fixture.service.upload(
                fixture.user, 10L, 20L, InputStream.nullInputStream(), "notes.docx", null));
        fixture.meeting.setStatus(EsMeeting.MeetingStatus.CLOSED);
        assertThrows(IllegalStateException.class, fixture::upload);
        assertFalse(fixture.storageCalled);
    }

    @Test
    void changesAreAllowedUntilClosedButNeverForCancelledOrPostponedItems() {
        EsMeeting meeting = meeting();
        EsMeetingAgendaItem item = item();
        for (EsMeeting.MeetingStatus status : EsMeeting.MeetingStatus.values()) {
            meeting.setStatus(status);
            if (status == EsMeeting.MeetingStatus.CLOSED || status == EsMeeting.MeetingStatus.CANCELLED) {
                assertThrows(IllegalStateException.class,
                        () -> MeetingAttachmentService.requireEditableTarget(meeting, item, 10L, 20L));
            } else {
                assertDoesNotThrow(() -> MeetingAttachmentService.requireEditableTarget(meeting, item, 10L, 20L));
            }
        }
        meeting.setStatus(EsMeeting.MeetingStatus.COMPLETED);
        for (EsMeetingAgendaItem.AgendaItemStatus status : EsMeetingAgendaItem.AgendaItemStatus.values()) {
            item.setStatus(status);
            if (status == EsMeetingAgendaItem.AgendaItemStatus.CANCELLED
                    || status == EsMeetingAgendaItem.AgendaItemStatus.POSTPONED) {
                assertThrows(IllegalStateException.class,
                        () -> MeetingAttachmentService.requireEditableTarget(meeting, item, 10L, 20L));
            } else {
                assertDoesNotThrow(() -> MeetingAttachmentService.requireEditableTarget(meeting, item, 10L, 20L));
            }
        }
    }

    @Test
    void forgedOrMissingTargetsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> MeetingAttachmentService.requireEditableTarget(meeting(), item(), 11L, 20L));
        assertThrows(IllegalArgumentException.class,
                () -> MeetingAttachmentService.requireEditableTarget(meeting(), item(), 10L, 21L));
        EsMeetingAgendaItem item = item();
        item.setEsMeetingId(11L);
        assertThrows(IllegalArgumentException.class,
                () -> MeetingAttachmentService.requireEditableTarget(meeting(), item, 10L, 20L));
        assertThrows(IllegalArgumentException.class,
                () -> MeetingAttachmentService.requireEditableTarget(meeting(), null, 10L, 20L));
    }

    @Test
    void meetingAllowlistIsNarrowerThanSharedStorage() {
        for (String filename : new String[] {"slides.ppt", "SLIDES.PPTX", "image.png", "a.jpeg", "a.webp", "a.gif", "a.pdf"}) {
            assertDoesNotThrow(() -> MeetingAttachmentService.requireAllowedFilename(filename));
        }
        for (String filename : new String[] {"notes.txt", "notes.doc", "notes.docx", "slides.pptm", "a.svg", "a.mp4", "a.zip"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> MeetingAttachmentService.requireAllowedFilename(filename));
        }
    }

    private static EsMeeting meeting() {
        EsMeeting meeting = new EsMeeting();
        meeting.setEsMeetingId(10L);
        meeting.setStatus(EsMeeting.MeetingStatus.IN_SESSION);
        return meeting;
    }

    private static EsMeetingAgendaItem item() {
        EsMeetingAgendaItem item = new EsMeetingAgendaItem();
        item.setEsMeetingAgendaItemId(20L);
        item.setEsMeetingId(10L);
        item.setStatus(EsMeetingAgendaItem.AgendaItemStatus.ACCEPTED);
        return item;
    }

    private static class Fixture {
        final EsMeeting meeting = meeting();
        final EsMeetingAgendaItem item = item();
        final User user = new User();
        boolean controlAllowed = true;
        boolean viewAllowed = true;
        boolean revokeDuringUpload;
        boolean storageCalled;
        boolean registered;
        boolean detached;
        int uploadCount;
        final MeetingAttachmentService service;

        Fixture() {
            user.setUserId(7L);
            service = new MeetingAttachmentService(new EsMeetingDao() {
                @Override public Optional<EsMeeting> findById(Long id) {
                    return id.equals(10L) ? Optional.of(meeting) : Optional.empty();
                }
            }, new EsMeetingAgendaItemDao() {
                @Override public Optional<EsMeetingAgendaItem> findById(Long id) {
                    return id.equals(20L) ? Optional.of(item) : Optional.empty();
                }
            }, new EsMeetingAgendaAttachmentDao() {
                @Override public StoredFile register(Long meetingId, Long itemId, Long userId, StoredFile file) {
                    assertEquals(10L, meetingId);
                    assertEquals(20L, itemId);
                    assertEquals(7L, userId);
                    registered = true;
                    return file;
                }
                @Override public void detach(Long meetingId, Long itemId, Long attachmentId, Long userId) {
                    assertEquals(10L, meetingId);
                    assertEquals(20L, itemId);
                    assertEquals(30L, attachmentId);
                    assertEquals(7L, userId);
                    detached = true;
                }
            }, new MeetingAuthorizationService() {
                @Override public boolean canControlMeeting(Long userId, EsMeeting supplied) {
                    return controlAllowed;
                }
            }, new TopicSpaceAccessService() {
                @Override public boolean canViewMeeting(User user, EsMeeting supplied) {
                    return viewAllowed;
                }
            }, new StoredFileService() {
                @Override public StoredFile upload(StoredFile existing, InputStream content, String filename,
                        String declaredType, Long uploader, boolean downloadOnly,
                        Function<StoredFile, StoredFile> register) {
                    assertNull(existing);
                    storageCalled = true;
                    uploadCount++;
                    if (revokeDuringUpload) {
                        controlAllowed = false;
                    }
                    StoredFile file = new StoredFile();
                    file.setOriginalFilename(filename);
                    file.setDownloadOnly(downloadOnly);
                    return register.apply(file);
                }
            });
        }

        StoredFile upload() throws IOException {
            return service.upload(user, 10L, 20L, new ByteArrayInputStream(new byte[] {1}), "slides.pptx", null);
        }
    }
}
