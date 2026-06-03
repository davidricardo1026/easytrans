CREATE TABLE IF NOT EXISTS t_user
(
    id
    BIGINT
    PRIMARY
    KEY
    AUTO_INCREMENT,
    name
    VARCHAR
(
    64
) NOT NULL
    );

CREATE TABLE IF NOT EXISTS t_goods
(
    id
    BIGINT
    PRIMARY
    KEY
    AUTO_INCREMENT,
    name
    VARCHAR
(
    128
) NOT NULL
    );

CREATE TABLE IF NOT EXISTS t_order
(
    id
    BIGINT
    PRIMARY
    KEY
    AUTO_INCREMENT,
    user_id
    BIGINT
    NOT
    NULL
);

CREATE TABLE IF NOT EXISTS t_order_item
(
    id
    BIGINT
    PRIMARY
    KEY
    AUTO_INCREMENT,
    order_id
    BIGINT
    NOT
    NULL,
    goods_id
    BIGINT
    NOT
    NULL
);

CREATE TABLE IF NOT EXISTS t_archive_order
(
    id
    BIGINT
    PRIMARY
    KEY
    AUTO_INCREMENT,
    user_id
    BIGINT
    NOT
    NULL
);

CREATE TABLE IF NOT EXISTS t_archive_order_item
(
    id
    BIGINT
    PRIMARY
    KEY
    AUTO_INCREMENT,
    order_id
    BIGINT
    NOT
    NULL,
    goods_id
    BIGINT
    NOT
    NULL
);
