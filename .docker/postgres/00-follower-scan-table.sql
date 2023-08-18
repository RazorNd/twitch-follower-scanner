CREATE TABLE follower_scan
(
    streamer_id varchar(50) NOT NULL,
    scan_number integer     NOT NULL,
    created_at  timestamp   NOT NULL,

    PRIMARY KEY (streamer_id, scan_number)
);

CREATE TABLE follower
(
    streamer_id varchar(50)   NOT NULL,
    user_id     varchar(50)   NOT NULL,
    scan_number integer       NOT NULL,
    user_name   varchar(2048) NOT NULL,
    followed_at timestamp     NOT NULL,

    PRIMARY KEY (streamer_id, user_id)
);