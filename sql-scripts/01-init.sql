-- Включаем генерацию случайных UUID (стандартное расширение PostgreSQL)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Создаем Группу (пока без куратора, чтобы не было ошибки внешнего ключа)
INSERT INTO public.groups (id, group_name, curator_id) 
VALUES (1, 'Group-101', NULL)
ON CONFLICT (group_name) DO NOTHING;

-- 2. Создаем 5 базовых пользователей со статичными UUID
-- Пароль у всех: password (твой проверенный хэш)
INSERT INTO public.users (id, login, password_hash, name, surname, father_name, group_id) VALUES
('00000000-0000-0000-0000-000000000001', 'admin', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Алексей', 'Админов', 'Иванович', NULL),
('00000000-0000-0000-0000-000000000002', 'chef_main', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Виктор', 'Баринов', 'Петрович', NULL),
('00000000-0000-0000-0000-000000000003', 'registrator', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Анна', 'Учетова', 'Сергеевна', NULL),
('00000000-0000-0000-0000-000000000004', 'curator_Group-101', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Мария', 'Классова', 'Владимировна', NULL),
('00000000-0000-0000-0000-000000000005', 'stud_Group-101_1', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Иван', 'Первый', 'Александрович', 1)
ON CONFLICT (login) DO NOTHING;

-- 3. Назначаем куратора группе
UPDATE public.groups SET curator_id = '00000000-0000-0000-0000-000000000004' WHERE id = 1;

-- 4. Выдаем базовые роли
DELETE FROM public.user_roles WHERE user_id IN ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000005');
INSERT INTO public.user_roles (user_id, role) VALUES
('00000000-0000-0000-0000-000000000001', 'ADMIN'),
('00000000-0000-0000-0000-000000000001', 'REGISTRATOR'),
('00000000-0000-0000-0000-000000000001', 'CHEF'),
('00000000-0000-0000-0000-000000000001', 'CURATOR'),
('00000000-0000-0000-0000-000000000001', 'STUDENT'),
('00000000-0000-0000-0000-000000000002', 'CHEF'),
('00000000-0000-0000-0000-000000000003', 'REGISTRATOR'),
('00000000-0000-0000-0000-000000000004', 'CURATOR'),
('00000000-0000-0000-0000-000000000005', 'STUDENT');

-- 5. Генерируем 26 студентов (используем тот же хэш)
WITH new_students AS (
    INSERT INTO public.users (id, login, password_hash, name, surname, father_name, group_id) VALUES
    (gen_random_uuid(), 'stud_Group-101_2', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Дмитрий', 'Соколов', 'Павлович', 1),
    (gen_random_uuid(), 'stud_Group-101_3', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Екатерина', 'Попова', 'Андреевна', 1),
    (gen_random_uuid(), 'stud_Group-101_4', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Максим', 'Лебедев', 'Игоревич', 1),
    (gen_random_uuid(), 'stud_Group-101_5', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Анастасия', 'Козлова', 'Дмитриевна', 1),
    (gen_random_uuid(), 'stud_Group-101_6', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Артем', 'Новиков', 'Алексеевич', 1),
    (gen_random_uuid(), 'stud_Group-101_7', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Дарья', 'Морозова', 'Владимировна', 1),
    (gen_random_uuid(), 'stud_Group-101_8', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Илья', 'Петров', 'Николаевич', 1),
    (gen_random_uuid(), 'stud_Group-101_9', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Мария', 'Волкова', 'Сергеевна', 1),
    (gen_random_uuid(), 'stud_Group-101_10', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Никита', 'Алексеев', 'Максимович', 1),
    (gen_random_uuid(), 'stud_Group-101_11', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Виктория', 'Лебедева', 'Антоновна', 1),
    (gen_random_uuid(), 'stud_Group-101_12', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Егор', 'Семенов', 'Ильич', 1),
    (gen_random_uuid(), 'stud_Group-101_13', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Полина', 'Егорова', 'Романовна', 1),
    (gen_random_uuid(), 'stud_Group-101_14', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Денис', 'Павлов', 'Олегович', 1),
    (gen_random_uuid(), 'stud_Group-101_15', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Ксения', 'Ковалева', 'Юрьевна', 1),
    (gen_random_uuid(), 'stud_Group-101_16', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Даниил', 'Романов', 'Константинович', 1),
    (gen_random_uuid(), 'stud_Group-101_17', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Александра', 'Голубева', 'Борисовна', 1),
    (gen_random_uuid(), 'stud_Group-101_18', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Тимофей', 'Ильин', 'Викторович', 1),
    (gen_random_uuid(), 'stud_Group-101_19', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Алина', 'Гусева', 'Вячеславовна', 1),
    (gen_random_uuid(), 'stud_Group-101_20', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Владислав', 'Титов', 'Леонидович', 1),
    (gen_random_uuid(), 'stud_Group-101_21', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Валерия', 'Кузьмина', 'Денисовна', 1),
    (gen_random_uuid(), 'stud_Group-101_22', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Роман', 'Баранов', 'Тимурович', 1),
    (gen_random_uuid(), 'stud_Group-101_23', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'София', 'Куликова', 'Александровна', 1),
    (gen_random_uuid(), 'stud_Group-101_24', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Матвей', 'Борисов', 'Григорьевич', 1),
    (gen_random_uuid(), 'stud_Group-101_25', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Алиса', 'Жукова', 'Артемовна', 1),
    (gen_random_uuid(), 'stud_Group-101_26', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Арсений', 'Степанов', 'Евгеньевич', 1),
    (gen_random_uuid(), 'stud_Group-101_27', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Елизавета', 'Смирнова', 'Вадимовна', 1)
    ON CONFLICT (login) DO NOTHING
    RETURNING id
)
INSERT INTO public.user_roles (user_id, role)
SELECT id, 'STUDENT' FROM new_students;

-- 6. Меню
INSERT INTO public.menu_items (id, date, description, name, photo_url) VALUES
(gen_random_uuid(), CURRENT_DATE, 'Овсяная каша с маслом, чай', 'Завтрак стандартный', NULL),
(gen_random_uuid(), CURRENT_DATE, 'Борщ, макароны с котлетой, компот', 'Обед плотный', NULL);

-- 7. Разрешение на питание
INSERT INTO public.meal_permission (date, breakfast, dinner, lunch, is_snack_allowed, is_special_allowed, reason, assigned_by_id, student_id)
VALUES (CURRENT_DATE, true, true, true, false, false, 'Обычный рацион', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000005');

-- 8. Транзакции
INSERT INTO public.meal_transaction (offline, meal_type, meal_timestamp, transaction_hash, chef_id, student_id)
VALUES 
(false, 0, CURRENT_TIMESTAMP - interval '4 hours', 'hash_test_breakfast_123', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000005'),
(false, 1, CURRENT_TIMESTAMP - interval '1 hour', 'hash_test_lunch_123', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000005');
