INSERT INTO t_user (id, name)
VALUES (1, '张三'),
       (2, '李四'),
       (3, '王五');

INSERT INTO t_goods (id, name)
VALUES (101, 'MacBook Pro'),
       (102, 'iPhone 15'),
       (103, 'AirPods Pro');

INSERT INTO t_order (id, user_id)
VALUES (1, 1),
       (2, 2),
       (3, 1);

INSERT INTO t_order_item (id, order_id, goods_id)
VALUES (1, 1, 101),
       (2, 1, 102),
       (3, 2, 103),
       (4, 3, 101),
       (5, 3, 103);

INSERT INTO t_archive_order (id, user_id)
VALUES (10, 3),
       (11, 2);

INSERT INTO t_archive_order_item (id, order_id, goods_id)
VALUES (10, 10, 102),
       (11, 11, 101);
