-- Release hardening: uniqueness + performance indexes for meal flows.

DO $$
BEGIN
    IF to_regclass('public.meal_transaction') IS NOT NULL THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uk_meal_transaction_hash
            ON public.meal_transaction (transaction_hash);

        CREATE INDEX IF NOT EXISTS idx_meal_tx_student_time_meal
            ON public.meal_transaction (student_id, meal_timestamp, meal_type);

        CREATE INDEX IF NOT EXISTS idx_meal_tx_hash
            ON public.meal_transaction (transaction_hash);
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('public.meal_permission') IS NOT NULL THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uk_meal_permission_student_date
            ON public.meal_permission (student_id, date);

        CREATE INDEX IF NOT EXISTS idx_meal_permission_date_student
            ON public.meal_permission (date, student_id);
    END IF;
END $$;
