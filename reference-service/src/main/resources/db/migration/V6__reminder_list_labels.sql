CREATE TEMPORARY TABLE reminder_list_labels (key VARCHAR(150), lang VARCHAR(64), value TEXT) ON COMMIT DROP;
INSERT INTO reminder_list_labels VALUES
('reminders.showAll', 'en', 'Show all reminders'),
('reminders.showAll', 'ru', 'Показать все напоминания'),
('reminders.showAll', 'uz', 'Barcha eslatmalarni ko‘rsatish'),
('reminders.showAll', 'fr', 'Afficher tous les rappels'),
('reminders.limited', 'en', 'Only the 200 newest reminders are listed. Narrow the filters to see the others.'),
('reminders.limited', 'ru', 'Показаны только 200 самых новых напоминаний. Сузьте фильтры, чтобы увидеть остальные.'),
('reminders.limited', 'uz', 'Faqat eng yangi 200 ta eslatma ko‘rsatilgan. Qolganlarini ko‘rish uchun filtrlarni toraytiring.'),
('reminders.limited', 'fr', 'Seuls les 200 rappels les plus récents sont affichés. Affinez les filtres pour voir les autres.');
INSERT INTO translation_keys (translation_key, required, active)
SELECT DISTINCT key, TRUE, TRUE FROM reminder_list_labels ON CONFLICT (translation_key) DO NOTHING;
INSERT INTO translations (translation_key_id, language_code, translation_value)
SELECT k.id, s.lang, s.value FROM reminder_list_labels s JOIN translation_keys k ON k.translation_key = s.key
ON CONFLICT (language_code, translation_key_id) DO NOTHING;
