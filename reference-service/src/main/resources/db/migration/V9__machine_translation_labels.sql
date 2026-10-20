-- Background machine translation: state of the translation queue as shown to staff in the service editor.
CREATE TEMPORARY TABLE machine_translation_labels (key VARCHAR(150), lang VARCHAR(64), value TEXT) ON COMMIT DROP;
INSERT INTO machine_translation_labels VALUES
('fnAdmin.autoBackground', 'en', 'Missing languages are translated automatically in the background; existing translations are never replaced.'),
('fnAdmin.autoBackground', 'ru', 'Недостающие языки переводятся автоматически в фоне; существующие переводы не заменяются.'),
('fnAdmin.autoBackground', 'uz', 'Yetishmayotgan tillar fonda avtomatik tarjima qilinadi; mavjud tarjimalar almashtirilmaydi.'),
('fnAdmin.autoBackground', 'fr', 'Les langues manquantes sont traduites automatiquement en arrière-plan ; les traductions existantes ne sont jamais remplacées.'),
('fnAdmin.autoQueued', 'en', 'Queued for automatic translation'),
('fnAdmin.autoQueued', 'ru', 'В очереди на автоматический перевод'),
('fnAdmin.autoQueued', 'uz', 'Avtomatik tarjima navbatida'),
('fnAdmin.autoQueued', 'fr', 'En attente de traduction automatique'),
('fnAdmin.autoRunning', 'en', 'Translating in the background…'),
('fnAdmin.autoRunning', 'ru', 'Перевод выполняется в фоне…'),
('fnAdmin.autoRunning', 'uz', 'Fonda tarjima qilinmoqda…'),
('fnAdmin.autoRunning', 'fr', 'Traduction en arrière-plan…'),
('fnAdmin.autoDone', 'en', 'Automatic translation finished'),
('fnAdmin.autoDone', 'ru', 'Автоматический перевод завершён'),
('fnAdmin.autoDone', 'uz', 'Avtomatik tarjima yakunlandi'),
('fnAdmin.autoDone', 'fr', 'Traduction automatique terminée'),
('fnAdmin.autoFailed', 'en', 'Automatic translation failed'),
('fnAdmin.autoFailed', 'ru', 'Автоматический перевод не удался'),
('fnAdmin.autoFailed', 'uz', 'Avtomatik tarjima amalga oshmadi'),
('fnAdmin.autoFailed', 'fr', 'La traduction automatique a échoué'),
('fnAdmin.autoSkipped', 'en', 'Automatic translation skipped'),
('fnAdmin.autoSkipped', 'ru', 'Автоматический перевод пропущен'),
('fnAdmin.autoSkipped', 'uz', 'Avtomatik tarjima o‘tkazib yuborildi'),
('fnAdmin.autoSkipped', 'fr', 'Traduction automatique ignorée'),
('fnAdmin.autoNextAttempt', 'en', 'Next attempt'),
('fnAdmin.autoNextAttempt', 'ru', 'Следующая попытка'),
('fnAdmin.autoNextAttempt', 'uz', 'Keyingi urinish'),
('fnAdmin.autoNextAttempt', 'fr', 'Prochaine tentative'),
('fnAdmin.autoAttempts', 'en', 'Attempts'),
('fnAdmin.autoAttempts', 'ru', 'Попыток'),
('fnAdmin.autoAttempts', 'uz', 'Urinishlar'),
('fnAdmin.autoAttempts', 'fr', 'Tentatives'),
('fnAdmin.autoRequeue', 'en', 'Queue again'),
('fnAdmin.autoRequeue', 'ru', 'Поставить в очередь снова'),
('fnAdmin.autoRequeue', 'uz', 'Yana navbatga qo‘yish'),
('fnAdmin.autoRequeue', 'fr', 'Remettre dans la file'),
('fnAdmin.autoRequeued', 'en', 'Queued for automatic translation'),
('fnAdmin.autoRequeued', 'ru', 'Поставлено в очередь на автоматический перевод'),
('fnAdmin.autoRequeued', 'uz', 'Avtomatik tarjima navbatiga qo‘yildi'),
('fnAdmin.autoRequeued', 'fr', 'Mis en file pour la traduction automatique');
INSERT INTO translation_keys (translation_key, required, active)
SELECT DISTINCT key, TRUE, TRUE FROM machine_translation_labels ON CONFLICT (translation_key) DO NOTHING;
INSERT INTO translations (translation_key_id, language_code, translation_value)
SELECT k.id, s.lang, s.value FROM machine_translation_labels s JOIN translation_keys k ON k.translation_key = s.key
ON CONFLICT (language_code, translation_key_id) DO NOTHING;
