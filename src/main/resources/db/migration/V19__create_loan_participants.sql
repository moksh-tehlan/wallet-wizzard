CREATE TABLE loan_participants (
    id           UUID           NOT NULL,
    user_id      UUID           NOT NULL,
    loan_id      UUID           NOT NULL,
    person_id    UUID           NOT NULL,
    share_percent NUMERIC(5, 2) NOT NULL,
    notes        VARCHAR(255),
    created_at   TIMESTAMPTZ    NOT NULL,
    updated_at   TIMESTAMPTZ    NOT NULL,

    CONSTRAINT pk_loan_participants         PRIMARY KEY (id),
    CONSTRAINT fk_loan_participants_user    FOREIGN KEY (user_id)   REFERENCES users(id)   ON DELETE CASCADE,
    CONSTRAINT fk_loan_participants_loan    FOREIGN KEY (loan_id)   REFERENCES loans(id)   ON DELETE CASCADE,
    CONSTRAINT fk_loan_participants_person  FOREIGN KEY (person_id) REFERENCES people(id),
    CONSTRAINT uq_loan_person               UNIQUE (loan_id, person_id),
    CONSTRAINT chk_share_percent            CHECK (share_percent > 0 AND share_percent <= 100)
);

CREATE INDEX idx_loan_participants_loan   ON loan_participants (loan_id);
CREATE INDEX idx_loan_participants_person ON loan_participants (person_id);

ALTER TABLE loan_participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE loan_participants FORCE  ROW LEVEL SECURITY;

CREATE POLICY pol_loan_participants ON loan_participants FOR ALL TO walletwizzard_app
    USING     (user_id::TEXT = current_setting('app.current_user_id', true))
    WITH CHECK (user_id::TEXT = current_setting('app.current_user_id', true));
