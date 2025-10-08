create table person (
    person_id SERIAL PRIMARY KEY NOT NULL,
    person_login TEXT NOT NULL unique,
    person_password TEXT NOT NULL,
    person_email TEXT,
    person_phone TEXT,
    subscription_end_date TIMESTAMP,
    telegram_chat_id BIGINT unique
);