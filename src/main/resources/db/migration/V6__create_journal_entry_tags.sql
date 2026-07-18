CREATE TABLE journal_entry_tags (
    journal_entry_id UUID NOT NULL,
    tag_id           UUID NOT NULL,

    CONSTRAINT pk_journal_entry_tags    PRIMARY KEY (journal_entry_id, tag_id),
    CONSTRAINT fk_jet_journal_entry     FOREIGN KEY (journal_entry_id) REFERENCES journal_entries(id) ON DELETE CASCADE,
    CONSTRAINT fk_jet_tag               FOREIGN KEY (tag_id)           REFERENCES tags(id)            ON DELETE CASCADE
);
