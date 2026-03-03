-- Включаем генерацию случайных UUID
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Группа
INSERT INTO public.groups (id, group_name)
VALUES (1, 'Group-101')
ON CONFLICT (group_name) DO NOTHING;

-- 2. Базовые пользователи (пароль у всех: password)
INSERT INTO public.users (id, login, password_hash, name, surname, father_name, group_id, student_category) VALUES
('00000000-0000-0000-0000-000000000001', 'admin', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Алексей', 'Админов', 'Иванович', NULL, 'SVO'),
('00000000-0000-0000-0000-000000000002', 'chef_main', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Виктор', 'Баринов', 'Петрович', NULL, NULL),
('00000000-0000-0000-0000-000000000003', 'registrator', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Анна', 'Учетова', 'Сергеевна', NULL, NULL),
('00000000-0000-0000-0000-000000000004', 'curator_Group-101', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Мария', 'Классова', 'Владимировна', NULL, NULL),
('00000000-0000-0000-0000-000000000005', 'stud_Group-101_1', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Иван', 'Первый', 'Александрович', 1, 'MANY_CHILDREN')
ON CONFLICT (login) DO NOTHING;

-- 3. Назначаем куратора группе (many-to-many)
INSERT INTO public.group_curators (group_id, curator_id)
VALUES (1, '00000000-0000-0000-0000-000000000004')
ON CONFLICT DO NOTHING;

-- 4. Роли
DELETE FROM public.user_roles WHERE user_id IN (
  '00000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000002',
  '00000000-0000-0000-0000-000000000003',
  '00000000-0000-0000-0000-000000000004',
  '00000000-0000-0000-0000-000000000005'
);

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

-- 5. Добавляем студентов
WITH new_students AS (
    INSERT INTO public.users (id, login, password_hash, name, surname, father_name, group_id, student_category) VALUES
    (gen_random_uuid(), 'stud_Group-101_2', '$2a$12$TtcYOKw5.MNmAS4BJTrn5OhMqSWqz6rYsSOPotcv37i41xD0trPKS', 'Дмитрий', 'Соколов', 'Павлович', 1, 'SVO')
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
INSERT INTO public.meal_permission (date, breakfast, lunch, reason, assigned_by_id, student_id)
VALUES (
  CURRENT_DATE,
  true,
  true,
  'Обычный рацион',
  '00000000-0000-0000-0000-000000000003',
  '00000000-0000-0000-0000-000000000005'
);

-- 8. Транзакции
INSERT INTO public.meal_transaction (offline, meal_type, meal_timestamp, transaction_hash, chef_id, student_id)
VALUES
(false, 0, CURRENT_TIMESTAMP - interval '4 hours', 'hash_test_breakfast_123', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000005'),
(false, 1, CURRENT_TIMESTAMP - interval '1 hour', 'hash_test_lunch_123', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000005');
