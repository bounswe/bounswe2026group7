ALTER TABLE user_notification_preferences
    ADD COLUMN feed_engagement_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN new_follower_enabled BOOLEAN NOT NULL DEFAULT TRUE;
