package org.airahub.interophub.dao;

import org.airahub.interophub.model.AdminNote;

public class AdminNoteDao extends GenericDao<AdminNote, Long> {
    public AdminNoteDao() {
        super(AdminNote.class);
    }
}
