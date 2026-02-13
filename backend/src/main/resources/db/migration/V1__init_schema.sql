CREATE EXTENSION IF NOT EXISTS vector;

-- =====================
-- conversation
-- =====================
CREATE TABLE conversation (
    conversation_id      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              VARCHAR(255) NOT NULL,
    getstream_channel_id VARCHAR(255),
    status               VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX idx_conversation_user ON conversation(user_id);

-- =====================
-- session
-- =====================
CREATE TABLE session (
    session_id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id     UUID         NOT NULL REFERENCES conversation(conversation_id),
    status              VARCHAR(32)  NOT NULL DEFAULT 'AI_HANDLING',
    assigned_agent_id   VARCHAR(255),
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_activity_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_session_conv ON session(conversation_id);
CREATE INDEX idx_session_status ON session(status);
CREATE INDEX idx_session_activity ON session(last_activity_at);

-- =====================
-- message
-- =====================
CREATE TABLE message (
    message_id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id      UUID         NOT NULL REFERENCES conversation(conversation_id),
    session_id           UUID         NOT NULL REFERENCES session(session_id),
    sender_type          VARCHAR(32)  NOT NULL,
    sender_id            VARCHAR(255) NOT NULL,
    content              TEXT         NOT NULL,
    metadata_json        TEXT,
    getstream_message_id VARCHAR(255),
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_message_conv ON message(conversation_id);
CREATE INDEX idx_message_session ON message(session_id);
CREATE INDEX idx_message_time ON message(created_at);

-- =====================
-- user_post (Mock 数据)
-- =====================
CREATE TABLE user_post (
    post_id    SERIAL       PRIMARY KEY,
    username   VARCHAR(255) NOT NULL,
    title      VARCHAR(512) NOT NULL,
    status     VARCHAR(32)  NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_post_user ON user_post(username);

-- =====================
-- faq_doc (向量存储)
-- =====================
CREATE TABLE faq_doc (
    faq_id     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    question   TEXT          NOT NULL,
    answer     TEXT          NOT NULL,
    embedding  vector(1024),
    created_at TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_faq_embedding ON faq_doc
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 10);
