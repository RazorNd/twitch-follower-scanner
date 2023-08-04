CREATE TABLE follower_scan
(
    streamer_id varchar(50) NOT NULL,
    scan_number integer     NOT NULL,
    created_at  timestamp   NOT NULL,

    PRIMARY KEY (streamer_id, scan_number)
)