set session cte_max_recursion_depth = 200000;
set @host_id = 900000;
set @first_user_id = 900001;
set @last_user_id = 1071000;
set @popup_store_id = 900000;
set @slot_id = 900000;
set @capacity = 999999;

delete from reservation
where slot_id = @slot_id
   or user_id between @first_user_id and @last_user_id;
delete from reservation_slot where id = @slot_id;
delete from popup_store where id = @popup_store_id;
delete from users
where id = @host_id or id between @first_user_id and @last_user_id;

insert into users (id, email, name, created_at, modified_at)
values (@host_id, 'load-host@test.com', 'load-host', now(), now());

insert into popup_store (
    id, user_id, title, location, fee_type, price,
    reservation_start_at, reservation_end_at, open_date, close_date,
    image_key, description, created_at, modified_at
)
values (
    @popup_store_id, @host_id, '부하테스트 팝업', '서울 테스트구', 'FREE', null,
    date_sub(now(), interval 1 day), date_add(now(), interval 7 day),
    date_sub(now(), interval 1 day), date_add(now(), interval 7 day),
    'load-test.jpg', '부하테스트용 팝업', now(), now()
);

insert into reservation_slot (
    id, popup_store_id, slot_date, start_time, capacity, reserved_count,
    created_at, modified_at
)
values (
    @slot_id, @popup_store_id, date_add(curdate(), interval 1 day),
    '10:00:00', @capacity, 0, now(), now()
);

insert into users (id, email, name, created_at, modified_at)
with recursive seq(n) as (
    select @first_user_id
    union all
    select n + 1 from seq where n < @last_user_id
)
select n, concat('load-user-', n, '@test.com'),
       concat('load-user-', n), now(), now()
from seq;
